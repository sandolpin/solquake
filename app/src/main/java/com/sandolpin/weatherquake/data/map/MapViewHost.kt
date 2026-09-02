package com.sandolpin.weatherquake.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapView

/**
 * MapViewをComposeのライフサイクルに連動させる共通ヘルパー。
 * WarnAreaMap.kt / QuakeHistoryMap.kt / QuakeCityIntensityMap.kt など、
 * MapLibreのMapViewを使うComposableはすべてこの関数を使う。
 *
 * AndroidViewのfactory内でMapViewをnewしてしまうと再コンポーズのたびに作り直されてしまうため、
 * rememberでインスタンスを1つに固定し、Activity/Fragmentのライフサイクルイベントを
 * そのままMapView.onXxx()へ転送する。
 */
@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        // addObserver()の時点で、現在のライフサイクル状態まで追いつくイベントが自動的に流れるため、
        // ここで明示的にmapView.onCreate()等を呼ぶ必要は無い(二重呼び出し防止)。
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun createMapView(context: Context): MapView = MapView(context)

/**
 * ロゴ・アトリビューション(右下の "i" アイコンとMapLibreロゴ)を非表示にする。
 * ライセンス上の表示義務は、設定画面に固定テキストとして明記する形で満たしている
 * (SettingsScreen.kt の「ライセンス」セクション参照)ため、地図上のバッジ自体は
 * UI上のノイズになるので消している。
 */
fun org.maplibre.android.maps.MapLibreMap.hideMapLibreBadges() {
    uiSettings.isLogoEnabled = false
    uiSettings.isAttributionEnabled = false
}

/**
 * 緯度・経度が地図に描画可能な範囲内かを確認する。
 *
 * P2P地震情報APIは、震源が特定できない場合に緯度・経度へ -200 のような
 * 「ありえない値」を入れて返すことがある(不明を表すセンチネル値)。
 * これをそのままLatLngに渡すと IllegalArgumentException でクラッシュするため、
 * 地図に座標を渡す前は必ずこの関数でチェックすること。
 */
fun isValidLatLng(latitude: Double, longitude: Double): Boolean =
    latitude in -90.0..90.0 && longitude in -180.0..180.0 && !latitude.isNaN() && !longitude.isNaN()