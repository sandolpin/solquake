package com.sandolpin.weatherquake.ui.eew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sandolpin.weatherquake.data.eew.EewCardState
import com.sandolpin.weatherquake.service.EewRepository

/** これまでにEewServiceが受信した緊急地震速報を、EventIDごと・第N報ごとにすべて表示する */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EewFullHistoryScreen(onBack: () -> Unit) {
    val historyMap by EewRepository.history.collectAsState()
    val allReports = historyMap.values.flatten().sortedByDescending { it.AnnouncedTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("緊急地震速報 受信履歴") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") }
                }
            )
        }
    ) { padding ->
        if (allReports.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("まだ受信履歴がありません")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(allReports, key = { index, eew -> "${eew.EventID}_${eew.Serial}_$index" }) { _, eew ->
                    EewCard(state = EewCardState(eew, receivedAt = 0L), defaultExpanded = false)
                }
            }
        }
    }
}