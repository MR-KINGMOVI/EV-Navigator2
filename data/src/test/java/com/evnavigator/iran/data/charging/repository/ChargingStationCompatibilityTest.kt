package com.evnavigator.iran.data.charging.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.vehicle.ConnectorType as VehicleConnectorType
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
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
 * Tests for Step R - Connector Compatibility Engine
 */
@RunWith(AndroidJUnit4::class)
class ChargingStationCompatibilityTest {

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
     * Test compatible connector types between vehicle and station
     */
    @Test
    fun testCompatibleConnectors() = runBlocking {
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
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test that we can find stations by connector type
        val stations = chargingStationRepository.getStationsByConnectorType("CCS_COMBO_2").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }

    /**
     * Test incompatible connector types between vehicle and station
     */
    @Test
    fun testIncompatibleConnectors() = runBlocking {
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
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test that we can find stations by incompatible connector type
        val stations = chargingStationRepository.getStationsByConnectorType("TYPE_2").first()
        
        assert(stations.isEmpty())
    }

    /**
     * Test unknown connector handling
     */
    @Test
    fun testUnknownConnectors() = runBlocking {
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
            connectorType = ConnectorType.UNKNOWN,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        // Test that we can find stations by unknown connector type
        val stations = chargingStationRepository.getStationsByConnectorType("UNKNOWN").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }

    /**
     * Test multiple connectors at same station
     */
    @Test
    fun testMultipleConnectorsAtStation() = runBlocking {
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

        val port1 = ChargingPortEntity(
            id = "port_1",
            stationId = "station_1",
            connectorType = ConnectorType.CCS_COMBO_2,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        val port2 = ChargingPortEntity(
            id = "port_2",
            stationId = "station_1",
            connectorType = ConnectorType.TYPE_2,
            isAc = true,
            isDc = false,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPorts(listOf(port1, port2))
        
        // Test finding by first connector type
        val stations1 = chargingStationRepository.getStationsByConnectorType("CCS_COMBO_2").first()
        assert(stations1.size == 1)
        assert(stations1[0].id == "station_1")
        
        // Test finding by second connector type
        val stations2 = chargingStationRepository.getStationsByConnectorType("TYPE_2").first()
        assert(stations2.size == 1)
        assert(stations2[0].id == "station_1")
    }
}