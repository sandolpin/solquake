package com.sandolpin.weatherquake.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.weatherquake.data.settings.AppSettingsState
import com.sandolpin.weatherquake.data.settings.SettingsRepository
import com.sandolpin.weatherquake.service.EewService
import com.sandolpin.weatherquake.service.NotificationHelper
import com.sandolpin.weatherquake.service.TestNotificationKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApiTestResult(val success: Boolean, val label: String, val message: String)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    private val _settings = MutableStateFlow(AppSettingsState())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private val _apiTestResult = MutableStateFlow<ApiTestResult?>(null)
    val apiTestResult: StateFlow<ApiTestResult?> = _apiTestResult.asStateFlow()

    private val _notificationTestMessage = MutableStateFlow<String?>(null)
    val notificationTestMessage: StateFlow<String?> = _notificationTestMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { _settings.value = it }
        }
    }

    fun update(transform: (AppSettingsState) -> AppSettingsState) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun runApiTest() {
        EewService.testConnection { success, label, message ->
            _apiTestResult.value = ApiTestResult(success, label, message)
        }
    }

    /**
     * NotificationHelper.sendTestNotificationは地図生成(MapSnapshotter)を伴うためsuspend funになった。
     * viewModelScope.launchで包むことで、呼び出し側(SettingsScreen)のonClickは従来通り
     * 通常の関数呼び出しのままで使える。
     */
    fun sendTestNotification(kind: TestNotificationKind) {
        viewModelScope.launch {
            val sent = NotificationHelper.sendTestNotification(getApplication(), kind, _settings.value)
            _notificationTestMessage.value = if (sent) {
                "テスト通知を送信しました。通知欄を確認してください。"
            } else {
                "通知が許可されていないため送信できませんでした。端末の通知設定を確認してください。"
            }
        }
    }
}