package com.sandolpin.weatherquake.data.quake

/**
 * P2P地震情報API(v2)の issue.type(発表種類)。
 * 気象庁の発表段階によって内容が変わるため、通知タイトルや地震画面のカードで
 * 「これはどの段階の情報か」を分かりやすく表示するために使う。
 *
 * 参考: https://github.com/p2pquake/epsp-specifications (json-api-v2.yaml)
 * - ScalePrompt          : 震度速報(震源はまだ不明)
 * - Destination          : 震源に関する情報(震源はわかったが規模は不明)
 * - ScaleAndDestination  : 震度・震源に関する情報
 * - DetailScale          : 各地の震度に関する情報(いわゆる「地震情報」)
 * - Foreign              : 遠地地震に関する情報
 * - Other                : その他の情報
 */
enum class QuakeIssueType(val apiValue: String, val displayName: String) {
    SCALE_PROMPT("ScalePrompt", "震度速報"),
    DESTINATION("Destination", "震源に関する情報"),
    SCALE_AND_DESTINATION("ScaleAndDestination", "震度・震源に関する情報"),
    DETAIL_SCALE("DetailScale", "地震情報"),
    FOREIGN("Foreign", "遠地地震に関する情報"),
    OTHER("Other", "地震に関する情報");

    companion object {
        fun fromApiValue(value: String?): QuakeIssueType =
            entries.firstOrNull { it.apiValue == value } ?: OTHER
    }
}