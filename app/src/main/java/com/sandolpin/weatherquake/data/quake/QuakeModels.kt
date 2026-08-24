package com.sandolpin.weatherquake.data.quake

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * P2P地震情報API v2 (https://www.p2pquake.net/develop/json_api_v2/) の
 * 「地震情報」(code = 551) レスポンス構造。
 *
 * GET https://api.p2pquake.net/v2/history?codes=551&limit=30 で配列そのものが返る。
 * リアルタイム受信にはWebSocket(wss://api.p2pquake.net/v2/ws)も提供されているが、
 * 本アプリでは既存のEewService(緊急地震速報)のポーリングループに相乗りする形で
 * 定期的にhistoryエンドポイントを叩く方式にしている(QuakeRepository参照)。
 */
data class P2pQuakeItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("code") val code: Int = 0,
    @SerializedName("time") val time: String = "",
    @SerializedName("earthquake") val earthquake: P2pEarthquake? = null,
    @SerializedName("points") val points: List<P2pPoint>? = null
)

data class P2pEarthquake(
    @SerializedName("time") val time: String = "",
    @SerializedName("hypocenter") val hypocenter: P2pHypocenter? = null,
    @SerializedName("maxScale") val maxScale: Int? = null,
    @SerializedName("domesticTsunami") val domesticTsunami: String? = null
)

data class P2pHypocenter(
    @SerializedName("name") val name: String = "",
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("depth") val depth: Int = -1, // -1 = 不明
    @SerializedName("magnitude") val magnitude: Double = -1.0 // -1 = 不明
)

data class P2pPoint(
    @SerializedName("pref") val pref: String = "",
    @SerializedName("addr") val addr: String = "",
    @SerializedName("scale") val scale: Int? = null
)

/** 画面表示用に整形した地震情報1件分 */
@Immutable
data class QuakeCardState(
    val id: String,
    val hypocenterName: String,
    val depthKm: Int?,      // nullは不明
    val magnitude: Double?, // nullは不明
    val occurredAtLabel: String,
    val latitude: Double,
    val longitude: Double,
    val maxScale: Int?,
    val points: List<P2pPoint>
)