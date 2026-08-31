// Copyright (c) Roberto Di Flumeri
package com.volcanoescape.app.ui.screens.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volcanoescape.app.data.model.SeismicEvent
import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.data.repository.SeismicRepository
import com.volcanoescape.app.data.repository.SeismicRiskAnalyzer
import com.volcanoescape.app.data.repository.SeismicRiskAssessment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonitoringUiState(
    val isLoading: Boolean = true,
    val events: List<SeismicEvent> = emptyList(),
    val riskAssessment: SeismicRiskAssessment? = null,
    val errorMessage: String? = null,
    val selectedDays: Long = 30,
)

class MonitoringViewModel(
    private val volcano: Volcano,
    private val repository: SeismicRepository = SeismicRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    init {
        loadRecentActivity()
    }

    /** Cambia il periodo di ricerca delle scosse (in giorni) e ricarica i dati. */
    fun onDaysSelected(days: Long) {
        if (days == _uiState.value.selectedDays) return
        _uiState.update { it.copy(selectedDays = days) }
        loadRecentActivity()
    }

    fun loadRecentActivity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.recentEvents(volcano, days = _uiState.value.selectedDays) }
                .onSuccess { events ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            events = events,
                            riskAssessment = SeismicRiskAnalyzer.analyze(events),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Errore di rete")
                    }
                }
        }
    }
}
