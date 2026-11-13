package com.evnavigator.iran.data.seed

import org.junit.Assert.*
import org.junit.Test

class CatalogCountAndProvenanceTest {

    @Test
    fun testStationCatalogExactCount() {
        val (stations, ports) = IranStationsCatalog.getAllStationsAndPorts()
        assertEquals("Station catalog must contain exactly 195 stations", 195, stations.size)
        assertTrue("Port count must be greater than station count", ports.size >= stations.size)
    }

    @Test
    fun testVehicleCatalogExactCountAndStandards() {
        val vehicles = IranVehiclesCatalog.getAllVehicles()
        assertEquals("Vehicle catalog must contain exactly 25 vehicles", 25, vehicles.size)

        for (vehicle in vehicles) {
            assertNotNull("Vehicle must have an id", vehicle.id)
            assertTrue("Battery capacity must be positive", vehicle.usableBatteryCapacityWh > 0)
            assertEquals("Default consumption standard must be labeled WLTP", "WLTP", vehicle.consumptionStandard)
            assertEquals("Reference speed must be 90 km/h", 90.0, vehicle.consumptionReferenceSpeedKmh, 0.001)
        }
    }
}
