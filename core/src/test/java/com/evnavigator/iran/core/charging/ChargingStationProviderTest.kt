package com.evnavigator.iran.core.charging

import com.evnavigator.iran.core.charging.provider.*
import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ChargingStationProviderTest {

    private fun createTestStation(id: String, name: String, lat: Double, lon: Double, kw: Double = 60.0): ChargingStation {
        val loc = StationLocation(
            latitude = lat,
            longitude = lon,
            province = "Tehran",
            city = "Tehran",
            address = "Street 1, Tehran"
        )
        val meta = StationMetadata(
            name = name,
            operator = "Mapna"
        )
        val port = ChargingPort(
            id = "${id}_p1",
            connectorType = ConnectorType.CCS_COMBO_2,
            power = kw,
            isDc = true,
            isAc = false
        )
        return ChargingStation(
            id = id,
            location = loc,
            metadata = meta,
            ports = listOf(port),
            powerCapabilities = listOf(PowerCapability(powerKw = kw, isDc = true, isAc = false, enforceStrictConsistency = false)),
            status = StationStatus.OPERATIONAL,
            isOperational = true,
            provenance = Provenance.createMinimal("TEST", System.currentTimeMillis())
        )
    }

    @Test
    fun testOfflineCatalogSpatialFiltering() = runTest {
        val tehranStation = createTestStation("st1", "Tehran Azadi", 35.6997, 51.3380)
        val qomStation = createTestStation("st2", "Qom Mehr-o-Mah", 34.6401, 50.8764)

        val provider = OfflineCatalogChargingStationProvider {
            listOf(tehranStation, qomStation)
        }

        // Query near Tehran with 10km radius
        val query = ProviderStationQuery(
            center = GeoCoordinate(35.6997, 51.3380),
            radiusMeters = 10_000.0
        )
        val result = provider.queryStations(query)

        assertEquals(1, result.stations.size)
        assertEquals("st1", result.stations.first().id)
        assertEquals(ChargingSourceProviderType.OFFLINE_CATALOG, result.providerType)
    }

    @Test
    fun testCompositeProviderFallbackAndDeduplication() = runTest {
        val s1 = createTestStation("st1", "Tehran Station", 35.6997, 51.3380)
        val s1Dup = createTestStation("st1_dup", "Tehran Station", 35.69971, 51.33801) // within 50m

        val mapnaProvider = MapnaChargingStationProvider(
            stationSupplier = { listOf(s1) }
        )
        val offlineFallback = OfflineCatalogChargingStationProvider {
            listOf(s1Dup)
        }

        val composite = CompositeChargingStationProvider(
            providers = listOf(mapnaProvider),
            offlineFallback = offlineFallback
        )

        val query = ProviderStationQuery(
            center = GeoCoordinate(35.6997, 51.3380),
            radiusMeters = 5000.0
        )
        val result = composite.queryStations(query)

        assertEquals("Deduplication should leave exactly 1 station", 1, result.stations.size)
        assertEquals(ChargingSourceProviderType.COMPOSITE, result.providerType)
    }

    @Test
    fun testMinPowerFiltering() = runTest {
        val lowPower = createTestStation("st_ac", "AC 22kW", 35.6997, 51.3380, kw = 22.0)
        val highPower = createTestStation("st_dc", "DC 120kW", 35.7010, 51.3390, kw = 120.0)

        val provider = OfflineCatalogChargingStationProvider {
            listOf(lowPower, highPower)
        }

        val query = ProviderStationQuery(
            center = GeoCoordinate(35.6997, 51.3380),
            radiusMeters = 5000.0,
            minPowerKw = 50.0 // filter out 22kW
        )
        val result = provider.queryStations(query)

        assertEquals(1, result.stations.size)
        assertEquals("st_dc", result.stations.first().id)
    }
}
