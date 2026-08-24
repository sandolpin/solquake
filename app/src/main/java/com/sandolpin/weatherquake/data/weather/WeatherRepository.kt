package com.sandolpin.weatherquake.data.weather

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Open-Meteo (https://open-meteo.com) との通信を担うRepository。
 * APIキー不要・無料で使えるため、通信自体はシンプルなGET+Gsonパースで済む。
 */
object WeatherRepository {

    private val client = OkHttpClient()
    private val gson = Gson()

    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val AIR_QUALITY_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"

    /** 天気画面右上の検索アイコンから使う、地名→緯度経度の変換 */
    suspend fun searchLocations(query: String): List<WeatherLocation> = withContext(Dispatchers.IO) {
        val url = "$GEOCODING_URL?name=${query}&count=8&language=ja&format=json"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val parsed = gson.fromJson(body, GeocodingResponse::class.java)
            parsed.results.orEmpty().map {
                val prefix = it.admin1?.let { admin -> "$admin " } ?: ""
                WeatherLocation(name = "$prefix${it.name}", latitude = it.latitude, longitude = it.longitude)
            }
        }
    }

    /** 指定地点の天気予報を取得し、画面表示用に整形する(AQI・UV・気圧・湿度も合わせて取得する) */
    suspend fun fetchWeather(location: WeatherLocation, displayName: String): WeatherUiState =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(FORECAST_URL)
                append("?latitude=${location.latitude}")
                append("&longitude=${location.longitude}")
                append("&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,is_day,pressure_msl,relative_humidity_2m")
                append("&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m")
                append("&minutely_15=temperature_2m,weather_code,precipitation,wind_speed_10m")
                append("&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min,weather_code")
                append("&timezone=Asia%2FTokyo")
                append("&forecast_days=2")
            }
            val request = Request.Builder().url(url).build()
            val baseState = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("Open-Meteo API error: ${response.code}")
                val body = response.body?.string() ?: throw java.io.IOException("empty body")
                val parsed = gson.fromJson(body, OpenMeteoResponse::class.java)
                toUiState(parsed, location, displayName)
            }

            // AQI・UVは天気本体とは別のAPI(Air Quality API)なので、失敗しても天気表示自体は
            // 継続できるよう例外を握りつぶし、取得できた分だけ反映する。
            val airQuality = runCatching { fetchAirQuality(location) }.getOrNull()
            baseState.copy(
                airQualityIndex = airQuality?.europeanAqi,
                uvIndex = airQuality?.uvIndex
            )
        }

    private suspend fun fetchAirQuality(location: WeatherLocation): AirQualityCurrent? = withContext(Dispatchers.IO) {
        val url = "$AIR_QUALITY_URL?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=european_aqi,uv_index&timezone=Asia%2FTokyo"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            gson.fromJson(body, AirQualityResponse::class.java).current
        }
    }

    private fun toUiState(res: OpenMeteoResponse, location: WeatherLocation, displayName: String): WeatherUiState {
        val current = res.current
        val daily = res.daily
        val condition = WeatherCondition.fromWmoCode(current?.weatherCode ?: 0)

        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val now = current?.time?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now()

        val sunrise = daily?.sunrise?.getOrNull(0)?.let { runCatching { LocalDateTime.parse(it).format(timeFmt) }.getOrNull() } ?: "--:--"
        val sunset = daily?.sunset?.getOrNull(0)?.let { runCatching { LocalDateTime.parse(it).format(timeFmt) }.getOrNull() } ?: "--:--"

        return WeatherUiState(
            location = location,
            updatedAtLabel = now.format(timeFmt),
            currentTemperature = (current?.temperature ?: 0.0).roundToInt(),
            apparentTemperature = (current?.apparentTemperature ?: 0.0).roundToInt(),
            condition = condition,
            isDay = current?.isDay != 0,
            tempMinToday = daily?.tempMin?.getOrNull(0)?.roundToInt() ?: 0,
            tempMaxToday = daily?.tempMax?.getOrNull(0)?.roundToInt() ?: 0,
            sunrise = sunrise,
            sunset = sunset,
            windSpeed = current?.windSpeed ?: 0.0,
            windDirectionLabel = windDirectionLabel(current?.windDirection ?: 0),
            minutely15 = buildForecastPoints(res.minutely15, now, isMinutely = true, stepMinutes = 15, count = 6),
            hourly = buildForecastPoints(res.hourly, now, isMinutely = false, stepMinutes = 60, count = 24),
            daily = buildDailyPoints(daily),
            pressureHpa = current?.pressureMsl?.roundToInt(),
            humidityPercent = current?.relativeHumidity
        )
    }

    private fun buildForecastPoints(
        block: HourlyBlock?,
        now: LocalDateTime,
        isMinutely: Boolean,
        stepMinutes: Int,
        count: Int
    ): List<ForecastPoint> {
        if (block == null) return emptyList()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        // 現在時刻以降のインデックスを探す(Open-Meteoは当日0時からの配列を返すため)
        val startIndex = block.time.indexOfFirst { runCatching { LocalDateTime.parse(it) >= now }.getOrDefault(false) }
            .let { if (it < 0) 0 else it }

        return (0 until count).mapNotNull { offset ->
            val idx = startIndex + offset
            val timeStr = block.time.getOrNull(idx) ?: return@mapNotNull null
            val temp = block.temperature.getOrNull(idx) ?: return@mapNotNull null
            val code = block.weatherCode.getOrNull(idx) ?: 0
            val precip = block.precipitation?.getOrNull(idx) ?: 0.0
            val wind = block.windSpeed.getOrNull(idx) ?: 0.0
            val parsedTime = runCatching { LocalDateTime.parse(timeStr) }.getOrNull()

            val label = when {
                offset == 0 -> "いま"
                isMinutely -> "${offset * stepMinutes}分後"
                else -> parsedTime?.format(timeFmt) ?: timeStr
            }

            ForecastPoint(
                label = label,
                temperature = temp.roundToInt(),
                condition = WeatherCondition.fromWmoCode(code),
                precipitationMm = precip,
                windSpeed = wind
            )
        }
    }

    private fun buildDailyPoints(daily: DailyBlock?): List<ForecastPoint> {
        if (daily == null) return emptyList()
        val dateFmt = DateTimeFormatter.ofPattern("M/d")
        return daily.time.indices.mapNotNull { idx ->
            val dateStr = daily.time.getOrNull(idx) ?: return@mapNotNull null
            val max = daily.tempMax.getOrNull(idx) ?: return@mapNotNull null
            val min = daily.tempMin.getOrNull(idx) ?: 0.0
            val parsedDate = runCatching { java.time.LocalDate.parse(dateStr) }.getOrNull()
            ForecastPoint(
                label = parsedDate?.format(dateFmt) ?: dateStr,
                temperature = max.roundToInt(),
                condition = WeatherCondition.CLEAR, // 簡易表示。daily.weather_codeを使う場合はここを拡張
                precipitationMm = 0.0,
                windSpeed = min // dailyのみ最低気温を一時的にここへ格納して呼び出し側で表示
            )
        }
    }

    private fun windDirectionLabel(degree: Int): String {
        val directions = listOf("北", "北東", "東", "南東", "南", "南西", "西", "北西")
        val index = (((degree % 360) + 360) % 360 / 45.0).roundToInt() % 8
        return directions[index]
    }
}