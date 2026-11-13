package com.evnavigator.iran.data.charging.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.core.charging.ChargingPort
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.GeoCoordinate
import com.evnavigator.iran.core.charging.StationLocation
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

@RunWith(AndroidJUnit4::class)
class ChargingStationRepositoryTest {

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

    @Test
    fun testInsertAndReadStation() = runBlocking {
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

        chargingStationRepository.insertStation(station)
        val foundStation = chargingStationRepository.getStationById("station_1")

        assert(foundStation != null)
        assert(foundStation!!.id == "station_1")
        assert(foundStation.canonicalName == "Test Station")
    }

    @Test
    fun testInsertAndReadStationByExternalId() = runBlocking {
        val station = ChargingStationEntity(
            id = "station_1",
            externalId = "ext_123",
            canonicalName = "Test Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        val foundStation = chargingStationRepository.getStationByExternalId("ext_123")

        assert(foundStation != null)
        assert(foundStation!!.externalId == "ext_123")
    }

    @Test
    fun testUpdateStation() = runBlocking {
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

        chargingStationRepository.insertStation(station)
        
        val updatedStation = station.copy(canonicalName = "Updated Station")
        chargingStationRepository.updateStation(updatedStation)
        
        val foundStation = chargingStationRepository.getStationById("station_1")
        assert(foundStation!!.canonicalName == "Updated Station")
    }

    @Test
    fun testDeleteStation() = runBlocking {
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

        chargingStationRepository.insertStation(station)
        chargingStationRepository.deleteStationById("station_1")
        
        val foundStation = chargingStationRepository.getStationById("station_1")
        assert(foundStation == null)
    }

    @Test
    fun testInsertAndReadPort() = runBlocking {
        val port = ChargingPortEntity(
            id = "port_1",
            stationId = "station_1",
            connectorType = com.evnavigator.iran.core.charging.ConnectorType.CCS_COMBO_2,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertPort(port)
        val foundPort = chargingStationRepository.getPortById("port_1")

        assert(foundPort != null)
        assert(foundPort!!.id == "port_1")
        assert(foundPort.stationId == "station_1")
        assert(foundPort.connectorType == com.evnavigator.iran.core.charging.ConnectorType.CCS_COMBO_2)
    }

    @Test
    fun testInsertAndReadPortsForStation() = runBlocking {
        val port1 = ChargingPortEntity(
            id = "port_1",
            stationId = "station_1",
            connectorType = com.evnavigator.iran.core.charging.ConnectorType.CCS_COMBO_2,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        val port2 = ChargingPortEntity(
            id = "port_2",
            stationId = "station_1",
            connectorType = com.evnavigator.iran.core.charging.ConnectorType.TYPE_2,
            isAc = true,
            isDc = false,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertPorts(listOf(port1, port2))
        val ports = chargingStationRepository.getPortsForStation("station_1").first()

        assert(ports.size == 2)
        assert(ports.any { it.id == "port_1" })
        assert(ports.any { it.id == "port_2" })
    }

    @Test
    fun testGetStationsNearby() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStations(listOf(station1, station2))
        
        // This should return both stations within the bounding box
        val nearbyStations = chargingStationRepository.getStationsNearby(
            35.6890,
            139.6910,
            10.0
        ).first()

        assert(nearbyStations.size == 2)
    }

    @Test
    fun testGetStationsByConnectorType() = runBlocking {
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
            connectorType = com.evnavigator.iran.core.charging.ConnectorType.CCS_COMBO_2,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN"
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        val stations = chargingStationRepository.getStationsByConnectorType("CCS_COMBO_2").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }

    @Test
    fun testGetStationsByMinimumPower() = runBlocking {
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
            connectorType = com.evnavigator.iran.core.charging.ConnectorType.CCS_COMBO_2,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            validationStatus = "UNKNOWN",
            powerKw = 50.0
        )

        chargingStationRepository.insertStation(station)
        chargingStationRepository.insertPort(port)
        
        val stations = chargingStationRepository.getStationsByMinimumPower(40.0).first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }

    @Test
    fun testGetStationsByOperator() = runBlocking {
        val station = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN",
            operatorName = "Test Operator"
        )

        chargingStationRepository.insertStation(station)
        
        val stations = chargingStationRepository.getStationsByOperator("Test Operator").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }

    @Test
    fun testGetStationsByProvince() = runBlocking {
        val station = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN",
            province = "Tehran"
        )

        chargingStationRepository.insertStation(station)
        
        val stations = chargingStationRepository.getStationsByProvince("Tehran").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }

    @Test
    fun testGetStationsByCity() = runBlocking {
        val station = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "UNKNOWN",
            city = "Tehran"
        )

        chargingStationRepository.insertStation(station)
        
        val stations = chargingStationRepository.getStationsByCity("Tehran").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }
}