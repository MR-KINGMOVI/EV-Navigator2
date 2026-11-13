package com.evnavigator.iran.data.charging.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for Step Q - Geographic Indexing / Performance optimization
 */
@RunWith(AndroidJUnit4::class)
class ChargingStationOptimizationTest {

    private lateinit var chargingStationRepository: ChargingStationRepository
    private lateinit var db: TestDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java).build()
        chargingStationRepository = ChargingStationRepository(db.chargingStationDao())
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    /**
     * Test that the optimized nearby search method exists and is callable
     */
    @Test
    fun testOptimizedNearbySearchMethodExists() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station1)
        
        // Test that the optimized method can be called
        val nearbyStations = chargingStationRepository.getStationsNearbyOptimized(
            35.6895,
            139.6917,
            10.0
        ).first()

        assert(nearbyStations.size == 1)
        assert(nearbyStations[0].id == "station_1")
    }

    /**
     * Test that the optimized method maintains expected behavior
     */
    @Test
    fun testOptimizedMethodBehavior() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Nearby Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Far Station",
            latitude = 35.7000,
            longitude = 139.7000,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStations(listOf(station1, station2))
        
        // Test both methods work
        val regularNearby = chargingStationRepository.getStationsNearby(
            35.6895,
            139.6917,
            1.0
        ).first()

        val optimizedNearby = chargingStationRepository.getStationsNearbyOptimized(
            35.6895,
            139.6917,
            1.0
        ).first()

        // Both should return the same result (or similar)
        assert(regularNearby.size >= optimizedNearby.size)
        assert(optimizedNearby.size >= 1) // At least the nearby station
    }

    @Test
    fun testOptimizedMethodGuardsAgainstInvalidInputs() = runBlocking {
        val station = ChargingStationEntity(
            id = "station_val",
            canonicalName = "Valid Station",
            latitude = 35.6892,
            longitude = 51.3890,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )
        chargingStationRepository.insertStation(station)

        // NaN coordinates or radius
        assert(chargingStationRepository.getStationsNearbyOptimized(Double.NaN, 51.3890, 10.0).first().isEmpty())
        assert(chargingStationRepository.getStationsNearbyOptimized(35.6892, Double.NaN, 10.0).first().isEmpty())
        assert(chargingStationRepository.getStationsNearbyOptimized(35.6892, 51.3890, Double.NaN).first().isEmpty())

        // Negative or zero or infinite radius
        assert(chargingStationRepository.getStationsNearbyOptimized(35.6892, 51.3890, -5.0).first().isEmpty())
        assert(chargingStationRepository.getStationsNearbyOptimized(35.6892, 51.3890, 0.0).first().isEmpty())
        assert(chargingStationRepository.getStationsNearbyOptimized(35.6892, 51.3890, Double.POSITIVE_INFINITY).first().isEmpty())

        // Out of bounds coordinates
        assert(chargingStationRepository.getStationsNearbyOptimized(95.0, 51.3890, 10.0).first().isEmpty())
        assert(chargingStationRepository.getStationsNearbyOptimized(-95.0, 51.3890, 10.0).first().isEmpty())
        assert(chargingStationRepository.getStationsNearbyOptimized(35.6892, 190.0, 10.0).first().isEmpty())
        assert(chargingStationRepository.getStationsNearbyOptimized(35.6892, -190.0, 10.0).first().isEmpty())
    }

    @Test
    fun testOptimizedMethodSortsStrictlyAscendingByHaversineDistance() = runBlocking {
        // Origin: Tehran (35.7000, 51.4000)
        // Station Close: ~1.1 km north (35.7100, 51.4000)
        // Station Medium: ~5.5 km north (35.7500, 51.4000)
        // Station Far: ~11.1 km north (35.8000, 51.4000)
        val stClose = ChargingStationEntity(
            id = "st_close",
            canonicalName = "Close",
            latitude = 35.7100,
            longitude = 51.4000,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )
        val stMedium = ChargingStationEntity(
            id = "st_medium",
            canonicalName = "Medium",
            latitude = 35.7500,
            longitude = 51.4000,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )
        val stFar = ChargingStationEntity(
            id = "st_far",
            canonicalName = "Far",
            latitude = 35.8000,
            longitude = 51.4000,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        // Insert in scrambled order: Far, Close, Medium
        chargingStationRepository.insertStations(listOf(stFar, stClose, stMedium))

        // Query radius 8.0 km -> Should only include Close and Medium, sorted [Close, Medium]
        val results = chargingStationRepository.getStationsNearbyOptimized(35.7000, 51.4000, 8.0).first()

        assert(results.size == 2) { "Expected 2 stations within 8km, got ${results.size}" }
        assert(results[0].id == "st_close") { "First must be closest station" }
        assert(results[1].id == "st_medium") { "Second must be medium station" }
    }
}