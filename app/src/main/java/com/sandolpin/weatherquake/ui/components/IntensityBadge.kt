package com.sandolpin.weatherquake.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.settings.IntensityColorContrast

/**
 * 震度バッジ。設定画面の「震度色の濃さ」設定(デフォルト/薄め/ハイコントラスト)を
 * contrastパラメータで反映する。
 */
@Composable
fun IntensityBadge(
    level: IntensityLevel,
    size: Dp = 56.dp,
    contrast: IntensityColorContrast = IntensityColorContrast.DEFAULT,
    modifier: Modifier = Modifier
) {
    val bg = adjustedColor(level, contrast)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size / 4),
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                level.label,
                color = level.textColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

private fun adjustedColor(level: IntensityLevel, contrast: IntensityColorContrast) = when (contrast) {
    IntensityColorContrast.DEFAULT -> level.bgColor
    IntensityColorContrast.LIGHT -> level.bgColor.copy(alpha = 0.65f)
    IntensityColorContrast.HIGH_CONTRAST -> level.bgColor
}
