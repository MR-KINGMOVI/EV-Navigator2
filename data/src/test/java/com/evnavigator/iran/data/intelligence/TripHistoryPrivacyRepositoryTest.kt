package com.evnavigator.iran.data.intelligence

import com.evnavigator.iran.data.intelligence.dao.IntelligencePreferencesDao
import com.evnavigator.iran.data.intelligence.dao.TripHistoryDao
import com.evnavigator.iran.data.intelligence.entity.IntelligencePreferencesEntity
import com.evnavigator.iran.data.intelligence.entity.TripHistorySummaryEntity
import com.evnavigator.iran.data.intelligence.repository.TripHistoryPrivacyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TripHistoryPrivacyRepositoryTest {

    private lateinit var fakeTripDao: FakeTripHistoryDao
    private lateinit var fakePrefsDao: FakeIntelligencePreferencesDao
    private lateinit var repository: TripHistoryPrivacyRepository

    @Before
    fun setup() {
        fakeTripDao = FakeTripHistoryDao()
        fakePrefsDao = FakeIntelligencePreferencesDao()
        repository = TripHistoryPrivacyRepository(fakeTripDao, fakePrefsDao)
    }

    private fun createSummary(tripId: String, vehicleId: String = "car_1", startEpoch: Long = System.currentTimeMillis()): TripHistorySummaryEntity {
        return TripHistorySummaryEntity(
            tripId = tripId,
            vehicleId = vehicleId,
            startEpochMs = startEpoch,
            endEpochMs = startEpoch + 3600_000L,
            distanceMeters = 50_000.0,
            predictedEnergyWh = 9_000.0,
            measuredEnergyWh = 9_500.0,
            averageSpeedKmh = 80.0,
            ambientTempCelsius = 22.0,
            isVerifiedTelemetry = true
        )
    }

    @Test
    fun testOptOutPreventsTripLogging() = runBlocking {
        // User opts out of history logging
        repository.updatePreferences(
            IntelligencePreferencesEntity(isTripHistoryLoggingEnabled = false)
        )

        val saved = repository.recordTripSummary(createSummary("trip_1"))
        assertFalse("When opted out, trip summary must not be persisted", saved)
        assertEquals(0, fakeTripDao.items.size)
    }

    @Test
    fun testOptInAllowsLoggingAndVehicleQuery() = runBlocking {
        repository.updatePreferences(
            IntelligencePreferencesEntity(isTripHistoryLoggingEnabled = true)
        )

        val saved = repository.recordTripSummary(createSummary("trip_1", "car_A"))
        assertTrue("When opted in, trip summary is saved", saved)
        assertEquals(1, fakeTripDao.items.size)

        val list = repository.getHistoryForVehicle("car_A")
        assertEquals(1, list.size)
        assertEquals("trip_1", list[0].tripId)
    }

    @Test
    fun testAutoPruningDeletesOldTrips() = runBlocking {
        repository.updatePreferences(
            IntelligencePreferencesEntity(isTripHistoryLoggingEnabled = true, maxHistoryRetentionDays = 30)
        )

        val fortyDaysAgo = System.currentTimeMillis() - (40L * 24 * 3600 * 1000L)
        fakeTripDao.items.add(createSummary("trip_ancient", "car_A", startEpoch = fortyDaysAgo))

        // Recording a new trip triggers auto-prune
        repository.recordTripSummary(createSummary("trip_new", "car_A"))

        val activeList = repository.getHistoryForVehicle("car_A")
        assertEquals(1, activeList.size)
        assertEquals("trip_new", activeList[0].tripId)
        assertFalse(activeList.any { it.tripId == "trip_ancient" })
    }

    @Test
    fun testClearVehicleHistory() = runBlocking {
        repository.updatePreferences(IntelligencePreferencesEntity(isTripHistoryLoggingEnabled = true))
        repository.recordTripSummary(createSummary("trip_A", "car_A"))
        repository.recordTripSummary(createSummary("trip_B", "car_B"))

        repository.clearHistoryForVehicle("car_A")
        assertTrue(repository.getHistoryForVehicle("car_A").isEmpty())
        assertEquals(1, repository.getHistoryForVehicle("car_B").size)
    }
}

class FakeTripHistoryDao : TripHistoryDao {
    val items = mutableListOf<TripHistorySummaryEntity>()

    override suspend fun insertSummary(summary: TripHistorySummaryEntity) {
        items.removeIf { it.tripId == summary.tripId }
        items.add(summary)
    }

    override suspend fun getSummariesForVehicle(vehicleId: String): List<TripHistorySummaryEntity> {
        return items.filter { it.vehicleId == vehicleId }
    }

    override suspend fun getAllSummaries(): List<TripHistorySummaryEntity> = items.toList()

    override suspend fun deleteSummariesForVehicle(vehicleId: String) {
        items.removeIf { it.vehicleId == vehicleId }
    }

    override suspend fun deleteOlderThan(cutoffEpochMs: Long): Int {
        val countBefore = items.size
        items.removeIf { it.startEpochMs < cutoffEpochMs }
        return countBefore - items.size
    }

    override suspend fun clearAll() {
        items.clear()
    }

    override suspend fun countSummariesForVehicle(vehicleId: String): Int = items.count { it.vehicleId == vehicleId }
}

class FakeIntelligencePreferencesDao : IntelligencePreferencesDao {
    private var prefs: IntelligencePreferencesEntity? = null

    override suspend fun savePreferences(preferences: IntelligencePreferencesEntity) {
        prefs = preferences
    }

    override suspend fun getPreferences(userId: String): IntelligencePreferencesEntity? = prefs

    override suspend fun clearPreferences(userId: String) {
        prefs = null
    }
}
