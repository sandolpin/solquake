package com.sandolpin.weatherquake.ui.quake

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.quake.P2pPoint
import com.sandolpin.weatherquake.data.settings.IntensityColorContrast
import com.sandolpin.weatherquake.ui.components.IntensityBadge

private enum class QuakeDisplayMode { BY_INTENSITY, BY_PREFECTURE }

/**
 * 住所文字列(P2P地震情報APIの addr)を「市町村のみ表示」設定に応じて整形する。
 * ONの場合、市区町村の末尾(市/区/町/村)より先の地区名(例: 「熊本市東区長嶺」の「長嶺」)を切り捨てる。
 * 該当する末尾が見つからない場合はそのまま返す。
 */
private fun formatAddr(addr: String, cityOnly: Boolean): String {
    if (!cityOnly) return addr
    val suffixes = charArrayOf('市', '区', '町', '村')
    val lastIndex = addr.indexOfLast { it in suffixes }
    return if (lastIndex >= 0) addr.substring(0, lastIndex + 1) else addr
}

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

    val level = IntensityLevel.fromP2pScale(quake.maxScale)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("詳細情報") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        with(sharedTransitionScope) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .sharedBounds(
                        rememberSharedContentState(key = "quake-bounds-${quake.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
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

                Spacer(Modifier.height(16.dp))

                when (displayMode) {
                    QuakeDisplayMode.BY_INTENSITY -> IntensityThenPrefectureList(quake.points, cityOnly, state.settings.intensityColorContrast)
                    QuakeDisplayMode.BY_PREFECTURE -> PrefectureThenIntensityList(quake.points, cityOnly)
                }
            }
        }
    }
}

/**
 * 「震度ごと」表示: 震度(チップ)で大きくグループ化し、その中をさらに都道府県ごとに分けて
 * 市区町村名を並べる。
 */
@Composable
private fun IntensityThenPrefectureList(points: List<P2pPoint>, cityOnly: Boolean, contrast: IntensityColorContrast) {
    val byIntensity = points.groupBy { IntensityLevel.fromP2pScale(it.scale) }
        .toList()
        .sortedByDescending { (level, _) -> level.ordinal }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        byIntensity.forEach { (level, levelPoints) ->
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

                val byPrefecture = levelPoints.groupBy { it.pref }
                byPrefecture.forEach { (pref, prefPoints) ->
                    Column {
                        Text(pref, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        val names = prefPoints.map { formatAddr(it.addr, cityOnly) }.distinct()
                        Text(names.joinToString("、"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

/**
 * 「都道府県ごと」表示: 都道府県(見出し)で大きくグループ化し、その中をさらに震度(チップ)ごとに
 * 分けて市区町村名を並べる。
 */
@Composable
private fun PrefectureThenIntensityList(points: List<P2pPoint>, cityOnly: Boolean) {
    val byPrefecture = points.groupBy { it.pref }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        byPrefecture.forEach { (pref, prefPoints) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pref, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                val byIntensity = prefPoints.groupBy { IntensityLevel.fromP2pScale(it.scale) }
                    .toList()
                    .sortedByDescending { (level, _) -> level.ordinal }

                byIntensity.forEach { (level, levelPoints) ->
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
                        Text(names.joinToString("、"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}