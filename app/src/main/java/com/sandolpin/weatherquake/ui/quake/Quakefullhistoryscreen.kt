package com.sandolpin.weatherquake.ui.quake

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 地震履歴の全件を、全画面地図+ボトムシート(下からスワイプで展開するリスト)の
 * 2レイヤー構成で表示する画面。QuakeListScreenの「過去の地震履歴」矢印ボタンから遷移する。
 *
 * BottomSheetScaffoldは、sheetContent(履歴リスト)を下部に半分見せた状態(PartiallyExpanded)で
 * 常駐させ、ユーザーが上にドラッグすると展開できるMaterial3標準コンポーネント。
 * 地図はcontent側にfillMaxSizeで敷き詰め、シートの下からでも常時操作できるようにしている。
 *
 * [ボトムシートの半透明化・地図のぼかしについて]
 * シートが展開(Expanded)されている間は地図側を軽くぼかし、シート自体は半透明の下地にすることで
 * 「すりガラス」のような、背景の地図とシートを見分けやすい見た目にしている
 * (QuakeDetailScreenと同じ方針)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuakeFullHistoryScreen(
    onBack: () -> Unit,
    onQuakeClick: (String) -> Unit,
    viewModel: QuakeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 240.dp,
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
            if (state.quakes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("まだ地震情報がありません")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        "履歴",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.quakes.forEach { quake ->
                            QuakeHistoryCard(
                                quake = quake,
                                contrast = state.settings.intensityColorContrast,
                                modifier = Modifier.fillMaxWidth().clickable { onQuakeClick(quake.id) }
                            )
                        }
                    }
                    // ナビゲーションバー等に隠れないよう、下に余白を確保
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 32.dp))
                }
            }
        }
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            QuakeHistoryMap(
                quakes = state.quakes,
                onOpenDetail = { quake -> onQuakeClick(quake.id) },
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