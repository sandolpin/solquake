package com.sandolpin.weatherquake.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sandolpin.weatherquake.data.settings.DarkModeOption

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFE7EAF2)
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF2A2C33)
)

/** 設定画面の「ダークモード(ON/OFF/システム)」を反映したうえでMaterialThemeを適用する */
@Composable
fun WeatherQuakeTheme(
    darkModeOption: DarkModeOption = DarkModeOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (darkModeOption) {
        DarkModeOption.ON -> true
        DarkModeOption.OFF -> false
        DarkModeOption.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
