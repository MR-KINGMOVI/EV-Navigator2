package com.evnavigator.iran.data.charging.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.TestDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ChargingStationDaoTest {

    private lateinit var chargingStationDao: ChargingStationDao
    private lateinit var db: TestDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java).build()
        chargingStationDao = db.chargingStationDao()
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

        chargingStationDao.insertStation(station)
        val foundStation = chargingStationDao.getStationById("station_1")

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

        chargingStationDao.insertStation(station)
        val foundStation = chargingStationDao.getStationByExternalId("ext_123")

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

        chargingStationDao.insertStation(station)
        
        val updatedStation = station.copy(canonicalName = "Updated Station")
        chargingStationDao.updateStation(updatedStation)
        
        val foundStation = chargingStationDao.getStationById("station_1")
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

        chargingStationDao.insertStation(station)
        chargingStationDao.deleteStationById("station_1")
        
        val foundStation = chargingStationDao.getStationById("station_1")
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

        chargingStationDao.insertPort(port)
        val foundPort = chargingStationDao.getPortById("port_1")

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

        chargingStationDao.insertPorts(listOf(port1, port2))
        val ports = chargingStationDao.getPortsForStation("station_1").first()

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

        chargingStationDao.insertStations(listOf(station1, station2))
        
        // This should return both stations within the bounding box
        val nearbyStations = chargingStationDao.getStationsNearby(
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 2)
    }

    // ============================================================================
    // SPATIAL INDEXING TESTS
    // ============================================================================

    @Test
    fun testGetStationsInBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            province = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            province = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Query for stations within bounding box
        val nearbyStations = chargingStationDao.getStationsInBoundingBox(
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 3)
        assert(nearbyStations.all { it.latitude >= 35.6890 && it.latitude <= 35.6900 })
        assert(nearbyStations.all { it.longitude >= 139.6910 && it.longitude <= 139.6925 })
    }

    @Test
    fun testGetStationsInBoundingBoxWithFilters() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            province = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            province = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2))

        // Query with province filter
        val nearbyStations = chargingStationDao.getStationsInBoundingBoxWithFilters(
            35.6890,
            35.6900,
            139.6910,
            139.6925,
            province = "Tehran"
        ).first()

        assert(nearbyStations.size == 2)
    }

    @Test
    fun testGetStationsInProvinceBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            province = "Isfahan",
            city = "Isfahan",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Query for stations in Tehran and bounding box
        val nearbyStations = chargingStationDao.getStationsInProvinceBoundingBox(
            "Tehran",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 2)
        assert(nearbyStations.all { it.province == "Tehran" })
    }

    @Test
    fun testGetStationsInCityBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            province = "Tehran",
            city = "Shiraz",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Query for stations in Tehran city and bounding box
        val nearbyStations = chargingStationDao.getStationsInCityBoundingBox(
            "Tehran",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 2)
        assert(nearbyStations.all { it.city == "Tehran" })
    }

    @Test
    fun testGetStationsByDatasetVersionBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            datasetVersion = "1.0.0",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            datasetVersion = "1.0.0",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            datasetVersion = "1.1.0",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Query for stations in dataset version 1.0.0 and bounding box
        val nearbyStations = chargingStationDao.getStationsByDatasetVersionBoundingBox(
            "1.0.0",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 2)
        assert(nearbyStations.all { it.datasetVersion == "1.0.0" })
    }

    @Test
    fun testGetStationsBySourceBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "ProviderA",
            sourceType = com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            source = "ProviderA",
            sourceType = com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            source = "ProviderB",
            sourceType = com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Query for stations from ProviderA and bounding box
        val nearbyStations = chargingStationDao.getStationsBySourceBoundingBox(
            "ProviderA",
            com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER.name,
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 2)
        assert(nearbyStations.all { it.source == "ProviderA" })
    }

    @Test
    fun testGetStationsByValidationStatusBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            validationStatus = "VALID",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            validationStatus = "VALID",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            validationStatus = "INVALID",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Query for stations with VALID status and bounding box
        val nearbyStations = chargingStationDao.getStationsByValidationStatusBoundingBox(
            "VALID",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 2)
        assert(nearbyStations.all { it.validationStatus == "VALID" })
    }

    @Test
    fun testGetStationsByConfidenceBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            confidence = 0.9,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID"
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            confidence = 0.9,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID"
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            confidence = 0.5,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID"
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Query for stations with confidence >= 0.8 and bounding box
        val nearbyStations = chargingStationDao.getStationsByConfidenceBoundingBox(
            0.8,
            35.6890,
            35.6900,
            139.6910,
            139.6925
        ).first()

        assert(nearbyStations.size == 2)
        assert(nearbyStations.all { it.confidence >= 0.8 })
    }

    @Test
    fun testCountStationsInBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            province = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            province = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Count stations within bounding box
        val count = chargingStationDao.countStationsInBoundingBox(
            35.6890,
            35.6900,
            139.6910,
            139.6925
        )

        assert(count == 3)
    }

    @Test
    fun testCountStationsInProvinceBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            province = "Isfahan",
            city = "Isfahan",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Count stations in Tehran and bounding box
        val count = chargingStationDao.countStationsInProvinceBoundingBox(
            "Tehran",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        )

        assert(count == 2)
    }

    @Test
    fun testCountStationsInCityBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            province = "Tehran",
            city = "Tehran",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            province = "Tehran",
            city = "Shiraz",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Count stations in Tehran city and bounding box
        val count = chargingStationDao.countStationsInCityBoundingBox(
            "Tehran",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        )

        assert(count == 2)
    }

    @Test
    fun testCountStationsByDatasetVersionBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            datasetVersion = "1.0.0",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            datasetVersion = "1.0.0",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            datasetVersion = "1.1.0",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Count stations in dataset version 1.0.0 and bounding box
        val count = chargingStationDao.countStationsByDatasetVersionBoundingBox(
            "1.0.0",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        )

        assert(count == 2)
    }

    @Test
    fun testCountStationsBySourceBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            source = "ProviderA",
            sourceType = com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            source = "ProviderA",
            sourceType = com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            source = "ProviderB",
            sourceType = com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID",
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Count stations from ProviderA and bounding box
        val count = chargingStationDao.countStationsBySourceBoundingBox(
            "ProviderA",
            com.evnavigator.iran.core.charging.SourceType.NETWORK_PROVIDER.name,
            35.6890,
            35.6900,
            139.6910,
            139.6925
        )

        assert(count == 2)
    }

    @Test
    fun testCountStationsByValidationStatusBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            validationStatus = "VALID",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.8
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            validationStatus = "VALID",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.8
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            validationStatus = "INVALID",
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.8
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Count stations with VALID status and bounding box
        val count = chargingStationDao.countStationsByValidationStatusBoundingBox(
            "VALID",
            35.6890,
            35.6900,
            139.6910,
            139.6925
        )

        assert(count == 2)
    }

    @Test
    fun testCountStationsByConfidenceBoundingBox() = runBlocking {
        val station1 = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Station 1",
            latitude = 35.6895,
            longitude = 139.6917,
            confidence = 0.9,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID"
        )

        val station2 = ChargingStationEntity(
            id = "station_2",
            canonicalName = "Station 2",
            latitude = 35.6896,
            longitude = 139.6918,
            confidence = 0.9,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID"
        )

        val station3 = ChargingStationEntity(
            id = "station_3",
            canonicalName = "Station 3",
            latitude = 35.6900,
            longitude = 139.6920,
            confidence = 0.5,
            source = "TestSource",
            sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VALID"
        )

        chargingStationDao.insertStations(listOf(station1, station2, station3))

        // Count stations with confidence >= 0.8 and bounding box
        val count = chargingStationDao.countStationsByConfidenceBoundingBox(
            0.8,
            35.6890,
            35.6900,
            139.6910,
            139.6925
        )

        assert(count == 2)
    }
}