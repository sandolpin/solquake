package com.sandolpin.weatherquake.data.weather

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * Open-Meteo Forecast API (https://open-meteo.com/en/docs) のレスポンス。
 * current: 実況値 / hourly: 1時間ごと / minutely15: 15分ごと / daily: 日ごと(最高・最低気温、日の出日の入り)
 */
data class OpenMeteoResponse(
    @SerializedName("current") val current: CurrentBlock?,
    @SerializedName("hourly") val hourly: HourlyBlock?,
    @SerializedName("minutely_15") val minutely15: HourlyBlock?,
    @SerializedName("daily") val daily: DailyBlock?
)

data class CurrentBlock(
    @SerializedName("time") val time: String,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("apparent_temperature") val apparentTemperature: Double,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
    @SerializedName("wind_direction_10m") val windDirection: Int,
    @SerializedName("is_day") val isDay: Int,
    @SerializedName("pressure_msl") val pressureMsl: Double? = null,
    @SerializedName("relative_humidity_2m") val relativeHumidity: Int? = null
)

/**
 * Open-Meteo Air Quality API (https://open-meteo.com/en/docs/air-quality-api) のレスポンス。
 * ヨーロッパ式AQI(european_aqi)とUVインデックスをここから取得する。
 * 気圧(hPa)・湿度(%)はメインのForecast APIのcurrentを使う。
 */
data class AirQualityResponse(
    @SerializedName("current") val current: AirQualityCurrent?
)

data class AirQualityCurrent(
    @SerializedName("european_aqi") val europeanAqi: Int?,
    @SerializedName("uv_index") val uvIndex: Double?
)

/** hourly / minutely_15 共通のフィールド構成(配列が時刻ごとに並行して並ぶ) */
data class HourlyBlock(
    @SerializedName("time") val time: List<String> = emptyList(),
    @SerializedName("temperature_2m") val temperature: List<Double> = emptyList(),
    @SerializedName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int>? = null,
    @SerializedName("precipitation") val precipitation: List<Double>? = null,
    @SerializedName("wind_speed_10m") val windSpeed: List<Double> = emptyList(),
    @SerializedName("wind_direction_10m") val windDirection: List<Int>? = null
)

data class DailyBlock(
    @SerializedName("time") val time: List<String> = emptyList(),
    @SerializedName("sunrise") val sunrise: List<String> = emptyList(),
    @SerializedName("sunset") val sunset: List<String> = emptyList(),
    @SerializedName("temperature_2m_max") val tempMax: List<Double> = emptyList(),
    @SerializedName("temperature_2m_min") val tempMin: List<Double> = emptyList()
)

/** Open-Meteo Geocoding API のレスポンス(地点検索) */
data class GeocodingResponse(
    @SerializedName("results") val results: List<GeocodingResult>?
)

data class GeocodingResult(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("admin1") val admin1: String?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

/** 設定・履歴に保存する地点情報 */
@Immutable
data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * WMO Weather interpretation codes (Open-Meteoが採用) を、
 * このアプリで背景・アイコンの出し分けに使う粒度に丸めたenum。
 * https://open-meteo.com/en/docs (WMO Weather code参照)
 */
enum class WeatherCondition(val label: String) {
    CLEAR("はれ"),
    PARTLY_CLOUDY("はれ時々くもり"),
    CLOUDY("厚い雲"),
    FOG("霧"),
    DRIZZLE("小雨"),
    RAIN("雨"),
    SNOW("雪"),
    THUNDERSTORM("雷雨");

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> CLOUDY
        }
    }
}

/** 画面に表示しやすい形に整形した、ある1時刻分の予報ポイント */
@Immutable
data class ForecastPoint(
    val label: String, // 「いま」「15分後」「16:00」等の表示ラベル
    val temperature: Int,
    val condition: WeatherCondition,
    val precipitationMm: Double,
    val windSpeed: Double
)

/** WeatherScreenが直接描画するために整形済みの状態 */
@Immutable
data class WeatherUiState(
    val location: WeatherLocation,
    val updatedAtLabel: String,
    val currentTemperature: Int,
    val apparentTemperature: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val tempMinToday: Int,
    val tempMaxToday: Int,
    val sunrise: String,
    val sunset: String,
    val windSpeed: Double,
    val windDirectionLabel: String,
    val minutely15: List<ForecastPoint>,
    val hourly: List<ForecastPoint>,
    val daily: List<ForecastPoint>,
    val airQualityIndex: Int? = null,   // ヨーロッパ式AQI(0が最良、値が大きいほど汚れている)
    val uvIndex: Double? = null,
    val pressureHpa: Int? = null,
    val humidityPercent: Int? = null    // 相対湿度(%)
)