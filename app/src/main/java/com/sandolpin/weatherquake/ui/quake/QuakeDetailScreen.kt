package com.sandolpin.weatherquake.ui.quake

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.quake.P2pPoint
import com.sandolpin.weatherquake.data.quake.QuakeCardState
import com.sandolpin.weatherquake.data.quake.tsunamiLabel
import com.sandolpin.weatherquake.data.settings.IntensityColorContrast
import com.sandolpin.weatherquake.ui.components.IntensityBadge

private enum class QuakeDisplayMode { BY_INTENSITY, BY_PREFECTURE }

// formatAddr() / buildCityIntensityMap() は QuakeAddrUtil.kt (同じパッケージ) に共通化した。
// QuakeCityIntensityMap側でも市町村タップ時の内訳表示に同じロジックが必要になったため。

/**
 * 地震情報の詳細画面。
 *
 * [画面構成の方針]
 * 地図を画面いっぱいに表示し、テキスト情報(震源・震度リスト・API情報など)はすべて
 * ボトムシートにまとめる構成にしている(QuakeFullHistoryScreenと同じ考え方)。
 * 以前は縦スクロールのColumn内に地図を埋め込んでいたが、その構成だと
 * 「地図の操作(パン/タップ)」と「画面全体のスクロール」が競合しやすく、
 * 安全に両立させるのが難しかったため、この構成に変更した。
 *
 * [選択中の市町村(selectedCityName)について]
 * 地図側(QuakeCityIntensityMap)とボトムシート側(市町村名テキスト)の両方から
 * 同じ市町村を選択できるようにするため、選択状態はこの画面でホイストして一元管理している。
 * どちらから選んでも、地図上でその市町村の縁取りが強調表示され、ポップアップが追従する。
 *
 * [ボトムシートの半透明化・地図のぼかしについて]
 * シートが展開(Expanded)されている間は地図側を軽くぼかし、シート自体は半透明の下地にすることで
 * 「すりガラス」のような、背景の地図とシートを見分けやすい見た目にしている。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuakeDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    quakeId: String?,
    onBack: () -> Unit,
    viewModel: QuakeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val quake = state.quakes.firstOrNull { it.id == quakeId } ?: return

    var displayMode by remember { mutableStateOf(QuakeDisplayMode.BY_INTENSITY) }
    var cityOnly by remember { mutableStateOf(true) }
    // 地図上で選択中(縁取り強調・ポップアップ表示対象)の市町村名。
    // 地図タップ・ボトムシート内の市町村名タップの両方から更新される。
    var selectedCityName by remember { mutableStateOf<String?>(null) }

    val level = IntensityLevel.fromP2pScale(quake.maxScale)

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded

    with(sharedTransitionScope) {
        BottomSheetScaffold(
            modifier = Modifier
                .sharedBounds(
                    rememberSharedContentState(key = "quake-bounds-${quake.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                ),
            scaffoldState = scaffoldState,
            sheetPeekHeight = 300.dp,
            // シートを半透明にして、背後の地図(ぼかし表示)がうっすら透けて見える
            // 「すりガラス」風の見た目にする。
            sheetContainerColor = BottomSheetDefaults.ContainerColor.copy(alpha = 0.85f),
            sheetDragHandle = {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
            },
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("最大震度", fontSize = 11.sp)
                            IntensityBadge(level = level, size = 64.dp, contrast = state.settings.intensityColorContrast)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(quake.occurredAtLabel + "ごろ", fontSize = 12.sp)
                            Text(quake.hypocenterName, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            val depthLabel = quake.depthKm?.let { "深さ${it}km" } ?: "深さ不明"
                            val magLabel = quake.magnitude?.let { "規模 M$it" } ?: "規模不明"
                            Text("$depthLabel / $magLabel", fontSize = 13.sp)
                        }
                    }

                    Text(
                        quake.issueType.displayName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("表示方法", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = displayMode == QuakeDisplayMode.BY_INTENSITY,
                            onClick = { displayMode = QuakeDisplayMode.BY_INTENSITY },
                            label = { Text("震度ごと") }
                        )
                        FilterChip(
                            selected = displayMode == QuakeDisplayMode.BY_PREFECTURE,
                            onClick = { displayMode = QuakeDisplayMode.BY_PREFECTURE },
                            label = { Text("都道府県ごと") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("市町村のみ表示", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = cityOnly, onCheckedChange = { cityOnly = it })
                    }

                    Text(
                        "地名をタップすると地図でその場所を強調表示します",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    when (displayMode) {
                        QuakeDisplayMode.BY_INTENSITY -> IntensityThenPrefectureList(
                            points = quake.points,
                            cityOnly = cityOnly,
                            contrast = state.settings.intensityColorContrast,
                            onCityClick = { name -> selectedCityName = name }
                        )
                        QuakeDisplayMode.BY_PREFECTURE -> PrefectureThenIntensityList(
                            points = quake.points,
                            cityOnly = cityOnly,
                            onCityClick = { name -> selectedCityName = name }
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    QuakeRawApiInfoSection(quake)
                    Spacer(Modifier.height(32.dp))
                }
            }
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                QuakeCityIntensityMap(
                    points = quake.points,
                    epicenterLatitude = quake.latitude,
                    epicenterLongitude = quake.longitude,
                    selectedCityName = selectedCityName,
                    onCitySelected = { name -> selectedCityName = name },
                    externalBlurActive = isSheetExpanded,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            }
        }
    }
}

/**
 * APIから取得できた情報をすべて表示するセクション。
 * 画面表示用に整形済みの項目(震源名・震度など)は上部で既に表示しているため、
 * ここではそれ以外の「生の」情報(ID・発表元・訂正・津波情報・生時刻など)を並べる。
 */
@Composable
private fun QuakeRawApiInfoSection(quake: QuakeCardState) {
    Text("API情報", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ApiInfoRow("ID", quake.id)
            ApiInfoRow("コード", quake.code.toString())
            ApiInfoRow("発表種類", quake.issueType.displayName)
            ApiInfoRow("発表元", quake.issueSource ?: "不明")
            ApiInfoRow("発表時刻", quake.issueTime ?: "不明")
            quake.issueCorrect?.let { ApiInfoRow("訂正区分", it) }
            ApiInfoRow("発生時刻(API)", quake.rawOccurredAt.ifBlank { "不明" })
            ApiInfoRow("国内の津波", tsunamiLabel(quake.domesticTsunami))
            quake.foreignTsunami?.let { ApiInfoRow("海外の津波", tsunamiLabel(it)) }
            ApiInfoRow("緯度・経度", "${quake.latitude}, ${quake.longitude}")
        }
    }
}

@Composable
private fun ApiInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * 市町村名の一覧を、1つずつタップ可能なテキストとして折り返し表示する共通部品。
 * 以前は複数の市町村名を「、」でまとめて1本のTextにしていたため、個々の地名をタップできなかった。
 * FlowRowで折り返しつつ、地名の間にだけ読点(、)を挟む形にして、見た目は従来とほぼ変えていない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClickableCityNames(
    names: List<String>,
    fontSize: TextUnit,
    onCityClick: (String) -> Unit
) {
    FlowRow(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        names.forEachIndexed { index, name ->
            Text(
                name,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                modifier = Modifier.clickable { onCityClick(name) }
            )
            if (index != names.lastIndex) {
                Text("、", fontWeight = FontWeight.Bold, fontSize = fontSize)
            }
        }
    }
}

/**
 * 「震度ごと」表示: 震度(チップ)で大きくグループ化し、その中をさらに都道府県ごとに分けて
 * 市区町村名を並べる。
 */
@Composable
private fun IntensityThenPrefectureList(
    points: List<P2pPoint>,
    cityOnly: Boolean,
    contrast: IntensityColorContrast,
    onCityClick: (String) -> Unit
) {
    val byIntensity = points.groupBy { IntensityLevel.fromP2pScale(it.scale) }
        .toList()
        .sortedByDescending { (level, _) -> level.ordinal }

    Column {
        byIntensity.forEachIndexed { intensityIndex, (level, levelPoints) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = level.bgColor) {
                    Text(
                        "震度${level.formalLabel}",
                        color = level.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                val byPrefecture = levelPoints.groupBy { it.pref }.toList()
                byPrefecture.forEachIndexed { prefIndex, (pref, prefPoints) ->
                    Column {
                        Text(pref, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        val names = prefPoints.map { formatAddr(it.addr, cityOnly) }.distinct()
                        ClickableCityNames(names = names, fontSize = 18.sp, onCityClick = onCityClick)
                    }
                    if (prefIndex != byPrefecture.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (intensityIndex != byIntensity.lastIndex) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(thickness = 2.dp)
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/**
 * 「都道府県ごと」表示: 都道府県(見出し)で大きくグループ化し、その中をさらに震度(チップ)ごとに
 * 分けて市区町村名を並べる。
 */
@Composable
private fun PrefectureThenIntensityList(
    points: List<P2pPoint>,
    cityOnly: Boolean,
    onCityClick: (String) -> Unit
) {
    val byPrefecture = points.groupBy { it.pref }.toList()

    Column {
        byPrefecture.forEachIndexed { prefIndex, (pref, prefPoints) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pref, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                val byIntensity = prefPoints.groupBy { IntensityLevel.fromP2pScale(it.scale) }
                    .toList()
                    .sortedByDescending { (level, _) -> level.ordinal }

                byIntensity.forEachIndexed { intensityIndex, (level, levelPoints) ->
                    Column {
                        Surface(shape = RoundedCornerShape(6.dp), color = level.bgColor) {
                            Text(
                                "震度${level.formalLabel}",
                                color = level.textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val names = levelPoints.map { formatAddr(it.addr, cityOnly) }.distinct()
                        ClickableCityNames(names = names, fontSize = 16.sp, onCityClick = onCityClick)
                    }
                    if (intensityIndex != byIntensity.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (prefIndex != byPrefecture.lastIndex) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(thickness = 2.dp)
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}