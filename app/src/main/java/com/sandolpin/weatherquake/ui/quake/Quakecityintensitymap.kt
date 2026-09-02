package com.sandolpin.weatherquake.ui.quake

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sandolpin.weatherquake.data.GeoJsonLoader
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.quake.P2pPoint
import com.sandolpin.weatherquake.map.EpicenterIconFactory
import com.sandolpin.weatherquake.map.MapCameraBoundsHelper
import com.sandolpin.weatherquake.map.MapLibreStyleFactory
import com.sandolpin.weatherquake.map.hideMapLibreBadges
import com.sandolpin.weatherquake.map.isValidLatLng
import com.sandolpin.weatherquake.map.rememberMapViewWithLifecycle
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.math.roundToInt

/**
 * 市町村レベル(assets/japan_city.geojson)で震度を塗り分けた地図。
 * QuakeDetailScreen(地震情報の詳細画面)の背景として全画面表示で使う。
 *
 * [表示方針の変更]
 * 詳細画面は「地図を画面いっぱいに表示し、テキストはボトムシートに表示する」構成に変更したため、
 * サイズは呼び出し側のmodifier(通常はfillMaxSize())で決める。震度データが無い場合でも
 * 国土の輪郭(地方予報区の下地レイヤー)は常に表示し、地図が真っ白/真っ黒にならないようにしている。
 *
 * [市町村選択(タップ/リスト連動)について]
 * 選択中の市町村は呼び出し側(QuakeDetailScreen)でホイストして保持する。これにより、
 * 「地図をタップして選ぶ」だけでなく「ボトムシートの市町村名テキストをタップして選ぶ」の
 * 両方から同じ状態を更新できる。選択されると:
 *   1. MapLibreStyleFactoryが用意している選択強調レイヤー(黒縁+黄色線)のfilter/visibilityを
 *      Style.getLayer()経由で更新し、その市町村の輪郭を目立たせる(スタイル全体は作り直さない)。
 *   2. 地図タップ由来ならタップ位置、リスト由来ならその市町村のbbox中心を「代表点」とし、
 *      リスト由来の場合はカメラをその市町村付近まで寄せる。
 *   3. 代表点の画面座標(スクリーン座標)を継続的に再計算し、下部のポップアップカードを
 *      その地点に追従させる(addOnCameraMoveListenerで地図が動くたびに再計算)。
 *
 * [背景のぼかしについて]
 * ポップアップ表示中、または呼び出し側のボトムシートが展開中(externalBlurActive)は、
 * 地図(AndroidView)にMoodifier.blur()を適用してすりガラス風にする。
 * ポップアップカード自体は半透明の下地にしており、ぼやけた地図の上に浮いているように見せている。
 */
@Composable
fun QuakeCityIntensityMap(
    points: List<P2pPoint>,
    epicenterLatitude: Double,
    epicenterLongitude: Double,
    selectedCityName: String?,
    onCitySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    externalBlurActive: Boolean = false
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    // スタイルが作り直される(setStyleが呼ばれる)たびにインクリメントする。
    // 選択ハイライトの適用は「スタイルが用意された後」でないと効かないため、これをトリガーに使う。
    var styleVersion by remember { mutableIntStateOf(0) }

    val cityIntensityByName = remember(points) { buildCityIntensityMap(points) }
    // 地図タップリスナーは登録タイミングが早いと最新のcityIntensityByNameを参照できないため、
    // rememberUpdatedStateで常に最新値を参照できるようにし、リスナー自体はmap取得時に1回だけ登録する
    // (setStyle()のたびに登録し直すと、スタイル再構築のたびにリスナーが重複登録されてしまうため)。
    val latestCityIntensityByName = rememberUpdatedState(cityIntensityByName)
    val latestOnCitySelected = rememberUpdatedState(onCitySelected)

    // 選択中の市町村の「代表点」(緯度経度)。ポップアップ追従・カメラ移動の基準にする。
    var selectedAnchorLatLng by remember { mutableStateOf<LatLng?>(null) }
    var popupScreenOffset by remember { mutableStateOf<Offset?>(null) }
    var containerSizePx by remember { mutableStateOf(Size.Zero) }
    var popupSizePx by remember { mutableStateOf(Size.Zero) }

    val blurActive = externalBlurActive || selectedCityName != null
    val blurRadius by animateDpAsState(targetValue = if (blurActive) 16.dp else 0.dp, label = "cityMapBlur")

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
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

        // 地図タップの検出とカメラ移動の監視は、mapが取得できたタイミングで1回だけ登録する
        // (スタイル再構築とは独立させ、リスナーの重複登録を防ぐ)。
        LaunchedEffect(maplibreMap) {
            val map = maplibreMap ?: return@LaunchedEffect

            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val features = map.queryRenderedFeatures(screenPoint, MapLibreStyleFactory.CITY_BASE_FILL_LAYER)
                val tappedName = features.firstOrNull()?.getStringProperty("name")
                if (tappedName != null && latestCityIntensityByName.value.containsKey(tappedName)) {
                    selectedAnchorLatLng = latLng
                    latestOnCitySelected.value(tappedName)
                    true
                } else {
                    false
                }
            }

            // 地図が動く(パン・ズーム・カメラ移動)たびに、選択中地点の画面座標を再計算して
            // ポップアップカードを追従させる。
            map.addOnCameraMoveListener {
                val anchor = selectedAnchorLatLng
                popupScreenOffset = if (anchor != null) {
                    val p = map.projection.toScreenLocation(anchor)
                    Offset(p.x, p.y)
                } else {
                    null
                }
            }
        }

        // 震度着色スタイルの構築。選択ハイライトはここでは扱わない(下の別LaunchedEffectで実行時更新する)。
        // 選択のたびにスタイル全体を作り直すと、シンボル(震源マーク)やカメラ位置がリセットされて
        // チラつくため、あえて分離している。
        LaunchedEffect(maplibreMap, cityIntensityByName, epicenterLatitude, epicenterLongitude) {
            val map = maplibreMap ?: return@LaunchedEffect
            val hasValidEpicenter = isValidLatLng(epicenterLatitude, epicenterLongitude)

            // 震度データが無くても(cityIntensityByNameが空でも)地方予報区の下地レイヤーは
            // 常に含める(regionIntensityByNameは常に空マップ=下地のみ表示、着色は市町村レイヤーのみ)。
            val styleBuilder = MapLibreStyleFactory.build(
                context = context,
                regionIntensityByName = emptyMap(),
                cityIntensityByName = cityIntensityByName
            )

            map.setStyle(styleBuilder) { style ->
                style.addImage(EpicenterIconFactory.ICON_ID_CROSS, EpicenterIconFactory.crossIcon())

                symbolManager?.onDestroy()
                val manager = SymbolManager(mapView, map, style)
                symbolManager = manager
                if (hasValidEpicenter) {
                    manager.create(
                        SymbolOptions()
                            .withLatLng(LatLng(epicenterLatitude, epicenterLongitude))
                            .withIconImage(EpicenterIconFactory.ICON_ID_CROSS)
                            .withIconSize(0.9f)
                    )
                }

                val boundsEpicenterLat = if (hasValidEpicenter) epicenterLatitude else 36.0
                val boundsEpicenterLon = if (hasValidEpicenter) epicenterLongitude else 138.0
                val bounds = MapCameraBoundsHelper.compute(
                    context = context,
                    loader = GeoJsonLoader.city,
                    coloredNames = cityIntensityByName.keys,
                    epicenterLon = boundsEpicenterLon,
                    epicenterLat = boundsEpicenterLat
                )
                runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 16)) }

                styleVersion++
            }
        }

        // 選択中の市町村の縁取りハイライトを実行時更新する(スタイルは作り直さない軽量な経路)。
        // リストから選択された場合(まだ代表点=タップ位置が無い場合)は、その市町村のbbox中心を
        // 代表点として採用し、市町村が画面内に収まるようカメラも寄せる。
        LaunchedEffect(maplibreMap, styleVersion, selectedCityName) {
            val map = maplibreMap ?: return@LaunchedEffect
            val style = map.style ?: return@LaunchedEffect

            val filter = Expression.eq(Expression.get("name"), Expression.literal(selectedCityName ?: ""))
            val visibility = if (selectedCityName != null) Property.VISIBLE else Property.NONE
            (style.getLayer(MapLibreStyleFactory.CITY_SELECTED_LINE_LAYER) as? LineLayer)?.apply {
                setFilter(filter)
                setProperties(PropertyFactory.visibility(visibility))
            }
            (style.getLayer(MapLibreStyleFactory.CITY_SELECTED_OUTLINE_LAYER) as? LineLayer)?.apply {
                setFilter(filter)
                setProperties(PropertyFactory.visibility(visibility))
            }

            if (selectedCityName == null) {
                selectedAnchorLatLng = null
                popupScreenOffset = null
                return@LaunchedEffect
            }

            // タップ由来ならすでにselectedAnchorLatLngがセットされているのでカメラは動かさない。
            // リスト由来(まだ代表点が無い)の場合のみ、その市町村のbboxを取得してカメラを寄せる。
            if (selectedAnchorLatLng == null) {
                val bbox = GeoJsonLoader.city.boundingBoxFor(context, listOf(selectedCityName))
                if (bbox != null) {
                    selectedAnchorLatLng = LatLng(bbox.centerLat, bbox.centerLon)
                    val lonPad = (bbox.maxLon - bbox.minLon).coerceAtLeast(0.02) * 1.5 + 0.05
                    val latPad = (bbox.maxLat - bbox.minLat).coerceAtLeast(0.02) * 1.5 + 0.05
                    val paddedBounds = LatLngBounds.Builder()
                        .include(LatLng(bbox.minLat - latPad, bbox.minLon - lonPad))
                        .include(LatLng(bbox.maxLat + latPad, bbox.maxLon + lonPad))
                        .build()
                    runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(paddedBounds, 16)) }
                }
            }

            selectedAnchorLatLng?.let { anchor ->
                val p = map.projection.toScreenLocation(anchor)
                popupScreenOffset = Offset(p.x, p.y)
            }
        }

        val cityName = selectedCityName
        if (cityName != null) {
            val entries = remember(cityName, points) { pointsInCity(points, cityName) }
            val prefLabel = entries.firstOrNull()?.pref?.let { "$it$cityName" } ?: cityName

            // ポップアップの位置は代表点の画面座標を基準にしつつ、コンテナ外にはみ出さないよう
            // クランプする(WeatherScreenの吹き出し配置と同じ考え方)。
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

            CityBreakdownCard(
                title = prefLabel,
                entries = entries,
                onDismiss = { onCitySelected(null) },
                modifier = Modifier
                    .onSizeChanged { size -> popupSizePx = Size(size.width.toFloat(), size.height.toFloat()) }
                    .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
            )
        }
    }
}

/** 画像デザイン(都道府県+市名 見出し、震度チップ+区域名を震度の高い順)に合わせたカード。
 *  震度ごとにグループ化し(1区域=1行ではなく、同じ震度の区域名はまとめて表示)、
 *  内容が画面高さを超える場合はスクロールできるようにしている。
 *  背景と見分けやすいよう半透明の下地にし(地図側はぼかし表示になる)、すりガラス風の見た目にしている。 */
@Composable
private fun CityBreakdownCard(
    title: String,
    entries: List<P2pPoint>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(entries) {
        entries
            .groupBy { IntensityLevel.fromP2pScale(it.scale) }
            .toList()
            .sortedByDescending { (level, _) -> level.ordinal }
    }

    Surface(
        modifier = modifier
            .width(280.dp)
            .heightIn(max = 360.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "閉じる", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                grouped.forEachIndexed { index, (level, points) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = level.bgColor) {
                            Text(
                                "震度${level.formalLabel}",
                                color = level.textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val names = points.map { it.addr }.distinct().joinToString("、")
                    Text(names, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (index != grouped.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}