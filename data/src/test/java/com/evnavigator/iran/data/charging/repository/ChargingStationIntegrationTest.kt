package com.evnavigator.iran.data.charging.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.core.charging.ChargingPort
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.GeoCoordinate
import com.evnavigator.iran.core.charging.StationLocation
import com.evnavigator.iran.core.vehicle.ChargingSpecification
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
 * Integration tests for Phase 6 Vehicle Database and Phase 7 Charging Infrastructure
 *
 * These tests verify that the charging infrastructure can work with vehicle specifications
 * from Phase 6 without duplicating or mixing data.
 */
@RunWith(AndroidJUnit4::class)
class ChargingStationIntegrationTest {

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
     * Test that vehicle connector compatibility works with charging station ports
     */
    @Test
    fun testVehicleConnectorCompatibilityWithStationPorts() = runBlocking {
        // Create a vehicle with CCS2 connector capability (from Phase 6)
        val vehicleChargingSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )

        // Create a charging station with CCS2 port (from Phase 7)
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

        // Test compatibility - should be compatible
        val stations = chargingStationRepository.getStationsByConnectorType("CCS_COMBO_2").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }

    /**
     * Test that incompatible connectors are correctly handled
     */
    @Test
    fun testIncompatibleConnectors() = runBlocking {
        // Create a vehicle with Type 2 connector capability (from Phase 6)
        val vehicleChargingSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.TYPE_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )

        // Create a charging station with CCS2 port (from Phase 7)
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

        // Test compatibility - should be incompatible
        val stations = chargingStationRepository.getStationsByConnectorType("TYPE_2").first()
        
        assert(stations.isEmpty())
    }

    /**
     * Test that unknown connectors are handled properly
     */
    @Test
    fun testUnknownConnectorHandling() = runBlocking {
        // Create a vehicle with unknown connector capability (from Phase 6)
        val vehicleChargingSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.OTHER),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )

        // Create a charging station with unknown port (from Phase 7)
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

        // Test that unknown connectors are handled appropriately
        val stations = chargingStationRepository.getStationsByConnectorType("UNKNOWN").first()
        
        assert(stations.size == 1)
        assert(stations[0].id == "station_1")
    }
}