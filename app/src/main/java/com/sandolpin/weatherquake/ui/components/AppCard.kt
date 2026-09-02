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
 * baseColor: カードの下地色。呼び出し側で明示的に指定することを推奨する。
 * 以前はMaterialTheme.colorScheme.surface固定だったため、
 * 「背景(単色白)の上でカードも白になり見えない」「白文字なのにカードが高不透明度で
 * 白系になり文字が読めなくなる」という問題があった。呼び出し側(天気画面)で
 * 背景の明暗と対になる色を明示的に渡すことで解決する。
 *
 * グラスモーフィズム風の表現は、Android/Composeでは背景の実際のブラーをリアルタイムに
 * 取得すること(いわゆる本物の背後ブラー)は標準APIだけでは難しいため、
 * baseColorの不透明度をFILLEDより低めにするだけの簡易表現にしている。
 */
@Composable
fun AppCard(
    opacity: Float,
    style: CardStyle,
    baseColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val backgroundModifier = when (style) {
        CardStyle.FILLED -> Modifier.background(baseColor.copy(alpha = (opacity + 0.5f).coerceAtMost(1f)), shape)
        CardStyle.GLASSMORPHISM -> Modifier.background(baseColor.copy(alpha = opacity), shape)
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxWidth().then(backgroundModifier)
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}