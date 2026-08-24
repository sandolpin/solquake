package com.sandolpin.weatherquake.data.eew

import androidx.compose.runtime.Immutable
import com.sandolpin.weatherquake.data.IntensityLevel

/**
 * 画面に表示する1枚のカードの状態。
 * EventID単位で管理し、続報が来たら同じカードを上書き更新する。
 * 別のEventIDが来た場合は、新しいカードとしてリストに追加される。
 */
@Immutable
data class EewCardState(
    val eew: JmaEew,
    val receivedAt: Long = System.currentTimeMillis()
) {
    val codeType: EewCodeType get() = EewCodeType.classify(eew)
    val intensity: IntensityLevel get() = IntensityLevel.fromApiString(eew.MaxIntensity)
}
