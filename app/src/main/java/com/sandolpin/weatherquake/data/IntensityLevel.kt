package com.sandolpin.weatherquake.data

import androidx.compose.ui.graphics.Color

/**
 * 気象庁震度階級(9段階)+ 不明 を表すenum。
 * 緊急地震速報(Wolfx API)・地震情報(P2P地震情報API)の両方から使用する共通の型。
 *
 * bgColor / textColor はデフォルトの配色。設定画面の「震度色の濃さ」設定
 * (デフォルト/薄め/ハイコントラスト)は IntensityColorScheme 側で別カラーセットを持ち、
 * 表示側でどちらを使うか選択する。
 */
enum class IntensityLevel(val label: String, val bgColor: Color, val textColor: Color) {
    UNKNOWN("不明", Color(0xFF424242), Color.White),
    ONE("1", Color(0xFF5C6678), Color.White),
    TWO("2", Color(0xFF4378B5), Color.White),
    THREE("3", Color(0xFF54E867), Color.Black),
    FOUR("4", Color(0xFFFFDF3D), Color.Black),
    // P2P地震情報APIのscale=46相当。震度速報(ScalePrompt)の直後など、
    // 「5弱以上と推定されるが、詳しい震度情報がまだ届いていない」状態を表す特別な値。
    FIVE_MINUS_OR_ABOVE_UNCONFIRMED("5+?", Color(0xFFFF8A00), Color.Black),
    FIVE_MINUS("5-", Color(0xFFFFAF24), Color.Black),
    FIVE_PLUS("5+", Color(0xFFFF6C0A), Color.White),
    SIX_MINUS("6-", Color(0xFFFF0000), Color.White),
    SIX_PLUS("6+", Color(0xFF8F0000), Color.White),
    SEVEN("7", Color(0xFF5C0EA1), Color.White);

    /** 通知文言・詳細画面見出し等で使う「震度〇弱」のような正式表記 */
    val formalLabel: String
        get() = when (this) {
            UNKNOWN -> "不明"
            FIVE_MINUS_OR_ABOVE_UNCONFIRMED -> "5弱以上と推定(未入電)"
            FIVE_MINUS -> "5弱"
            FIVE_PLUS -> "5強"
            SIX_MINUS -> "6弱"
            SIX_PLUS -> "6強"
            else -> label
        }

    companion object {
        /** Wolfx APIのMaxIntensity ("5弱"等の文字列) からenumへ変換する */
        fun fromApiString(raw: String?): IntensityLevel = when (raw) {
            "1" -> ONE
            "2" -> TWO
            "3" -> THREE
            "4" -> FOUR
            "5弱" -> FIVE_MINUS
            "5強" -> FIVE_PLUS
            "6弱" -> SIX_MINUS
            "6強" -> SIX_PLUS
            "7" -> SEVEN
            else -> UNKNOWN
        }

        /**
         * P2P地震情報API(v2)の scale (震度*10。5弱=45, 5強=50, 6弱=55, 6強=60等)からenumへ変換する。
         * scaleがnull、または未観測(-1)の場合はUNKNOWNを返す。
         * 46は「震度5弱以上と推定されるが、詳しい震度情報をまだ入手していない」特別な値。
         */
        fun fromP2pScale(scale: Int?): IntensityLevel = when (scale) {
            10 -> ONE
            20 -> TWO
            30 -> THREE
            40 -> FOUR
            46 -> FIVE_MINUS_OR_ABOVE_UNCONFIRMED
            45 -> FIVE_MINUS
            50 -> FIVE_PLUS
            55 -> SIX_MINUS
            60 -> SIX_PLUS
            70 -> SEVEN
            else -> UNKNOWN
        }

        /** 震度6弱以上かどうか(EEW特別警報の判定・通知の重要度判定に使用) */
        fun isSixMinusOrAbove(level: IntensityLevel): Boolean =
            level == SIX_MINUS || level == SIX_PLUS || level == SEVEN
    }
}