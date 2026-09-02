package com.sandolpin.weatherquake.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.weatherquake.data.settings.CardStyle
import com.sandolpin.weatherquake.data.settings.DarkModeOption
import com.sandolpin.weatherquake.data.settings.IntensityColorContrast
import com.sandolpin.weatherquake.data.settings.TemperatureFontStyle
import com.sandolpin.weatherquake.data.settings.WeatherBackgroundStyle
import com.sandolpin.weatherquake.service.NotificationHelper
import com.sandolpin.weatherquake.service.TestNotificationKind

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onOpenEewHistory: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val apiTestResult by viewModel.apiTestResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.headlineSmall)

        // --- 全体 ---
        SettingsSegmentedRow(
            title = "ダークモード",
            options = listOf(DarkModeOption.ON to "ON", DarkModeOption.OFF to "OFF", DarkModeOption.SYSTEM to "システム"),
            selected = settings.darkMode,
            onSelect = { value -> viewModel.update { it.copy(darkMode = value) } }
        )

        // --- 天気画面設定 ---
        SettingsSectionTitle("天気画面設定")
        SettingsSegmentedRow(
            title = "天気の背景設定",
            options = listOf(
                WeatherBackgroundStyle.DYNAMIC to "動的背景",
                WeatherBackgroundStyle.PLAIN_WHITE to "単色(白)",
                WeatherBackgroundStyle.PLAIN_BLACK to "単色(黒)"
            ),
            selected = settings.weatherBackgroundStyle,
            onSelect = { value -> viewModel.update { it.copy(weatherBackgroundStyle = value) } }
        )
        SettingsSwitchRow("日の入り・日の出を表示", settings.showSunTimes) { checked ->
            viewModel.update { it.copy(showSunTimes = checked) }
        }
        SettingsSwitchRow("時間ごとの天気を表示", settings.showHourlyForecast) { checked ->
            viewModel.update { it.copy(showHourlyForecast = checked) }
        }
        SettingsSwitchRow("詳細情報を表示", settings.showWeatherDetails) { checked ->
            viewModel.update { it.copy(showWeatherDetails = checked) }
        }
        SettingsSegmentedRow(
            title = "カードのスタイル",
            options = listOf(CardStyle.FILLED to "塗りつぶし", CardStyle.GLASSMORPHISM to "グラスモーフィズム"),
            selected = settings.weatherCardStyle,
            onSelect = { value -> viewModel.update { it.copy(weatherCardStyle = value) } }
        )
        SettingsSliderRow(
            title = "カードの透明度",
            value = settings.weatherCardOpacity,
            valueRange = 0f..0.8f,
            valueLabel = "${(settings.weatherCardOpacity * 100).toInt()}%",
            onValueChange = { value -> viewModel.update { it.copy(weatherCardOpacity = value) } }
        )
        SettingsSegmentedRow(
            title = "気温のフォント",
            options = listOf(TemperatureFontStyle.DEFAULT to "デフォルト", TemperatureFontStyle.RECOMMENDED to "アプリのおすすめ"),
            selected = settings.temperatureFont,
            onSelect = { value -> viewModel.update { it.copy(temperatureFont = value) } }
        )

        // --- 地震画面設定 ---
        SettingsSectionTitle("地震画面設定")
        SettingsSegmentedRow(
            title = "震度色の濃さ",
            options = listOf(
                IntensityColorContrast.DEFAULT to "デフォルト",
                IntensityColorContrast.LIGHT to "薄め",
                IntensityColorContrast.HIGH_CONTRAST to "ハイコントラスト"
            ),
            selected = settings.intensityColorContrast,
            onSelect = { value -> viewModel.update { it.copy(intensityColorContrast = value) } }
        )
        SettingsSwitchRow("深さ・規模が不明な地震情報を表示しない", settings.hideUnknownDepthMagnitude) { checked ->
            viewModel.update { it.copy(hideUnknownDepthMagnitude = checked) }
        }

        // --- 通知設定(天気) ---
        SettingsSectionTitle("通知設定 - 天気予報")
        SettingsSwitchRow("朝に通知する", settings.notifyWeatherMorning) { viewModel.update { s -> s.copy(notifyWeatherMorning = it) } }
        SettingsSwitchRow("昼に通知する", settings.notifyWeatherNoon) { viewModel.update { s -> s.copy(notifyWeatherNoon = it) } }
        SettingsSwitchRow("夕方に通知する", settings.notifyWeatherEvening) { viewModel.update { s -> s.copy(notifyWeatherEvening = it) } }
        SettingsSwitchRow("夜に通知する", settings.notifyWeatherNight) { viewModel.update { s -> s.copy(notifyWeatherNight = it) } }
        SettingsSwitchRow("通知アイコンに天気を表示", settings.weatherNotifShowIcon) { viewModel.update { s -> s.copy(weatherNotifShowIcon = it) } }
        SettingsSwitchRow("画像部分に時間ごとの予報を表示", settings.weatherNotifShowHourlyImage) { viewModel.update { s -> s.copy(weatherNotifShowHourlyImage = it) } }

        // --- 通知設定(緊急地震速報) ---
        SettingsSectionTitle("通知設定 - 緊急地震速報")
        SettingsSwitchRow("緊急地震速報発報時に通知", settings.notifyOnEew) { viewModel.update { s -> s.copy(notifyOnEew = it) } }
        SettingsSwitchRow("警報のみ受信", settings.eewWarningOnly) { viewModel.update { s -> s.copy(eewWarningOnly = it) } }
        SettingsSliderRow(
            title = "通知する震度しきい値",
            value = settings.eewMinIntensityOrdinal.toFloat(),
            valueRange = 1f..9f,
            valueLabel = NotificationHelper.selectableLevels.getOrNull(settings.eewMinIntensityOrdinal - 1)?.formalLabel ?: "1",
            onValueChange = { value -> viewModel.update { it.copy(eewMinIntensityOrdinal = value.toInt().coerceIn(1, 9)) } }
        )
        SettingsSwitchRow("予想震度がわかっていない速報も受信", settings.eewReceiveUnknownIntensity) { viewModel.update { s -> s.copy(eewReceiveUnknownIntensity = it) } }
        SettingsSwitchRow("震源・強い揺れが予想される地域の地図を表示", settings.eewShowMapInNotification) { viewModel.update { s -> s.copy(eewShowMapInNotification = it) } }
        SettingsSwitchRow("予想震度・対象地域を音声で読み上げる", settings.eewTtsReadout) { viewModel.update { s -> s.copy(eewTtsReadout = it) } }

        // --- 通知設定(地震情報) ---
        SettingsSectionTitle("通知設定 - 地震情報")
        SettingsSwitchRow("地震情報発表時に通知", settings.notifyOnQuake) { viewModel.update { s -> s.copy(notifyOnQuake = it) } }
        SettingsSliderRow(
            title = "通知する震度",
            value = settings.quakeMinIntensityOrdinal.toFloat(),
            valueRange = 1f..9f,
            valueLabel = NotificationHelper.selectableLevels.getOrNull(settings.quakeMinIntensityOrdinal - 1)?.formalLabel ?: "1",
            onValueChange = { value -> viewModel.update { it.copy(quakeMinIntensityOrdinal = value.toInt().coerceIn(1, 9)) } }
        )
        SettingsSwitchRow("震源・観測した震度の地図を表示", settings.quakeShowMapInNotification) { viewModel.update { s -> s.copy(quakeShowMapInNotification = it) } }
        SettingsSwitchRow("最大震度・観測地域を音声で読み上げる", settings.quakeTtsReadout) { viewModel.update { s -> s.copy(quakeTtsReadout = it) } }

        // --- 通知テスト ---
        SettingsSectionTitle("通知テスト")
        val notificationTestMessage by viewModel.notificationTestMessage.collectAsState()
        OutlinedButton(onClick = { viewModel.sendTestNotification(TestNotificationKind.WEATHER) }, modifier = Modifier.fillMaxSize()) {
            Text("天気の通知をテスト")
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = { viewModel.sendTestNotification(TestNotificationKind.EEW) }, modifier = Modifier.fillMaxSize()) {
            Text("緊急地震速報(予報)の通知をテスト")
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = { viewModel.sendTestNotification(TestNotificationKind.EEW_WARNING) }, modifier = Modifier.fillMaxSize()) {
            Text("緊急地震速報(警報)の通知をテスト")
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = { viewModel.sendTestNotification(TestNotificationKind.EEW_EMERGENCY_WARNING) }, modifier = Modifier.fillMaxSize()) {
            Text("緊急地震速報(特別警報)の通知をテスト")
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = { viewModel.sendTestNotification(TestNotificationKind.QUAKE) }, modifier = Modifier.fillMaxSize()) {
            Text("地震情報の通知をテスト")
        }
        notificationTestMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        OutlinedButton(
            onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxSize().padding(top = 8.dp)
        ) {
            Text("端末の通知設定を開く")
        }

        // --- その他 ---
        SettingsSectionTitle("その他")
        SettingsClickRow(
            title = "いままで受信した緊急地震速報の情報を表示",
            onClick = onOpenEewHistory
        )
        Button(onClick = { viewModel.runApiTest() }, modifier = Modifier.fillMaxSize()) {
            Text("API接続テスト")
        }
        apiTestResult?.let { result ->
            Text(
                "${result.label}: ${result.message}",
                color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // --- ライセンス ---
        // 地図描画にMapLibre(オープンソースの地図描画ライブラリ)を使用している旨を明記する。
        // 地図上のロゴ/アトリビューション表示は非表示にしているため(WarnAreaMap.kt等参照)、
        // その代わりにここで固定テキストとして表示している。
        SettingsSectionTitle("ライセンス")
        Text(
            "地図の描画には、オープンソースの地図描画ライブラリ「MapLibre」(MapLibre Native, BSD 2-Clauseライセンス)を使用しています。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "地図データは、気象庁の地方予報区・市町村区分に基づく境界データを使用しています。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
    }
}