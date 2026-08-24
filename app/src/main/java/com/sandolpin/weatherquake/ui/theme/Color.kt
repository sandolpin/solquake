package com.sandolpin.weatherquake.ui.theme

import androidx.compose.ui.graphics.Color

// --- EEWカードのヘッダー色(予報デザイン画像に準拠) ---
val CardForecast = Color(0xFFFFA000)   // オレンジ(予報)
val CardWarning = Color(0xFFE53935)    // 赤(警報・特別警報)
val CardEmergency = Color(0xFFB71C1C)  // 濃い赤(特別警報を強調したい場合)
val CardCancel = Color(0xFF9E9E9E)     // グレー(取消)

// --- ライトテーマの基本色 ---
val LightPrimary = Color(0xFF3E7BFA)
val LightBackground = Color(0xFFF4F6FB)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1B1B1F)

// --- ダークテーマの基本色 ---
val DarkPrimary = Color(0xFF7FA8FF)
val DarkBackground = Color(0xFF121318)
val DarkSurface = Color(0xFF1C1D22)
val DarkOnSurface = Color(0xFFE3E2E6)

/**
 * 天気の背景アニメーション(WeatherBackground)で使う、
 * 「天気種別 × 時間帯」ごとのグラデーション色セット。
 * 色は3〜4色を指定し、Canvas側でこれらの間をゆっくり移動する複数のブロブとして描画する。
 */
data class WeatherPalette(val colors: List<Color>)

enum class DayPhase { MORNING, DAY, EVENING, NIGHT }

object WeatherPalettes {
    // はれ
    val clearDay = WeatherPalette(listOf(Color(0xFF4FC3F7), Color(0xFF2196F3), Color(0xFF1565C0)))
    val clearMorning = WeatherPalette(listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7)))
    val clearEvening = WeatherPalette(listOf(Color(0xFFFF7043), Color(0xFFFF5E62), Color(0xFF512DA8)))
    val clearNight = WeatherPalette(listOf(Color(0xFF0D1B4C), Color(0xFF1A237E), Color(0xFF283593)))

    // くもり
    val cloudyDay = WeatherPalette(listOf(Color(0xFF90A4AE), Color(0xFF607D8B), Color(0xFF455A64)))
    val cloudyNight = WeatherPalette(listOf(Color(0xFF2C3540), Color(0xFF37474F), Color(0xFF263238)))

    // 雨
    val rainDay = WeatherPalette(listOf(Color(0xFF64748B), Color(0xFF475569), Color(0xFF334155)))
    val rainNight = WeatherPalette(listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF111827)))

    // 雪
    val snowDay = WeatherPalette(listOf(Color(0xFFB0C4DE), Color(0xFF90A4C4), Color(0xFF6E85A8)))
    val snowNight = WeatherPalette(listOf(Color(0xFF29344A), Color(0xFF1C2536), Color(0xFF141B29)))

    // 雷雨
    val thunderstorm = WeatherPalette(listOf(Color(0xFF3B3B58), Color(0xFF232946), Color(0xFF121629)))
}
