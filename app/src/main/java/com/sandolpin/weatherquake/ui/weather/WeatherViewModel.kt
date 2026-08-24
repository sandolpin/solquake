package com.sandolpin.weatherquake.ui.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.weatherquake.data.settings.AppSettingsState
import com.sandolpin.weatherquake.data.settings.SettingsRepository
import com.sandolpin.weatherquake.data.weather.WeatherLocation
import com.sandolpin.weatherquake.data.weather.WeatherRepository
import com.sandolpin.weatherquake.data.weather.WeatherUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeatherScreenState(
    val weather: WeatherUiState? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<WeatherLocation> = emptyList(),
    val isSearching: Boolean = false,
    val settings: AppSettingsState = AppSettingsState()
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    // Open-Meteoは位置情報のAPIキーが不要なため、まずはデフォルト地点(前橋市)から開始する。
    // 「検索アイコン」から任意の地点に切り替え可能。
    private val defaultLocation = WeatherLocation(name = "前橋市", latitude = 36.3894, longitude = 139.0634)

    private val settingsRepository = SettingsRepository(application)

    private val _state = MutableStateFlow(WeatherScreenState())
    val state: StateFlow<WeatherScreenState> = _state.asStateFlow()

    private var currentLocation: WeatherLocation = defaultLocation

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.value = _state.value.copy(settings = settings)
            }
        }
        refresh()
        // 15分ごとの短期予報を最新に保つため、10分間隔で自動更新する
        viewModelScope.launch {
            while (true) {
                delay(10 * 60 * 1000L)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val result = WeatherRepository.fetchWeather(currentLocation, currentLocation.name)
                _state.value = _state.value.copy(weather = result, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "天気情報を取得できませんでした")
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSearching = true)
            val results = runCatching { WeatherRepository.searchLocations(query) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(searchResults = results, isSearching = false)
        }
    }

    fun selectLocation(location: WeatherLocation) {
        currentLocation = location
        _state.value = _state.value.copy(searchQuery = "", searchResults = emptyList())
        refresh()
    }
}
