package com.sandolpin.weatherquake

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.sandolpin.weatherquake.data.settings.DarkModeOption
import com.sandolpin.weatherquake.data.settings.SettingsRepository
import com.sandolpin.weatherquake.service.EewService
import com.sandolpin.weatherquake.ui.navigation.AppNavHost
import com.sandolpin.weatherquake.ui.theme.WeatherQuakeTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 結果は特に処理不要 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 緊急地震速報(WebSocket)・地震情報(ポーリング)を受信する常駐Serviceは、
        // ユーザーが実際にアプリを開いた(=フォアグラウンドの)このタイミングで起動する。
        // Application.onCreate()から呼ぶと、Android 12以降で
        // ForegroundServiceStartNotAllowedExceptionによりクラッシュすることがあるため、
        // 念のためtry-catchもしておく(端末やOSのタイミングにより稀に拒否される場合の保険)。
        try {
            EewService.start(this)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "EewServiceの起動に失敗しました", e)
        }

        val settingsRepository = SettingsRepository(applicationContext)

        setContent {
            val darkModeFlow = remember { settingsRepository.settingsFlow.map { it.darkMode } }
            val darkMode by darkModeFlow.collectAsState(initial = DarkModeOption.SYSTEM)

            WeatherQuakeTheme(darkModeOption = darkMode) {
                // enableEdgeToEdge()によりコンテンツがステータスバー/ナビゲーションバーの裏まで
                // 描画されるため(Android 15以降はtargetSdk35で強制的にこの挙動になる)、
                // ここで明示的にシステムバー分の余白を確保し、UIが被らないようにする。
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    AppNavHost()
                }
            }
        }
    }
}