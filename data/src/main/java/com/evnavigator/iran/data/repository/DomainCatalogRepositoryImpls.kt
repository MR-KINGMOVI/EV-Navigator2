package com.evnavigator.iran.data.repository

import com.evnavigator.iran.data.cache.EnvironmentCacheDao
import com.evnavigator.iran.data.charging.dao.ChargingStationDao
import com.evnavigator.iran.data.intelligence.dao.TripHistoryDao
import com.evnavigator.iran.data.journey.ActiveJourneyDao
import com.evnavigator.iran.data.mapper.*
import com.evnavigator.iran.data.vehicle.dao.VehicleDao
import com.evnavigator.iran.data.vehicle.repository.ActiveVehicleRepository
import com.evnavigator.iran.domain.model.catalog.*
import com.evnavigator.iran.domain.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

@Singleton
class DefaultVehicleCatalogRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) : VehicleCatalogRepository {

    override fun observeAllVehicles(): Flow<List<DomainVehicle>> =
        vehicleDao.getAllVehiclesFlow().map { list -> list.map { it.toDomainVehicle() } }

    override suspend fun getAllVehicles(): List<DomainVehicle> =
        vehicleDao.getAllVehicles().map { it.toDomainVehicle() }

    override suspend fun getVehicleById(id: String): DomainVehicle? =
        vehicleDao.getVehicleById(id)?.toDomainVehicle()

    override suspend fun insertVehicle(vehicle: DomainVehicle) {
        vehicleDao.insertVehicle(vehicle.toVehicleEntity())
    }

    override suspend fun updateVehicle(vehicle: DomainVehicle) {
        vehicleDao.updateVehicle(vehicle.toVehicleEntity())
    }

    override suspend fun deleteVehicleById(id: String) {
        vehicleDao.deleteVehicleById(id)
    }

    override suspend fun countVehicles(): Int = vehicleDao.countVehicles()
}

@Singleton
class DefaultActiveVehicleStateRepository(
    private val activeVehicleRepository: ActiveVehicleRepository,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) : ActiveVehicleStateRepository {

    @Inject
    constructor(activeVehicleRepository: ActiveVehicleRepository) : this(
        activeVehicleRepository = activeVehicleRepository,
        dispatcher = Dispatchers.Default
    )

    constructor(vehicleDao: VehicleDao, dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default) : this(
        activeVehicleRepository = ActiveVehicleRepository(vehicleDao),
        dispatcher = dispatcher
    )

    private val adapterScope = CoroutineScope(SupervisorJob() + dispatcher)

    override val activeVehicleId: StateFlow<String>
        get() = activeVehicleRepository.activeVehicleId

    private val _domainActiveVehicle = MutableStateFlow(activeVehicleRepository.activeVehicle.value?.toDomainVehicle())
    override val activeVehicle: StateFlow<DomainVehicle?> = _domainActiveVehicle.asStateFlow()

    private val _domainSelectionState = MutableStateFlow(activeVehicleRepository.selectionState.value.toDomainSelectionState())
    override val selectionState: StateFlow<DomainActiveVehicleSelectionState> = _domainSelectionState.asStateFlow()

    override val batterySocPercent: StateFlow<Double>
        get() = activeVehicleRepository.batterySocPercent

    override val confirmedBatterySocPercent: StateFlow<Double?>
        get() = activeVehicleRepository.confirmedBatterySocPercent

    override val estimatedBatterySocPercent: StateFlow<Double?>
        get() = activeVehicleRepository.estimatedBatterySocPercent

    private val _domainSocSource = MutableStateFlow(activeVehicleRepository.socDataSource.value.toDomainSocSource())
    override val socDataSource: StateFlow<DomainVehicleSocSource> = _domainSocSource.asStateFlow()

    override val lastSocUpdatedEpochMs: StateFlow<Long?>
        get() = activeVehicleRepository.lastSocUpdatedEpochMs

    private val _domainHandoff = MutableStateFlow(activeVehicleRepository.pendingNavigationHandoff.value?.toDomainHandoff())
    override val pendingNavigationHandoff: StateFlow<DomainPendingNavigationHandoff?> = _domainHandoff.asStateFlow()

    override val lastPersistenceError: StateFlow<String?>
        get() = activeVehicleRepository.lastPersistenceError

    override val isPersistenceConfirmed: StateFlow<Boolean>
        get() = activeVehicleRepository.isPersistenceConfirmed

    init {
        activeVehicleRepository.activeVehicle
            .onEach { _domainActiveVehicle.value = it?.toDomainVehicle() }
            .launchIn(adapterScope)

        activeVehicleRepository.selectionState
            .onEach { _domainSelectionState.value = it.toDomainSelectionState() }
            .launchIn(adapterScope)

        activeVehicleRepository.socDataSource
            .onEach { _domainSocSource.value = it.toDomainSocSource() }
            .launchIn(adapterScope)

        activeVehicleRepository.pendingNavigationHandoff
            .onEach { _domainHandoff.value = it?.toDomainHandoff() }
            .launchIn(adapterScope)
    }

    override suspend fun setActiveVehicleId(vehicleId: String): DomainActiveVehicleSelectionState {
        val state = activeVehicleRepository.setActiveVehicleId(vehicleId).toDomainSelectionState()
        _domainActiveVehicle.value = activeVehicleRepository.activeVehicle.value?.toDomainVehicle()
        _domainSelectionState.value = state
        return state
    }

    override suspend fun getActiveVehicle(): DomainVehicle? {
        val v = activeVehicleRepository.getActiveVehicle()?.toDomainVehicle()
        _domainActiveVehicle.value = v
        _domainSelectionState.value = activeVehicleRepository.selectionState.value.toDomainSelectionState()
        return v
    }

    override suspend fun refreshActiveVehicle() {
        activeVehicleRepository.refreshActiveVehicle()
        _domainActiveVehicle.value = activeVehicleRepository.activeVehicle.value?.toDomainVehicle()
        _domainSelectionState.value = activeVehicleRepository.selectionState.value.toDomainSelectionState()
    }

    override fun updateBatterySoc(
        newSoc: Double,
        source: DomainVehicleSocSource,
        timestampEpochMs: Long,
        nowEpochMs: Long
    ): Boolean {
        val ok = activeVehicleRepository.updateBatterySoc(
            newSoc = newSoc,
            source = source.toDataSocSource(),
            timestampEpochMs = timestampEpochMs,
            nowEpochMs = nowEpochMs
        )
        _domainSocSource.value = activeVehicleRepository.socDataSource.value.toDomainSocSource()
        return ok
    }

    override fun isSocConfirmed(nowEpochMs: Long): Boolean =
        activeVehicleRepository.isSocConfirmed(nowEpochMs)

    override fun setPendingNavigationHandoff(handoff: DomainPendingNavigationHandoff?) {
        activeVehicleRepository.setPendingNavigationHandoff(handoff?.toDataHandoff())
        _domainHandoff.value = activeVehicleRepository.pendingNavigationHandoff.value?.toDomainHandoff()
    }

    override fun consumePendingNavigationHandoff(): DomainPendingNavigationHandoff? {
        val consumed = activeVehicleRepository.consumePendingNavigationHandoff()?.toDomainHandoff()
        _domainHandoff.value = null
        return consumed
    }
}

@Singleton
class DefaultChargingStationCatalogRepository @Inject constructor(
    private val chargingStationDao: ChargingStationDao
) : ChargingStationCatalogRepository {

    override fun observeAllStationsWithPorts(): Flow<List<DomainChargingStationWithPorts>> =
        chargingStationDao.getAllStationsWithPorts().map { list ->
            list.map { it.toDomainStationWithPorts() }
        }

    override fun observeAllStations(): Flow<List<DomainChargingStation>> =
        chargingStationDao.getAllStations().map { list ->
            list.map { it.toDomainStation() }
        }

    override fun observeStationsNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): Flow<List<DomainChargingStation>> {
        val latDelta = radiusKm / 111.32
        val cosLat = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val lonDelta = radiusKm / (111.32 * cosLat)
        return chargingStationDao.getStationsNearby(
            minLat = latitude - latDelta,
            maxLat = latitude + latDelta,
            minLng = longitude - lonDelta,
            maxLng = longitude + lonDelta
        ).map { list -> list.map { it.toDomainStation() } }
    }

    override suspend fun getStationWithPorts(stationId: String): DomainChargingStationWithPorts? =
        chargingStationDao.getStationWithPorts(stationId)?.toDomainStationWithPorts()

    override suspend fun getStationById(stationId: String): DomainChargingStation? =
        chargingStationDao.getStationById(stationId)?.toDomainStation()

    override suspend fun insertStation(station: DomainChargingStation) {
        chargingStationDao.insertStation(station.toStationEntity())
    }

    override suspend fun insertPort(port: DomainChargingPort) {
        chargingStationDao.insertPort(port.toPortEntity())
    }

    override suspend fun upsertStationWithPorts(
        station: DomainChargingStation,
        ports: List<DomainChargingPort>
    ) {
        chargingStationDao.insertStation(station.toStationEntity())
        if (ports.isNotEmpty()) {
            chargingStationDao.insertPorts(ports.map { it.toPortEntity() })
        }
    }

    override suspend fun deletePortsForStation(stationId: String) {
        chargingStationDao.deletePortsForStation(stationId)
    }

    override suspend fun deleteStationById(stationId: String) {
        chargingStationDao.deletePortsForStation(stationId)
        chargingStationDao.deleteStationById(stationId)
    }

    override suspend fun countStations(): Int = chargingStationDao.countStations()
}

@Singleton
class DefaultActiveJourneyStateRepository @Inject constructor(
    private val activeJourneyDao: ActiveJourneyDao
) : ActiveJourneyStateRepository {

    override suspend fun saveActiveJourney(journey: DomainActiveJourneyState) {
        activeJourneyDao.saveActiveJourney(journey.toActiveJourneyEntity())
    }

    override suspend fun getActiveJourney(id: String): DomainActiveJourneyState? =
        activeJourneyDao.getActiveJourney(id)?.toDomainActiveJourney()

    override suspend fun getLatestActiveJourney(): DomainActiveJourneyState? =
        activeJourneyDao.getLatestActiveJourney()?.toDomainActiveJourney()

    override suspend fun deleteJourney(id: String) {
        activeJourneyDao.deleteJourney(id)
    }

    override suspend fun clearAll() {
        activeJourneyDao.clearAll()
    }
}

@Singleton
class DefaultTripHistoryRepository @Inject constructor(
    private val tripHistoryDao: TripHistoryDao
) : TripHistoryRepository {

    override suspend fun getAllSummaries(): List<DomainTripHistorySummary> =
        tripHistoryDao.getAllSummaries().map { it.toDomainSummary() }

    override suspend fun getSummariesForVehicle(vehicleId: String): List<DomainTripHistorySummary> =
        tripHistoryDao.getSummariesForVehicle(vehicleId).map { it.toDomainSummary() }

    override suspend fun insertSummary(summary: DomainTripHistorySummary) {
        tripHistoryDao.insertSummary(summary.toSummaryEntity())
    }

    override suspend fun deleteSummariesForVehicle(vehicleId: String) {
        tripHistoryDao.deleteSummariesForVehicle(vehicleId)
    }

    override suspend fun clearAll() {
        tripHistoryDao.clearAll()
    }
}

@Singleton
class RoomEnvironmentCacheStore @Inject constructor(
    private val environmentCacheDao: EnvironmentCacheDao
) : EnvironmentCacheStore {

    override suspend fun insertOrUpdate(entry: DomainEnvironmentCacheRecord) {
        environmentCacheDao.insertOrUpdate(entry.toCacheEntity())
    }

    override suspend fun insertAll(entries: List<DomainEnvironmentCacheRecord>) {
        environmentCacheDao.insertAll(entries.map { it.toCacheEntity() })
    }

    override suspend fun getValidEntry(key: String, now: Long): DomainEnvironmentCacheRecord? =
        environmentCacheDao.getValidEntry(key, now)?.toDomainRecord()

    override suspend fun getValidEntriesInBounds(
        cacheType: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        now: Long
    ): List<DomainEnvironmentCacheRecord> =
        environmentCacheDao.getValidEntriesInBounds(cacheType, minLat, maxLat, minLon, maxLon, now)
            .map { it.toDomainRecord() }

    override suspend fun pruneExpired(now: Long) {
        environmentCacheDao.pruneExpired(now)
    }

    override suspend fun deleteInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        environmentCacheDao.deleteInBounds(minLat, maxLat, minLon, maxLon)
    }

    override suspend fun clearAll() {
        environmentCacheDao.clearAll()
    }
}
