package com.evnavigator.iran.data.charging.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.core.charging.ACDCCompatibilityEngine
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
 * Tests for Step T - AC/DC Compatibility
 */
@RunWith(AndroidJUnit4::class)
class ChargingStationACDCCompatibilityTest {

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
     * Test AC/DC compatibility between vehicle and station
     */
    @Test
    fun testAcDcCompatibilityIntegration() = runBlocking {
        // Create a vehicle with specific charging capabilities (from Phase 6)
        val vehicleIsAc = true   // Vehicle supports AC charging
        val vehicleIsDc = false  // Vehicle does not support DC charging
        
        // Create a charging station with AC capability (from Phase 7)
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
            connectorType = ConnectorType.TYPE_2,
            isAc = true,   // Station provides AC charging
            isDc = false,  // Station does not provide DC charging
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test the compatibility engine directly (not using repository)
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = vehicleIsAc,
            vehicleIsDc = vehicleIsDc,
            stationIsAc = port.isAc,
            stationIsDc = port.isDc
        )
        
        // Vehicle supports AC, station provides AC - compatible
        assert(result.status == com.evnavigator.iran.core.charging.ACDCCompatibility.COMPATIBLE)
        assert(result.vehicleIsAc == true)
        assert(result.vehicleIsDc == false)
        assert(result.stationIsAc == true)
        assert(result.stationIsDc == false)
    }

    /**
     * Test AC/DC compatibility with DC vehicle and DC station
     */
    @Test
    fun testDcVehicleWithDcStation() = runBlocking {
        // Create a vehicle with DC charging capability (from Phase 6)
        val vehicleIsAc = false  // Vehicle does not support AC charging  
        val vehicleIsDc = true   // Vehicle supports DC charging
        
        // Create a charging station with DC capability (from Phase 7)
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
            isAc = false,  // Station does not provide AC charging
            isDc = true,   // Station provides DC charging
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test the compatibility engine directly (not using repository)
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = vehicleIsAc,
            vehicleIsDc = vehicleIsDc,
            stationIsAc = port.isAc,
            stationIsDc = port.isDc
        )
        
        // Vehicle supports DC, station provides DC - compatible
        assert(result.status == com.evnavigator.iran.core.charging.ACDCCompatibility.COMPATIBLE)
        assert(result.vehicleIsAc == false)
        assert(result.vehicleIsDc == true)
        assert(result.stationIsAc == false)
        assert(result.stationIsDc == true)
    }

    /**
     * Test AC/DC compatibility with unknown values
     */
    @Test
    fun testAcDcCompatibilityWithUnknownValues() = runBlocking {
        // Create a vehicle with unknown charging capabilities (from Phase 6)
        val vehicleIsAc = null   // Unknown
        val vehicleIsDc = null   // Unknown
        
        // Create a charging station with known AC capability (from Phase 7)
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
            connectorType = ConnectorType.TYPE_2,
            isAc = true,   // Station provides AC charging
            isDc = false,  // Station does not provide DC charging
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test the compatibility engine directly (not using repository)
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = vehicleIsAc,
            vehicleIsDc = vehicleIsDc,
            stationIsAc = port.isAc,
            stationIsDc = port.isDc
        )
        
        // With unknown vehicle capabilities, result should be UNKNOWN
        assert(result.status == com.evnavigator.iran.core.charging.ACDCCompatibility.UNKNOWN)
        assertNull(result.vehicleIsAc)
        assertNull(result.vehicleIsDc)
        assert(result.stationIsAc == true)
        assert(result.stationIsDc == false)
    }
}