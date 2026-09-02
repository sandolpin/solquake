package com.sandolpin.weatherquake

import android.app.Application
import com.sandolpin.weatherquake.data.GeoJsonLoader
import com.sandolpin.weatherquake.service.EewService
import com.sandolpin.weatherquake.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class WeatherQuakeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        // MapLibre本体の初期化。MapView/MapSnapshotterを使うより前に一度だけ呼んでおく必要がある。
        // (アクセストークンはMapLibreでは不要。タイル配信を使わずassets内のgeojsonのみで
        //  描画する構成のため、ネットワークキー等の設定も不要)
        MapLibre.getInstance(this)

        // 地方予報区(japan.geojson)・市町村(japan_city.geojson)のバウンディングボックスを
        // メインスレッドをブロックしないようIOスレッドで事前計算しておく。
        // どちらか一方(特にjapan_city.geojsonをまだ配置していない場合)が読めなくても、
        // GeoJsonLoader.preloadAll内でtry-catchしているため、アプリ全体が落ちることはない。
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GeoJsonLoader.preloadAll(this@WeatherQuakeApp)
            } catch (e: Exception) {
                android.util.Log.w("WeatherQuakeApp", "GeoJSONの事前読み込みに失敗しました(assets/japan.geojson, assets/japan_city.geojsonを確認してください)", e)
            }
        }

        // 緊急地震速報(WebSocket)・地震情報(ポーリング)を受信する常駐Serviceを起動
        EewService.start(this)
    }
}