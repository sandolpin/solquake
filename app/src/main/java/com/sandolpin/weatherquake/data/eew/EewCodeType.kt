package com.sandolpin.weatherquake.data.eew

import com.sandolpin.weatherquake.data.IntensityLevel

/**
 * 緊急地震速報の種別(予報・警報・特別警報・取消)。
 *
 * Wolfx APIのCodeType文字列だけでは種別を安定して判定できないため、
 * isCancel / isWarn / MaxIntensity から種別を判定するロジックを持たせている。
 * 気象庁の実運用では、警報のうち最大震度6弱以上のものを「特別警報」として扱う。
 */
enum class EewCodeType(val displayName: String) {
    FORECAST("緊急地震速報（予報）"),
    WARNING("緊急地震速報（警報）"),
    EMERGENCY_WARNING("緊急地震速報（特別警報）"),
    CANCEL("緊急地震速報（取消）");

    companion object {
        fun classify(eew: JmaEew): EewCodeType {
            if (eew.isCancel) return CANCEL
            if (eew.isWarn) {
                val level = IntensityLevel.fromApiString(eew.MaxIntensity)
                return if (IntensityLevel.isSixMinusOrAbove(level)) EMERGENCY_WARNING else WARNING
            }
            return FORECAST
        }
    }
}
