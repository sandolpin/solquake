package com.sandolpin.weatherquake.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.sandolpin.weatherquake.data.GeoJsonLoader
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.eew.JmaEew
import com.sandolpin.weatherquake.map.EpicenterIconFactory
import com.sandolpin.weatherquake.map.MapCameraBoundsHelper
import com.sandolpin.weatherquake.map.MapLibreStyleFactory
import com.sandolpin.weatherquake.map.isValidLatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

/**
 * 通知(NotificationCompat.BigPictureStyle)に添付する地図画像を、
 * MapLibreのMapSnapshotter(画面表示せずBitmapだけを生成するAPI)で描画する。
 *
 * [重要] MapSnapshotterはメインスレッド(Looperのあるスレッド)からの生成・start()呼び出しを
 * 前提としているAPIのため、このオブジェクトの関数はすべてsuspend funにしてある。
 * 呼び出し元(NotificationHelper)もsuspend化する必要がある。
 *
 * 震源マーカー(×印/○印)はMapSnapshotter単体では重ねられないため、
 * スナップショット取得後にandroid.graphics.Canvasで自前合成している
 * (点滅は不要な静止画のため、ここでは常に不透明で描く)。
 *
 * [震源マーカーのサイズについて]
 * 通知画像は小さいため、EpicenterIconFactory側のビットマップ拡大に合わせて
 * 合成先のサイズ(dstSize)も64px→96pxに拡大し、通知上でも視認しやすくしている。
 */
object WarnAreaMapRenderer {

    private const val MAP_WIDTH_PX = 720
    private const val MAP_HEIGHT_PX = 360
    private const val SNAPSHOT_TIMEOUT_MS = 8000L
    private const val TAG = "WarnAreaMapRenderer"

    suspend fun renderForEew(context: Context, eew: JmaEew): Bitmap? {
        val epicenterLon = eew.Longitude
        val epicenterLat = eew.Latitude
        val warnAreas = eew.WarnArea.orEmpty()
        if (epicenterLon == 0.0 && epicenterLat == 0.0 && warnAreas.isEmpty()) return null

        val intensityByName = LinkedHashMap<String, IntensityLevel>()
        warnAreas.forEach { area ->
            intensityByName.putIfAbsent(area.Chiiki, IntensityLevel.fromApiString(area.Shindo1))
        }

        return renderCore(
            context = context,
            regionIntensityByName = intensityByName,
            epicenterLon = epicenterLon,
            epicenterLat = epicenterLat,
            isAssumption = eew.isAssumption
        )
    }

    suspend fun renderForQuake(
        context: Context,
        epicenterLon: Double,
        epicenterLat: Double,
        pointNameToScale: Map<String, IntensityLevel>
    ): Bitmap? {
        if (epicenterLon == 0.0 && epicenterLat == 0.0 && pointNameToScale.isEmpty()) return null
        return renderCore(
            context = context,
            regionIntensityByName = LinkedHashMap(pointNameToScale),
            epicenterLon = epicenterLon,
            epicenterLat = epicenterLat,
            isAssumption = false
        )
    }

    private suspend fun renderCore(
        context: Context,
        regionIntensityByName: Map<String, IntensityLevel>,
        epicenterLon: Double,
        epicenterLat: Double,
        isAssumption: Boolean
    ): Bitmap? = try {
        // P2P地震情報API等が「震源不明」を-200のようなセンチネル値で表すことがあるため、
        // 地図座標として使う前に必ず範囲チェックする(そのままLatLngに渡すとクラッシュする)。
        val hasValidEpicenter = isValidLatLng(epicenterLat, epicenterLon)
        val boundsEpicenterLat = if (hasValidEpicenter) epicenterLat else 36.0
        val boundsEpicenterLon = if (hasValidEpicenter) epicenterLon else 138.0

        val bounds = MapCameraBoundsHelper.compute(
            context = context,
            loader = GeoJsonLoader.region,
            coloredNames = regionIntensityByName.keys,
            epicenterLon = boundsEpicenterLon,
            epicenterLat = boundsEpicenterLat
        )

        val styleBuilder = MapLibreStyleFactory.build(context, regionIntensityByName)

        val rawSnapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
            takeSnapshot(context, styleBuilder, bounds)
        }
        if (rawSnapshot == null) {
            Log.w(TAG, "スナップショット取得に失敗またはタイムアウトしました(${SNAPSHOT_TIMEOUT_MS}ms)")
            null
        } else if (!hasValidEpicenter) {
            // 震源座標が不明な場合は震源マーカーを合成せず、スナップショットのBitmapをそのまま返す
            rawSnapshot.bitmap
        } else {
            compositeEpicenterMarker(
                snapshotResult = rawSnapshot,
                epicenterLon = epicenterLon,
                epicenterLat = epicenterLat,
                isAssumption = isAssumption
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "地図Bitmapの生成中に例外が発生しました", e)
        null
    }

    /**
     * MapSnapshotterはメインスレッドで生成・start()する必要があるため、
     * withContext(Dispatchers.Main)に切り替えた上でsuspendCancellableCoroutineで
     * コールバックAPIを橋渡しする。
     */
    private suspend fun takeSnapshot(
        context: Context,
        styleBuilder: org.maplibre.android.maps.Style.Builder,
        bounds: org.maplibre.android.geometry.LatLngBounds
    ): SnapshotResult? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<SnapshotResult?> { continuation ->
            val options = MapSnapshotter.Options(MAP_WIDTH_PX, MAP_HEIGHT_PX)
                .withStyleBuilder(styleBuilder)
                .withRegion(bounds)
                .withLogo(false)

            val snapshotter = MapSnapshotter(context, options)

            // [要検証] start()の引数(SnapshotReadyCallback / ErrorHandler)はJavaのfunctional
            // interfaceなのでKotlinからラムダで渡せるはずだが、SDKバージョンによっては
            // object : MapSnapshotter.SnapshotReadyCallback { override fun onSnapshotReady(...) }
            // のような明示的な実装が必要になることがある。ビルドエラーが出た場合はここを確認。
            snapshotter.start(
                { snapshot ->
                    if (continuation.isActive) {
                        continuation.resume(SnapshotResult(snapshot.bitmap, snapshot))
                    }
                },
                { error ->
                    Log.w(TAG, "MapSnapshotterがエラーを返しました: $error")
                    if (continuation.isActive) continuation.resume(null)
                }
            )

            continuation.invokeOnCancellation {
                runCatching { snapshotter.cancel() }
            }
        }
    }

    /**
     * MapSnapshotterで得たBitmapの上に、震源マーカー(×印/○印)を合成する。
     * MapSnapshot.pixelForLatLng()を使って、緯度経度→Bitmap上のピクセル座標に変換する。
     */
    private fun compositeEpicenterMarker(
        snapshotResult: SnapshotResult,
        epicenterLon: Double,
        epicenterLat: Double,
        isAssumption: Boolean
    ): Bitmap {
        val bitmap = snapshotResult.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(bitmap)

        val point = runCatching {
            snapshotResult.snapshot.pixelForLatLng(LatLng(epicenterLat, epicenterLon))
        }.getOrNull()

        if (point != null) {
            val icon = if (isAssumption) EpicenterIconFactory.assumptionIcon() else EpicenterIconFactory.crossIcon()
            val dstSize = 96
            val dst = android.graphics.RectF(
                point.x - dstSize / 2f,
                point.y - dstSize / 2f,
                point.x + dstSize / 2f,
                point.y + dstSize / 2f
            )
            canvas.drawBitmap(icon, null, dst, null)
        }

        return bitmap
    }

    private data class SnapshotResult(
        val bitmap: Bitmap,
        val snapshot: org.maplibre.android.snapshotter.MapSnapshot
    )
}