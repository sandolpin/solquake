package com.sandolpin.weatherquake.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sandolpin.weatherquake.data.eew.JmaEew
import com.sandolpin.weatherquake.data.quake.QuakeRepository
import com.sandolpin.weatherquake.data.settings.AppSettingsState
import com.sandolpin.weatherquake.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Wolfx APIのWebSocketに常時接続し、緊急地震速報を受信するForeground Service。
 * 加えて、同じポーリングループの中でP2P地震情報(地震情報/code=551)も定期取得する
 * (常駐Serviceを2つ持たずに済ませるための相乗り設計)。
 *
 * ポイント:
 * - startForeground()を5秒以内に呼ばないとOSにクラッシュさせられるため、onStartCommand内で最優先に実行する
 * - 通信が切れても5秒後に自動で再接続する
 * - 受信したデータはEewRepository/QuakeRepositoryを通じてUI側に伝える
 * - 通知要否・スタイルの判定にはDataStore(SettingsRepository)の最新値を都度参照する
 */
class EewService : Service() {

    companion object {
        private const val SERVICE_CHANNEL_ID = "eew_service_channel"
        private const val SERVICE_NOTIF_ID = 1
        private const val WS_URL = "wss://ws-api.wolfx.jp/jma_eew"
        private const val API_TEST_URL = "https://api.wolfx.jp/jma_eew.json"
        private const val RECONNECT_DELAY_MS = 5000L
        // P2P地震情報は緊急地震速報ほど即時性が求められないため、EEWポーリングより長い間隔で確認する
        private const val QUAKE_POLL_INTERVAL_MS = 20_000L

        fun start(context: Context) {
            val intent = Intent(context, EewService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EewService::class.java))
        }

        /** 設定画面の「API接続テスト」用の一回限りのテスト通信 */
        fun testConnection(onResult: (success: Boolean, label: String, message: String) -> Unit) {
            val client = OkHttpClient()
            val request = Request.Builder().url(API_TEST_URL).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val (label, message) = ErrorMessageMapper.fromThrowable(e)
                    onResult(false, label, message)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        onResult(true, "OK", "接続されています")
                    } else {
                        val (label, message) = ErrorMessageMapper.fromHttpCode(response.code)
                        onResult(false, label, message)
                    }
                    response.close()
                }
            })
        }
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var quakePollingJob: Job? = null

    private lateinit var settingsRepository: SettingsRepository
    @Volatile private var latestSettings: AppSettingsState = AppSettingsState()

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
        NotificationHelper.createChannels(this)
        TtsAnnouncer.init(this)

        settingsRepository = SettingsRepository(applicationContext)
        ApiPollingSettings.load(this)

        // DataStoreの最新設定値を、コールバック(非suspend)からも参照できるようキャッシュしておく
        serviceScope.launch {
            settingsRepository.settingsFlow.collect { latestSettings = it }
        }

        serviceScope.launch {
            ApiPollingSettings.intervalSeconds.collect { seconds ->
                startPolling(seconds)
            }
        }

        startQuakePolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("天気・地震 監視中")
            .setContentText("緊急地震速報・地震情報を受信しています")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(SERVICE_NOTIF_ID, notif)

        connect()
        return START_STICKY
    }

    private fun connect() {
        EewRepository.updateConnectionStatus(ConnectionStatus.Connecting)
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                EewRepository.updateConnectionStatus(ConnectionStatus.Connected)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val (label, message) = if (response != null) {
                    ErrorMessageMapper.fromHttpCode(response.code)
                } else {
                    ErrorMessageMapper.fromThrowable(t)
                }
                EewRepository.updateConnectionStatus(ConnectionStatus.Disconnected(label, message))
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                EewRepository.updateConnectionStatus(
                    ConnectionStatus.Disconnected("切断", "サーバーとの接続が切断されました")
                )
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { connect() }
        reconnectRunnable = runnable
        mainHandler.postDelayed(runnable, RECONNECT_DELAY_MS)
    }

    /** WebSocketとは別に、指定した間隔(秒)でWolfx APIをHTTPで取得し直すループ */
    private fun startPolling(intervalSeconds: Int) {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                fetchOnce()
                delay(intervalSeconds * 1000L)
            }
        }
    }

    /** P2P地震情報(地震情報)を定期取得し、新着があれば通知する */
    private fun startQuakePolling() {
        quakePollingJob?.cancel()
        quakePollingJob = serviceScope.launch {
            while (isActive) {
                val newlyAdded = QuakeRepository.pollOnce()
                newlyAdded.forEach { quake ->
                    NotificationHelper.sendQuakeNotification(this@EewService, quake, latestSettings)
                }
                delay(QUAKE_POLL_INTERVAL_MS)
            }
        }
    }

    private fun fetchOnce() {
        try {
            val request = Request.Builder().url(API_TEST_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string() ?: return

                EewRepository.markFetched()

                val eew = gson.fromJson(body, JmaEew::class.java)
                if (eew.isTraining || eew.EventID.isBlank()) return

                val isNewOrUpdated = EewRepository.onEewReceived(eew)
                if (isNewOrUpdated) {
                    NotificationHelper.sendEewNotification(this, eew, latestSettings)
                }
            }
        } catch (e: Exception) {
            // 通信失敗時は次回のポーリングで再試行するため、ここでは無視する
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            EewRepository.markFetched()

            val type = json.get("type")?.asString ?: return
            if (type != "jma_eew") return

            val eew = gson.fromJson(text, JmaEew::class.java)
            if (eew.isTraining) return

            val isNewOrUpdated = EewRepository.onEewReceived(eew)
            if (isNewOrUpdated) {
                NotificationHelper.sendEewNotification(this, eew, latestSettings)
            }
        } catch (e: Exception) {
            // ハートビートや解釈不能なメッセージはここで無視される
        }
    }

    private fun createServiceChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL_ID, "常駐通知", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        webSocket?.close(1000, "Service stopped")
        pollingJob?.cancel()
        quakePollingJob?.cancel()
        TtsAnnouncer.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
