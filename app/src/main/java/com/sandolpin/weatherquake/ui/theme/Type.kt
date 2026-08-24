package com.sandolpin.weatherquake.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sandolpin.weatherquake.R

/**
 * 設定画面の「気温のフォント: アプリのおすすめ」で使用するMontserrat。
 * res/font/montserrat.ttf・montserrat_bold.ttf の配置が必要(README参照)。
 * 通常のUIテキスト(日本語)は端末デフォルトの游ゴシック/Noto Sans JP系にフォールバックさせるため、
 * このFontFamilyは「一番大きい気温表示」専用として個別に呼び出す。
 */
val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)

/** 天気画面の大きな気温表示(例: 「25℃」)専用のTextStyleを、フォント設定に応じて返す */
fun temperatureDisplayStyle(useRecommendedFont: Boolean): TextStyle = TextStyle(
    fontFamily = if (useRecommendedFont) MontserratFontFamily else FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp
)

val AppTypography = Typography()
