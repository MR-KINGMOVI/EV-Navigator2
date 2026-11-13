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
 * Tests for Step P - Geospatial Nearby Search functionality
 */
@RunWith(AndroidJUnit4::class)
class ChargingStationNearbySearchTest {

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
     * Test that nearby search works with exact coordinates in range
     */
    @Test
    fun testNearbySearchWithExactCoordinates() = runBlocking {
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
            canonicalName = "Another Nearby Station", 
            latitude = 35.6896,
            longitude = 139.6918,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStations(listOf(station1, station2))
        
        // Search within a 1km radius around the first station
        val nearbyStations = chargingStationRepository.getStationsNearby(
            35.6895,
            139.6917,
            1.0
        ).first()

        // Both stations should be returned since they're very close
        assert(nearbyStations.size == 2)
        assert(nearbyStations.any { it.id == "station_1" })
        assert(nearbyStations.any { it.id == "station_2" })
    }

    /**
     * Test that nearby search filters out stations outside radius
     */
    @Test
    fun testNearbySearchFiltersByRadius() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Close Station",
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
            latitude = 35.7000, // Far away
            longitude = 139.7000,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStations(listOf(station1, station2))
        
        // Search within a 1km radius around the first station
        val nearbyStations = chargingStationRepository.getStationsNearby(
            35.6895,
            139.6917,
            1.0
        ).first()

        // Only close station should be returned
        assert(nearbyStations.size == 1)
        assert(nearbyStations[0].id == "station_1")
    }

    /**
     * Test that nearby search with zero radius returns empty list
     */
    @Test
    fun testNearbySearchWithZeroRadius() = runBlocking {
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
        
        // Search with zero radius
        val nearbyStations = chargingStationRepository.getStationsNearby(
            35.6895,
            139.6917,
            0.0
        ).first()

        // Should return empty list (radius too small to include any)
        assert(nearbyStations.isEmpty())
    }

    /**
     * Test that nearby search handles edge cases properly
     */
    @Test
    fun testNearbySearchEdgeCases() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Equator Station",
            latitude = 0.0,
            longitude = 0.0,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station1)
        
        // Search around equator with a 1km radius
        val nearbyStations = chargingStationRepository.getStationsNearby(
            0.0,
            0.0,
            1.0
        ).first()

        assert(nearbyStations.size == 1)
        assert(nearbyStations[0].id == "station_1")
    }
}