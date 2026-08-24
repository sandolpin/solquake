package com.sandolpin.weatherquake.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.sandolpin.weatherquake.data.settings.WeatherBackgroundStyle
import com.sandolpin.weatherquake.data.weather.WeatherCondition
import com.sandolpin.weatherquake.ui.theme.DayPhase
import com.sandolpin.weatherquake.ui.theme.WeatherPalette
import com.sandolpin.weatherquake.ui.theme.WeatherPalettes
import kotlin.math.PI
import kotlin.random.Random

/** kotlin.mathのsin/cosはFloat/Double両方のオーバーロードを持つため、
 *  Float演算の途中でDoubleに昇格して型不一致エラーになるのを避けるべく、
 *  「Double計算→最後にFloatへ変換」で型を確定させる小さなヘルパー */
private fun sinF(x: Float): Float = kotlin.math.sin(x.toDouble()).toFloat()
private fun cosF(x: Float): Float = kotlin.math.cos(x.toDouble()).toFloat()

/**
 * 天気画面の背景全体を担うComposable。
 *
 * 設定画面の「天気の背景設定」により3パターンに出し分ける:
 * - DYNAMIC: 天気×時間帯に応じた色のグラデーションブロブがゆっくり動き回るアニメーション背景。
 *            雨・雪の場合はパーティクル(雨粒/雪)を重ねて降らせる。
 *            夜間は原則として星を表示するが、天気が曇り系(曇り/一部曇り/霧)の場合は
 *            星の代わりに雲のシルエットを表示する(曇っている夜に星は見えないため)。
 * - PLAIN_WHITE / PLAIN_BLACK: 単色背景(アニメーション無し。バッテリー・可読性重視の人向け)。
 *
 * 天気・時間帯が変わった際は、色を瞬時に切り替えず Crossfade で滑らかに繋ぐ。
 */
@Composable
fun WeatherBackground(
    condition: WeatherCondition,
    dayPhase: DayPhase,
    style: WeatherBackgroundStyle,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    when (style) {
        WeatherBackgroundStyle.PLAIN_WHITE -> Box(modifier.fillMaxSize().background(Color.White), content = content)
        WeatherBackgroundStyle.PLAIN_BLACK -> Box(modifier.fillMaxSize().background(Color.Black), content = content)
        WeatherBackgroundStyle.DYNAMIC -> {
            val palette = paletteFor(condition, dayPhase)
            val isCloudy = condition == WeatherCondition.CLOUDY ||
                    condition == WeatherCondition.PARTLY_CLOUDY ||
                    condition == WeatherCondition.FOG
            val showStars = dayPhase == DayPhase.NIGHT && !isCloudy
            val showClouds = isCloudy

            Box(modifier.fillMaxSize()) {
                Crossfade(targetState = palette, label = "weatherPalette") { p ->
                    DynamicGradientCanvas(palette = p, showStars = showStars, showClouds = showClouds)
                }
                ParticleOverlay(condition = condition)
                Box(Modifier.fillMaxSize(), content = content)
            }
        }
    }
}

private fun paletteFor(condition: WeatherCondition, dayPhase: DayPhase): WeatherPalette = when (condition) {
    WeatherCondition.CLEAR -> when (dayPhase) {
        DayPhase.MORNING -> WeatherPalettes.clearMorning
        DayPhase.DAY -> WeatherPalettes.clearDay
        DayPhase.EVENING -> WeatherPalettes.clearEvening
        DayPhase.NIGHT -> WeatherPalettes.clearNight
    }
    WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY, WeatherCondition.FOG ->
        if (dayPhase == DayPhase.NIGHT) WeatherPalettes.cloudyNight else WeatherPalettes.cloudyDay
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN ->
        if (dayPhase == DayPhase.NIGHT) WeatherPalettes.rainNight else WeatherPalettes.rainDay
    WeatherCondition.SNOW ->
        if (dayPhase == DayPhase.NIGHT) WeatherPalettes.snowNight else WeatherPalettes.snowDay
    WeatherCondition.THUNDERSTORM -> WeatherPalettes.thunderstorm
}

/**
 * 3つの色玉(ブロブ)をゆっくり異なる軌道・周期で動かし、放射グラデーションで重ね合わせることで
 * 「色が混ざって動く」有機的な背景を表現する。
 * 各ブロブは異なるtween周期のInfiniteTransitionで駆動し、位相をずらすことで単調に見えないようにしている。
 */
@Composable
private fun DynamicGradientCanvas(palette: WeatherPalette, showStars: Boolean, showClouds: Boolean) {
    val transition = rememberInfiniteTransition(label = "bgBlobs")
    val t1 by transition.animateFloatLoop(durationMillis = 18000, label = "t1")
    val t2 by transition.animateFloatLoop(durationMillis = 23000, label = "t2", reverse = true)
    val t3 by transition.animateFloatLoop(durationMillis = 29000, label = "t3")
    val twinkle by transition.animateFloatLoop(durationMillis = 3000, label = "twinkle", reverse = true)
    // 雲はゆっくり左右に流れるだけにする(瞬きは不要なので専用の遅い周期)
    val cloudDrift by transition.animateFloatLoop(durationMillis = 40000, label = "cloudDrift")

    val stars = remember { List(40) { Offset(Random.nextFloat(), Random.nextFloat()) } }
    // 雲のシルエット(複数の円を重ねて雲っぽい輪郭にする)の初期パラメータを1回だけ乱数で決める
    data class CloudSeed(val xRatio: Float, val yRatio: Float, val scale: Float, val speed: Float, val alpha: Float)
    val cloudSeeds = remember {
        List(4) {
            CloudSeed(
                xRatio = Random.nextFloat(),
                yRatio = 0.08f + Random.nextFloat() * 0.28f,
                scale = 0.6f + Random.nextFloat() * 0.7f,
                speed = 0.4f + Random.nextFloat() * 0.5f,
                alpha = 0.18f + Random.nextFloat() * 0.14f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baseColor = palette.colors.last()
        drawRect(color = baseColor)

        val positions = listOf(
            Offset(w * (0.25f + 0.30f * sinF(t1 * 2f * PI.toFloat())), h * (0.18f + 0.16f * cosF(t1 * 2f * PI.toFloat()))),
            Offset(w * (0.72f + 0.22f * cosF(t2 * 2f * PI.toFloat())), h * (0.40f + 0.24f * sinF(t2 * 2f * PI.toFloat()))),
            Offset(w * (0.42f + 0.30f * sinF(t3 * 2f * PI.toFloat() + 1.2f)), h * (0.80f + 0.16f * cosF(t3 * 2f * PI.toFloat())))
        )

        positions.forEachIndexed { index, center ->
            val color = palette.colors.getOrElse(index) { palette.colors.last() }
            val radius = maxOf(w, h) * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        if (showStars) {
            stars.forEachIndexed { i, ratio ->
                val alpha = 0.3f + 0.5f * ((sinF(twinkle * 2f * PI.toFloat() + i.toFloat()) + 1f) / 2f)
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 2.5f,
                    center = Offset(ratio.x * w, ratio.y * h * 0.5f) // 星は上半分のみに配置
                )
            }
        }

        if (showClouds) {
            cloudSeeds.forEach { seed ->
                // 画面幅の外からゆっくり流れ込んでループする横位置
                val driftX = ((seed.xRatio + cloudDrift * seed.speed) % 1.2f - 0.1f) * w
                val cy = h * seed.yRatio
                val baseRadius = w * 0.14f * seed.scale
                val cloudColor = Color.White.copy(alpha = seed.alpha)
                // 複数の円を横に並べて重ねることで、単純な丸ではなく「もこもこした雲」の輪郭にする
                drawCircle(color = cloudColor, radius = baseRadius, center = Offset(driftX, cy))
                drawCircle(color = cloudColor, radius = baseRadius * 0.75f, center = Offset(driftX - baseRadius * 0.9f, cy + baseRadius * 0.25f))
                drawCircle(color = cloudColor, radius = baseRadius * 0.8f, center = Offset(driftX + baseRadius * 0.9f, cy + baseRadius * 0.2f))
                drawCircle(color = cloudColor, radius = baseRadius * 0.55f, center = Offset(driftX + baseRadius * 0.3f, cy - baseRadius * 0.35f))
            }
        }
    }
}

/** 0f→1f をループするInfiniteTransitionアニメーションを簡潔に作るための拡張関数 */
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateFloatLoop(
    durationMillis: Int,
    label: String,
    reverse: Boolean = false
) = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = durationMillis, easing = LinearEasing),
        repeatMode = if (reverse) RepeatMode.Reverse else RepeatMode.Restart
    ),
    label = label
)

/**
 * 雨・雪のパーティクル降下アニメーション。
 * 各粒子は初期化時に乱数で「横位置・速度・大きさ・不透明度」を1回だけ決め、
 * 以降はアニメーション時間(0〜1をループするFloat)から現在位置を計算式で求める方式にしている
 * (毎フレームStateを書き換えるより軽量で、Composeの再コンポーズ回数を最小化できる)。
 */
@Composable
private fun ParticleOverlay(condition: WeatherCondition) {
    val isRain = condition == WeatherCondition.RAIN || condition == WeatherCondition.DRIZZLE || condition == WeatherCondition.THUNDERSTORM
    val isSnow = condition == WeatherCondition.SNOW
    if (!isRain && !isSnow) return

    data class Particle(
        val xRatio: Float,
        val startYRatio: Float,
        val speed: Float,
        val size: Float,
        val alpha: Float,
        val swayPhase: Float
    )

    val count = if (isRain) 70 else 45
    val particles = remember(isRain, isSnow) {
        List(count) {
            Particle(
                xRatio = Random.nextFloat(),
                startYRatio = Random.nextFloat(),
                speed = if (isRain) 1.6f + Random.nextFloat() * 1.2f else 0.35f + Random.nextFloat() * 0.35f,
                size = if (isRain) 14f + Random.nextFloat() * 10f else 3f + Random.nextFloat() * 3f,
                alpha = 0.35f + Random.nextFloat() * 0.45f,
                swayPhase = Random.nextFloat() * 2f * PI.toFloat()
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "particles")
    // 27時間かけて1周する非常に長いループ。実用上リセットの継ぎ目が見えることはほぼ無い。
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 100_000_000, easing = LinearEasing)),
        label = "particleTime"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val progress = (p.startYRatio + time * p.speed * 3600f) % 1f
            val y = progress * h
            val sway = if (isSnow) sinF(time * 3600f * 2f * PI.toFloat() + p.swayPhase) * 14f else 0f
            val x = (p.xRatio * w + sway).let { if (it < 0f) it + w else if (it > w) it - w else it }

            if (isRain) {
                drawLine(
                    color = Color(0xFFDCEEFF).copy(alpha = p.alpha),
                    start = Offset(x, y),
                    end = Offset(x - p.size * 0.3f, y + p.size * 3.2f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            } else {
                drawCircle(color = Color.White.copy(alpha = p.alpha), radius = p.size, center = Offset(x, y))
            }
        }
    }
}