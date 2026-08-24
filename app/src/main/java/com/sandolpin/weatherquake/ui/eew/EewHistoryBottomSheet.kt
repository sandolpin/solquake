package com.sandolpin.weatherquake.ui.eew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sandolpin.weatherquake.data.eew.EewCardState
import com.sandolpin.weatherquake.service.EewRepository

/**
 * EEWカードをタップした時に表示する「更新履歴」ボトムシート。
 * 全開状態はskipPartiallyExpanded=trueで確保し、LazyColumn自体には高さ制限を設けず
 * ModalBottomSheet本来のサイズ管理に任せる(スクロール時のバウンド挙動を避けるため)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EewHistoryBottomSheet(eventId: String, onDismiss: () -> Unit) {
    val historyMap by EewRepository.history.collectAsState()
    val reports = historyMap[eventId].orEmpty()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "更新履歴",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (reports.isEmpty()) {
                Text(
                    "記録された履歴がありません",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val latestSerial = reports.first().Serial

                    itemsIndexed(reports, key = { index, eew -> "${eew.Serial}_$index" }) { _, eew ->
                        EewCard(
                            state = EewCardState(eew, receivedAt = 0L),
                            isFinalOverride = eew.Serial == latestSerial,
                            showMap = true,
                            defaultExpanded = false
                        )
                        if (eew.Serial != reports.last().Serial) {
                            HistoryDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDivider() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}