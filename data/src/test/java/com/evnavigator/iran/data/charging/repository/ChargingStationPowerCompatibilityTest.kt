package com.evnavigator.iran.data.charging.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.core.charging.PowerCompatibilityEngine
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.vehicle.ChargingSpecification
import com.evnavigator.iran.core.vehicle.ConnectorType as VehicleConnectorType
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for Step S - Charging Power Compatibility
 */
@RunWith(AndroidJUnit4::class)
class ChargingStationPowerCompatibilityTest {

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
     * Test power compatibility between vehicle and station
     */
    @Test
    fun testPowerCompatibilityIntegration() = runBlocking {
        // Create a vehicle with specific charging capabilities (from Phase 6)
        val vehicleMaxPowerKw = 50.0 // Vehicle can charge up to 50kW
        
        // Create a charging station with power capability (from Phase 7)
        val station = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        val port = ChargingPortEntity(
            id = "port_1",
            stationId = "station_1",
            connectorType = ConnectorType.CCS_COMBO_2,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN",
            powerKw = 100.0 // Station can provide up to 100kW
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test the compatibility engine directly (not using repository)
        val result = PowerCompatibilityEngine.determineEffectivePower(
            vehicleMaxPowerKw,
            port.powerKw
        )
        
        // Vehicle can charge at 50kW, station can provide 100kW, so effective is 50kW
        assert(result.status == com.evnavigator.iran.core.charging.PowerCompatibility.COMPATIBLE)
        assert(result.vehiclePowerKw == 50.0)
        assert(result.stationPowerKw == 100.0)
        assert(result.effectivePowerKw == 50.0)
    }

    /**
     * Test power compatibility with unknown values
     */
    @Test
    fun testPowerCompatibilityWithUnknownValues() = runBlocking {
        // Create a vehicle with unknown charging capability (from Phase 6)
        val vehicleMaxPowerKw = null // Unknown
        
        // Create a charging station with known power capability (from Phase 7)
        val station = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        val port = ChargingPortEntity(
            id = "port_1",
            stationId = "station_1",
            connectorType = ConnectorType.CCS_COMBO_2,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN",
            powerKw = 100.0 // Station can provide up to 100kW
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test the compatibility engine directly (not using repository)
        val result = PowerCompatibilityEngine.determineEffectivePower(
            vehicleMaxPowerKw,
            port.powerKw
        )
        
        // With unknown vehicle power, result should be UNKNOWN
        assert(result.status == com.evnavigator.iran.core.charging.PowerCompatibility.UNKNOWN)
        assertNull(result.vehiclePowerKw)
        assertEquals(100.0, result.stationPowerKw ?: 0.0, 0.001)
        assertNull(result.effectivePowerKw)
    }
}