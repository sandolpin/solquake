package com.sandolpin.weatherquake.ui.quake

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.quake.QuakeCardState
import com.sandolpin.weatherquake.map.EpicenterIconFactory
import com.sandolpin.weatherquake.map.MapLibreStyleFactory
import com.sandolpin.weatherquake.map.hideMapLibreBadges
import com.sandolpin.weatherquake.map.isValidLatLng
import com.sandolpin.weatherquake.map.rememberMapViewWithLifecycle
import com.sandolpin.weatherquake.ui.components.IntensityBadge
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import kotlin.math.roundToInt

/**
 * 複数の地震(過去の履歴)を震源位置にピンで表示する地図。
 * ピンは「震度で色、規模(マグニチュード)で大きさが変わる半透明の円」(EpicenterIconFactory.magnitudeCircle)。
 * ピンをタップすると、その地震の概要ポップアップが表示され、
 * 「詳細を見る」から詳細画面(QuakeDetailScreen)へ遷移できる(onOpenDetailコールバック)。
 *
 * [ポップアップの追従について]
 * 以前は画面内の固定位置にポップアップを表示していたが、タップしたピンの真上に追従するよう変更した。
 * MapLibreMap.addOnCameraMoveListener()で地図の移動・ズームを監視し、選択中の震源座標を
 * 都度スクリーン座標へ再投影してポップアップの位置を更新する。
 * 画面端でカードが見切れないよう、コンテナサイズとカードサイズをもとにクランプしている。
 *
 * [背景のぼかしについて]
 * ポップアップ表示中、または呼び出し側のボトムシートが展開中(externalBlurActive)は、
 * 地図を軽くぼかしてすりガラス風の見た目にし、手前のカード・シートとの区別をつけやすくする。
 */
@Composable
fun QuakeHistoryMap(
    quakes: List<QuakeCardState>,
    onOpenDetail: (QuakeCardState) -> Unit,
    modifier: Modifier = Modifier,
    externalBlurActive: Boolean = false
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var selectedQuake by remember { mutableStateOf<QuakeCardState?>(null) }
    val symbolIdToQuake = remember { mutableStateMapOf<Long, QuakeCardState>() }

    var popupScreenOffset by remember { mutableStateOf<Offset?>(null) }
    var containerSizePx by remember { mutableStateOf(Size.Zero) }
    var popupSizePx by remember { mutableStateOf(Size.Zero) }

    val blurActive = externalBlurActive || selectedQuake != null
    val blurRadius by animateDpAsState(targetValue = if (blurActive) 16.dp else 0.dp, label = "historyMapBlur")

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> containerSizePx = Size(size.width.toFloat(), size.height.toFloat()) }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().blur(blurRadius),
            factory = { mapView }
        ) { view ->
            view.getMapAsync { map ->
                map.hideMapLibreBadges()
                maplibreMap = map
            }
        }

        // 地図が動く(パン・ズーム)たびに、選択中の震源の画面座標を再計算してポップアップを追従させる。
        LaunchedEffect(maplibreMap) {
            val map = maplibreMap ?: return@LaunchedEffect
            map.addOnCameraMoveListener {
                val quake = selectedQuake
                popupScreenOffset = if (quake != null) {
                    val p = map.projection.toScreenLocation(LatLng(quake.latitude, quake.longitude))
                    Offset(p.x, p.y)
                } else {
                    null
                }
            }
        }

        LaunchedEffect(maplibreMap, quakes) {
            val map = maplibreMap ?: return@LaunchedEffect
            val validQuakes = quakes.filter { isValidLatLng(it.latitude, it.longitude) && (it.latitude != 0.0 || it.longitude != 0.0) }
            if (validQuakes.isEmpty()) return@LaunchedEffect

            // このマップは震源位置のピン表示のみが目的で、震度による地域塗りつぶしは行わない
            // (震度はピンの色で表現し、詳細はポップアップ/詳細画面で確認する設計)。
            val styleBuilder = MapLibreStyleFactory.build(context, emptyMap())
            map.setStyle(styleBuilder) { style ->
                symbolManager?.onDestroy()
                val manager = SymbolManager(mapView, map, style)
                symbolManager = manager
                symbolIdToQuake.clear()

                validQuakes.forEach { quake ->
                    val level = IntensityLevel.fromP2pScale(quake.maxScale)
                    val iconId = "quake-marker-${quake.id}"
                    val bitmap = EpicenterIconFactory.magnitudeCircle(quake.magnitude, level.bgColor.toArgb())
                    style.addImage(iconId, bitmap)

                    val symbol = manager.create(
                        SymbolOptions()
                            .withLatLng(LatLng(quake.latitude, quake.longitude))
                            .withIconImage(iconId)
                            .withIconSize(1.0f)
                    )
                    symbolIdToQuake[symbol.id] = quake
                }

                manager.addClickListener { symbol ->
                    val quake = symbolIdToQuake[symbol.id]
                    selectedQuake = quake
                    if (quake != null) {
                        val p = map.projection.toScreenLocation(LatLng(quake.latitude, quake.longitude))
                        popupScreenOffset = Offset(p.x, p.y)
                    }
                    true
                }

                val boundsBuilder = LatLngBounds.Builder()
                validQuakes.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                val fitResult = runCatching {
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
                }
                if (fitResult.isFailure) {
                    val first = validQuakes.first()
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 6.0))
                }
            }
        }

        val quake = selectedQuake
        if (quake != null) {
            val offset = popupScreenOffset
            val marginPx = with(LocalDensity.current) { 12.dp.toPx() }
            val fallbackX = containerSizePx.width / 2f
            val fallbackY = containerSizePx.height / 2f
            val rawX = (offset?.x ?: fallbackX) - popupSizePx.width / 2f
            val rawY = (offset?.y ?: fallbackY) - popupSizePx.height - marginPx
            val maxX = (containerSizePx.width - popupSizePx.width - marginPx).coerceAtLeast(marginPx)
            val maxY = (containerSizePx.height - popupSizePx.height - marginPx).coerceAtLeast(marginPx)
            val clampedX = rawX.coerceIn(marginPx, maxX)
            val clampedY = rawY.coerceIn(marginPx, maxY)

            QuakeMapPopupCard(
                quake = quake,
                onDismiss = { selectedQuake = null },
                onOpenDetail = {
                    selectedQuake = null
                    onOpenDetail(quake)
                },
                modifier = Modifier
                    .onSizeChanged { size -> popupSizePx = Size(size.width.toFloat(), size.height.toFloat()) }
                    .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
            )
        }
    }
}

/** 画像デザインに合わせた、震源ピンタップ時のポップアップ(震度バッジ+震源情報+詳細ボタン)。
 *  背景と見分けやすいよう半透明の下地にし(地図側はぼかし表示になる)、すりガラス風の見た目にしている。 */
@Composable
private fun QuakeMapPopupCard(
    quake: QuakeCardState,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val level = IntensityLevel.fromP2pScale(quake.maxScale)
    Surface(
        modifier = modifier.width(300.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(
                    quake.occurredAtLabel + "ごろ",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "閉じる", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("最大震度", fontSize = 11.sp)
                    IntensityBadge(level = level, size = 64.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("震源", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(quake.hypocenterName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    val depthLabel = quake.depthKm?.let { "深さ${it}km" } ?: "深さ不明"
                    val magLabel = quake.magnitude?.let { "M${it}" } ?: "M不明"
                    Text("$depthLabel  $magLabel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                Text("詳細を見る")
            }
        }
    }
}