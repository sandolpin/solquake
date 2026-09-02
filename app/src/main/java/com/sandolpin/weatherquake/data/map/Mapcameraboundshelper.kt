package com.sandolpin.weatherquake.map

import android.content.Context
import com.sandolpin.weatherquake.data.BoundingBox
import com.sandolpin.weatherquake.data.GeoJsonLoader
import org.maplibre.android.geometry.LatLngBounds

/**
 * 震源位置と、着色されたエリア名一覧から、地図に表示すべき範囲(LatLngBounds)を計算する。
 * 旧WarnAreaMapRenderer.renderCore()内にあったbbox計算(パディングの付け方含む)を
 * そのまま踏襲している。
 */
object MapCameraBoundsHelper {

    fun compute(
        context: Context,
        loader: GeoJsonLoader,
        coloredNames: Collection<String>,
        epicenterLon: Double,
        epicenterLat: Double
    ): LatLngBounds {
        val coloredBbox = loader.boundingBoxFor(context, coloredNames)

        val bbox = if (coloredBbox != null) {
            BoundingBox(
                minLon = minOf(coloredBbox.minLon, epicenterLon),
                maxLon = maxOf(coloredBbox.maxLon, epicenterLon),
                minLat = minOf(coloredBbox.minLat, epicenterLat),
                maxLat = maxOf(coloredBbox.maxLat, epicenterLat)
            )
        } else {
            // 着色エリアが1つも無い(震度速報のみ等)場合、震源を中心に一定範囲を表示する
            val soloSpanDegrees = 1.2
            BoundingBox(
                minLon = epicenterLon - soloSpanDegrees, maxLon = epicenterLon + soloSpanDegrees,
                minLat = epicenterLat - soloSpanDegrees, maxLat = epicenterLat + soloSpanDegrees
            )
        }

        val lonPad = (bbox.maxLon - bbox.minLon).coerceAtLeast(0.08) * 0.8 + 0.3
        val latPad = (bbox.maxLat - bbox.minLat).coerceAtLeast(0.08) * 0.8 + 0.3

        return LatLngBounds.Builder()
            .include(org.maplibre.android.geometry.LatLng(bbox.minLat - latPad, bbox.minLon - lonPad))
            .include(org.maplibre.android.geometry.LatLng(bbox.maxLat + latPad, bbox.maxLon + lonPad))
            .build()
    }
}