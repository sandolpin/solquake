package com.sandolpin.weatherquake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sandolpin.weatherquake.data.settings.CardStyle

/**
 * 設定画面の「カードのスタイル(塗りつぶし/グラスモーフィズム)」「カードの透明度」を
 * 反映した共通カードコンテナ。天気画面の各情報カードで使用する。
 *
 * グラスモーフィズム風の表現は、Android/Composeでは背景の実際のブラーをリアルタイムに
 * 取得すること(いわゆる本物の背後ブラー)は標準APIだけでは難しいため、
 * 「半透明の白/黒レイヤー + 薄い枠線」で近似する簡易グラスモーフィズムにしている。
 */
@Composable
fun AppCard(
    opacity: Float,
    style: CardStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val baseColor = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(20.dp)
    val backgroundModifier = when (style) {
        CardStyle.FILLED -> Modifier.background(baseColor.copy(alpha = (opacity + 0.5f).coerceAtMost(1f)), shape)
        CardStyle.GLASSMORPHISM -> Modifier
            .background(Color.White.copy(alpha = opacity * 0.5f), shape)
            .background(baseColor.copy(alpha = opacity), shape)
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxWidth().then(backgroundModifier)
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
