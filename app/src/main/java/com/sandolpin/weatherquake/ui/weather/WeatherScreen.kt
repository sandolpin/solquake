package com.sandolpin.weatherquake.ui.weather

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.weatherquake.data.location.CurrentLocationProvider
import com.sandolpin.weatherquake.data.settings.AppSettingsState
import com.sandolpin.weatherquake.data.settings.CardStyle
import com.sandolpin.weatherquake.data.settings.TemperatureFontStyle
import com.sandolpin.weatherquake.data.settings.WeatherBackgroundStyle
import com.sandolpin.weatherquake.data.weather.ForecastPoint
import com.sandolpin.weatherquake.data.weather.WeatherCondition
import com.sandolpin.weatherquake.data.weather.WeatherLocation
import com.sandolpin.weatherquake.data.weather.WeatherUiState
import com.sandolpin.weatherquake.ui.components.AppCard
import com.sandolpin.weatherquake.ui.components.WeatherBackground
import com.sandolpin.weatherquake.ui.components.WeatherIcon
import com.sandolpin.weatherquake.ui.theme.DayPhase
import com.sandolpin.weatherquake.ui.theme.temperatureDisplayStyle
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 天気画面のテキスト・アイコンの前景色。
 * 背景が「単色(白)」のときは白文字だと同化して読めなくなるため、濃色に切り替える。
 * それ以外(動的背景・単色黒)は白のまま。この画面配下のすべてのText/Icon/Canvas描画は
 * ここから色を取得することで、背景スタイルに応じて自動的に読める色へ切り替わるようにしている。
 */
private val LocalWeatherContentColor = staticCompositionLocalOf { Color.White }

/**
 * カードの下地色。
 * 以前はカードの背景に固定でColor.White(半透明)を使っていたため、
 * 「背景を単色(白)にするとカードが真っ白で見えない」
 * 「動的背景・単色(黒)でカードの不透明度を上げると、カードが白っぽくなり白文字が見えなくなる」
 * という2つの不具合が起きていた。
 * 動的背景では意図的にカードを白にしたいという要望があるため、
 * 「カードの下地色」と「カードの中の文字色(LocalWeatherCardContentColor)」を必ずペアで
 * 切り替えることで、カードが白になっても中の文字が読めなくなることが無いようにしている。
 */
private val LocalWeatherCardColor = staticCompositionLocalOf { Color.White }

/**
 * カードの「中」で使う文字・アイコン色。背景に直接乗る文字(LocalWeatherContentColor)とは別に管理する。
 * カードの下地色(LocalWeatherCardColor)と常に逆の明るさになるよう連動させ、
 * カードの塗りつぶし方(不透明度・スタイル)によらず必ず読めるようにしている。
 */
private val LocalWeatherCardContentColor = staticCompositionLocalOf { Color(0xFF1B1B1F) }

/**
 * ヘッダー部(地域名・日付・時刻・気温など、背景に直接乗るテキスト)に付ける影。
 * 動的背景の上でも文字の輪郭がはっきり見えるようにするための、ごく控えめなドロップシャドウ。
 */
private val HeaderTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.35f),
    offset = Offset(0f, 2f),
    blurRadius = 6f
)

/**
 * 天気画面本体。
 *
 * ホーム画面には複数地点を登録でき、[androidx.compose.foundation.pager.HorizontalPager]で
 * 左右スワイプして切り替えられるようにしている(WeatherViewModel.state.pagesが表示対象の地点一覧)。
 * 検索アイコンをタップすると、地点検索・検索履歴・ホーム画面への追加/並び替えをまとめて行える
 * ボトムシート(LocationSearchSheetContent)が開く。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    val pages = state.pages
    val context = LocalContext.current

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    // 位置情報の権限要求。許可されたら現在地取得を実行する。
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.useCurrentLocation(context)
    }
    val onUseCurrentLocation: () -> Unit = {
        if (CurrentLocationProvider.hasLocationPermission(context)) {
            viewModel.useCurrentLocation(context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // 検索結果を選んだ直後・現在地取得完了直後・並び替えチップをタップした直後などに、
    // 検索ダイアログを閉じつつ、ページャーを目的のページへ自動スクロールさせる。
    LaunchedEffect(state.pendingScrollToIndex, pages.size) {
        val target = state.pendingScrollToIndex
        if (target != null && target < pages.size) {
            searchExpanded = false
            pagerState.animateScrollToPage(target)
            viewModel.consumePendingScroll()
        }
    }

    // 背景色・時間帯判定は「今スワイプで見えているページ」の天気を基準にする
    val currentWeather = pages.getOrNull(pagerState.currentPage)?.weather
    val dayPhase = remember(currentWeather) { currentWeather?.let { computeDayPhase(it) } ?: DayPhase.DAY }

    val bgStyle = state.settings.weatherBackgroundStyle
    val contentColor = if (bgStyle == WeatherBackgroundStyle.PLAIN_WHITE) Color(0xFF1B1B1F) else Color.White
    val cardColor = when (bgStyle) {
        WeatherBackgroundStyle.PLAIN_BLACK -> Color(0xFF14161C)
        WeatherBackgroundStyle.PLAIN_WHITE -> Color(0xFFE7E8ED)
        WeatherBackgroundStyle.DYNAMIC -> Color.White
    }
    val cardContentColor = if (bgStyle == WeatherBackgroundStyle.PLAIN_BLACK) Color.White else Color(0xFF1B1B1F)

    CompositionLocalProvider(
        LocalWeatherContentColor provides contentColor,
        LocalWeatherCardColor provides cardColor,
        LocalWeatherCardContentColor provides cardContentColor
    ) {
        WeatherBackground(
            condition = currentWeather?.condition ?: WeatherCondition.CLEAR,
            dayPhase = dayPhase,
            style = state.settings.weatherBackgroundStyle
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(48.dp))

                // --- 検索アイコン(タップで検索・履歴・ホーム地点管理のシートを開く) ---
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { searchExpanded = true }) {
                        Surface(shape = CircleShape, color = contentColor.copy(alpha = 0.18f)) {
                            Icon(Icons.Filled.Search, contentDescription = "地点を検索", tint = contentColor, modifier = Modifier.padding(8.dp))
                        }
                    }
                }

                if (pages.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    PageIndicatorDots(count = pages.size, currentIndex = pagerState.currentPage, activeColor = contentColor)
                }

                if (pages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("読み込み中…", color = contentColor)
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { index ->
                        WeatherPageBody(
                            page = pages[index],
                            settings = state.settings,
                            contentColor = contentColor,
                            cardColor = cardColor,
                            cardContentColor = cardContentColor
                        )
                    }
                }
            }
        }
    }

    if (searchExpanded) {
        Dialog(
            onDismissRequest = { searchExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LocationSearchSheetContent(
                        query = state.searchQuery,
                        results = state.searchResults,
                        isSearching = state.isSearching,
                        history = state.settings.searchHistory,
                        homeLocations = pages.map { it.location },
                        isLocatingCurrentPosition = state.isLocatingCurrentPosition,
                        locationError = state.locationError,
                        onDismiss = { searchExpanded = false },
                        onQueryChange = viewModel::onSearchQueryChange,
                        onSelectResult = { location -> viewModel.selectSearchResult(location) },
                        onToggleHome = viewModel::toggleHomeLocation,
                        onPinDefault = viewModel::pinAsDefault,
                        onDelete = viewModel::deleteFromHistory,
                        onReorderTapToFront = viewModel::pinAsDefault,
                        onUseCurrentLocation = onUseCurrentLocation
                    )
                }
            }
        }
    }
}

/** ホーム画面が複数ページある場合に、現在何ページ目かを示すドットインジケーター */
@Composable
private fun PageIndicatorDots(count: Int, currentIndex: Int, activeColor: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            val isActive = index == currentIndex
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isActive) 12.dp else 9.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else activeColor.copy(alpha = 0.35f))
            )
        }
    }
}

/**
 * ホーム画面1ページぶんの中身(地点名・日付・天気アイコン・気温・各種カード)。
 * これまで単一地点だったWeatherScreen本体の表示ロジックをそのままこの関数へ移し、
 * HorizontalPagerの各ページから呼び出す形にした。
 */
@Composable
private fun WeatherPageBody(
    page: HomeWeatherPage,
    settings: AppSettingsState,
    contentColor: Color,
    cardColor: Color,
    cardContentColor: Color
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        val weather = page.weather
        if (weather == null) {
            Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (page.isLoading) "読み込み中…" else (page.errorMessage ?: "天気情報を取得できませんでした"),
                    color = contentColor
                )
            }
            return@Column
        }

        val dayPhase = remember(weather) { computeDayPhase(weather) }

        // --- ヘッダー: 都市名・日付・時刻、天気アイコン、気温・体感温度をすべて中央ぞろえで表示 ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                weather.location.name,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                style = TextStyle(shadow = HeaderTextShadow)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                todayLabel(),
                color = contentColor.copy(alpha = 0.9f),
                fontSize = 15.sp,
                style = TextStyle(shadow = HeaderTextShadow)
            )
            Text(
                "🕒 ${weather.updatedAtLabel}",
                color = contentColor.copy(alpha = 0.9f),
                fontSize = 14.sp,
                style = TextStyle(shadow = HeaderTextShadow)
            )

            Spacer(Modifier.height(12.dp))

            WeatherIcon(condition = weather.condition, dayPhase = dayPhase, size = 84.dp)

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    weather.condition.label,
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = HeaderTextShadow)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${weather.currentTemperature}℃",
                    color = contentColor,
                    style = temperatureDisplayStyle(settings.temperatureFont == TemperatureFontStyle.RECOMMENDED)
                        .copy(shadow = HeaderTextShadow)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "体感${weather.apparentTemperature}℃",
                    color = contentColor.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    style = TextStyle(shadow = HeaderTextShadow)
                )
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

        // --- 日照アーク+風(タップで詳細ダイアログを開く) ---
        var showSunWindDialog by remember { mutableStateOf(false) }
        if (settings.showSunTimes) {
            AppCard(
                opacity = settings.weatherCardOpacity,
                style = settings.weatherCardStyle,
                baseColor = cardColor,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clickable { showSunWindDialog = true }
            ) {
                Column(Modifier.padding(16.dp)) {
                    SunArc(sunrise = weather.sunrise, sunset = weather.sunset)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Air, contentDescription = null, tint = cardContentColor)
                        Spacer(Modifier.width(6.dp))
                        Text("${weather.windDirectionLabel}より${weather.windSpeed.toInt()}m/s", color = cardContentColor)
                    }
                }
            }
        }
        if (showSunWindDialog) {
            SunWindDetailDialog(
                weather = weather,
                onDismiss = { showSunWindDialog = false }
            )
        }

        Spacer(Modifier.height(12.dp))

        // --- これからの予報 ---
        if (settings.showHourlyForecast) {
            AppCard(
                opacity = settings.weatherCardOpacity,
                style = settings.weatherCardStyle,
                baseColor = cardColor,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                ForecastSection(weather = weather)
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- 空気の汚れ具合・UV強さ・気圧・湿度(コンパクト⇔展開切り替え) ---
        if (settings.showWeatherDetails) {
            Box(Modifier.padding(vertical = 6.dp)) {
                EnvironmentInfoSection(
                    weather = weather,
                    cardOpacity = settings.weatherCardOpacity,
                    cardStyle = settings.weatherCardStyle
                )
            }
        }

        // 下部ナビゲーションバー(項目切り替えバー)がコンテンツの上に浮いて重なる構成のため、
        // 最後のカードがバーの下に隠れないよう十分な余白を確保する
        Spacer(Modifier.height(110.dp))
    }
}

/**
 * 検索アイコンから開くボトムシートの中身。
 * - 検索欄に文字を入れている間は、その場でAPI検索結果を表示する。
 * - 空欄のときは「履歴」(🏠でホーム画面への追加/削除、📌でデフォルト地点に設定、🗑で削除)と
 *   「並び替え」(ホーム画面に登録済みの地点をチップで表示。タップで先頭=デフォルトに移動)を表示する。
 *
 * 本格的なドラッグ&ドロップ並び替えはCompose上でも実装がかなり複雑になるため、
 * 今回は「タップで先頭に移動」という簡易的な並び替えにしている。
 */
@Composable
private fun LocationSearchSheetContent(
    query: String,
    results: List<WeatherLocation>,
    isSearching: Boolean,
    history: List<WeatherLocation>,
    homeLocations: List<WeatherLocation>,
    isLocatingCurrentPosition: Boolean,
    locationError: String?,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectResult: (WeatherLocation) -> Unit,
    onToggleHome: (WeatherLocation, Boolean) -> Unit,
    onPinDefault: (WeatherLocation) -> Unit,
    onDelete: (WeatherLocation) -> Unit,
    onReorderTapToFront: (WeatherLocation) -> Unit,
    onUseCurrentLocation: () -> Unit
) {
    val homeNames = remember(homeLocations) { homeLocations.map { it.name }.toSet() }
    val defaultName = homeLocations.firstOrNull()?.name

    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("地点を検索", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "閉じる")
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("地点を検索") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "クリア")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onUseCurrentLocation,
            enabled = !isLocatingCurrentPosition,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLocatingCurrentPosition) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("取得中…")
            } else {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("現在地から取得")
            }
        }
        locationError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (query.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            when {
                isSearching -> Text("検索中…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                results.isEmpty() -> Text(
                    "該当する地点が見つかりませんでした",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                else -> results.forEach { location ->
                    Text(
                        location.name,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectResult(location) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        Text("履歴", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("でホーム画面に表示します", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                "検索履歴はまだありません",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            history.forEach { location ->
                HistoryRow(
                    location = location,
                    isPinnedDefault = location.name == defaultName,
                    isOnHome = location.name in homeNames,
                    onPinClick = { onPinDefault(location) },
                    onHomeToggle = { onToggleHome(location, location.name !in homeNames) },
                    onDeleteClick = { onDelete(location) }
                )
            }
        }

        if (homeLocations.size > 1) {
            Spacer(Modifier.height(20.dp))
            Text("並び替え", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                homeLocations.forEachIndexed { index, location ->
                    FilterChip(
                        selected = index == 0,
                        onClick = { onReorderTapToFront(location) },
                        label = { Text(location.name) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "一番左の地域がアプリ起動時に表示されます(タップで先頭に移動できます)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

/** 検索シートの履歴1行ぶん。地点名+📌(デフォルトにする)+🏠(ホーム画面に表示)+🗑(削除)。 */
@Composable
private fun HistoryRow(
    location: WeatherLocation,
    isPinnedDefault: Boolean,
    isOnHome: Boolean,
    onPinClick: () -> Unit,
    onHomeToggle: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(location.name, fontSize = 16.sp, modifier = Modifier.weight(1f))

        IconButton(onClick = onPinClick) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "デフォルト地点にする",
                tint = if (isPinnedDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        IconButton(onClick = onHomeToggle) {
            Icon(
                Icons.Filled.Home,
                contentDescription = "ホーム画面に表示",
                tint = if (isOnHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "履歴から削除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/** 最低〜最高気温のグラデーションバーに、現在気温の位置をマーカーで示すスライダー(表示専用) */
@Composable
private fun TemperatureRangeSlider(current: Int, min: Int, max: Int) {
    val contentColor = LocalWeatherContentColor.current
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
                    .background(contentColor)
            ) {
                Box(Modifier.padding(4.dp).fillMaxSize().clip(CircleShape).background(Color(0xFF616161)))
            }

            Text(
                "$current℃",
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(markerOffsetPx, -with(density) { 16.dp.roundToPx() }) }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("最低:${min}℃", color = contentColor.copy(alpha = 0.85f), fontSize = 12.sp)
            Text("最高:${max}℃", color = contentColor.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

/** 日の出〜日の入りを点線+太陽アイコンのマーカーで表す(画像デザインに合わせたスタイル) */
@Composable
private fun SunArc(sunrise: String, sunset: String, contentColor: Color = LocalWeatherCardContentColor.current) {
    val progress = remember(sunrise, sunset) { sunProgress(sunrise, sunset) }
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { size -> containerWidthPx = size.width }
        ) {
            // 点線トラック
            Canvas(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center)) {
                val dashCount = 32
                val gap = size.width / dashCount
                for (i in 0 until dashCount step 2) {
                    drawLine(
                        color = contentColor.copy(alpha = 0.35f),
                        start = Offset(gap * i, size.height / 2f),
                        end = Offset(gap * i + gap * 0.6f, size.height / 2f),
                        strokeWidth = size.height
                    )
                }
            }

            // 現在時刻の位置に太陽アイコンのマーカー
            val markerSize = 28.dp
            val markerSizePx = with(density) { markerSize.roundToPx() }
            val t = progress.coerceIn(0f, 1f)
            val markerCenterPx = (containerWidthPx * t).toInt()
            val markerOffsetPx = (markerCenterPx - markerSizePx / 2).coerceIn(0, (containerWidthPx - markerSizePx).coerceAtLeast(0))

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(markerOffsetPx, 0) }
                    .size(markerSize),
                shape = CircleShape,
                color = Color(0xFFF2A354)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sunrise, color = contentColor, fontSize = 13.sp)
            Text(sunset, color = contentColor, fontSize = 13.sp)
        }
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

/** 日の出/日の入りまでの残り時間を"H:MM"形式で計算する。現在が昼間なら日の入りまで、夜間なら次の日の出まで。 */
private fun computeSunCountdown(sunrise: String, sunset: String): Pair<String, String> {
    return try {
        val now = LocalTime.now()
        val rise = LocalTime.parse(sunrise)
        val set = LocalTime.parse(sunset)
        val (target, label) = when {
            now.isBefore(rise) -> rise to "日の出"
            now.isBefore(set) -> set to "日の入り"
            else -> rise to "日の出" // 日没後は翌日の日の出までの残り(日をまたぐ)
        }
        val nowSeconds = now.toSecondOfDay()
        val targetSeconds = target.toSecondOfDay()
        val diffSeconds = if (targetSeconds >= nowSeconds) {
            targetSeconds - nowSeconds
        } else {
            (24 * 3600 - nowSeconds) + targetSeconds
        }
        val h = diffSeconds / 3600
        val m = (diffSeconds % 3600) / 60
        "%d:%02d".format(h, m) to label
    } catch (e: Exception) {
        "--:--" to "日の入り"
    }
}

// ============================== 日照・風の詳細ダイアログ ==============================

/**
 * 日照カードをタップすると開くダイアログ。
 * - 日の出/日の入りまでの残り時間
 * - SunArc(同じ点線+太陽アイコンのスタイル)
 * - 風速・風向
 * - 端末のコンパス(回転ベクトルセンサー)から向いている方角を取得し、
 *   気象データの風向(どちらから吹いてくるか)と比較して「向かい風/追い風/横風」を判定して表示する
 */
@Composable
private fun SunWindDetailDialog(
    weather: WeatherUiState,
    onDismiss: () -> Unit
) {
    val (countdown, eventLabel) = remember(weather.sunrise, weather.sunset) {
        computeSunCountdown(weather.sunrise, weather.sunset)
    }
    val deviceHeading = rememberDeviceHeadingDegrees()
    val windFromDegree = remember(weather.windDirectionLabel) {
        windDirectionLabelToDegree(weather.windDirectionLabel)
    }
    // 風が実際に流れていく方向(=風向+180度)を、端末が向いている方角を基準にした相対角度に変換する。
    // 0度=自分が向いている方向へ風が流れていく(=追い風)、180度=自分に向かって風が流れてくる(=向かい風)。
    val relativeFlowAngle = remember(windFromDegree, deviceHeading) {
        (((windFromDegree + 180f) - deviceHeading) % 360f + 360f) % 360f
    }
    val windRelationLabel = when {
        relativeFlowAngle in 135f..225f -> "向かい風"
        relativeFlowAngle <= 45f || relativeFlowAngle >= 315f -> "追い風"
        else -> "横風"
    }

    // このダイアログは天気画面の背景設定(動的背景/単色)ではなく、アプリ全体のダークモード設定に
    // 追従させたいため、天気カード用のCompositionLocal(LocalWeatherCardColor等)ではなく
    // MaterialTheme.colorSchemeを直接使う(検索ダイアログと同じ方針)。
    val dialogColor = MaterialTheme.colorScheme.surface
    val dialogContentColor = MaterialTheme.colorScheme.onSurface

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = dialogColor) {
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "あと${countdown}で${eventLabel}",
                    color = dialogContentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(16.dp))
                SunArc(sunrise = weather.sunrise, sunset = weather.sunset, contentColor = dialogContentColor)

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Air, contentDescription = null, tint = dialogContentColor)
                    Spacer(Modifier.width(6.dp))
                    Text("${weather.windDirectionLabel}より${weather.windSpeed.toInt()}m/s", color = dialogContentColor)
                }

                Spacer(Modifier.height(28.dp))

                // 風向コンパス: 円の中心=自分。矢印は風が流れていく方向(端末の向きが基準)を指す。
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color(0xFF29B6F6), modifier = Modifier.fillMaxSize()) {}
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(56.dp)
                            .rotate(relativeFlowAngle)
                    )
                }

                Spacer(Modifier.height(20.dp))
                val annotatedLabel = buildAnnotatedString {
                    append("いま")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)) {
                        append(windRelationLabel)
                    }
                    append("をうけています")
                }
                Text(annotatedLabel, color = dialogContentColor, fontSize = 16.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(50)) {
                    Text("閉じる")
                }
            }
        }
    }
}

/** 8方位ラベル("北"・"北東"等)を、風向の角度(0=北、時計回り)に変換する。WeatherRepository側の変換と対にしている。 */
private fun windDirectionLabelToDegree(label: String): Float = when (label) {
    "北" -> 0f
    "北東" -> 45f
    "東" -> 90f
    "南東" -> 135f
    "南" -> 180f
    "南西" -> 225f
    "西" -> 270f
    "北西" -> 315f
    else -> 0f
}

/**
 * 端末の回転ベクトルセンサー(TYPE_ROTATION_VECTOR)から、現在端末が向いている方角(0=北、時計回り)を
 * 度数で返すComposable。位置情報の権限とは異なり、センサー自体は実行時権限が不要なため
 * 追加のパーミッション要求は行っていない。
 */
@Composable
private fun rememberDeviceHeadingDegrees(): Float {
    val context = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (degrees < 0f) degrees += 360f
                heading = degrees
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return heading
}

/**
 * 「これからの予報」。アイコン行と下の折れ線グラフを同じ横スクロール(rememberScrollState)に
 * 乗せることで、スクロールしても両者がズレずに一緒に動くようにしている。
 * 以前はアイコン行(LazyRow)とグラフ(固定幅Canvas)が別々のスクロール軸だったため、
 * アイコン行をスクロールしてもグラフだけ取り残されてズレる不具合があった。
 *
 * 各アイテムをタップすると、その地点の詳しい情報(時刻/天気/気温レンジ/降水量/風)を
 * 吹き出しで表示する。吹き出しの横位置は、タップした項目の実際の描画位置(onGloballyPositioned)
 * から算出しているため、横スクロールしていても常にタップした項目の真上に表示される。
 */
@Composable
private fun ForecastSection(weather: WeatherUiState) {
    val contentColor = LocalWeatherCardContentColor.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val tabs = listOf("15分ごと", "1時間ごと", "日ごと")
    val points = when (selectedTab) {
        0 -> weather.minutely15
        1 -> weather.hourly
        else -> weather.daily
    }
    val itemWidth = 68.dp
    val scrollState = rememberScrollState()

    // タブを切り替えたら選択中の吹き出しは閉じる(indexの意味が変わってしまうため)
    LaunchedEffect(selectedTab) { selectedIndex = null }

    // 「これからの予報」コンテナ自体のroot座標と、各アイテムのroot座標を記録しておき、
    // その差分から「コンテナ内でのアイテムの位置(横スクロール分も自動的に反映される)」を求める。
    var containerPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val itemAnchors = remember { mutableStateMapOf<Int, Offset>() } // index -> (中心X, 上端Y) コンテナ基準

    // 吹き出しが画面の左右からはみ出さないよう、実際の画面幅を基準に
    // 「コンテナ内座標系での吹き出し左端の許容範囲」を求めておく。
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val bubbleMarginPx = with(density) { 12.dp.toPx() }
    val minBubbleLeftPx = bubbleMarginPx - containerPositionInRoot.x
    val maxBubbleRightPx = screenWidthPx - bubbleMarginPx - containerPositionInRoot.x

    Box(
        Modifier
            .padding(16.dp)
            .onGloballyPositioned { coords -> containerPositionInRoot = coords.positionInRoot() }
    ) {
        Column {
            Text("これからの予報", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))

            Column(Modifier.horizontalScroll(scrollState)) {
                Row(Modifier.width(itemWidth * points.size)) {
                    points.forEachIndexed { index, point ->
                        Box(
                            modifier = Modifier
                                .width(itemWidth)
                                .onGloballyPositioned { coords ->
                                    val posInRoot = coords.positionInRoot()
                                    val centerX = posInRoot.x + coords.size.width / 2f
                                    itemAnchors[index] = Offset(
                                        centerX - containerPositionInRoot.x,
                                        posInRoot.y - containerPositionInRoot.y
                                    )
                                }
                                .clickable {
                                    selectedIndex = if (selectedIndex == index) null else index
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ForecastColumn(point)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ForecastLineGraph(
                    points = points,
                    itemWidth = itemWidth,
                    modifier = Modifier.width(itemWidth * points.size).height(30.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = contentColor
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
        }

        val index = selectedIndex
        val point = index?.let { points.getOrNull(it) }
        val anchor = index?.let { itemAnchors[it] }
        if (point != null && anchor != null) {
            ForecastDetailBubble(
                point = point,
                dailyMinTemp = weather.tempMinToday,
                dailyMaxTemp = weather.tempMaxToday,
                anchor = anchor,
                minLeftPx = minBubbleLeftPx,
                maxRightPx = maxBubbleRightPx,
                onDismiss = { selectedIndex = null }
            )
        }
    }
}

@Composable
private fun ForecastColumn(point: ForecastPoint) {
    val contentColor = LocalWeatherCardContentColor.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(point.label, color = contentColor, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        // 絵文字ではなく、天気画面の他の場所と同じWeatherIconコンポーネントを小さいサイズで使う
        WeatherIcon(condition = point.condition, dayPhase = DayPhase.DAY, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Text("${point.temperature}℃", color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("${point.precipitationMm}mm", color = contentColor.copy(alpha = 0.75f), fontSize = 11.sp)
        Text("${point.windSpeed.toInt()}m/s", color = contentColor.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

/**
 * 予報カード下部の折れ線グラフ(気温の推移を簡易的に可視化)。
 * 各点のx座標は、アイコン行の各アイテム(幅itemWidthのBox)の「中心」と一致させている
 * (itemWidth×(index+0.5))。以前は size.width/(points.size-1) で端から端に均等配置しており、
 * アイコン行の中央寄せ配置とズレが生じていた。
 */
@Composable
private fun ForecastLineGraph(points: List<ForecastPoint>, itemWidth: Dp, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val contentColor = LocalWeatherCardContentColor.current
    val minTemp = points.minOf { it.temperature }
    val maxTemp = points.maxOf { it.temperature }
    val range = max(1, maxTemp - minTemp)

    Canvas(modifier = modifier) {
        val itemWidthPx = itemWidth.toPx()
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, point ->
            val x = itemWidthPx * (index + 0.5f)
            val normalized = (point.temperature - minTemp).toFloat() / range
            val y = size.height - (normalized * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = contentColor.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))

        points.forEachIndexed { index, point ->
            val x = itemWidthPx * (index + 0.5f)
            val normalized = (point.temperature - minTemp).toFloat() / range
            val y = size.height - (normalized * size.height)
            drawCircle(color = contentColor, radius = 4f, center = Offset(x, y))
        }
    }
}

/**
 * 「これからの予報」の各アイテムをタップした時に出す詳細吹き出し。
 * anchor(タップした項目のコンテナ内での中心X・上端Y)を基準に、吹き出しの先端(三角形)が
 * その項目の真上に来るよう配置する。
 *
 * 吹き出し本体の横幅は固定(BUBBLE_WIDTH)にしている。Composeでは実際にレイアウトされるまで
 * 自分自身の幅が分からないため、「anchorを中心に据えて幅の半分だけ左にずらす」計算をするには
 * 幅を先に確定させておく必要があり、そのための単純化。
 * ダークモード設定にも追従するよう、天気カード用の色ではなくMaterialTheme.colorSchemeを使う。
 */
@Composable
private fun ForecastDetailBubble(
    point: ForecastPoint,
    dailyMinTemp: Int,
    dailyMaxTemp: Int,
    anchor: Offset,
    minLeftPx: Float,
    maxRightPx: Float,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val bubbleContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 「晴れ時々曇り」のような長い天気文言でも折り返さず1行で収まるよう、吹き出しの幅は
    // 固定値ではなく中身に合わせて自動で伸びるようにする(widthIn(min, max)で伸縮可能にし、
    // 実際に測定された幅をonSizeChangedで受け取って位置計算に使う)。
    var measuredWidthPx by remember { mutableFloatStateOf(with(density) { 200.dp.toPx() }) }

    val rawOffsetX = anchor.x - measuredWidthPx / 2f
    // 画面の左右からはみ出さないよう、呼び出し側から渡された許容範囲(minLeftPx〜maxRightPx)に収める。
    val clampedMaxLeft = (maxRightPx - measuredWidthPx).coerceAtLeast(minLeftPx)
    val offsetXPx = rawOffsetX.coerceIn(minLeftPx, clampedMaxLeft)
    // 吹き出し本体+三角形の高さぶん、タップした項目の上端よりさらに上に表示する(固定の目安値)
    val offsetYPx = anchor.y - with(density) { 186.dp.toPx() }

    Box(
        Modifier.offset {
            IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt())
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bubbleColor,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(min = 170.dp, max = 300.dp)
                    .onSizeChanged { size -> measuredWidthPx = size.width.toFloat() }
                    .clickable(onClick = onDismiss)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(point.label, color = bubbleContentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        WeatherIcon(condition = point.condition, dayPhase = DayPhase.DAY, size = 28.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            point.condition.label,
                            color = bubbleContentColor,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${point.temperature}°", color = bubbleContentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(10.dp))
                    ForecastPointRangeBar(current = point.temperature, min = dailyMinTemp, max = dailyMaxTemp, contentColor = bubbleContentColor)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = bubbleContentColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${point.precipitationMm}mm", color = bubbleContentColor, fontSize = 13.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Air, contentDescription = null, tint = bubbleContentColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${point.windSpeed}m/s", color = bubbleContentColor, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            // 吹き出しの三角形(下向き)。吹き出し本体の幅が可変になっても、常にその中央に配置される
            // (Column(horizontalAlignment = CenterHorizontally)の効果)。
            Canvas(modifier = Modifier.size(width = 18.dp, height = 9.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = bubbleColor)
            }
        }
    }
}

/** 吹き出し内の気温レンジバー(当日の最低〜最高気温の中で、この地点の気温がどこにあるかを示す) */
@Composable
private fun ForecastPointRangeBar(current: Int, min: Int, max: Int, contentColor: Color) {
    val safeMax = max(max, min + 1)
    val ratio = ((current - min).toFloat() / (safeMax - min).toFloat()).coerceIn(0f, 1f)

    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center)) {
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
            val markerSize = 14.dp
            val markerOffset = (maxWidth - markerSize) * ratio
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = markerOffset)
                    .size(markerSize)
                    .clip(CircleShape)
                    .background(contentColor)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${min}°", color = contentColor.copy(alpha = 0.75f), fontSize = 11.sp)
            Text("${max}°", color = contentColor.copy(alpha = 0.75f), fontSize = 11.sp)
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
 *
 * cardOpacity/cardStyleは設定画面の「カードの透明度」「カードのスタイル」をそのまま受け取り、
 * 下のenvCardAlpha()で他のカード(AppCard)と同じ計算式のalphaに変換して適用する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EnvironmentInfoSection(weather: WeatherUiState, cardOpacity: Float, cardStyle: CardStyle) {
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
                    cardOpacity = cardOpacity,
                    cardStyle = cardStyle,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                    onCardClick = { expanded = true }
                )
            } else {
                EnvironmentExpandedList(
                    items = items,
                    cardOpacity = cardOpacity,
                    cardStyle = cardStyle,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                    onCollapse = { expanded = false }
                )
            }
        }
    }
}

/**
 * 設定の「カードのスタイル」「カードの透明度」から、環境情報カードに使う実際のalpha値を計算する。
 * AppCard(他のカードで使用している共通カード)と全く同じ計算式(FILLEDは+0.5f、GLASSMORPHISMは
 * そのまま)にすることで、天気画面内の全カードの見た目に一貫性を持たせている。
 */
private fun envCardAlpha(opacity: Float, style: CardStyle): Float = when (style) {
    CardStyle.FILLED -> (opacity + 0.5f).coerceAtMost(1f)
    CardStyle.GLASSMORPHISM -> opacity
}

/** コンパクト表示: 2列グリッドで、カードごとに「アイコン+ラベル」「メーター」「大きな数値」「補足」を縦に並べる */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun EnvironmentCompactGrid(
    items: List<EnvMeterData>,
    cardOpacity: Float,
    cardStyle: CardStyle,
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
                            cardOpacity = cardOpacity,
                            cardStyle = cardStyle,
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
    cardOpacity: Float,
    cardStyle: CardStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor = LocalWeatherCardContentColor.current
    val cardColor = LocalWeatherCardColor.current
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.05f),
        shape = RoundedCornerShape(24.dp),
        color = cardColor.copy(alpha = envCardAlpha(cardOpacity, cardStyle))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.label, color = contentColor, fontSize = 13.sp, maxLines = 1)
            }

            MeterBar(progress = item.progress, trackBrush = item.trackBrush, thumbSize = 20.dp)

            Column {
                Text(item.valueLabel, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                item.subLabel?.let {
                    Text(it, color = contentColor.copy(alpha = 0.85f), fontSize = 12.sp)
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
    cardOpacity: Float,
    cardStyle: CardStyle,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCollapse: () -> Unit
) {
    val contentColor = LocalWeatherContentColor.current
    val cardColor = LocalWeatherCardColor.current
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
                    color = cardColor.copy(alpha = envCardAlpha(cardOpacity, cardStyle))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        EnvironmentMeterRow(item = item)
                    }
                }
            }

            OutlinedButton(
                onClick = onCollapse,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                border = BorderStroke(1.dp, contentColor.copy(alpha = 0.6f))
            ) {
                Text("コンパクトビューにする")
            }
        }
    }
}

/** 展開カード1件分の中身(アイコン+ラベル+数値の行、メーター、補足) */
@Composable
private fun EnvironmentMeterRow(item: EnvMeterData) {
    val contentColor = LocalWeatherCardContentColor.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(item.label, color = contentColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(item.valueLabel, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        Spacer(Modifier.height(10.dp))
        MeterBar(progress = item.progress, trackBrush = item.trackBrush, thumbSize = 20.dp)
        item.subLabel?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = contentColor.copy(alpha = 0.85f), fontSize = 12.sp)
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
    val contentColor = LocalWeatherCardContentColor.current
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
                        color = contentColor.copy(alpha = 0.6f),
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
                .background(contentColor)
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