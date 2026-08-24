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

/**
 * 地震情報一覧画面。
 *
 * 注意: SharedTransitionLayoutの sharedBounds/sharedElement は、LazyColumn/LazyRow の
 * アイテム(表示コンポーザブルを使い回す=リサイクルする仕組み)と相性が悪く、
 * カードの重なり・間隔崩壊・意図しない移動といった表示バグを引き起こすことが知られている。
 * 地震履歴はAPI側の取得上限(最大30件程度)で十分小さいため、
 * ここでは LazyColumn ではなく通常の Column + verticalScroll を使い、
 * この問題を根本的に回避している。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun QuakeListScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCardClick: (String) -> Unit,
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("過去の地震履歴", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        state.quakes.forEachIndexed { index, quake ->
            with(sharedTransitionScope) {
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

@Composable
private fun QuakeHistoryCard(quake: QuakeCardState, contrast: com.sandolpin.weatherquake.data.settings.IntensityColorContrast, modifier: Modifier = Modifier) {
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