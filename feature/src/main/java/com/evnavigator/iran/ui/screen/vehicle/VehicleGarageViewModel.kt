package com.evnavigator.iran.ui.screen.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.domain.model.catalog.DomainActiveVehicleSelectionState
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.catalog.DomainVehicleSocSource
import com.evnavigator.iran.domain.repository.ActiveVehicleStateRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CustomVehicleForm(
    val name: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val modelYear: Int = 2024,
    val batteryGrossKwh: Double = 70.0,
    val batteryUsableKwh: Double = 66.0,
    val maxDcPowerKw: Double = 100.0,
    val maxAcPowerKw: Double = 11.0,
    val connectorTypes: String = "CCS2,Type 2",
    val massKg: Double = 1850.0,
    val dragCoefficient: Double = 0.28,
    val baselineConsumptionWhPer100Km: Double = 16_000.0
)

data class VehicleGarageUiState(
    val vehicles: List<DomainVehicle> = emptyList(),
    val activeVehicleId: String = "",
    val searchQuery: String = "",
    val selectedConnectorFilter: String = "ALL",
    val selectedPowertrainFilter: String = "ALL",
    val currentSocPercent: Double = 85.0,
    val targetArrivalSocPercent: Double = 15.0,
    val minReserveSocPercent: Double = 10.0,
    val isCustomVehicleDialogOpen: Boolean = false,
    val customForm: CustomVehicleForm = CustomVehicleForm(),
    val statusMessage: String? = null
)

@HiltViewModel
class VehicleGarageViewModel @Inject constructor(
    private val vehicleCatalogRepository: VehicleCatalogRepository,
    private val activeVehicleRepository: ActiveVehicleStateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleGarageUiState())
    val uiState: StateFlow<VehicleGarageUiState> = _uiState.asStateFlow()

    init {
        loadVehicles()
        viewModelScope.launch {
            activeVehicleRepository.activeVehicleId.collect { id ->
                if (_uiState.value.activeVehicleId != id) {
                    _uiState.value = _uiState.value.copy(activeVehicleId = id)
                }
            }
        }
        viewModelScope.launch {
            activeVehicleRepository.batterySocPercent.collect { sharedSoc ->
                if (_uiState.value.currentSocPercent != sharedSoc) {
                    _uiState.value = _uiState.value.copy(currentSocPercent = sharedSoc)
                }
            }
        }
        viewModelScope.launch {
            try {
                vehicleCatalogRepository.observeAllVehicles().collect { dbVehicles ->
                    if (dbVehicles.isNotEmpty() && _uiState.value.vehicles.isEmpty()) {
                        val active = activeVehicleRepository.getActiveVehicle()
                        _uiState.value = _uiState.value.copy(
                            vehicles = dbVehicles,
                            activeVehicleId = active?.id ?: activeVehicleRepository.activeVehicleId.value
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun loadVehicles() {
        viewModelScope.launch {
            try {
                val dbVehicles = vehicleCatalogRepository.getAllVehicles()
                val active = activeVehicleRepository.getActiveVehicle()
                _uiState.value = _uiState.value.copy(
                    vehicles = dbVehicles,
                    activeVehicleId = active?.id ?: activeVehicleRepository.activeVehicleId.value
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("VehicleGarageViewModel", "Failed to load vehicles from repository", e)
            }
        }
    }

    fun selectActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            val selectionState = activeVehicleRepository.setActiveVehicleId(vehicleId)
            when (selectionState) {
                is DomainActiveVehicleSelectionState.Selected -> {
                    _uiState.value = _uiState.value.copy(
                        activeVehicleId = selectionState.vehicle.id,
                        statusMessage = "خودروی فعال با موفقیت به «${selectionState.vehicle.name}» تغییر یافت"
                    )
                }
                is DomainActiveVehicleSelectionState.InvalidSelection -> {
                    _uiState.value = _uiState.value.copy(
                        activeVehicleId = "",
                        statusMessage = "خطا: خودروی انتخاب‌شده (${selectionState.requestedId}) در پایگاه داده یافت نشد."
                    )
                }
                is DomainActiveVehicleSelectionState.NoActiveVehicle -> {
                    _uiState.value = _uiState.value.copy(
                        activeVehicleId = "",
                        statusMessage = "هیچ خودروی فعالی انتخاب نشده است."
                    )
                }
            }
        }
    }

    fun updateBatterySoc(newSoc: Double) {
        val clamped = newSoc.coerceIn(5.0, 100.0)
        _uiState.value = _uiState.value.copy(currentSocPercent = clamped)
        activeVehicleRepository.updateBatterySoc(
            newSoc = clamped,
            source = DomainVehicleSocSource.USER_MANUAL_INPUT
        )
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onConnectorFilterSelected(filter: String) {
        _uiState.value = _uiState.value.copy(selectedConnectorFilter = filter)
    }

    fun onPowertrainFilterSelected(filter: String) {
        _uiState.value = _uiState.value.copy(selectedPowertrainFilter = filter)
    }

    fun openCustomVehicleDialog() {
        _uiState.value = _uiState.value.copy(
            isCustomVehicleDialogOpen = true,
            customForm = CustomVehicleForm()
        )
    }

    fun closeCustomVehicleDialog() {
        _uiState.value = _uiState.value.copy(isCustomVehicleDialogOpen = false)
    }

    fun onCustomFormChanged(form: CustomVehicleForm) {
        _uiState.value = _uiState.value.copy(customForm = form)
    }

    fun saveCustomVehicle() {
        val form = _uiState.value.customForm
        if (form.name.isBlank() || form.manufacturer.isBlank() || form.model.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "لطفاً نام، سازنده و مدل خودرو را وارد نمایید")
            return
        }
        if (form.modelYear !in 1990..2030) {
            _uiState.value = _uiState.value.copy(statusMessage = "سال تولید باید بین ۱۹۹۰ تا ۲۰۳۰ باشد")
            return
        }
        if (form.batteryGrossKwh !in 10.0..300.0) {
            _uiState.value = _uiState.value.copy(statusMessage = "ظرفیت کل باتری باید بین ۱۰ تا ۳۰۰ کیلووات‌ساعت باشد")
            return
        }
        if (form.batteryUsableKwh <= 0.0 || form.batteryUsableKwh > form.batteryGrossKwh) {
            _uiState.value = _uiState.value.copy(statusMessage = "ظرفیت قابل استفاده باتری نامعتبر است (باید کمتر یا مساوی ظرفیت ناخالص باشد)")
            return
        }
        if (form.massKg !in 600.0..5000.0) {
            _uiState.value = _uiState.value.copy(statusMessage = "وزن خودرو باید بین ۶۰۰ تا ۵۰۰۰ کیلوگرم باشد")
            return
        }
        if (form.dragCoefficient !in 0.15..0.60) {
            _uiState.value = _uiState.value.copy(statusMessage = "ضریب درگ باید بین ۰.۱۵ تا ۰.۶۰ باشد")
            return
        }

        val now = System.currentTimeMillis()
        val customId = "custom_${UUID.randomUUID().toString().take(8)}"

        val vehicle = DomainVehicle(
            id = customId,
            name = form.name.trim(),
            manufacturer = form.manufacturer.trim(),
            model = form.model.trim(),
            generation = "Custom",
            modelYear = form.modelYear,
            trim = "Custom Trim",
            market = "USER_CUSTOM",
            powertrainType = "BEV",
            curbMassKg = form.massKg,
            grossVehicleMassKg = form.massKg + 400.0,
            lengthMm = 4600.0,
            widthMm = 1850.0,
            heightMm = 1550.0,
            wheelbaseMm = 2750.0,
            groundClearanceMm = 150.0,
            dragCoefficient = form.dragCoefficient,
            frontalAreaM2 = 2.30,
            rollingResistanceCoefficient = 0.010,
            nominalBatteryCapacityWh = form.batteryGrossKwh * 1000.0,
            usableBatteryCapacityWh = form.batteryUsableKwh * 1000.0,
            batteryChemistry = "LFP/NMC",
            nominalVoltageV = 400.0,
            batteryConfiguration = "Custom",
            motorCount = 1,
            frontMotorPowerKw = 150.0,
            rearMotorPowerKw = null,
            peakMotorPowerKw = 150.0,
            continuousMotorPowerKw = 70.0,
            torqueNm = 300.0,
            drivetrainType = "FWD",
            baselineConsumptionWhPer100Km = form.baselineConsumptionWhPer100Km,
            acChargingMaxPowerKw = form.maxAcPowerKw,
            dcChargingMaxPowerKw = form.maxDcPowerKw,
            chargingVoltageRangeV = "300-500",
            chargingCurrentRangeA = "0-250",
            chargePortCount = 2,
            connectorTypes = com.evnavigator.iran.domain.model.catalog.ConnectorNormalizer.normalizeFromCsvBoundary(form.connectorTypes),
            wheelDiameterMm = 457.0,
            tireWidthMm = 225.0,
            dataQuality = "ASSUMED",
            source = "USER_INPUT",
            sourceVersion = "1.0",
            retrievedDate = now,
            confidence = 0.50,
            validationStatus = "ASSUMED",
            notes = "مشخصات تخمینی ثبت‌شده توسط کاربر (ASSUMED)",
            schemaVersion = 1,
            dataVersion = 1,
            vehicleRecordVersion = "1.0",
            createdAt = now,
            updatedAt = now
        )

        viewModelScope.launch {
            try {
                vehicleCatalogRepository.insertVehicle(vehicle)
                loadVehicles()
                _uiState.value = _uiState.value.copy(
                    isCustomVehicleDialogOpen = false,
                    activeVehicleId = customId,
                    statusMessage = "خودروی سفارشی «${vehicle.name}» با موفقیت ذخیره و انتخاب شد"
                )
                activeVehicleRepository.setActiveVehicleId(customId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(statusMessage = "خطا در ذخیره‌سازی: ${e.message}")
            }
        }
    }

    fun duplicateVehicle(vehicle: DomainVehicle) {
        val now = System.currentTimeMillis()
        val duplicatedId = "copy_${UUID.randomUUID().toString().take(8)}"
        val copy = vehicle.copy(
            id = duplicatedId,
            name = "${vehicle.name} (نسخه شخصی)",
            source = "USER_INPUT",
            validationStatus = "ASSUMED",
            confidence = 0.70,
            notes = "نسخه شخصی مشتق‌شده از ${vehicle.name} (ASSUMED)",
            createdAt = now,
            updatedAt = now
        )

        viewModelScope.launch {
            try {
                vehicleCatalogRepository.insertVehicle(copy)
                loadVehicles()
                _uiState.value = _uiState.value.copy(
                    activeVehicleId = duplicatedId,
                    statusMessage = "یک نسخه شخصی از «${vehicle.name}» ایجاد شد"
                )
                activeVehicleRepository.setActiveVehicleId(duplicatedId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(statusMessage = "خطا در کپی: ${e.message}")
            }
        }
    }

    fun deleteVehicle(vehicleId: String) {
        viewModelScope.launch {
            try {
                vehicleCatalogRepository.deleteVehicleById(vehicleId)
                loadVehicles()
                if (_uiState.value.activeVehicleId == vehicleId) {
                    activeVehicleRepository.refreshActiveVehicle()
                }
                _uiState.value = _uiState.value.copy(statusMessage = "خودرو حذف شد")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(statusMessage = "خطا در حذف: ${e.message}")
            }
        }
    }

    fun dismissStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }
}
