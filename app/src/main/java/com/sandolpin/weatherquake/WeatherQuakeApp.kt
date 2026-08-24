package com.sandolpin.weatherquake

import android.app.Application
import com.sandolpin.weatherquake.data.GeoJsonLoader
import com.sandolpin.weatherquake.service.EewService
import com.sandolpin.weatherquake.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WeatherQuakeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        // 地図描画で使うGeoJSON(19MB規模)は、メインスレッドをブロックしないようIOスレッドで事前解析する。
        // assets/japan.geojson が未配置の場合はFileNotFoundException等が出るが、
        // ここで捕捉しないとアプリ全体が起動直後にクラッシュしてしまうため必ずtry-catchする
        // (地図なしでも天気・地震情報自体は表示できるようにするため、失敗しても致命的にしない)。
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GeoJsonLoader.preload(this@WeatherQuakeApp)
            } catch (e: Exception) {
                android.util.Log.w("WeatherQuakeApp", "GeoJSONの事前読み込みに失敗しました(assets/japan.geojsonを確認してください)", e)
            }
        }
        // 緊急地震速報(WebSocket)・地震情報(ポーリング)を受信する常駐Serviceを起動
        EewService.start(this)
    }
}