package com.sandolpin.weatherquake.ui.quake

import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.quake.P2pPoint

/**
 * 住所文字列(P2P地震情報APIの addr)を「市町村のみ表示」設定に応じて整形する。
 *
 * ルール(先勝ち):
 * 1. 「区」を含む場合 → 最後の「区」までを残す(例: 熊本市東区 → 熊本市東区)
 * 2. 「区」が無く「市」を含む場合 → 最初の「市」までを残す(例: 渋川市赤城 → 渋川市)
 * 3. 「市」も「区」も無く「町」または「村」を含む場合 → 最初の「町/村」までを残す
 * 4. どれも無ければそのまま返す。
 *
 * [市町村震度地図での利用について]
 * この関数で得られる名前が、assets/japan_city.geojson の properties.name と
 * 一致している必要がある(一致しない地点は地図上で塗り分けられない)。
 */
internal fun formatAddr(addr: String, cityOnly: Boolean): String {
    if (!cityOnly) return addr

    val wardIndex = addr.lastIndexOf('区')
    if (wardIndex >= 0) {
        return addr.substring(0, wardIndex + 1)
    }
    val cityIndex = addr.indexOf('市')
    if (cityIndex >= 0) {
        return addr.substring(0, cityIndex + 1)
    }
    val townOrVillageIndex = addr.indexOfFirst { it == '町' || it == '村' }
    if (townOrVillageIndex >= 0) {
        return addr.substring(0, townOrVillageIndex + 1)
    }
    return addr
}

/** 観測点一覧から、市町村名(formatAddrで正規化)ごとの最大震度マップを作る(地図の着色に使う) */
internal fun buildCityIntensityMap(points: List<P2pPoint>): Map<String, IntensityLevel> {
    val result = LinkedHashMap<String, IntensityLevel>()
    points.forEach { point ->
        val cityName = formatAddr(point.addr, cityOnly = true)
        if (cityName.isBlank()) return@forEach
        val level = IntensityLevel.fromP2pScale(point.scale)
        val existing = result[cityName]
        if (existing == null || level.ordinal > existing.ordinal) {
            result[cityName] = level
        }
    }
    return result
}

/** 指定した市町村名(formatAddr後)に属する観測点だけを取り出し、震度の高い順に並べる */
internal fun pointsInCity(points: List<P2pPoint>, cityName: String): List<P2pPoint> =
    points
        .filter { formatAddr(it.addr, cityOnly = true) == cityName }
        .sortedByDescending { IntensityLevel.fromP2pScale(it.scale).ordinal }