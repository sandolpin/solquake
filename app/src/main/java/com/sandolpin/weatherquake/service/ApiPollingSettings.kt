package com.sandolpin.weatherquake.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wolfx API(緊急地震速報)に加えて、定期的にAPIをHTTPで取得し直す間隔(秒)の設定。
 * WebSocketが途切れた瞬間の取りこぼしを補完する目的で、一定間隔でAPIを叩き直す。
 * P2P地震情報のポーリング(QuakeRepository.pollOnce)もこの間隔に相乗りする。
 *
 * 設定画面本体の項目(SettingsRepository)とは別に、Service起動時から即座に参照できる
 * 軽量なSharedPreferencesベースの設定として独立させている
 * (DataStoreの非同期初期化を待たずにEewServiceのポーリングループを開始できるようにするため)。
 */
object ApiPollingSettings {
    private const val PREFS_NAME = "eew_polling_prefs"
    private const val KEY_INTERVAL = "interval_seconds"
    const val DEFAULT_INTERVAL_SECONDS = 5

    /** 設定画面で選択できる間隔の候補 */
    val availableIntervals = listOf(1, 2, 3, 5, 10)

    private val _intervalSeconds = MutableStateFlow(DEFAULT_INTERVAL_SECONDS)
    val intervalSeconds: StateFlow<Int> = _intervalSeconds

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SECONDS)
        _intervalSeconds.value = saved
    }

    fun setIntervalSeconds(context: Context, seconds: Int) {
        _intervalSeconds.value = seconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL, seconds).apply()
    }
}
