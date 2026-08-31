package com.volcanoescape.app.ui.screens.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volcanoescape.app.data.location.LocationProvider
import com.volcanoescape.app.data.model.EscapeRouteOptions
import com.volcanoescape.app.data.model.GeoPoint
import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.data.repository.RoutingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EscapeRouteUiState(
    val isLoading: Boolean = false,
    val userLocation: GeoPoint? = null,
    val routeOptions: EscapeRouteOptions? = null,
    val errorMessage: String? = null,
)

class EscapeRouteViewModel(
    private val volcano: Volcano,
    private val locationProvider: LocationProvider,
    private val routingRepository: RoutingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EscapeRouteUiState())
    val uiState: StateFlow<EscapeRouteUiState> = _uiState.asStateFlow()

    fun loadEscapeRoute() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            if (!locationProvider.hasLocationPermission()) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Permesso di localizzazione non concesso")
                }
                return@launch
            }

            val location = runCatching { locationProvider.currentLocation() }.getOrNull()
            if (location == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Impossibile ottenere la posizione attuale")
                }
                return@launch
            }

            runCatching { routingRepository.findLeastCongestedEscapeRoute(volcano, location) }
                .onSuccess { options ->
                    _uiState.update {
                        it.copy(isLoading = false, userLocation = location, routeOptions = options)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userLocation = location,
                            errorMessage = error.message ?: "Errore nel calcolo del percorso",
                        )
                    }
                }
        }
    }
}
