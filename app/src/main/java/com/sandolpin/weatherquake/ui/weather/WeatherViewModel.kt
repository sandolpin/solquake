package com.sandolpin.weatherquake.ui.weather

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.weatherquake.data.location.CurrentLocationProvider
import com.sandolpin.weatherquake.data.settings.AppSettingsState
import com.sandolpin.weatherquake.data.settings.SettingsRepository
import com.sandolpin.weatherquake.data.weather.WeatherLocation
import com.sandolpin.weatherquake.data.weather.WeatherRepository
import com.sandolpin.weatherquake.data.weather.WeatherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ホーム画面の1ページ分(1地点分)の天気状態。HorizontalPagerの各ページに対応する。 */
@Immutable
data class HomeWeatherPage(
    val location: WeatherLocation,
    val weather: WeatherUiState? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class WeatherScreenState(
    // ホーム画面に表示する地点ぶんのページ。先頭(index 0)が起動時のデフォルト地点。
    val pages: List<HomeWeatherPage> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<WeatherLocation> = emptyList(),
    val isSearching: Boolean = false,
    val settings: AppSettingsState = AppSettingsState(),
    // 検索結果選択・並び替え操作の直後、ページャーをこのindexへスクロールさせるための一時的な指示値。
    // ページャーがスクロールし終えたらconsumePendingScroll()でnullに戻す(1回だけ実行するため)。
    val pendingScrollToIndex: Int? = null,
    // 検索シートの「現在地から取得」ボタンの状態
    val isLocatingCurrentPosition: Boolean = false,
    val locationError: String? = null
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    // ホーム地点が1件も設定されていない(=まだカスタマイズしていない)場合のフォールバック地点。
    // Open-Meteoは位置情報のAPIキーが不要なため、初回起動時はこの地点から始める。
    private val defaultLocation = WeatherLocation(name = "前橋市", latitude = 36.3894, longitude = 139.0634)

    private val settingsRepository = SettingsRepository(application)

    private val _state = MutableStateFlow(WeatherScreenState())
    val state: StateFlow<WeatherScreenState> = _state.asStateFlow()

    // 地点名 -> 取得済み天気 のキャッシュ(ページ切り替えのたびに再取得しなくて済むようにするため)
    private val weatherCache = HashMap<String, WeatherUiState>()
    private val loadingNames = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val effectiveHomeLocations = settings.homeLocations.ifEmpty { listOf(defaultLocation) }
                _state.value = _state.value.copy(
                    settings = settings,
                    pages = buildPages(effectiveHomeLocations)
                )
                effectiveHomeLocations.forEach { location -> ensureWeatherLoaded(location) }
            }
        }
        // 15分ごとの短期予報を最新に保つため、10分間隔でホーム画面の全地点を自動更新する
        viewModelScope.launch {
            while (true) {
                delay(10 * 60 * 1000L)
                refreshAll()
            }
        }
    }

    private fun buildPages(locations: List<WeatherLocation>): List<HomeWeatherPage> =
        locations.map { location ->
            val cached = weatherCache[location.name]
            HomeWeatherPage(location = location, weather = cached, isLoading = cached == null)
        }

    private fun currentHomeLocations(): List<WeatherLocation> =
        _state.value.settings.homeLocations.ifEmpty { listOf(defaultLocation) }

    private fun ensureWeatherLoaded(location: WeatherLocation) {
        if (weatherCache.containsKey(location.name)) return
        if (!loadingNames.add(location.name)) return
        viewModelScope.launch {
            try {
                val result = WeatherRepository.fetchWeather(location, location.name)
                weatherCache[location.name] = result
                _state.value = _state.value.copy(pages = buildPages(currentHomeLocations()))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    pages = _state.value.pages.map { page ->
                        if (page.location.name == location.name) {
                            page.copy(isLoading = false, errorMessage = "天気情報を取得できませんでした")
                        } else page
                    }
                )
            } finally {
                loadingNames.remove(location.name)
            }
        }
    }

    /** 指定した地点だけを再取得する */
    fun refreshLocation(location: WeatherLocation) {
        weatherCache.remove(location.name)
        _state.value = _state.value.copy(
            pages = _state.value.pages.map { page ->
                if (page.location.name == location.name) page.copy(isLoading = true, errorMessage = null) else page
            }
        )
        ensureWeatherLoaded(location)
    }

    /** ホーム画面の全地点を再取得する */
    fun refreshAll() {
        currentHomeLocations().forEach { refreshLocation(it) }
    }

    private var searchJob: Job? = null

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isSearching = true)
            delay(300) // 入力の区切りを少し待ってからAPIを叩く(打鍵ごとの過剰なリクエストを防ぐ)
            val results = runCatching { WeatherRepository.searchLocations(query) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(searchResults = results, isSearching = false)
        }
    }

    /**
     * 検索結果から地点を選択する。
     * 履歴に追加した上で、まだホーム画面に無ければ末尾に追加し、そのページへスクロールさせる。
     * 既にホーム画面にある地点を選んだ場合は、単純にそのページへジャンプする。
     */
    fun selectSearchResult(location: WeatherLocation) {
        _state.value = _state.value.copy(searchQuery = "", searchResults = emptyList())
        viewModelScope.launch {
            settingsRepository.addSearchHistory(location)

            val currentHome = _state.value.settings.homeLocations.ifEmpty { listOf(defaultLocation) }
            val existingIndex = currentHome.indexOfFirst { it.name == location.name }
            if (existingIndex >= 0) {
                _state.value = _state.value.copy(pendingScrollToIndex = existingIndex)
            } else {
                settingsRepository.setHomeLocationIncluded(location, included = true, fallbackWhenEmpty = defaultLocation)
                _state.value = _state.value.copy(pendingScrollToIndex = currentHome.size)
            }
        }
    }

    /** ページャーのスクロールを消費したら呼ぶ(1回だけスクロールさせるため) */
    fun consumePendingScroll() {
        _state.value = _state.value.copy(pendingScrollToIndex = null)
    }

    /** 履歴の1件を、ホーム画面に表示する/しないを切り替える(検索シートの🏠アイコン) */
    fun toggleHomeLocation(location: WeatherLocation, included: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHomeLocationIncluded(location, included, defaultLocation)
        }
    }

    /** 指定した地点をホーム地点一覧の先頭(=起動時のデフォルト)にする(検索シートの📌アイコン・並び替えチップのタップ) */
    fun pinAsDefault(location: WeatherLocation) {
        viewModelScope.launch {
            settingsRepository.pinAsDefaultHomeLocation(location, defaultLocation)
        }
    }

    /** 履歴・ホーム画面の両方から地点を完全に削除する(検索シートの🗑アイコン) */
    fun deleteFromHistory(location: WeatherLocation) {
        weatherCache.remove(location.name)
        viewModelScope.launch {
            settingsRepository.removeLocationEverywhere(location)
        }
    }

    /**
     * 検索シートの「現在地から取得」ボタンから呼ばれる。
     * 権限確認・権限要求はComposable側(WeatherScreen)で行った上でこの関数を呼ぶ前提。
     * 端末の現在地を取得→逆ジオコーディングで地名に変換→selectSearchResult()と同じ流れ
     * (履歴登録・ホーム画面への追加・該当ページへスクロール)に乗せる。
     */
    fun useCurrentLocation(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLocatingCurrentPosition = true, locationError = null)

            val androidLocation = CurrentLocationProvider.getCurrentLocation(context)
            if (androidLocation == null) {
                _state.value = _state.value.copy(
                    isLocatingCurrentPosition = false,
                    locationError = "現在地を取得できませんでした。位置情報の設定をご確認ください。"
                )
                return@launch
            }

            val name = withContext(Dispatchers.IO) {
                CurrentLocationProvider.reverseGeocodeName(context, androidLocation.latitude, androidLocation.longitude)
            }

            _state.value = _state.value.copy(isLocatingCurrentPosition = false)
            selectSearchResult(
                WeatherLocation(name = name, latitude = androidLocation.latitude, longitude = androidLocation.longitude)
            )
        }
    }
}