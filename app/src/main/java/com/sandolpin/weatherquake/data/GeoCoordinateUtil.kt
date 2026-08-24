package com.sandolpin.weatherquake.data

/**
 * バウンディングボックス(表示範囲)を表すシンプルなデータクラス。
 */
data class BoundingBox(
    val minLon: Double, val maxLon: Double,
    val minLat: Double, val maxLat: Double
) {
    val centerLon: Double get() = (minLon + maxLon) / 2
    val centerLat: Double get() = (minLat + maxLat) / 2
}

object GeoCoordinateUtil {

    /**
     * FloatArray(経度,緯度を交互に格納)のリストから、
     * 全点を包含するバウンディングボックスを計算する。
     */
    fun boundingBox(polygons: List<FloatArray>): BoundingBox? {
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var found = false

        polygons.forEach { ring ->
            var i = 0
            while (i < ring.size) {
                val lon = ring[i].toDouble()
                val lat = ring[i + 1].toDouble()
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                found = true
                i += 2
            }
        }

        return if (found) BoundingBox(minLon, maxLon, minLat, maxLat) else null
    }
}
