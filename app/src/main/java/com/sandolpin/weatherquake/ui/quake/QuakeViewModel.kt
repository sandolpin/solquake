package com.sandolpin.weatherquake.ui.quake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.weatherquake.data.quake.QuakeCardState
import com.sandolpin.weatherquake.data.quake.QuakeRepository
import com.sandolpin.weatherquake.data.settings.AppSettingsState
import com.sandolpin.weatherquake.data.settings.SettingsRepository
import com.sandolpin.weatherquake.service.ConnectionStatus
import com.sandolpin.weatherquake.service.EewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class QuakeScreenState(
    val quakes: List<QuakeCardState> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Connecting,
    val lastUpdated: Long = System.currentTimeMillis(),
    val eewCards: List<com.sandolpin.weatherquake.data.eew.EewCardState> = emptyList(),
    val settings: AppSettingsState = AppSettingsState()
)

class QuakeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private val _state = MutableStateFlow(QuakeScreenState())
    val state: StateFlow<QuakeScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                QuakeRepository.quakes,
                EewRepository.connectionStatus,
                EewRepository.lastUpdated,
                EewRepository.cards,
                settingsRepository.settingsFlow
            ) { quakes, status, updated, eewCards, settings ->
                QuakeScreenState(
                    quakes = filterHidden(quakes, settings.hideUnknownDepthMagnitude),
                    connectionStatus = status,
                    lastUpdated = updated,
                    eewCards = eewCards,
                    settings = settings
                )
            }.collect { _state.value = it }
        }
    }

    private fun filterHidden(quakes: List<QuakeCardState>, hideUnknown: Boolean): List<QuakeCardState> {
        if (!hideUnknown) return quakes
        return quakes.filter { it.depthKm != null && it.magnitude != null }
    }
}
