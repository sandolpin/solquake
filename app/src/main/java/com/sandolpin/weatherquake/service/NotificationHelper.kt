package com.sandolpin.weatherquake.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.RawRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import com.sandolpin.weatherquake.MainActivity
import com.sandolpin.weatherquake.R
import com.sandolpin.weatherquake.data.IntensityLevel
import com.sandolpin.weatherquake.data.eew.EewCodeType
import com.sandolpin.weatherquake.data.eew.JmaEew
import com.sandolpin.weatherquake.data.eew.WarnArea
import com.sandolpin.weatherquake.data.quake.QuakeCardState
import com.sandolpin.weatherquake.data.settings.AppSettingsState
import com.sandolpin.weatherquake.data.weather.WeatherUiState

/**
 * 通知チャンネルの作成、通知の送信を担当する。
 * しきい値・ON/OFFなどの判定はAppSettingsState(DataStoreから読み込んだ値)を受け取って行う。
 *
 * [MapLibre移行に伴う変更点]
 * sendEewNotification / sendQuakeNotification / sendTestNotification は、地図画像の生成に
 * WarnAreaMapRenderer(MapSnapshotterベース、内部でメインスレッドに切り替える)を使うため
 * suspend funになった。呼び出し元はすべてコルーチンスコープから呼ぶ必要がある
 * (EewService, SettingsViewModel側の対応も参照)。
 */
object NotificationHelper {

    const val CHANNEL_ID_FORECAST = "eew_forecast_v3"
    const val CHANNEL_ID_WARNING = "eew_warning_v3"
    const val CHANNEL_ID_EMERGENCY_WARNING = "eew_emergency_warning_v3"

    const val CHANNEL_ID_QUAKE = "quake_info"
    const val CHANNEL_ID_WEATHER = "weather_periodic"

    private val PREFECTURE_NAMES = listOf(
        "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
        "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
        "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県",
        "静岡県", "愛知県", "三重県", "滋賀県", "京都府", "大阪府", "兵庫県",
        "奈良県", "和歌山県", "鳥取県", "島根県", "岡山県", "広島県", "山口県",
        "徳島県", "香川県", "愛媛県", "高知県", "福岡県", "佐賀県", "長崎県",
        "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"
    )

    private fun extractPrefecture(chiiki: String): String =
        PREFECTURE_NAMES.firstOrNull { chiiki.startsWith(it) } ?: chiiki

    val selectableLevels: List<IntensityLevel> = listOf(
        IntensityLevel.ONE, IntensityLevel.TWO, IntensityLevel.THREE, IntensityLevel.FOUR,
        IntensityLevel.FIVE_MINUS, IntensityLevel.FIVE_PLUS,
        IntensityLevel.SIX_MINUS, IntensityLevel.SIX_PLUS, IntensityLevel.SEVEN
    )

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_FORECAST, "緊急地震速報（予報）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            buildSoundChannel(context, CHANNEL_ID_WARNING, "緊急地震速報（警報）", NotificationManager.IMPORTANCE_HIGH, R.raw.eew1, vibrate = true)
        )
        manager.createNotificationChannel(
            buildSoundChannel(context, CHANNEL_ID_EMERGENCY_WARNING, "緊急地震速報（特別警報）", NotificationManager.IMPORTANCE_HIGH, R.raw.eew2, vibrate = true)
        )

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_QUAKE, "地震情報", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_WEATHER, "天気予報通知", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun playForecastSound(context: Context, level: IntensityLevel) {
        val soundUri = when (level) {
            IntensityLevel.TWO -> Uri.parse("android.resource://${context.packageName}/${R.raw.intensity_2}")
            IntensityLevel.THREE -> Uri.parse("android.resource://${context.packageName}/${R.raw.intensity_3}")
            IntensityLevel.FOUR -> Uri.parse("android.resource://${context.packageName}/${R.raw.intensity_4}")
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, soundUri)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); true }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            // 再生に失敗しても通知自体の表示は継続するため、ここでは無視する
        }
    }

    private fun buildSoundChannel(
        context: Context,
        id: String,
        name: String,
        importance: Int,
        @RawRes soundRes: Int,
        vibrate: Boolean = false
    ): NotificationChannel {
        val soundUri = Uri.parse("android.resource://${context.packageName}/$soundRes")
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return NotificationChannel(id, name, importance).apply {
            setSound(soundUri, audioAttributes)
            enableVibration(vibrate)
        }
    }

    private fun buildContentIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ============================== 緊急地震速報 ==============================

    fun isEnabledForEew(eew: JmaEew, settings: AppSettingsState): Boolean {
        if (!settings.notifyOnEew) return false
        if (eew.isCancel) return true
        if (settings.eewWarningOnly && !eew.isWarn) return false

        val level = IntensityLevel.fromApiString(eew.MaxIntensity)
        if (level == IntensityLevel.UNKNOWN) return settings.eewReceiveUnknownIntensity

        val minLevel = selectableLevels.getOrElse(settings.eewMinIntensityOrdinal - 1) { IntensityLevel.ONE }
        return level.ordinal >= minLevel.ordinal
    }

    private fun buildIntensityIcon(context: Context, level: IntensityLevel): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = level.bgColor.toArgb() }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 24f, 24f, bgPaint)

        val montserrat = ResourcesCompat.getFont(context, R.font.montserrat)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = level.textColor.toArgb()
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = montserrat ?: Typeface.DEFAULT_BOLD
        }
        val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(level.label, size / 2f, yPos, textPaint)
        return bitmap
    }

    suspend fun sendEewNotification(context: Context, eew: JmaEew, settings: AppSettingsState) {
        val codeType = EewCodeType.classify(eew)
        if (!isEnabledForEew(eew, settings)) return

        val level = IntensityLevel.fromApiString(eew.MaxIntensity)
        val isWarningTier = codeType == EewCodeType.WARNING || codeType == EewCodeType.EMERGENCY_WARNING
        val serialLabel = "第${eew.Serial}報"
        val warnAreaLabel = eew.WarnArea.orEmpty().map { extractPrefecture(it.Chiiki) }.distinct().joinToString("、")

        val notificationTitle: String
        val body: String
        when {
            codeType == EewCodeType.CANCEL -> {
                notificationTitle = "緊急地震速報 (取消)$serialLabel"
                body = "この緊急地震速報は取り消されました。"
            }
            isWarningTier -> {
                notificationTitle = "${eew.Hypocenter}で地震 強い揺れに警戒 $serialLabel"
                body = buildString {
                    append("予想最大震度${level.label}、深さ${eew.Depth.toInt()}km、M${eew.Magunitude}")
                    if (warnAreaLabel.isNotEmpty()) append("、強い揺れに警戒:$warnAreaLabel")
                }
            }
            else -> {
                notificationTitle = "緊急地震速報 (予報)$serialLabel"
                body = "${eew.Hypocenter}で地震 予想最大震度${level.label}、深さ${eew.Depth.toInt()}km、M${eew.Magunitude}"
            }
        }

        val channelId = when (codeType) {
            EewCodeType.EMERGENCY_WARNING -> CHANNEL_ID_EMERGENCY_WARNING
            EewCodeType.WARNING -> CHANNEL_ID_WARNING
            EewCodeType.CANCEL, EewCodeType.FORECAST -> CHANNEL_ID_FORECAST
        }

        val notifId = eew.EventID.hashCode()

        val notifBuilder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(notificationTitle)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setLargeIcon(buildIntensityIcon(context, level))
            .setPriority(if (isWarningTier) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, notifId))

        if (settings.eewShowMapInNotification) {
            val mapBitmap = runCatching { WarnAreaMapRenderer.renderForEew(context, eew) }.getOrNull()
            if (mapBitmap != null) {
                notifBuilder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(mapBitmap)
                        .setBigContentTitle(notificationTitle)
                        .setSummaryText(body)
                )
            }
        }

        NotificationManagerCompat.from(context).notify(notifId, notifBuilder.build())

        if (codeType == EewCodeType.FORECAST || codeType == EewCodeType.CANCEL) {
            playForecastSound(context, level)
        }

        if (settings.eewTtsReadout) {
            TtsAnnouncer.announceEew(eew.Hypocenter, level.formalLabel, isWarningTier, warnAreaLabel.ifEmpty { null })
        }
    }

    // ============================== 地震情報(P2P) ==============================

    fun isEnabledForQuake(quake: QuakeCardState, settings: AppSettingsState): Boolean {
        if (!settings.notifyOnQuake) return false
        val level = IntensityLevel.fromP2pScale(quake.maxScale)
        if (level == IntensityLevel.UNKNOWN) return true
        val minLevel = selectableLevels.getOrElse(settings.quakeMinIntensityOrdinal - 1) { IntensityLevel.ONE }
        return level.ordinal >= minLevel.ordinal
    }

    suspend fun sendQuakeNotification(context: Context, quake: QuakeCardState, settings: AppSettingsState) {
        if (!isEnabledForQuake(quake, settings)) return

        val level = IntensityLevel.fromP2pScale(quake.maxScale)
        val maxPoint = quake.points.maxByOrNull { it.scale ?: -1 }
        val areaLabel = maxPoint?.let { "${it.pref}${it.addr}" }
        val notificationTitle = quake.issueType.displayName

        val body = buildString {
            append("${quake.hypocenterName}で地震がありました。")
            append(" 最大震度は${level.label}")
            quake.depthKm?.let { append(" 深さ${it}km") }
            quake.magnitude?.let { append(" M$it") }
            areaLabel?.let { append("\n観測: $it") }
        }

        val notifId = quake.id.hashCode()

        val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID_QUAKE)
            .setContentTitle(notificationTitle)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(buildIntensityIcon(context, level))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, notifId))

        if (settings.quakeShowMapInNotification) {
            val pointLevels = quake.points.associate { it.pref to IntensityLevel.fromP2pScale(it.scale) }
            val mapBitmap = runCatching {
                WarnAreaMapRenderer.renderForQuake(context, quake.longitude, quake.latitude, pointLevels)
            }.getOrNull()
            if (mapBitmap != null) {
                notifBuilder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(mapBitmap)
                        .setBigContentTitle(notificationTitle)
                        .setSummaryText(body)
                )
            }
        }

        NotificationManagerCompat.from(context).notify(notifId, notifBuilder.build())

        if (settings.quakeTtsReadout) {
            TtsAnnouncer.announceQuake(level.formalLabel, quake.hypocenterName, areaLabel)
        }
    }

    // ============================== 天気の定時通知 ==============================

    fun sendWeatherNotification(context: Context, weather: WeatherUiState, settings: AppSettingsState, periodLabel: String) {
        val title = "${periodLabel}の天気 ${weather.location.name}"
        val body = "${weather.condition.label} ${weather.currentTemperature}℃（最高${weather.tempMaxToday}℃ / 最低${weather.tempMinToday}℃）"

        val notifId = "weather_$periodLabel".hashCode()

        val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID_WEATHER)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, notifId))

        if (settings.weatherNotifShowHourlyImage && weather.hourly.isNotEmpty()) {
            val bitmap = buildHourlyForecastBitmap(context, weather.hourly.take(6))
            notifBuilder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        }

        NotificationManagerCompat.from(context).notify(notifId, notifBuilder.build())
    }

    private fun buildHourlyForecastBitmap(context: Context, points: List<com.sandolpin.weatherquake.data.weather.ForecastPoint>): Bitmap {
        val width = 720
        val height = 260
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#1976D2"))

        if (points.isEmpty()) return bitmap
        val slotWidth = width / points.size
        val montserrat = ResourcesCompat.getFont(context, R.font.montserrat)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 26f
        }
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 40f
            typeface = montserrat ?: Typeface.DEFAULT_BOLD
        }

        points.forEachIndexed { index, point ->
            val cx = slotWidth * index + slotWidth / 2f
            canvas.drawText(point.label, cx, 50f, labelPaint)
            canvas.drawText("${point.temperature}℃", cx, height - 40f, tempPaint)
        }
        return bitmap
    }

    // ============================== 通知テスト(設定画面用) ==============================

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * @return 実際に通知の送信を試みたらtrue。通知が許可されていない場合はfalseを返し、呼び出し側で案内する。
     * EEW/地震情報のテストは地図生成(MapSnapshotter)を伴うためsuspend fun。
     */
    suspend fun sendTestNotification(context: Context, kind: TestNotificationKind, settings: AppSettingsState): Boolean {
        if (!areNotificationsEnabled(context)) return false
        val relaxedEewSettings = settings.copy(
            notifyOnEew = true, eewMinIntensityOrdinal = 1,
            eewReceiveUnknownIntensity = true, eewWarningOnly = false
        )
        when (kind) {
            TestNotificationKind.EEW -> sendEewNotification(context, sampleEewForecast(), relaxedEewSettings)
            TestNotificationKind.EEW_WARNING -> sendEewNotification(context, sampleEewWarning(), relaxedEewSettings)
            TestNotificationKind.EEW_EMERGENCY_WARNING -> sendEewNotification(context, sampleEewEmergencyWarning(), relaxedEewSettings)
            TestNotificationKind.QUAKE -> sendQuakeNotification(
                context, sampleQuake(),
                settings.copy(notifyOnQuake = true, quakeMinIntensityOrdinal = 1)
            )
            TestNotificationKind.WEATHER -> sendWeatherNotification(
                context, sampleWeather(),
                settings.copy(weatherNotifShowHourlyImage = true),
                periodLabel = "テスト"
            )
        }
        return true
    }

    private fun sampleEewForecast() = JmaEew(
        Title = "緊急地震速報（予報）", CodeType = "緊急地震速報", EventID = "test_event_forecast",
        Serial = 3, AnnouncedTime = "2026/08/21 19:10:15", OriginTime = "2026/08/21 19:09:45",
        Hypocenter = "茨城県南部", Latitude = 36.02, Longitude = 140.20, Magunitude = 4.4, Depth = 50.0,
        MaxIntensity = "3", isWarn = false, isFinal = false
    )

    private fun sampleEewWarning() = JmaEew(
        Title = "緊急地震速報（警報）", CodeType = "緊急地震速報", EventID = "test_event_warning",
        Serial = 6, AnnouncedTime = "2026/08/21 19:10:20", OriginTime = "2026/08/21 19:09:45",
        Hypocenter = "茨城県南部", Latitude = 36.02, Longitude = 140.20, Magunitude = 6.0, Depth = 50.0,
        MaxIntensity = "5弱", isWarn = true, isFinal = false,
        WarnArea = listOf(
            WarnArea(Chiiki = "茨城県南部", Shindo1 = "5弱", Shindo2 = "5弱", Type = "警報", Arrive = true),
            WarnArea(Chiiki = "群馬県南部", Shindo1 = "4", Shindo2 = "4", Type = "警報", Arrive = true),
            WarnArea(Chiiki = "栃木県南部", Shindo1 = "5弱", Shindo2 = "5弱", Type = "警報", Arrive = true)
        )
    )

    private fun sampleEewEmergencyWarning() = JmaEew(
        Title = "緊急地震速報（警報）", CodeType = "緊急地震速報", EventID = "test_event_emergency",
        Serial = 9, AnnouncedTime = "2026/08/21 19:10:25", OriginTime = "2026/08/21 19:09:45",
        Hypocenter = "茨城県南部", Latitude = 36.02, Longitude = 140.20, Magunitude = 7.5, Depth = 50.0,
        MaxIntensity = "7", isWarn = true, isFinal = true,
        WarnArea = listOf(
            WarnArea(Chiiki = "茨城県南部", Shindo1 = "7", Shindo2 = "7", Type = "警報", Arrive = true),
            WarnArea(Chiiki = "群馬県南部", Shindo1 = "6強", Shindo2 = "6強", Type = "警報", Arrive = true),
            WarnArea(Chiiki = "栃木県北部", Shindo1 = "6弱", Shindo2 = "6強", Type = "警報", Arrive = true),
            WarnArea(Chiiki = "茨城県北部", Shindo1 = "6弱", Shindo2 = "6強", Type = "警報", Arrive = true),
            WarnArea(Chiiki = "栃木県南部", Shindo1 = "6強", Shindo2 = "6弱", Type = "警報", Arrive = true)
        )
    )

    private fun sampleQuake() = QuakeCardState(
        id = "test_quake", hypocenterName = "茨城県南部", depthKm = 50, magnitude = 4.4,
        occurredAtLabel = "8/21 19:09", latitude = 36.02, longitude = 140.20, maxScale = 30,
        points = listOf(
            com.sandolpin.weatherquake.data.quake.P2pPoint(pref = "茨城県", addr = "水戸市", scale = 30),
            com.sandolpin.weatherquake.data.quake.P2pPoint(pref = "栃木県", addr = "宇都宮市", scale = 20)
        ),
        issueType = com.sandolpin.weatherquake.data.quake.QuakeIssueType.DETAIL_SCALE
    )

    private fun sampleWeather(): com.sandolpin.weatherquake.data.weather.WeatherUiState {
        val samplePoint = { label: String, temp: Int ->
            com.sandolpin.weatherquake.data.weather.ForecastPoint(
                label = label, temperature = temp,
                condition = com.sandolpin.weatherquake.data.weather.WeatherCondition.CLEAR,
                precipitationMm = 0.0, windSpeed = 3.0
            )
        }
        return com.sandolpin.weatherquake.data.weather.WeatherUiState(
            location = com.sandolpin.weatherquake.data.weather.WeatherLocation("前橋市", 36.3894, 139.0634),
            updatedAtLabel = "15:00",
            currentTemperature = 25,
            apparentTemperature = 28,
            condition = com.sandolpin.weatherquake.data.weather.WeatherCondition.CLEAR,
            isDay = true,
            tempMinToday = 21,
            tempMaxToday = 35,
            sunrise = "05:45",
            sunset = "18:30",
            windSpeed = 5.0,
            windDirectionLabel = "北",
            minutely15 = emptyList(),
            hourly = listOf(
                samplePoint("いま", 25), samplePoint("16:00", 26), samplePoint("17:00", 26),
                samplePoint("18:00", 25), samplePoint("19:00", 23), samplePoint("20:00", 22)
            ),
            daily = emptyList()
        )
    }
}

enum class TestNotificationKind { WEATHER, EEW, EEW_WARNING, EEW_EMERGENCY_WARNING, QUAKE }