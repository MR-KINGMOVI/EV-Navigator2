package com.evnavigator.iran.domain.repository

import com.evnavigator.iran.domain.model.catalog.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for managing the vehicle catalog and user garage.
 */
interface VehicleCatalogRepository {
    fun observeAllVehicles(): Flow<List<DomainVehicle>>
    suspend fun getAllVehicles(): List<DomainVehicle>
    suspend fun getVehicleById(id: String): DomainVehicle?
    suspend fun insertVehicle(vehicle: DomainVehicle)
    suspend fun updateVehicle(vehicle: DomainVehicle)
    suspend fun deleteVehicleById(id: String)
    suspend fun countVehicles(): Int
}

/**
 * Domain repository contract for authoritative active vehicle selection, SOC provenance,
 * and navigation handoff state.
 */
interface ActiveVehicleStateRepository {
    companion object {
        const val CANONICAL_DEFAULT_VEHICLE_ID = "vw_id4_crozz_2024"
        const val UNCONFIRMED_FALLBACK_SOC_PERCENT = 85.0
    }

    val activeVehicleId: StateFlow<String>
    val activeVehicle: StateFlow<DomainVehicle?>
    val selectionState: StateFlow<DomainActiveVehicleSelectionState>
    val batterySocPercent: StateFlow<Double>
    val currentBatterySocPercent: StateFlow<Double>
        get() = batterySocPercent
    val confirmedBatterySocPercent: StateFlow<Double?>
    val estimatedBatterySocPercent: StateFlow<Double?>
    val socDataSource: StateFlow<DomainVehicleSocSource>
    val lastSocUpdatedEpochMs: StateFlow<Long?>
    val pendingNavigationHandoff: StateFlow<DomainPendingNavigationHandoff?>
    val lastPersistenceError: StateFlow<String?>
    val isPersistenceConfirmed: StateFlow<Boolean>

    suspend fun setActiveVehicleId(vehicleId: String): DomainActiveVehicleSelectionState
    suspend fun getActiveVehicle(): DomainVehicle?
    suspend fun refreshActiveVehicle()

    fun updateBatterySoc(
        newSoc: Double,
        source: DomainVehicleSocSource = DomainVehicleSocSource.USER_MANUAL_INPUT,
        timestampEpochMs: Long = System.currentTimeMillis(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean

    fun isSocConfirmed(nowEpochMs: Long = System.currentTimeMillis()): Boolean
    fun setPendingNavigationHandoff(handoff: DomainPendingNavigationHandoff?)
    fun stageNavigationHandoff(handoff: DomainPendingNavigationHandoff?) = setPendingNavigationHandoff(handoff)
    fun consumePendingNavigationHandoff(): DomainPendingNavigationHandoff?
}

/**
 * Domain repository contract for querying and managing charging stations and ports.
 */
interface ChargingStationCatalogRepository {
    fun observeAllStationsWithPorts(): Flow<List<DomainChargingStationWithPorts>>
    fun getAllStationsWithPorts(): Flow<List<DomainChargingStationWithPorts>> = observeAllStationsWithPorts()
    fun observeAllStations(): Flow<List<DomainChargingStation>>
    fun observeStationsNearby(latitude: Double, longitude: Double, radiusKm: Double): Flow<List<DomainChargingStation>>
    suspend fun getStationWithPorts(stationId: String): DomainChargingStationWithPorts?
    suspend fun getStationById(stationId: String): DomainChargingStation?
    suspend fun insertStation(station: DomainChargingStation)
    suspend fun insertPort(port: DomainChargingPort)
    suspend fun upsertStationWithPorts(station: DomainChargingStation, ports: List<DomainChargingPort>)
    suspend fun deletePortsForStation(stationId: String)
    suspend fun deleteStationById(stationId: String)
    suspend fun countStations(): Int
}

typealias ChargingStationRepository = ChargingStationCatalogRepository

/**
 * Domain repository contract for persisting and recovering the active journey state.
 */
interface ActiveJourneyStateRepository {
    suspend fun saveActiveJourney(journey: DomainActiveJourneyState)
    suspend fun getActiveJourney(id: String): DomainActiveJourneyState?
    suspend fun getLatestActiveJourney(): DomainActiveJourneyState?
    suspend fun deleteJourney(id: String)
    suspend fun clearAll()
}

typealias ActiveJourneyPersistenceRepository = ActiveJourneyStateRepository

/**
 * Domain repository contract for reading and managing privacy-preserving trip history summaries.
 */
interface TripHistoryRepository {
    suspend fun getAllSummaries(): List<DomainTripHistorySummary>
    suspend fun getSummariesForVehicle(vehicleId: String): List<DomainTripHistorySummary>
    suspend fun insertSummary(summary: DomainTripHistorySummary)
    suspend fun deleteSummariesForVehicle(vehicleId: String)
    suspend fun clearAll()
}

/**
 * Domain repository contract for offline map & routing package management with integrity verification.
 */
interface OfflinePackageRepository {
    val packages: StateFlow<List<DomainOfflinePackageMetadata>>
    suspend fun installPackage(packId: String): Result<DomainOfflinePackageMetadata>
    suspend fun cancelInstall(packId: String)
    suspend fun retryInstall(packId: String): Result<DomainOfflinePackageMetadata>
    suspend fun deletePackage(packId: String)
    fun verifyChecksum(payloadBytes: ByteArray, expectedSha256Hex: String): Boolean
}

/**
 * Domain persistence contract for weather and traffic environment cache records.
 * Decouples `:core:weather` from `:data` Room entities and DAOs.
 */
data class DomainEnvironmentCacheRecord(
    val key: String,
    val cacheType: String,
    val latitude: Double,
    val longitude: Double,
    val payloadJson: String,
    val cachedAt: Long,
    val expiresAt: Long
)

interface EnvironmentCacheStore {
    suspend fun insertOrUpdate(entry: DomainEnvironmentCacheRecord)
    suspend fun insertAll(entries: List<DomainEnvironmentCacheRecord>)
    suspend fun getValidEntry(key: String, now: Long = System.currentTimeMillis()): DomainEnvironmentCacheRecord?
    suspend fun getValidEntriesInBounds(
        cacheType: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        now: Long = System.currentTimeMillis()
    ): List<DomainEnvironmentCacheRecord>
    suspend fun pruneExpired(now: Long = System.currentTimeMillis())
    suspend fun deleteInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double)
    suspend fun clearAll()
}
