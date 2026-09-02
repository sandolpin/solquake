package com.sandolpin.weatherquake.ui.quake

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.quake.QuakeCardState
import com.sandolpin.weatherquake.service.ConnectionStatus
import com.sandolpin.weatherquake.ui.components.IntensityBadge
import com.sandolpin.weatherquake.ui.eew.EewCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 「過去の地震履歴」に表示する最大件数。これを超える分は全履歴画面(QuakeFullHistoryScreen)で確認する。 */
private const val HISTORY_PREVIEW_COUNT = 3

/**
 * 地震情報一覧画面。
 *
 * [変更点] 「過去の地震履歴」は直近3件のみをプレビュー表示し、それ以上は
 * ヘッダー右の矢印ボタンから遷移する全履歴画面(地図+全件リスト)で確認する形にした。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun QuakeListScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCardClick: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    viewModel: QuakeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionStatusBar(state.connectionStatus, state.lastUpdated)

        if (state.eewCards.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "緊急地震速報は発表されていません",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.eewCards.forEach { card ->
                    EewCard(state = card, intensityContrast = state.settings.intensityColorContrast)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenHistory),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("過去の地震履歴", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "地震履歴をすべて見る")
            }
        }

        val previewQuakes = remember(state.quakes) { state.quakes.take(HISTORY_PREVIEW_COUNT) }

        previewQuakes.forEachIndexed { index, quake ->
            with(sharedTransitionScope) {
                Column {
                    QuakeHistoryCard(
                        quake = quake,
                        contrast = state.settings.intensityColorContrast,
                        modifier = Modifier
                            .sharedBounds(
                                rememberSharedContentState(key = "quake-bounds-${quake.id}_$index"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clickable { onCardClick(quake.id) }
                    )
                    Text(
                        quake.issueType.displayName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBar(status: ConnectionStatus, lastUpdated: Long) {
    val (label, color) = when (status) {
        is ConnectionStatus.Connected -> "接続済み" to Color(0xFF2E7D32)
        is ConnectionStatus.Connecting -> "接続中…" to Color(0xFFF9A825)
        is ConnectionStatus.Disconnected -> status.statusLabel to Color(0xFFC62828)
    }
    val timeLabel = remember(lastUpdated) { SimpleDateFormat("HH:mm.ss", Locale.JAPAN).format(Date(lastUpdated)) }

    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = color, modifier = Modifier.width(10.dp).height(10.dp)) {}
                Spacer(Modifier.width(8.dp))
                Text(label, color = color, fontWeight = FontWeight.Bold)
            }
            Text("${timeLabel}更新", color = color)
        }
    }
}

/**
 * 地震履歴1件分のカード。QuakeListScreen(プレビュー3件)・QuakeFullHistoryScreen(全件)の
 * 両方から使うためinternalにしている。
 */
@Composable
internal fun QuakeHistoryCard(quake: QuakeCardState, contrast: com.sandolpin.weatherquake.data.settings.IntensityColorContrast, modifier: Modifier = Modifier) {
    val level = IntensityLevel.fromP2pScale(quake.maxScale)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = level.bgColor.copy(alpha = 0.18f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("最大震度", fontSize = 11.sp)
                IntensityBadge(level = level, size = 56.dp, contrast = contrast)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(quake.occurredAtLabel + "ごろ", fontSize = 12.sp)
                Text(quake.hypocenterName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                val depthLabel = quake.depthKm?.let { "深さ${it}km" } ?: "深さ不明"
                val magLabel = quake.magnitude?.let { "規模 M$it" } ?: "規模不明"
                Text("$depthLabel / $magLabel", fontSize = 12.sp)
            }
        }
    }
}