package com.sandolpin.weatherquake.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
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
 */
object NotificationHelper {

    const val CHANNEL_ID_FORECAST = "eew_forecast"
    const val CHANNEL_ID_WARNING = "eew_warning"
    const val CHANNEL_ID_WARNING_BYPASS = "eew_warning_bypass" // サイレントモード中も鳴らす用
    const val CHANNEL_ID_QUAKE = "quake_info"
    const val CHANNEL_ID_WEATHER = "weather_periodic"

    /**
     * WarnArea[].ChiikiのAdmin(例: "熊本県熊本地方")から都道府県名だけを取り出すための一覧。
     * 47都道府県の名称は互いに他のプレフィックスにならないため、startsWithで安全に抽出できる。
     */
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

    /** しきい値スライダーに使う震度の並び(UNKNOWNは意味が無いため除外) */
    val selectableLevels: List<IntensityLevel> = listOf(
        IntensityLevel.ONE, IntensityLevel.TWO, IntensityLevel.THREE, IntensityLevel.FOUR,
        IntensityLevel.FIVE_MINUS, IntensityLevel.FIVE_PLUS,
        IntensityLevel.SIX_MINUS, IntensityLevel.SIX_PLUS, IntensityLevel.SEVEN
    )

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_FORECAST, "緊急地震速報（予報）", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_WARNING, "緊急地震速報（警報・特別警報）", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
        // サイレントモード(おやすみモード)中も鳴らすための専用チャンネル。
        // Androidの仕様上、チャンネルのbypassDndは作成時にしか設定できないため、
        // 通常の警報チャンネルとは別に用意し、設定ONの時だけこちらへ通知する。
        // ユーザーは初回、端末側の「マナーモードの例外を許可」を求められる/設定で許可する必要がある。
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_WARNING_BYPASS, "緊急地震速報（警報・サイレント時も通知）", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setBypassDnd(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_QUAKE, "地震情報", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_WEATHER, "天気予報通知", NotificationManager.IMPORTANCE_LOW)
        )
    }

    // ============================== 緊急地震速報 ==============================

    fun isEnabledForEew(eew: JmaEew, settings: AppSettingsState): Boolean {
        if (!settings.notifyOnEew) return false
        if (eew.isCancel) return true // 取消は常に通知
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

    fun sendEewNotification(context: Context, eew: JmaEew, settings: AppSettingsState) {
        val codeType = EewCodeType.classify(eew)
        if (!isEnabledForEew(eew, settings)) return

        val level = IntensityLevel.fromApiString(eew.MaxIntensity)
        val isWarningTier = codeType == EewCodeType.WARNING || codeType == EewCodeType.EMERGENCY_WARNING
        val channelId = when {
            isWarningTier && settings.eewOverrideSilentMode -> CHANNEL_ID_WARNING_BYPASS
            isWarningTier -> CHANNEL_ID_WARNING
            else -> CHANNEL_ID_FORECAST
        }

        val serialLabel = "第${eew.Serial}報${if (eew.isFinal) "（最終）" else ""}"

        // 警報・特別警報のみ「○○で地震 強い揺れに警戒」の専用タイトルにする。
        // 予報・取消は従来通り種別名+第N報のタイトルのまま。
        val notificationTitle = if (isWarningTier) {
            "${eew.Hypocenter}で地震 強い揺れに警戒"
        } else {
            "${codeType.displayName} $serialLabel"
        }

        // 警戒地域(都道府県)のリストは通知本文・読み上げの両方で使うため先に計算しておく
        val warnPrefectures = if (eew.isWarn) {
            eew.WarnArea.orEmpty().map { extractPrefecture(it.Chiiki) }.distinct()
        } else {
            emptyList()
        }

        val body = buildString {
            // 警報・特別警報は「予想最大震度〇」から書き出し、深さ・M・警戒地域は従来通り続ける。
            // 予報・取消は「○○で地震 推定震度は〇」のまま。
            if (isWarningTier) {
                if (eew.isAssumption) {
                    append("予想最大震度${level.label}（PLUM法による推定震源）")
                } else {
                    append("予想最大震度${level.label} 深さ${eew.Depth.toInt()}km M${eew.Magunitude}")
                }
            } else {
                if (eew.isAssumption) {
                    append("${eew.Hypocenter}で地震 推定震度は${level.label}（PLUM法による推定震源）")
                } else {
                    append("${eew.Hypocenter}で地震 推定震度は${level.label} 深さ${eew.Depth.toInt()}km M${eew.Magunitude}")
                }
            }
            if (warnPrefectures.isNotEmpty()) {
                append("\n強い揺れに警戒:${warnPrefectures.joinToString("、")}")
            }
        }

        val notifBuilder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(notificationTitle)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setLargeIcon(buildIntensityIcon(context, level))
            .setPriority(if (isWarningTier) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

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

        NotificationManagerCompat.from(context).notify(eew.EventID.hashCode(), notifBuilder.build())

        if (settings.eewTtsReadout) {
            TtsAnnouncer.announceEew(
                hypocenter = eew.Hypocenter,
                maxIntensityLabel = level.formalLabel,
                warnAreaLabel = warnPrefectures.takeIf { it.isNotEmpty() }?.joinToString("、")
            )
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

    fun sendQuakeNotification(context: Context, quake: QuakeCardState, settings: AppSettingsState) {
        if (!isEnabledForQuake(quake, settings)) return

        val level = IntensityLevel.fromP2pScale(quake.maxScale)
        val maxPoint = quake.points.maxByOrNull { it.scale ?: -1 }
        val areaLabel = maxPoint?.let { "${it.pref}${it.addr}" }

        val body = buildString {
            append("${quake.hypocenterName}で地震")
            append(" 最大震度${level.label}")
            quake.depthKm?.let { append(" 深さ${it}km") }
            quake.magnitude?.let { append(" M$it") }
            areaLabel?.let { append("\n観測: $it") }
        }

        val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID_QUAKE)
            .setContentTitle("地震情報")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(buildIntensityIcon(context, level))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)

        if (settings.quakeShowMapInNotification) {
            val pointLevels = quake.points.associate { it.pref to IntensityLevel.fromP2pScale(it.scale) }
            val mapBitmap = runCatching {
                WarnAreaMapRenderer.renderForQuake(context, quake.longitude, quake.latitude, pointLevels)
            }.getOrNull()
            if (mapBitmap != null) {
                notifBuilder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(mapBitmap)
                        .setBigContentTitle("地震情報")
                        .setSummaryText(body)
                )
            }
        }

        NotificationManagerCompat.from(context).notify(quake.id.hashCode(), notifBuilder.build())

        if (settings.quakeTtsReadout) {
            TtsAnnouncer.announceQuake(
                occurredAtLabel = quake.occurredAtLabel,
                hypocenter = quake.hypocenterName,
                depthKm = quake.depthKm,
                magnitude = quake.magnitude,
                maxIntensityLabel = level.formalLabel,
                observedPrefecture = maxPoint?.pref
            )
        }
    }

    // ============================== 天気の定時通知 ==============================

    /** 「アイコンに天気」「画像部分に時間ごと予報」のスタイルで、指定の時間帯ラベル付き通知を送る */
    fun sendWeatherNotification(context: Context, weather: WeatherUiState, settings: AppSettingsState, periodLabel: String) {
        val title = "${periodLabel}の天気 ${weather.location.name}"
        val body = "${weather.condition.label} ${weather.currentTemperature}℃（最高${weather.tempMaxToday}℃ / 最低${weather.tempMinToday}℃）"

        val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID_WEATHER)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        if (settings.weatherNotifShowHourlyImage && weather.hourly.isNotEmpty()) {
            val bitmap = buildHourlyForecastBitmap(context, weather.hourly.take(6))
            notifBuilder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        }

        NotificationManagerCompat.from(context).notify("weather_$periodLabel".hashCode(), notifBuilder.build())
    }

    /** 時間ごとの気温を横並びで描画した簡易バーチャート風Bitmap(通知の画像部分に使用) */
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

    /** 端末側で通知が許可されているか(OFFの場合、notify()は例外を出さず黙って失敗する) */
    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** @return 実際に通知の送信を試みたらtrue。通知が許可されていない場合はfalseを返し、呼び出し側で案内する。 */
    fun sendTestNotification(context: Context, kind: TestNotificationKind, settings: AppSettingsState): Boolean {
        if (!areNotificationsEnabled(context)) return false
        when (kind) {
            TestNotificationKind.EEW -> sendEewNotification(
                context, sampleEew(),
                settings.copy(notifyOnEew = true, eewMinIntensityOrdinal = 1, eewReceiveUnknownIntensity = true, eewWarningOnly = false)
            )
            TestNotificationKind.EEW_WARNING -> sendEewNotification(
                context, sampleEewWarning(),
                settings.copy(notifyOnEew = true, eewMinIntensityOrdinal = 1, eewReceiveUnknownIntensity = true, eewWarningOnly = false)
            )
            TestNotificationKind.EEW_EMERGENCY_WARNING -> sendEewNotification(
                context, sampleEewEmergencyWarning(),
                settings.copy(notifyOnEew = true, eewMinIntensityOrdinal = 1, eewReceiveUnknownIntensity = true, eewWarningOnly = false)
            )
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

    private fun sampleEew() = JmaEew(
        Title = "緊急地震速報（テスト）", CodeType = "緊急地震速報", EventID = "test_event",
        Serial = 6, AnnouncedTime = "2026/08/21 19:10:15", OriginTime = "2026/08/21 19:09:45",
        Hypocenter = "熊本県熊本地方", Latitude = 32.8, Longitude = 130.7, Magunitude = 4.4, Depth = 10.0,
        MaxIntensity = "3", isWarn = false, isFinal = false
    )

    /** 通知テスト用: 警報(震度6弱未満)のサンプル */
    private fun sampleEewWarning() = JmaEew(
        Title = "緊急地震速報（警報）", CodeType = "緊急地震速報", EventID = "test_event_warning",
        Serial = 3, AnnouncedTime = "2026/08/24 10:00:15", OriginTime = "2026/08/24 09:59:45",
        Hypocenter = "茨城県沖", Latitude = 36.0, Longitude = 140.9, Magunitude = 6.2, Depth = 40.0,
        MaxIntensity = "5強", isWarn = true, isFinal = false,
        WarnArea = listOf(
            WarnArea(Chiiki = "茨城県北部", Shindo1 = "5強"),
            WarnArea(Chiiki = "栃木県南部", Shindo1 = "5弱")
        )
    )

    /** 通知テスト用: 特別警報(震度6弱以上)のサンプル */
    private fun sampleEewEmergencyWarning() = JmaEew(
        Title = "緊急地震速報（特別警報）", CodeType = "緊急地震速報", EventID = "test_event_emergency",
        Serial = 4, AnnouncedTime = "2026/08/24 10:05:20", OriginTime = "2026/08/24 10:04:50",
        Hypocenter = "千葉県東方沖", Latitude = 35.6, Longitude = 140.9, Magunitude = 7.3, Depth = 30.0,
        MaxIntensity = "6強", isWarn = true, isFinal = true,
        WarnArea = listOf(
            WarnArea(Chiiki = "千葉県北東部", Shindo1 = "6強"),
            WarnArea(Chiiki = "茨城県南部", Shindo1 = "6弱")
        )
    )

    private fun sampleQuake() = QuakeCardState(
        id = "test_quake", hypocenterName = "熊本県熊本地方", depthKm = 10, magnitude = 4.4,
        occurredAtLabel = "8/21 19:09", latitude = 32.8, longitude = 130.7, maxScale = 30, points = emptyList()
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