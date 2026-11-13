package com.evnavigator.iran.ui.screen.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehicleDetailUiState(
    val vehicle: DomainVehicle? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    private val vehicleCatalogRepository: VehicleCatalogRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vehicleId: String = savedStateHandle.get<String>("vehicleId").orEmpty()

    private val _uiState = MutableStateFlow(VehicleDetailUiState())
    val uiState: StateFlow<VehicleDetailUiState> = _uiState.asStateFlow()

    init {
        loadVehicleDetails(vehicleId)
    }

    fun loadVehicleDetails(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val data = if (id.isNotBlank()) {
                    vehicleCatalogRepository.getVehicleById(id)
                } else null

                _uiState.update {
                    it.copy(
                        vehicle = data,
                        isLoading = false,
                        errorMessage = if (data == null) "خودرو با شناسه مورد نظر یافت نشد" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "خطا در بازیابی مشخصات خودرو"
                    )
                }
            }
        }
    }
}
