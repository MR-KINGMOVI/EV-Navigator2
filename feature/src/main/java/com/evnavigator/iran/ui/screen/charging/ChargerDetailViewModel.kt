package com.evnavigator.iran.ui.screen.charging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChargerDetailUiState(
    val stationWithPorts: DomainChargingStationWithPorts? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class ChargerDetailViewModel @Inject constructor(
    private val chargingStationRepository: ChargingStationCatalogRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chargerId: String = savedStateHandle.get<String>("chargerId").orEmpty()

    private val _uiState = MutableStateFlow(ChargerDetailUiState())
    val uiState: StateFlow<ChargerDetailUiState> = _uiState.asStateFlow()

    init {
        loadChargerDetails(chargerId)
    }

    fun loadChargerDetails(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val data = if (id.isNotBlank()) {
                    chargingStationRepository.getStationWithPorts(id)
                } else null

                _uiState.update {
                    it.copy(
                        stationWithPorts = data,
                        isLoading = false,
                        errorMessage = if (data == null) "ایستگاه شارژ با شناسه مورد نظر یافت نشد" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "خطا در برقراری ارتباط با پایگاه داده"
                    )
                }
            }
        }
    }
}
