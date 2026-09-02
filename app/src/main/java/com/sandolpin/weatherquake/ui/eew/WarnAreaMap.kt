package com.sandolpin.weatherquake.ui.eew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sandolpin.weatherquake.data.GeoJsonLoader
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.eew.WarnArea
import com.sandolpin.weatherquake.map.EpicenterIconFactory
import com.sandolpin.weatherquake.map.MapCameraBoundsHelper
import com.sandolpin.weatherquake.map.MapLibreStyleFactory
import com.sandolpin.weatherquake.map.hideMapLibreBadges
import com.sandolpin.weatherquake.map.isValidLatLng
import com.sandolpin.weatherquake.map.rememberMapViewWithLifecycle
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

internal const val MAP_HEIGHT_DP = 220
internal const val BLINK_INTERVAL_MS = 500L

/**
 * WarnArea[].Chiiki を assets/japan.geojson の properties.name と突き合わせ、
 * 一致したFeatureを震度色で塗りつぶして表示する地図(MapLibre版)。
 * 震源位置には×印(PLUM法は〇印)を点滅表示する。
 *
 * 呼び出し側(EewCard.kt)は旧バージョンと同じ引数で呼べるよう、シグネチャを変更していない。
 *
 * [震源マーカーのサイズについて]
 * 「×印が小さい」との指摘を受け、EpicenterIconFactory側のビットマップ自体を拡大した上で、
 * ここでのwithIconSize()も0.55f→0.75fに引き上げ、視認性を上げている。
 *
 * [MapLibreロゴ/アトリビューション表示について]
 * uiSettingsで地図上のバッジ表示は無効にしている。ライセンス上の表示義務は、
 * 設定画面(SettingsScreen.kt「ライセンス」セクション)に固定テキストとして
 * 明記する形で満たす方針にしている。
 */
@Composable
fun WarnAreaMap(
    warnAreas: List<WarnArea>,
    latitude: Double,
    longitude: Double,
    isAssumption: Boolean,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current

    val intensityByName = remember(warnAreas) {
        val map = LinkedHashMap<String, IntensityLevel>()
        warnAreas.forEach { area -> map.putIfAbsent(area.Chiiki, IntensityLevel.fromApiString(area.Shindo1)) }
        map
    }

    val mapView = rememberMapViewWithLifecycle()
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var epicenterSymbol by remember { mutableStateOf<Symbol?>(null) }

    val onReadyState = rememberUpdatedState(onReady)

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        factory = { mapView }
    ) { view ->
        view.getMapAsync { map ->
            map.hideMapLibreBadges()
            maplibreMap = map
        }
    }

    // データ(震度・震源)が変わるたびにスタイルを組み直し、カメラを合わせ直す。
    LaunchedEffect(maplibreMap, intensityByName, latitude, longitude, isAssumption) {
        val map = maplibreMap ?: return@LaunchedEffect
        if (latitude == 0.0 && longitude == 0.0 && intensityByName.isEmpty()) return@LaunchedEffect
        val hasValidEpicenter = isValidLatLng(latitude, longitude)

        val styleBuilder = MapLibreStyleFactory.build(context, intensityByName)
        map.setStyle(styleBuilder) { style ->
            style.addImage(EpicenterIconFactory.ICON_ID_CROSS, EpicenterIconFactory.crossIcon())
            style.addImage(EpicenterIconFactory.ICON_ID_ASSUMPTION, EpicenterIconFactory.assumptionIcon())

            symbolManager?.onDestroy()
            val newManager = SymbolManager(mapView, map, style)
            symbolManager = newManager

            if (hasValidEpicenter) {
                val iconId = if (isAssumption) EpicenterIconFactory.ICON_ID_ASSUMPTION else EpicenterIconFactory.ICON_ID_CROSS
                epicenterSymbol = newManager.create(
                    SymbolOptions()
                        .withLatLng(LatLng(latitude, longitude))
                        .withIconImage(iconId)
                        .withIconSize(0.75f)
                )
            } else {
                epicenterSymbol = null
            }

            val boundsEpicenterLat = if (hasValidEpicenter) latitude else 36.0
            val boundsEpicenterLon = if (hasValidEpicenter) longitude else 138.0
            val bounds = MapCameraBoundsHelper.compute(
                context = context,
                loader = GeoJsonLoader.region,
                coloredNames = intensityByName.keys,
                epicenterLon = boundsEpicenterLon,
                epicenterLat = boundsEpicenterLat
            )
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 16))

            onReadyState.value()
        }
    }

    // 震源マーカーの点滅(500ms間隔でアイコンサイズを0にして消す/戻す)
    LaunchedEffect(epicenterSymbol) {
        val manager = symbolManager
        val symbol = epicenterSymbol
        if (manager == null || symbol == null) return@LaunchedEffect
        var visible = true
        while (true) {
            delay(BLINK_INTERVAL_MS)
            visible = !visible
            symbol.iconOpacity = if (visible) 1.0f else 0.0f
            manager.update(symbol)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            symbolManager?.onDestroy()
        }
    }
}