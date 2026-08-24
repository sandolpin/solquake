package com.sandolpin.weatherquake.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.sandolpin.weatherquake.data.BoundingBox
import com.sandolpin.weatherquake.data.GeoCoordinateUtil
import com.sandolpin.weatherquake.data.GeoJsonLoader
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.eew.JmaEew
import kotlin.math.cos
import kotlin.math.min

/**
 * 通知(NotificationCompat.BigPictureStyle)に添付する地図画像を、
 * Compose版の地図(WarnAreaMap)と同じ見た目のロジックでandroid.graphics.Bitmapとして描画する。
 * NotificationHelperはCompose UIツリーの外(Service/バックグラウンド)から呼ばれるため、
 * ここでは素のandroid.graphics APIで描画する。
 */
object WarnAreaMapRenderer {

    private const val MAP_WIDTH_PX = 720
    private const val MAP_HEIGHT_PX = 360

    /** 緊急地震速報(EEW)用: 警戒エリア・震源位置をもとに地図Bitmapを生成する */
    fun renderForEew(context: Context, eew: JmaEew): Bitmap? {
        val epicenterLon = eew.Longitude
        val epicenterLat = eew.Latitude
        val warnAreas = eew.WarnArea.orEmpty()

        if (epicenterLon == 0.0 && epicenterLat == 0.0 && warnAreas.isEmpty()) return null

        val intensityByName = LinkedHashMap<String, IntensityLevel>()
        warnAreas.forEach { area ->
            intensityByName.putIfAbsent(area.Chiiki, IntensityLevel.fromApiString(area.Shindo1))
        }
        return renderCore(context, intensityByName, epicenterLon, epicenterLat, eew.isAssumption)
    }

    /** 地震情報(P2P)用: 観測地点の震度をもとに地図Bitmapを生成する */
    fun renderForQuake(
        context: Context,
        epicenterLon: Double,
        epicenterLat: Double,
        pointNameToScale: Map<String, IntensityLevel>
    ): Bitmap? {
        if (epicenterLon == 0.0 && epicenterLat == 0.0 && pointNameToScale.isEmpty()) return null
        return renderCore(context, LinkedHashMap(pointNameToScale), epicenterLon, epicenterLat, isAssumption = false)
    }

    private fun renderCore(
        context: Context,
        intensityByName: LinkedHashMap<String, IntensityLevel>,
        epicenterLon: Double,
        epicenterLat: Double,
        isAssumption: Boolean
    ): Bitmap? {
        val prepared = try {
            GeoJsonLoader.preparedFeatures(context)
        } catch (e: Exception) {
            return null
        }

        val names = intensityByName.keys.toList()
        val matchedFeatures = prepared.filter { it.name in names }
        val colored = matchedFeatures.mapNotNull { feature ->
            if (feature.polygons.isEmpty()) return@mapNotNull null
            val level = intensityByName[feature.name] ?: IntensityLevel.UNKNOWN
            feature.polygons to level.bgColor.toArgb()
        }

        val coloredBbox = GeoCoordinateUtil.boundingBox(colored.flatMap { it.first })
        val bbox = if (coloredBbox != null) {
            BoundingBox(
                minLon = minOf(coloredBbox.minLon, epicenterLon),
                maxLon = maxOf(coloredBbox.maxLon, epicenterLon),
                minLat = minOf(coloredBbox.minLat, epicenterLat),
                maxLat = maxOf(coloredBbox.maxLat, epicenterLat)
            )
        } else {
            val soloSpanDegrees = 1.2
            BoundingBox(
                minLon = epicenterLon - soloSpanDegrees, maxLon = epicenterLon + soloSpanDegrees,
                minLat = epicenterLat - soloSpanDegrees, maxLat = epicenterLat + soloSpanDegrees
            )
        }

        val lonPad = (bbox.maxLon - bbox.minLon).coerceAtLeast(0.08) * 0.8 + 0.3
        val latPad = (bbox.maxLat - bbox.minLat).coerceAtLeast(0.08) * 0.8 + 0.3
        val viewMinLon = bbox.minLon - lonPad
        val viewMaxLon = bbox.maxLon + lonPad
        val viewMinLat = bbox.minLat - latPad
        val viewMaxLat = bbox.maxLat + latPad

        val matchedNameSet = matchedFeatures.map { it.name }.toSet()
        val basePolygons = prepared.mapNotNull { feature ->
            if (feature.name in matchedNameSet) return@mapNotNull null
            if (feature.polygons.isEmpty()) return@mapNotNull null
            val featureBbox = feature.bbox ?: return@mapNotNull null
            val intersects = featureBbox.minLon <= viewMaxLon && featureBbox.maxLon >= viewMinLon &&
                    featureBbox.minLat <= viewMaxLat && featureBbox.maxLat >= viewMinLat
            if (!intersects) return@mapNotNull null
            feature.polygons
        }.flatten()

        return draw(bbox, basePolygons, colored, epicenterLon, epicenterLat, isAssumption, isSystemDarkMode(context))
    }

    private fun draw(
        bbox: BoundingBox,
        basePolygons: List<FloatArray>,
        coloredPolygons: List<Pair<List<FloatArray>, Int>>,
        epicenterLon: Double,
        epicenterLat: Double,
        isAssumption: Boolean,
        isDark: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(MAP_WIDTH_PX, MAP_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundColor = if (isDark) Color.parseColor("#2C2C2C") else Color.parseColor("#F0F0F0")
        canvas.drawColor(backgroundColor)

        val baseFillColor = if (isDark) Color.parseColor("#3A3A3A") else Color.WHITE
        val borderColor = if (isDark) Color.parseColor("#8A8A8A") else Color.parseColor("#9E9E9E")

        val latCorrection = cos(Math.toRadians(bbox.centerLat)).coerceAtLeast(0.3)
        val paddingRatio = 0.35
        val rawLonSpan = (bbox.maxLon - bbox.minLon).coerceAtLeast(0.08)
        val rawLatSpan = (bbox.maxLat - bbox.minLat).coerceAtLeast(0.08)
        val lonSpanMeters = rawLonSpan * latCorrection
        val latSpanMeters = rawLatSpan
        val spanMeters = maxOf(lonSpanMeters, latSpanMeters) * (1 + paddingRatio)
        val scale = (min(MAP_WIDTH_PX, MAP_HEIGHT_PX) / spanMeters).toFloat()

        fun project(lon: Double, lat: Double): PointF {
            val xMeters = (lon - bbox.centerLon) * latCorrection
            val yMeters = lat - bbox.centerLat
            val x = MAP_WIDTH_PX / 2f + xMeters.toFloat() * scale
            val y = MAP_HEIGHT_PX / 2f - yMeters.toFloat() * scale
            return PointF(x, y)
        }

        fun ringToPath(ring: FloatArray): Path? {
            val pointCount = ring.size / 2
            if (pointCount < 3) return null
            val path = Path()
            val start = project(ring[0].toDouble(), ring[1].toDouble())
            path.moveTo(start.x, start.y)
            var i = 1
            while (i < pointCount) {
                val p = project(ring[i * 2].toDouble(), ring[i * 2 + 1].toDouble())
                path.lineTo(p.x, p.y)
                i++
            }
            path.close()
            return path
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        basePolygons.forEach { ring ->
            val path = ringToPath(ring) ?: return@forEach
            fillPaint.color = baseFillColor
            canvas.drawPath(path, fillPaint)
            strokePaint.color = borderColor
            strokePaint.strokeWidth = 2f
            canvas.drawPath(path, strokePaint)
        }

        coloredPolygons.forEach { (polygons, colorInt) ->
            polygons.forEach { ring ->
                val path = ringToPath(ring) ?: return@forEach
                fillPaint.color = ColorUtils.setAlphaComponent(colorInt, (0.85f * 255).toInt())
                canvas.drawPath(path, fillPaint)
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = 3.5f
                canvas.drawPath(path, strokePaint)
            }
        }

        val markerCenter = project(epicenterLon, epicenterLat)
        drawEpicenterMarker(canvas, markerCenter, isAssumption)

        return bitmap
    }

    private fun drawEpicenterMarker(canvas: Canvas, center: PointF, isAssumption: Boolean) {
        val shadowDx = 3f
        val shadowDy = 3.5f
        val shadowColor = ColorUtils.setAlphaComponent(Color.BLACK, (0.4f * 255).toInt())
        val outlineColor = Color.WHITE
        val fillColor = Color.parseColor("#E53935")

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isAssumption) {
            val radius = 16f
            paint.style = Paint.Style.FILL
            paint.color = shadowColor
            canvas.drawCircle(center.x + shadowDx, center.y + shadowDy, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5.5f
            paint.color = outlineColor
            canvas.drawCircle(center.x, center.y, radius, paint)

            paint.strokeWidth = 4.5f
            paint.color = fillColor
            canvas.drawCircle(center.x, center.y, radius - 2.75f, paint)
        } else {
            // 通知の地図画像(720x360px)に対して以前は×印が大きすぎたため、約半分のサイズに縮小した
            val armLength = 20f
            val outlineWidth = 11f
            val fillWidth = 6f

            fun crossPath(cx: Float, cy: Float): Path = Path().apply {
                moveTo(cx - armLength, cy - armLength)
                lineTo(cx + armLength, cy + armLength)
                moveTo(cx + armLength, cy - armLength)
                lineTo(cx - armLength, cy + armLength)
            }

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND

            paint.strokeWidth = fillWidth
            paint.color = shadowColor
            canvas.drawPath(crossPath(center.x + shadowDx, center.y + shadowDy), paint)

            paint.strokeWidth = outlineWidth
            paint.color = outlineColor
            canvas.drawPath(crossPath(center.x, center.y), paint)

            paint.strokeWidth = fillWidth
            paint.color = fillColor
            canvas.drawPath(crossPath(center.x, center.y), paint)
        }
    }

    private fun isSystemDarkMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}