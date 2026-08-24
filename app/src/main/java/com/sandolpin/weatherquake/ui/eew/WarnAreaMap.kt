package com.sandolpin.weatherquake.ui.eew

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.sandolpin.weatherquake.data.BoundingBox
import com.sandolpin.weatherquake.data.GeoCoordinateUtil
import com.sandolpin.weatherquake.data.GeoJsonLoader
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.eew.WarnArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

internal const val MAP_HEIGHT_DP = 220
internal const val BASE_RING_MAX_POINTS = 80
internal const val COLORED_RING_MAX_POINTS = 200
internal const val BLINK_INTERVAL_MS = 500L
private const val MAP_DATA_CACHE_LIMIT = 12
private const val MAP_BITMAP_CACHE_LIMIT = 8

@Immutable
internal data class ColoredPolygons(val polygons: List<FloatArray>, val color: Color)

@Immutable
private data class MapData(
    val coloredPolygons: List<ColoredPolygons>,
    val basePolygons: List<FloatArray>,
    val bbox: BoundingBox,
    val epicenterLon: Double,
    val epicenterLat: Double
)

@Immutable
internal data class Projection(val bbox: BoundingBox, val latCorrection: Double, val spanMeters: Double)

@Immutable
private data class RenderedMap(
    val bitmap: Bitmap,
    val projection: Projection,
    val epicenterLon: Double,
    val epicenterLat: Double
)

private object MapDataCache {
    private val cache = object : LinkedHashMap<String, MapData>(MAP_DATA_CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MapData>?): Boolean =
            size > MAP_DATA_CACHE_LIMIT
    }

    @Synchronized fun get(key: String): MapData? = cache[key]
    @Synchronized fun put(key: String, value: MapData) { cache[key] = value }
}

private object MapBitmapCache {
    private val cache = object : LinkedHashMap<String, Bitmap>(MAP_BITMAP_CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAP_BITMAP_CACHE_LIMIT
    }

    @Synchronized fun get(key: String): Bitmap? = cache[key]
    @Synchronized fun put(key: String, value: Bitmap) { cache[key] = value }
}

private fun buildCacheKey(intensityByName: Map<String, IntensityLevel>, lon: Double, lat: Double): String {
    val namesPart = intensityByName.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.name}" }
    val lonRounded = (lon * 100).roundToInt()
    val latRounded = (lat * 100).roundToInt()
    return "$namesPart|$lonRounded|$latRounded"
}

/**
 * WarnArea[].Chiiki を assets/japan.geojson の properties.name と突き合わせ、
 * 一致したFeatureを震度色で塗りつぶして表示する地図。震源位置には×印(PLUM法は〇印)を点滅表示する。
 *
 * 陸地の塗りつぶし・縁取りはバックグラウンドスレッドで一度だけBitmapとして描き上げ、
 * Compose側はその完成画像をImageとして貼るだけにすることで、地図描画の重さを吸収している。
 * 震源マーカーだけは点滅させる必要があるため、画像の上に軽量なCanvasを重ねる構成。
 */
@Composable
fun WarnAreaMap(
    warnAreas: List<WarnArea>,
    latitude: Double,
    longitude: Double,
    isAssumption: Boolean,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val intensityByName = remember(warnAreas) {
        val map = LinkedHashMap<String, IntensityLevel>()
        warnAreas.forEach { area -> map.putIfAbsent(area.Chiiki, IntensityLevel.fromApiString(area.Shindo1)) }
        map
    }

    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { size ->
                widthPx = size.width.coerceAtLeast(1)
                heightPx = size.height.coerceAtLeast(1)
            }
    ) {
        if (widthPx == 0 || heightPx == 0) return@Box

        val rendered by produceState<RenderedMap?>(initialValue = null, intensityByName, latitude, longitude, isDark, widthPx, heightPx) {
            value = withContext(Dispatchers.Default) {
                try {
                    val dataKey = buildCacheKey(intensityByName, longitude, latitude)
                    val mapData = MapDataCache.get(dataKey)
                        ?: buildMapData(context, intensityByName, longitude, latitude)?.also { MapDataCache.put(dataKey, it) }
                        ?: return@withContext null

                    val projection = buildProjection(mapData.bbox)
                    val bitmapKey = "$dataKey|${widthPx}x$heightPx|${if (isDark) "d" else "l"}"
                    val bitmap = MapBitmapCache.get(bitmapKey)
                        ?: renderMapBitmap(mapData.basePolygons, mapData.coloredPolygons, projection, isDark, widthPx, heightPx)
                            .also { MapBitmapCache.put(bitmapKey, it) }

                    RenderedMap(bitmap, projection, mapData.epicenterLon, mapData.epicenterLat)
                } catch (e: Exception) {
                    null
                }
            }
        }

        val result = rendered ?: return@Box

        LaunchedEffect(result) { onReady() }

        var markerVisible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(BLINK_INTERVAL_MS)
                markerVisible = !markerVisible
            }
        }

        Image(
            bitmap = remember(result.bitmap) { result.bitmap.asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        EpicenterMarkerCanvas(
            projection = result.projection,
            epicenterLon = result.epicenterLon,
            epicenterLat = result.epicenterLat,
            isAssumption = isAssumption,
            visible = markerVisible
        )
    }
}

private fun buildMapData(
    context: android.content.Context,
    intensityByName: Map<String, IntensityLevel>,
    epicenterLon: Double,
    epicenterLat: Double
): MapData? {
    if (epicenterLon == 0.0 && epicenterLat == 0.0) return null

    val names = intensityByName.keys.toList()
    val prepared = GeoJsonLoader.preparedFeatures(context)

    val matchedFeatures = prepared.filter { it.name in names }
    val colored = matchedFeatures.mapNotNull { feature ->
        if (feature.polygons.isEmpty()) return@mapNotNull null
        val level = intensityByName[feature.name] ?: IntensityLevel.UNKNOWN
        val decimatedPolygons = feature.polygons.map { decimateRing(it, COLORED_RING_MAX_POINTS) }
        ColoredPolygons(decimatedPolygons, level.bgColor)
    }

    val coloredBbox = GeoCoordinateUtil.boundingBox(colored.flatMap { it.polygons })
    val warnBbox = if (coloredBbox != null) {
        BoundingBox(
            minLon = minOf(coloredBbox.minLon, epicenterLon), maxLon = maxOf(coloredBbox.maxLon, epicenterLon),
            minLat = minOf(coloredBbox.minLat, epicenterLat), maxLat = maxOf(coloredBbox.maxLat, epicenterLat)
        )
    } else {
        val soloSpanDegrees = 1.2
        BoundingBox(
            minLon = epicenterLon - soloSpanDegrees, maxLon = epicenterLon + soloSpanDegrees,
            minLat = epicenterLat - soloSpanDegrees, maxLat = epicenterLat + soloSpanDegrees
        )
    }

    val lonPad = (warnBbox.maxLon - warnBbox.minLon).coerceAtLeast(0.08) * 0.8 + 0.3
    val latPad = (warnBbox.maxLat - warnBbox.minLat).coerceAtLeast(0.08) * 0.8 + 0.3
    val viewMinLon = warnBbox.minLon - lonPad
    val viewMaxLon = warnBbox.maxLon + lonPad
    val viewMinLat = warnBbox.minLat - latPad
    val viewMaxLat = warnBbox.maxLat + latPad

    val matchedNameSet = matchedFeatures.map { it.name }.toSet()
    val basePolygons = prepared.mapNotNull { feature ->
        if (feature.name in matchedNameSet) return@mapNotNull null
        if (feature.polygons.isEmpty()) return@mapNotNull null
        val featureBbox = feature.bbox ?: return@mapNotNull null
        val intersects = featureBbox.minLon <= viewMaxLon && featureBbox.maxLon >= viewMinLon &&
                featureBbox.minLat <= viewMaxLat && featureBbox.maxLat >= viewMinLat
        if (!intersects) return@mapNotNull null
        feature.polygons
    }.flatten().map { decimateRing(it, BASE_RING_MAX_POINTS) }

    return MapData(colored, basePolygons, warnBbox, epicenterLon, epicenterLat)
}

internal fun decimateRing(ring: FloatArray, maxPoints: Int): FloatArray {
    val pointCount = ring.size / 2
    if (pointCount <= maxPoints) return ring

    val stride = Math.ceil(pointCount.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val outCount = (pointCount + stride - 1) / stride
    val result = FloatArray(outCount * 2 + 2)
    var writeIdx = 0
    var i = 0
    while (i < pointCount) {
        result[writeIdx] = ring[i * 2]
        result[writeIdx + 1] = ring[i * 2 + 1]
        writeIdx += 2
        i += stride
    }
    val lastIdx = pointCount - 1
    if (lastIdx % stride != 0) {
        result[writeIdx] = ring[lastIdx * 2]
        result[writeIdx + 1] = ring[lastIdx * 2 + 1]
        writeIdx += 2
    }
    return if (writeIdx == result.size) result else result.copyOf(writeIdx)
}

internal fun buildProjection(bbox: BoundingBox): Projection {
    val latCorrection = cos(Math.toRadians(bbox.centerLat)).coerceAtLeast(0.3)
    val paddingRatio = 0.35
    val rawLonSpan = (bbox.maxLon - bbox.minLon).coerceAtLeast(0.08)
    val rawLatSpan = (bbox.maxLat - bbox.minLat).coerceAtLeast(0.08)
    val lonSpanMeters = rawLonSpan * latCorrection
    val latSpanMeters = rawLatSpan
    val spanMeters = max(lonSpanMeters, latSpanMeters) * (1 + paddingRatio)
    return Projection(bbox, latCorrection, spanMeters)
}

internal fun Projection.project(canvasWidth: Float, canvasHeight: Float, lon: Double, lat: Double): Offset {
    val scale = minOf(canvasWidth, canvasHeight) / spanMeters.toFloat()
    val xMeters = (lon - bbox.centerLon) * latCorrection
    val yMeters = lat - bbox.centerLat
    val x = canvasWidth / 2f + xMeters.toFloat() * scale
    val y = canvasHeight / 2f - yMeters.toFloat() * scale
    return Offset(x, y)
}

internal fun renderMapBitmap(
    basePolygons: List<FloatArray>,
    coloredPolygons: List<ColoredPolygons>,
    projection: Projection,
    isDark: Boolean,
    widthPx: Int,
    heightPx: Int
): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val backgroundColor = if (isDark) AndroidColor.parseColor("#2C2C2C") else AndroidColor.parseColor("#F0F0F0")
    canvas.drawColor(backgroundColor)

    val baseFillColor = if (isDark) AndroidColor.parseColor("#3A3A3A") else AndroidColor.WHITE
    val borderColor = if (isDark) AndroidColor.parseColor("#8A8A8A") else AndroidColor.parseColor("#9E9E9E")

    val fillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.FILL }
    val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.STROKE }

    val w = widthPx.toFloat()
    val h = heightPx.toFloat()

    fun ringToAndroidPath(ring: FloatArray): AndroidPath? {
        val pointCount = ring.size / 2
        if (pointCount < 3) return null
        val path = AndroidPath()
        val start = projection.project(w, h, ring[0].toDouble(), ring[1].toDouble())
        path.moveTo(start.x, start.y)
        var i = 1
        while (i < pointCount) {
            val p = projection.project(w, h, ring[i * 2].toDouble(), ring[i * 2 + 1].toDouble())
            path.lineTo(p.x, p.y)
            i++
        }
        path.close()
        return path
    }

    basePolygons.forEach { ring ->
        val path = ringToAndroidPath(ring) ?: return@forEach
        fillPaint.color = baseFillColor
        canvas.drawPath(path, fillPaint)
        strokePaint.color = borderColor
        strokePaint.strokeWidth = 1.5f
        canvas.drawPath(path, strokePaint)
    }

    coloredPolygons.forEach { colored ->
        val colorInt = colored.color.toArgb()
        colored.polygons.forEach { ring ->
            val path = ringToAndroidPath(ring) ?: return@forEach
            fillPaint.color = ColorUtils.setAlphaComponent(colorInt, (0.85f * 255).toInt())
            canvas.drawPath(path, fillPaint)
            strokePaint.color = AndroidColor.WHITE
            strokePaint.strokeWidth = 2.5f
            canvas.drawPath(path, strokePaint)
        }
    }

    return bitmap
}

@Composable
internal fun EpicenterMarkerCanvas(
    projection: Projection,
    epicenterLon: Double,
    epicenterLat: Double,
    isAssumption: Boolean,
    visible: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (!visible) return@Canvas
        val center = projection.project(size.width, size.height, epicenterLon, epicenterLat)
        drawEpicenterMarker(center = center, isAssumption = isAssumption)
    }
}

internal fun DrawScope.drawEpicenterMarker(center: Offset, isAssumption: Boolean) {
    val shadowOffset = Offset(4f, 5f)
    val shadowColor = Color.Black.copy(alpha = 0.4f)
    val outlineColor = Color.White
    val fillColor = Color(0xFFE53935)

    if (isAssumption) {
        val radius = 20f
        drawCircle(color = shadowColor, radius = radius, center = center + shadowOffset)
        drawCircle(color = outlineColor, radius = radius, center = center, style = Stroke(width = 7f))
        drawCircle(color = fillColor, radius = radius - 3.5f, center = center, style = Stroke(width = 6f))
    } else {
        val armLength = 26f
        val outlineWidth = 15f
        val fillWidth = 8f

        fun crossPath(c: Offset): Path = Path().apply {
            moveTo(c.x - armLength, c.y - armLength)
            lineTo(c.x + armLength, c.y + armLength)
            moveTo(c.x + armLength, c.y - armLength)
            lineTo(c.x - armLength, c.y + armLength)
        }

        drawPath(crossPath(center + shadowOffset), color = shadowColor, style = Stroke(width = fillWidth, cap = StrokeCap.Round))
        drawPath(crossPath(center), color = outlineColor, style = Stroke(width = outlineWidth, cap = StrokeCap.Round))
        drawPath(crossPath(center), color = fillColor, style = Stroke(width = fillWidth, cap = StrokeCap.Round))
    }
}