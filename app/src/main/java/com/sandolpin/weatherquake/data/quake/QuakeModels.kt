package com.sandolpin.weatherquake.data.quake

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * P2P地震情報API v2 (https://www.p2pquake.net/develop/json_api_v2/) の
 * 「地震情報」(code = 551) レスポンス構造。
 *
 * GET https://api.p2pquake.net/v2/history?codes=551&limit=30 で配列そのものが返る。
 */
data class P2pQuakeItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("code") val code: Int = 0,
    @SerializedName("time") val time: String = "",
    @SerializedName("issue") val issue: P2pIssue? = null,
    @SerializedName("earthquake") val earthquake: P2pEarthquake? = null,
    @SerializedName("points") val points: List<P2pPoint>? = null
)

/** 発表種類(震度速報/震源情報/地震情報 等)。QuakeIssueType.fromApiValueで変換して使う */
data class P2pIssue(
    @SerializedName("source") val source: String? = null,
    @SerializedName("time") val time: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("correct") val correct: String? = null
)

data class P2pEarthquake(
    @SerializedName("time") val time: String = "",
    @SerializedName("hypocenter") val hypocenter: P2pHypocenter? = null,
    @SerializedName("maxScale") val maxScale: Int? = null,
    @SerializedName("domesticTsunami") val domesticTsunami: String? = null,
    @SerializedName("foreignTsunami") val foreignTsunami: String? = null
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
    @SerializedName("scale") val scale: Int? = null,
    @SerializedName("isArea") val isArea: Boolean? = null
)

/**
 * 画面表示用に整形した地震情報1件分。
 *
 * [APIで取得できたすべての情報を表示する機能への対応]
 * 従来は画面表示に必要な項目だけを保持していたが、地震詳細画面で
 * 「APIから取得できた情報をすべて表示する」ためのフィールド
 * (id, code, 発表元/訂正/津波情報, 生の発生時刻文字列)を追加した。
 */
@Immutable
data class QuakeCardState(
    val id: String,
    val code: Int = 551,
    val hypocenterName: String,
    val depthKm: Int?,      // nullは不明
    val magnitude: Double?, // nullは不明
    val occurredAtLabel: String,
    val rawOccurredAt: String = "", // API生の時刻文字列(例: "2026/08/31 10:30:00.000")
    val latitude: Double,
    val longitude: Double,
    val maxScale: Int?,
    val points: List<P2pPoint>,
    val issueType: QuakeIssueType = QuakeIssueType.OTHER,
    val issueSource: String? = null,
    val issueTime: String? = null,
    val issueCorrect: String? = null,
    val domesticTsunami: String? = null,
    val foreignTsunami: String? = null
)

/** domesticTsunami/foreignTsunamiの値(英語)を日本語表示に変換する */
fun tsunamiLabel(raw: String?): String = when (raw) {
    null -> "情報なし"
    "None" -> "被害の心配なし"
    "Unknown" -> "不明"
    "Checking" -> "調査中"
    "NonEffective" -> "若干の海面変動が予想されるが被害の心配なし"
    "Watch" -> "津波注意報"
    "Warning" -> "津波警報等"
    else -> raw
}