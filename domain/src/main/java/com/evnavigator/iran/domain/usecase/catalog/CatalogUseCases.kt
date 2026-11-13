package com.evnavigator.iran.domain.usecase.catalog

import com.evnavigator.iran.domain.model.catalog.*
import com.evnavigator.iran.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Focused UseCase for observing and selecting the active vehicle and validated battery SOC.
 */
class ObserveActiveVehicleUseCase @Inject constructor(
    private val activeVehicleStateRepository: ActiveVehicleStateRepository
) {
    val activeVehicleId: StateFlow<String> get() = activeVehicleStateRepository.activeVehicleId
    val activeVehicle: StateFlow<DomainVehicle?> get() = activeVehicleStateRepository.activeVehicle
    val selectionState: StateFlow<DomainActiveVehicleSelectionState> get() = activeVehicleStateRepository.selectionState
    val batterySocPercent: StateFlow<Double> get() = activeVehicleStateRepository.batterySocPercent
    val currentBatterySocPercent: StateFlow<Double> get() = activeVehicleStateRepository.currentBatterySocPercent
    val confirmedBatterySocPercent: StateFlow<Double?> get() = activeVehicleStateRepository.confirmedBatterySocPercent
    val estimatedBatterySocPercent: StateFlow<Double?> get() = activeVehicleStateRepository.estimatedBatterySocPercent
    val socDataSource: StateFlow<DomainVehicleSocSource> get() = activeVehicleStateRepository.socDataSource
    val lastSocUpdatedEpochMs: StateFlow<Long?> get() = activeVehicleStateRepository.lastSocUpdatedEpochMs
    val isPersistenceConfirmed: StateFlow<Boolean> get() = activeVehicleStateRepository.isPersistenceConfirmed

    fun observeSelectionState(): Flow<DomainActiveVehicleSelectionState> =
        activeVehicleStateRepository.selectionState

    fun observeActiveVehicle(): Flow<DomainVehicle?> =
        activeVehicleStateRepository.activeVehicle

    suspend fun getActiveVehicle(): DomainVehicle? =
        activeVehicleStateRepository.getActiveVehicle()

    suspend fun refreshActiveVehicle() =
        activeVehicleStateRepository.refreshActiveVehicle()

    fun isSocConfirmed(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        activeVehicleStateRepository.isSocConfirmed(nowEpochMs)
}

/**
 * Focused UseCase for updating active vehicle selection and battery SOC with strict provenance rules.
 */
class UpdateVehicleSocUseCase @Inject constructor(
    private val activeVehicleStateRepository: ActiveVehicleStateRepository
) {
    suspend fun selectVehicle(vehicleId: String): DomainActiveVehicleSelectionState =
        activeVehicleStateRepository.setActiveVehicleId(vehicleId)

    fun updateSoc(
        socPercent: Double,
        source: DomainVehicleSocSource = DomainVehicleSocSource.USER_MANUAL_INPUT,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): Boolean = activeVehicleStateRepository.updateBatterySoc(socPercent, source, timestampEpochMs)
}

/**
 * Focused UseCase for CRUD operations on the EV garage catalog.
 */
class ManageVehicleGarageUseCase @Inject constructor(
    private val vehicleCatalogRepository: VehicleCatalogRepository,
    private val activeVehicleStateRepository: ActiveVehicleStateRepository
) {
    fun observeAllVehicles(): Flow<List<DomainVehicle>> = vehicleCatalogRepository.observeAllVehicles()

    suspend fun getAllVehicles(): List<DomainVehicle> = vehicleCatalogRepository.getAllVehicles()

    suspend fun getVehicleById(id: String): DomainVehicle? = vehicleCatalogRepository.getVehicleById(id)

    suspend fun saveVehicle(vehicle: DomainVehicle) {
        vehicleCatalogRepository.insertVehicle(vehicle)
    }

    suspend fun updateVehicle(vehicle: DomainVehicle) {
        vehicleCatalogRepository.updateVehicle(vehicle)
    }

    suspend fun deleteVehicle(vehicleId: String) {
        vehicleCatalogRepository.deleteVehicleById(vehicleId)
        if (activeVehicleStateRepository.activeVehicleId.value == vehicleId) {
            activeVehicleStateRepository.refreshActiveVehicle()
        }
    }
}

/**
 * Focused UseCase for querying, filtering, and registering EV charging stations and ports.
 */
class DiscoverChargingStationsUseCase @Inject constructor(
    private val chargingStationCatalogRepository: ChargingStationCatalogRepository
) {
    fun observeAllStationsWithPorts(): Flow<List<DomainChargingStationWithPorts>> =
        chargingStationCatalogRepository.observeAllStationsWithPorts()

    suspend fun getStationWithPorts(stationId: String): DomainChargingStationWithPorts? =
        chargingStationCatalogRepository.getStationWithPorts(stationId)

    suspend fun registerStationWithPort(station: DomainChargingStation, port: DomainChargingPort) {
        chargingStationCatalogRepository.upsertStationWithPorts(station, listOf(port))
    }

    suspend fun deleteStation(stationId: String) {
        chargingStationCatalogRepository.deleteStationById(stationId)
    }
}
