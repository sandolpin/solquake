package com.sandolpin.weatherquake.ui.weather

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.weatherquake.data.weather.ForecastPoint
import com.sandolpin.weatherquake.data.weather.WeatherCondition
import com.sandolpin.weatherquake.data.weather.WeatherLocation
import com.sandolpin.weatherquake.data.weather.WeatherUiState
import com.sandolpin.weatherquake.data.settings.TemperatureFontStyle
import com.sandolpin.weatherquake.ui.components.AppCard
import com.sandolpin.weatherquake.ui.components.WeatherBackground
import com.sandolpin.weatherquake.ui.components.WeatherIcon
import com.sandolpin.weatherquake.ui.theme.DayPhase
import com.sandolpin.weatherquake.ui.theme.temperatureDisplayStyle
import java.time.LocalTime
import kotlin.math.max

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val weather = state.weather
    var searchExpanded by remember { mutableStateOf(false) }

    val dayPhase = remember(weather) { weather?.let { computeDayPhase(it) } ?: DayPhase.DAY }

    WeatherBackground(
        condition = weather?.condition ?: WeatherCondition.CLEAR,
        dayPhase = dayPhase,
        style = state.settings.weatherBackgroundStyle
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            // --- 検索アイコン ---
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { searchExpanded = !searchExpanded }) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.25f)) {
                        Icon(Icons.Filled.Search, contentDescription = "地点を検索", tint = Color.White, modifier = Modifier.padding(8.dp))
                    }
                }
            }

            AnimatedVisibility(visible = searchExpanded) {
                LocationSearchBar(
                    query = state.searchQuery,
                    results = state.searchResults,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSelect = {
                        viewModel.selectLocation(it)
                        searchExpanded = false
                    }
                )
            }

            if (weather == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.isLoading) "読み込み中…" else (state.errorMessage ?: ""), color = Color.White)
                }
                return@Column
            }

            // --- ヘッダー: 都市名・日付・時刻、天気アイコン、気温・体感温度をすべて中央ぞろえで表示 ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(weather.location.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Spacer(Modifier.height(4.dp))
                Text(todayLabel(), color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
                Text("🕒 ${weather.updatedAtLabel}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)

                Spacer(Modifier.height(12.dp))

                WeatherIcon(condition = weather.condition, dayPhase = dayPhase, size = 84.dp)

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(weather.condition.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${weather.currentTemperature}℃",
                        color = Color.White,
                        style = temperatureDisplayStyle(state.settings.temperatureFont == TemperatureFontStyle.RECOMMENDED)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("体感${weather.apparentTemperature}℃", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- 気温レンジスライダー ---
            TemperatureRangeSlider(
                current = weather.currentTemperature,
                min = weather.tempMinToday,
                max = weather.tempMaxToday
            )

            Spacer(Modifier.height(16.dp))

            // --- 日照アーク+風 ---
            if (state.settings.showSunTimes) {
                AppCard(opacity = state.settings.weatherCardOpacity, style = state.settings.weatherCardStyle, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SunArc(sunrise = weather.sunrise, sunset = weather.sunset)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Air, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("${weather.windDirectionLabel}より${weather.windSpeed.toInt()}m/s", color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- これからの予報 ---
            if (state.settings.showHourlyForecast) {
                AppCard(opacity = state.settings.weatherCardOpacity, style = state.settings.weatherCardStyle, modifier = Modifier.padding(vertical = 6.dp)) {
                    ForecastSection(weather = weather)
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- 空気の汚れ具合・UV強さ・気圧・湿度(コンパクト⇔展開切り替え) ---
            if (state.settings.showWeatherDetails) {
                Box(Modifier.padding(vertical = 6.dp)) {
                    EnvironmentInfoSection(weather = weather)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LocationSearchBar(
    query: String,
    results: List<WeatherLocation>,
    onQueryChange: (String) -> Unit,
    onSelect: (WeatherLocation) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("地点を検索(例: 前橋市)") },
            modifier = Modifier.fillMaxWidth()
        )
        results.forEach { location ->
            Text(
                location.name,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(location) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

/** 最低〜最高気温のグラデーションバーに、現在気温の位置をマーカーで示すスライダー(表示専用) */
@Composable
private fun TemperatureRangeSlider(current: Int, min: Int, max: Int) {
    val safeMax = max(max, min + 1)
    val ratio = ((current - min).toFloat() / (safeMax - min).toFloat()).coerceIn(0f, 1f)
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { size -> containerWidthPx = size.width }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center)) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFF44336))
                    ),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }

            val markerSizePx = with(density) { 28.dp.roundToPx() }
            val markerCenterPx = (containerWidthPx * ratio).toInt()
            val markerOffsetPx = (markerCenterPx - markerSizePx / 2).coerceIn(0, (containerWidthPx - markerSizePx).coerceAtLeast(0))

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(markerOffsetPx, 0) }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Box(Modifier.padding(4.dp).fillMaxSize().clip(CircleShape).background(Color(0xFF616161)))
            }

            Text(
                "$current℃",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(markerOffsetPx, -with(density) { 16.dp.roundToPx() }) }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("最低:${min}℃", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            Text("最高:${max}℃", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

/** 日の出〜日の入りを半円の弧で表し、現在時刻の位置にマーカーを表示する */
@Composable
private fun SunArc(sunrise: String, sunset: String) {
    val progress = remember(sunrise, sunset) { sunProgress(sunrise, sunset) }

    Box(Modifier.fillMaxWidth().height(70.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height * 2f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h / 2f)
                quadraticBezierTo(w / 2f, -h * 0.15f, w, h / 2f)
            }
            drawPath(path, color = Color.White.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))

            val t = progress.coerceIn(0f, 1f)
            val x = w * t
            val y = (h / 2f) + (-h * 0.15f - h / 2f) * (1 - (2 * t - 1) * (2 * t - 1))
            drawCircle(color = Color.White, radius = 8f, center = Offset(x, y))
        }
        Text(sunrise, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomStart))
        Text(sunset, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

private fun sunProgress(sunrise: String, sunset: String): Float {
    return try {
        val now = LocalTime.now()
        val rise = LocalTime.parse(sunrise)
        val set = LocalTime.parse(sunset)
        val totalMinutes = (set.toSecondOfDay() - rise.toSecondOfDay()) / 60f
        val elapsed = (now.toSecondOfDay() - rise.toSecondOfDay()) / 60f
        if (totalMinutes <= 0f) 0.5f else (elapsed / totalMinutes)
    } catch (e: Exception) {
        0.5f
    }
}

/**
 * 「これからの予報」。アイコン行と下の折れ線グラフを同じ横スクロール(rememberScrollState)に
 * 乗せることで、スクロールしても両者がズレずに一緒に動くようにしている。
 * 以前はアイコン行(LazyRow)とグラフ(固定幅Canvas)が別々のスクロール軸だったため、
 * アイコン行をスクロールしてもグラフだけ取り残されてズレる不具合があった。
 */
@Composable
private fun ForecastSection(weather: WeatherUiState) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("15分ごと", "1時間ごと", "日ごと")
    val points = when (selectedTab) {
        0 -> weather.minutely15
        1 -> weather.hourly
        else -> weather.daily
    }
    val itemWidth = 68.dp
    val scrollState = rememberScrollState()

    Column(Modifier.padding(16.dp)) {
        Text("これからの予報", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))

        Column(Modifier.horizontalScroll(scrollState)) {
            Row(Modifier.width(itemWidth * points.size)) {
                points.forEach { point ->
                    Box(Modifier.width(itemWidth), contentAlignment = Alignment.Center) {
                        ForecastColumn(point)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ForecastLineGraph(points, modifier = Modifier.width(itemWidth * points.size).height(30.dp))
        }

        Spacer(Modifier.height(12.dp))
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.White
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
    }
}

@Composable
private fun ForecastColumn(point: ForecastPoint) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(point.label, color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        // 絵文字ではなく、天気画面の他の場所と同じWeatherIconコンポーネントを小さいサイズで使う
        WeatherIcon(condition = point.condition, dayPhase = DayPhase.DAY, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Text("${point.temperature}℃", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("${point.precipitationMm}mm", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        Text("${point.windSpeed.toInt()}m/s", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

/** 予報カード下部の折れ線グラフ(気温の推移を簡易的に可視化)。幅は呼び出し側で明示的に指定する。 */
@Composable
private fun ForecastLineGraph(points: List<ForecastPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val minTemp = points.minOf { it.temperature }
    val maxTemp = points.maxOf { it.temperature }
    val range = max(1, maxTemp - minTemp)

    Canvas(modifier = modifier) {
        val stepX = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, point ->
            val x = stepX * index
            val normalized = (point.temperature - minTemp).toFloat() / range
            val y = size.height - (normalized * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color.White.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))

        points.forEachIndexed { index, point ->
            val x = stepX * index
            val normalized = (point.temperature - minTemp).toFloat() / range
            val y = size.height - (normalized * size.height)
            drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))
        }
    }
}

// ============================== 空気の汚れ具合・UV強さ・気圧・湿度 ==============================

/** 環境情報1項目分の表示用データ(コンパクトカード・展開カードの両方で共有する) */
private data class EnvMeterData(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val valueLabel: String,
    val progress: Float,
    val trackBrush: Brush?,
    val subLabel: String?
)

/** WeatherUiStateの値のうち、取得できているものだけを表示項目に変換する */
private fun buildEnvItems(weather: WeatherUiState): List<EnvMeterData> {
    val items = mutableListOf<EnvMeterData>()

    weather.airQualityIndex?.let { aqi ->
        items += EnvMeterData(
            key = "aqi",
            icon = Icons.Filled.Grain,
            label = "空気の汚れ具合",
            valueLabel = "$aqi",
            progress = (aqi / 150f).coerceIn(0f, 1f),
            trackBrush = Brush.horizontalGradient(
                listOf(Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF7B1FA2))
            ),
            subLabel = aqiCategoryLabel(aqi)
        )
    }
    weather.uvIndex?.let { uv ->
        items += EnvMeterData(
            key = "uv",
            icon = Icons.Filled.WbSunny,
            label = "UV強さ",
            valueLabel = String.format("%.1f", uv),
            progress = (uv / 11f).toFloat().coerceIn(0f, 1f),
            trackBrush = Brush.horizontalGradient(
                listOf(Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF9C27B0))
            ),
            subLabel = uvCategoryLabel(uv)
        )
    }
    weather.pressureHpa?.let { pressure ->
        items += EnvMeterData(
            key = "pressure",
            icon = Icons.Filled.Speed,
            label = "気圧",
            valueLabel = "${"%,d".format(pressure)}hPa",
            progress = 0.5f,
            trackBrush = null,
            subLabel = null
        )
    }
    weather.humidityPercent?.let { humidity ->
        items += EnvMeterData(
            key = "humidity",
            icon = Icons.Filled.WaterDrop,
            label = "湿度",
            valueLabel = "$humidity%",
            progress = (humidity / 100f).coerceIn(0f, 1f),
            trackBrush = null,
            subLabel = null
        )
    }
    return items
}

/**
 * 環境情報セクションの本体。
 * 「コンパクト(2列グリッド)」⇔「展開(縦一列・詳細)」を同じ画面内で切り替える。
 *
 * QuakeListScreen⇔QuakeDetailScreenのようなNavHost間の画面遷移ではなく、
 * 同じComposable内でのレイアウト切り替えのため、
 * SharedTransitionLayout + AnimatedContent(targetState = 展開中か否か) の組み合わせを使う。
 * 各カードには共通のkey(例: "env-card-aqi")を持つsharedBoundsを付与しており、
 * タップした瞬間にカードの位置・サイズ・角丸がなめらかにモーフィングする。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EnvironmentInfoSection(weather: WeatherUiState) {
    val items = remember(weather) { buildEnvItems(weather) }
    var expanded by remember { mutableStateOf(false) }

    if (items.isEmpty()) return

    SharedTransitionLayout {
        AnimatedContent(
            targetState = expanded,
            label = "envDisplayMode",
            transitionSpec = {
                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
            }
        ) { isExpanded ->
            if (!isExpanded) {
                EnvironmentCompactGrid(
                    items = items,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                    onCardClick = { expanded = true }
                )
            } else {
                EnvironmentExpandedList(
                    items = items,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                    onCollapse = { expanded = false }
                )
            }
        }
    }
}

/** コンパクト表示: 2列グリッドで、カードごとに「アイコン+ラベル」「メーター」「大きな数値」「補足」を縦に並べる */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EnvironmentCompactGrid(
    items: List<EnvMeterData>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCardClick: () -> Unit
) {
    with(sharedTransitionScope) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        EnvironmentCompactCard(
                            item = item,
                            modifier = Modifier
                                .weight(1f)
                                .sharedBounds(
                                    rememberSharedContentState(key = "env-card-${item.key}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                ),
                            onClick = onCardClick
                        )
                    }
                    // 項目数が奇数の場合、最後の行の空いた列を埋めて幅を揃える
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentCompactCard(
    item: EnvMeterData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.05f),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.label, color = Color.White, fontSize = 13.sp, maxLines = 1)
            }

            MeterBar(progress = item.progress, trackBrush = item.trackBrush, thumbSize = 20.dp)

            Column {
                Text(item.valueLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                item.subLabel?.let {
                    Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }
    }
}

/** 展開表示: 縦一列で各項目を独立したカードにし、末尾に「コンパクトビューにする」ボタンを表示する */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EnvironmentExpandedList(
    items: List<EnvMeterData>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCollapse: () -> Unit
) {
    with(sharedTransitionScope) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items.forEach { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedBounds(
                            rememberSharedContentState(key = "env-card-${item.key}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        EnvironmentMeterRow(item = item)
                    }
                }
            }

            OutlinedButton(
                onClick = onCollapse,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
            ) {
                Text("コンパクトビューにする")
            }
        }
    }
}

/** 展開カード1件分の中身(アイコン+ラベル+数値の行、メーター、補足) */
@Composable
private fun EnvironmentMeterRow(item: EnvMeterData) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(item.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(item.valueLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        Spacer(Modifier.height(10.dp))
        MeterBar(progress = item.progress, trackBrush = item.trackBrush, thumbSize = 20.dp)
        item.subLabel?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

/**
 * AQI/UV/気圧/湿度共通のメーターバー。
 * trackBrushを指定するとグラデーションの帯、nullの場合は点線の帯(気圧・湿度のように「良い/悪い」の
 * 明確な指標が無い値向け)を描画する。コンパクトカード・展開カードの両方から呼ばれる共通部品。
 */
@Composable
private fun MeterBar(
    progress: Float,
    trackBrush: Brush?,
    modifier: Modifier = Modifier,
    thumbSize: Dp = 20.dp
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier
            .fillMaxWidth()
            .height(thumbSize)
            .onSizeChanged { size -> containerWidthPx = size.width }
    ) {
        if (trackBrush != null) {
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center)) {
                drawLine(
                    brush = trackBrush,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center)) {
                val dashCount = 24
                val gap = size.width / dashCount
                for (i in 0 until dashCount step 2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(gap * i, size.height / 2f),
                        end = Offset(gap * i + gap * 0.6f, size.height / 2f),
                        strokeWidth = size.height
                    )
                }
            }
        }

        val thumbSizePx = with(density) { thumbSize.roundToPx() }
        val thumbCenterPx = (containerWidthPx * progress).toInt()
        val thumbOffsetPx = (thumbCenterPx - thumbSizePx / 2).coerceIn(0, (containerWidthPx - thumbSizePx).coerceAtLeast(0))

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbOffsetPx, 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** ヨーロッパ式AQIの目安ラベル(参考値。厳密な基準値は環境省等の資料を参照) */
private fun aqiCategoryLabel(aqi: Int): String = when {
    aqi <= 20 -> "良好"
    aqi <= 40 -> "ある程度よい"
    aqi <= 60 -> "普通"
    aqi <= 80 -> "やや悪い"
    aqi <= 100 -> "悪い"
    else -> "非常に悪い"
}

private fun uvCategoryLabel(uv: Double): String = when {
    uv < 3 -> "問題なし"
    uv < 6 -> "注意"
    uv < 8 -> "やや危険"
    uv < 11 -> "危険"
    else -> "非常に危険"
}

private fun computeDayPhase(weather: WeatherUiState): DayPhase {
    return try {
        val now = LocalTime.now()
        val sunrise = LocalTime.parse(weather.sunrise)
        val sunset = LocalTime.parse(weather.sunset)
        when {
            now.isBefore(sunrise) || now.isAfter(sunset) -> DayPhase.NIGHT
            now.isBefore(sunrise.plusHours(1)) -> DayPhase.MORNING
            now.isAfter(sunset.minusHours(1)) -> DayPhase.EVENING
            else -> DayPhase.DAY
        }
    } catch (e: Exception) {
        DayPhase.DAY
    }
}

private fun todayLabel(): String {
    val today = java.time.LocalDate.now()
    val dow = listOf("月", "火", "水", "木", "金", "土", "日")[today.dayOfWeek.value - 1]
    return "${today.monthValue}/${today.dayOfMonth} ($dow)"
}