package com.sandolpin.weatherquake.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sandolpin.weatherquake.data.weather.WeatherLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 設定画面の項目数が多いため、SharedPreferencesではなくDataStore(Preferences)でまとめて管理する。
val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

enum class DarkModeOption { ON, OFF, SYSTEM }
enum class WeatherBackgroundStyle { DYNAMIC, PLAIN_WHITE, PLAIN_BLACK }
enum class CardStyle { FILLED, GLASSMORPHISM }
enum class TemperatureFontStyle { DEFAULT, RECOMMENDED }
enum class IntensityColorContrast { DEFAULT, LIGHT, HIGH_CONTRAST }

/** 設定画面で編集する全項目をまとめた状態。デフォルト値もここで定義する。 */
data class AppSettingsState(
    // 全体
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,

    // 天気画面設定
    val weatherBackgroundStyle: WeatherBackgroundStyle = WeatherBackgroundStyle.DYNAMIC,
    val showSunTimes: Boolean = true,
    val showHourlyForecast: Boolean = true,
    val showWeatherDetails: Boolean = true,
    val weatherCardStyle: CardStyle = CardStyle.GLASSMORPHISM,
    val weatherCardOpacity: Float = 0.4f, // 0.0〜0.8
    val temperatureFont: TemperatureFontStyle = TemperatureFontStyle.RECOMMENDED,

    // 天気画面: 地点検索履歴・ホーム画面に表示する地域(複数)
    // searchHistory  : これまでに検索したことがある地点すべて(検索シートの「履歴」に表示)
    // homeLocations  : ホーム画面でスワイプ表示する地点(順序を保持。先頭 = 起動時のデフォルト地点)
    //                   空の場合はWeatherViewModel側で「前橋市」を仮のデフォルトとして扱う。
    val searchHistory: List<WeatherLocation> = emptyList(),
    val homeLocations: List<WeatherLocation> = emptyList(),

    // 地震画面設定
    val intensityColorContrast: IntensityColorContrast = IntensityColorContrast.DEFAULT,
    val hideUnknownDepthMagnitude: Boolean = false,

    // 通知設定(天気)
    val notifyWeatherMorning: Boolean = false,
    val notifyWeatherNoon: Boolean = false,
    val notifyWeatherEvening: Boolean = false,
    val notifyWeatherNight: Boolean = false,
    val weatherNotifShowIcon: Boolean = true,
    val weatherNotifShowHourlyImage: Boolean = true,

    // 通知設定(緊急地震速報)
    val notifyOnEew: Boolean = true,
    val eewWarningOnly: Boolean = false,
    val eewMinIntensityOrdinal: Int = 1, // IntensityLevel.ordinal (1=ONEに相当させたい場合はUI側で+1補正)
    val eewReceiveUnknownIntensity: Boolean = true,
    val eewShowMapInNotification: Boolean = true,
    val eewTtsReadout: Boolean = false,

    // 通知設定(地震情報)
    val notifyOnQuake: Boolean = true,
    val quakeMinIntensityOrdinal: Int = 1,
    val quakeShowMapInNotification: Boolean = true,
    val quakeTtsReadout: Boolean = false,

    // 通信
    val apiPollingIntervalSeconds: Int = 5
)

/**
 * DataStoreへの読み書きを担うRepository。
 * settingsFlowはUI(SettingsScreen・各ViewModel)で常時collectして利用する想定。
 */
class SettingsRepository(private val context: Context) {

    // 検索履歴・ホーム地点(どちらもList<WeatherLocation>)はDataStore Preferencesが直接扱えない型のため、
    // Gsonで1本のJSON文字列にシリアライズしてstringPreferencesKeyに保存する。
    private val gson = Gson()

    private object Keys {
        val DARK_MODE = stringPreferencesKey("dark_mode")

        val WEATHER_BG_STYLE = stringPreferencesKey("weather_bg_style")
        val SHOW_SUN_TIMES = booleanPreferencesKey("show_sun_times")
        val SHOW_HOURLY_FORECAST = booleanPreferencesKey("show_hourly_forecast")
        val SHOW_WEATHER_DETAILS = booleanPreferencesKey("show_weather_details")
        val WEATHER_CARD_STYLE = stringPreferencesKey("weather_card_style")
        val WEATHER_CARD_OPACITY = floatPreferencesKey("weather_card_opacity")
        val TEMPERATURE_FONT = stringPreferencesKey("temperature_font")

        val SEARCH_HISTORY_JSON = stringPreferencesKey("search_history_json")
        val HOME_LOCATIONS_JSON = stringPreferencesKey("home_locations_json")

        val INTENSITY_CONTRAST = stringPreferencesKey("intensity_contrast")
        val HIDE_UNKNOWN = booleanPreferencesKey("hide_unknown_depth_magnitude")

        val NOTIFY_WEATHER_MORNING = booleanPreferencesKey("notify_weather_morning")
        val NOTIFY_WEATHER_NOON = booleanPreferencesKey("notify_weather_noon")
        val NOTIFY_WEATHER_EVENING = booleanPreferencesKey("notify_weather_evening")
        val NOTIFY_WEATHER_NIGHT = booleanPreferencesKey("notify_weather_night")
        val WEATHER_NOTIF_SHOW_ICON = booleanPreferencesKey("weather_notif_show_icon")
        val WEATHER_NOTIF_SHOW_HOURLY_IMAGE = booleanPreferencesKey("weather_notif_show_hourly_image")

        val NOTIFY_ON_EEW = booleanPreferencesKey("notify_on_eew")
        val EEW_WARNING_ONLY = booleanPreferencesKey("eew_warning_only")
        val EEW_MIN_INTENSITY = intPreferencesKey("eew_min_intensity")
        val EEW_RECEIVE_UNKNOWN = booleanPreferencesKey("eew_receive_unknown")
        val EEW_SHOW_MAP = booleanPreferencesKey("eew_show_map")
        val EEW_TTS = booleanPreferencesKey("eew_tts")

        val NOTIFY_ON_QUAKE = booleanPreferencesKey("notify_on_quake")
        val QUAKE_MIN_INTENSITY = intPreferencesKey("quake_min_intensity")
        val QUAKE_SHOW_MAP = booleanPreferencesKey("quake_show_map")
        val QUAKE_TTS = booleanPreferencesKey("quake_tts")

        val API_POLLING_INTERVAL = intPreferencesKey("api_polling_interval")
    }

    val settingsFlow: Flow<AppSettingsState> = context.settingsDataStore.data.map { prefs ->
        AppSettingsState(
            darkMode = prefs[Keys.DARK_MODE]?.let { runCatching { DarkModeOption.valueOf(it) }.getOrNull() } ?: DarkModeOption.SYSTEM,
            weatherBackgroundStyle = prefs[Keys.WEATHER_BG_STYLE]?.let { runCatching { WeatherBackgroundStyle.valueOf(it) }.getOrNull() } ?: WeatherBackgroundStyle.DYNAMIC,
            showSunTimes = prefs[Keys.SHOW_SUN_TIMES] ?: true,
            showHourlyForecast = prefs[Keys.SHOW_HOURLY_FORECAST] ?: true,
            showWeatherDetails = prefs[Keys.SHOW_WEATHER_DETAILS] ?: true,
            weatherCardStyle = prefs[Keys.WEATHER_CARD_STYLE]?.let { runCatching { CardStyle.valueOf(it) }.getOrNull() } ?: CardStyle.GLASSMORPHISM,
            weatherCardOpacity = prefs[Keys.WEATHER_CARD_OPACITY] ?: 0.4f,
            temperatureFont = prefs[Keys.TEMPERATURE_FONT]?.let { runCatching { TemperatureFontStyle.valueOf(it) }.getOrNull() } ?: TemperatureFontStyle.RECOMMENDED,

            searchHistory = prefs[Keys.SEARCH_HISTORY_JSON]?.let { json -> parseLocationList(json) } ?: emptyList(),
            homeLocations = prefs[Keys.HOME_LOCATIONS_JSON]?.let { json -> parseLocationList(json) } ?: emptyList(),

            intensityColorContrast = prefs[Keys.INTENSITY_CONTRAST]?.let { runCatching { IntensityColorContrast.valueOf(it) }.getOrNull() } ?: IntensityColorContrast.DEFAULT,
            hideUnknownDepthMagnitude = prefs[Keys.HIDE_UNKNOWN] ?: false,

            notifyWeatherMorning = prefs[Keys.NOTIFY_WEATHER_MORNING] ?: false,
            notifyWeatherNoon = prefs[Keys.NOTIFY_WEATHER_NOON] ?: false,
            notifyWeatherEvening = prefs[Keys.NOTIFY_WEATHER_EVENING] ?: false,
            notifyWeatherNight = prefs[Keys.NOTIFY_WEATHER_NIGHT] ?: false,
            weatherNotifShowIcon = prefs[Keys.WEATHER_NOTIF_SHOW_ICON] ?: true,
            weatherNotifShowHourlyImage = prefs[Keys.WEATHER_NOTIF_SHOW_HOURLY_IMAGE] ?: true,

            notifyOnEew = prefs[Keys.NOTIFY_ON_EEW] ?: true,
            eewWarningOnly = prefs[Keys.EEW_WARNING_ONLY] ?: false,
            eewMinIntensityOrdinal = prefs[Keys.EEW_MIN_INTENSITY] ?: 1,
            eewReceiveUnknownIntensity = prefs[Keys.EEW_RECEIVE_UNKNOWN] ?: true,
            eewShowMapInNotification = prefs[Keys.EEW_SHOW_MAP] ?: true,
            eewTtsReadout = prefs[Keys.EEW_TTS] ?: false,

            notifyOnQuake = prefs[Keys.NOTIFY_ON_QUAKE] ?: true,
            quakeMinIntensityOrdinal = prefs[Keys.QUAKE_MIN_INTENSITY] ?: 1,
            quakeShowMapInNotification = prefs[Keys.QUAKE_SHOW_MAP] ?: true,
            quakeTtsReadout = prefs[Keys.QUAKE_TTS] ?: false,

            apiPollingIntervalSeconds = prefs[Keys.API_POLLING_INTERVAL] ?: 5
        )
    }

    private fun parseLocationList(json: String): List<WeatherLocation>? = runCatching {
        val type = object : TypeToken<List<WeatherLocation>>() {}.type
        gson.fromJson<List<WeatherLocation>>(json, type)
    }.getOrNull()

    suspend fun update(transform: (AppSettingsState) -> AppSettingsState) {
        // 現在値を読んでから更新後の値をまとめて書き込む(項目ごとの個別update関数を量産しないための共通口)
        val current = settingsFlow.first()
        val updated = transform(current)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = updated.darkMode.name
            prefs[Keys.WEATHER_BG_STYLE] = updated.weatherBackgroundStyle.name
            prefs[Keys.SHOW_SUN_TIMES] = updated.showSunTimes
            prefs[Keys.SHOW_HOURLY_FORECAST] = updated.showHourlyForecast
            prefs[Keys.SHOW_WEATHER_DETAILS] = updated.showWeatherDetails
            prefs[Keys.WEATHER_CARD_STYLE] = updated.weatherCardStyle.name
            prefs[Keys.WEATHER_CARD_OPACITY] = updated.weatherCardOpacity
            prefs[Keys.TEMPERATURE_FONT] = updated.temperatureFont.name

            prefs[Keys.SEARCH_HISTORY_JSON] = gson.toJson(updated.searchHistory)
            prefs[Keys.HOME_LOCATIONS_JSON] = gson.toJson(updated.homeLocations)

            prefs[Keys.INTENSITY_CONTRAST] = updated.intensityColorContrast.name
            prefs[Keys.HIDE_UNKNOWN] = updated.hideUnknownDepthMagnitude

            prefs[Keys.NOTIFY_WEATHER_MORNING] = updated.notifyWeatherMorning
            prefs[Keys.NOTIFY_WEATHER_NOON] = updated.notifyWeatherNoon
            prefs[Keys.NOTIFY_WEATHER_EVENING] = updated.notifyWeatherEvening
            prefs[Keys.NOTIFY_WEATHER_NIGHT] = updated.notifyWeatherNight
            prefs[Keys.WEATHER_NOTIF_SHOW_ICON] = updated.weatherNotifShowIcon
            prefs[Keys.WEATHER_NOTIF_SHOW_HOURLY_IMAGE] = updated.weatherNotifShowHourlyImage

            prefs[Keys.NOTIFY_ON_EEW] = updated.notifyOnEew
            prefs[Keys.EEW_WARNING_ONLY] = updated.eewWarningOnly
            prefs[Keys.EEW_MIN_INTENSITY] = updated.eewMinIntensityOrdinal
            prefs[Keys.EEW_RECEIVE_UNKNOWN] = updated.eewReceiveUnknownIntensity
            prefs[Keys.EEW_SHOW_MAP] = updated.eewShowMapInNotification
            prefs[Keys.EEW_TTS] = updated.eewTtsReadout

            prefs[Keys.NOTIFY_ON_QUAKE] = updated.notifyOnQuake
            prefs[Keys.QUAKE_MIN_INTENSITY] = updated.quakeMinIntensityOrdinal
            prefs[Keys.QUAKE_SHOW_MAP] = updated.quakeShowMapInNotification
            prefs[Keys.QUAKE_TTS] = updated.quakeTtsReadout

            prefs[Keys.API_POLLING_INTERVAL] = updated.apiPollingIntervalSeconds
        }
    }

    /** 検索した地点を履歴の先頭に追加する(同名地点は除去してから追加し、最大12件まで保持) */
    suspend fun addSearchHistory(location: WeatherLocation) {
        update { current ->
            val deduped = current.searchHistory.filterNot { it.name == location.name }
            current.copy(searchHistory = (listOf(location) + deduped).take(12))
        }
    }

    /**
     * ホーム画面に表示する地点の一覧に追加/削除する。
     * 一覧が空(=まだカスタマイズしていない、前橋市が仮デフォルトとして使われている)状態から
     * 追加する場合は、fallbackWhenEmptyを先に一覧へ含めてから追加する。
     * こうしないと「初めて1件をホームに追加した瞬間、それまで仮表示されていた前橋市が消える」
     * という直感に反する挙動になってしまう。
     */
    suspend fun setHomeLocationIncluded(location: WeatherLocation, included: Boolean, fallbackWhenEmpty: WeatherLocation) {
        update { current ->
            val base = current.homeLocations.ifEmpty { listOf(fallbackWhenEmpty) }
            val without = base.filterNot { it.name == location.name }
            val updatedList = if (included) without + location else without
            current.copy(homeLocations = updatedList)
        }
    }

    /** 指定した地点をホーム地点一覧の先頭(=起動時のデフォルト)に移動する。まだ一覧に無ければ先頭に新規追加する。 */
    suspend fun pinAsDefaultHomeLocation(location: WeatherLocation, fallbackWhenEmpty: WeatherLocation) {
        update { current ->
            val base = current.homeLocations.ifEmpty { listOf(fallbackWhenEmpty) }
            val without = base.filterNot { it.name == location.name }
            current.copy(homeLocations = listOf(location) + without)
        }
    }

    /** 履歴からも、ホーム画面表示一覧からも、指定した地点を完全に削除する */
    suspend fun removeLocationEverywhere(location: WeatherLocation) {
        update { current ->
            current.copy(
                searchHistory = current.searchHistory.filterNot { it.name == location.name },
                homeLocations = current.homeLocations.filterNot { it.name == location.name }
            )
        }
    }
}