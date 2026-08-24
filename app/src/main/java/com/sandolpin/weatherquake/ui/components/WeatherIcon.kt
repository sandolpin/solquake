package com.sandolpin.weatherquake.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sandolpin.weatherquake.data.weather.WeatherCondition
import com.sandolpin.weatherquake.ui.theme.DayPhase

/**
 * デザイン画像の「角丸ボックス+アイコン」を再現する天気アイコン。
 * 天気種別と時間帯(昼/夜)の組み合わせで背景色・アイコンを切り替え、
 * 切り替わり時はCrossfadeで滑らかに繋ぐ。
 */
@Composable
fun WeatherIcon(
    condition: WeatherCondition,
    dayPhase: DayPhase,
    size: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val (bgColor, icon) = iconFor(condition, dayPhase)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size / 4),
        color = bgColor
    ) {
        Crossfade(targetState = icon, label = "weatherIcon") { currentIcon ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = currentIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

private fun iconFor(condition: WeatherCondition, dayPhase: DayPhase): Pair<Color, ImageVector> {
    val isNight = dayPhase == DayPhase.NIGHT
    return when (condition) {
        WeatherCondition.CLEAR ->
            if (isNight) Color(0xFF3B4A78) to Icons.Filled.Bedtime
            else Color(0xFFF2A354) to Icons.Filled.WbSunny
        WeatherCondition.PARTLY_CLOUDY -> Color(0xFF7C93A8) to Icons.Filled.Cloud
        WeatherCondition.CLOUDY -> Color(0xFF6E6E6E) to Icons.Filled.Cloud
        WeatherCondition.FOG -> Color(0xFF8C97A0) to Icons.Filled.Cloud
        WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> Color(0xFF4B7BE5) to Icons.Filled.WaterDrop
        WeatherCondition.SNOW -> Color(0xFF7FA7DE) to Icons.Filled.AcUnit
        WeatherCondition.THUNDERSTORM -> Color(0xFF5C4B99) to Icons.Filled.Bolt
    }
}