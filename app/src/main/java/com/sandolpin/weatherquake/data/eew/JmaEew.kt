package com.sandolpin.weatherquake.data.eew

import androidx.compose.runtime.Immutable

/**
 * Wolfx API (JMA緊急地震速報) のレスポンスに対応するデータクラス。
 * 公式ドキュメント: https://wolfx.jp/apidoc
 *
 * 注意: Magunitude は公式APIの実際のフィールド名(Magnitudeのtypo)。
 * わざとこの綴りにしないとJSONパースに失敗するので変更しないこと。
 *
 * @Immutable を付けることで、List<WarnArea>?を持つこのクラスをComposeが
 * 「安全な不変オブジェクト」として扱えるようになり、不要な再コンポーズを防ぐ。
 */
@Immutable
data class JmaEew(
    val type: String? = null,
    val Title: String = "",
    val CodeType: String = "",
    val EventID: String = "",
    val Serial: Int = 0,
    val AnnouncedTime: String = "",
    val OriginTime: String = "",
    val Hypocenter: String = "",
    val Latitude: Double = 0.0,
    val Longitude: Double = 0.0,
    val Magunitude: Double = 0.0,
    val Depth: Double = 0.0,
    val MaxIntensity: String = "不明",
    val WarnArea: List<WarnArea>? = null,
    val isSea: Boolean = false,
    val isTraining: Boolean = false,
    val isAssumption: Boolean = false,
    val isWarn: Boolean = false,
    val isFinal: Boolean = false,
    val isCancel: Boolean = false
)

@Immutable
data class WarnArea(
    val Chiiki: String = "",
    val Shindo1: String = "",
    val Shindo2: String = "",
    val Time: String = "",
    val Type: String = "",
    val Arrive: Boolean = false
)
