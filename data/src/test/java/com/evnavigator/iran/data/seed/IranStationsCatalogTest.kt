package com.evnavigator.iran.data.seed

import com.evnavigator.iran.core.charging.ValidationStatus
import org.junit.Assert.*
import org.junit.Test

class IranStationsCatalogTest {

    @Test
    fun testCatalogIntegrityAndStationCount() {
        val nov2026EpochMs = 1_794_441_600_000L
        val (stations, ports) = IranStationsCatalog.getAllStationsAndPorts(catalogGeneratedAtEpochMs = nov2026EpochMs)

        // 1. Exact count verification
        assertEquals("Total station records in catalog must equal 195", 195, stations.size)
        assertTrue("Port count must be positive and exceed station count", ports.size >= 195)

        // 2. Zero duplicate station IDs
        val uniqueIds = stations.map { it.id }.toSet()
        assertEquals("All station IDs must be strictly unique", 195, uniqueIds.size)

        // 3. Geographic bounding box validation (Iran mainland + islands)
        for (station in stations) {
            assertTrue(
                "Station ${station.id} latitude (${station.latitude}) must be inside Iran (25.0..39.8)",
                station.latitude in 25.0..39.8
            )
            assertTrue(
                "Station ${station.id} longitude (${station.longitude}) must be inside Iran (44.0..63.5)",
                station.longitude in 44.0..63.5
            )
        }

        // 4. Honest V13.2 verification status distribution in late 2026 (2025 verifications are STALE -> PARTIALLY_VERIFIED)
        val verifiedStations = stations.filter { it.validationStatus == ValidationStatus.VERIFIED.name }
        val partiallyVerified = stations.filter { it.validationStatus == ValidationStatus.PARTIALLY_VERIFIED.name }
        val unverifiedOrAssumed = stations.filter { it.validationStatus == ValidationStatus.UNVERIFIED.name || it.validationStatus == ValidationStatus.ASSUMED.name }

        assertEquals("No static station with a 2025 timestamp or unchecked URL may claim fresh VERIFIED in Nov 2026", 0, verifiedStations.size)
        assertEquals("93 stations must be PARTIALLY_VERIFIED (83 municipal + 10 stale 2025 operator records)", 93, partiallyVerified.size)
        assertEquals("102 stations must be UNVERIFIED corridor survey candidates", 102, unverifiedOrAssumed.size)

        // Ensure UNVERIFIED / ASSUMED is never represented as VERIFIED
        for (st in unverifiedOrAssumed) {
            assertNotEquals("Unverified/Assumed station must never have VERIFIED status", ValidationStatus.VERIFIED.name, st.validationStatus)
            assertTrue("Unverified/Assumed station confidence must be moderate (<= 0.60)", st.confidence <= 0.60)
        }

        // 5. Zero occurrences of fake MAPNA-SPEC tokens and bare pricePerKwh values
        for (port in ports) {
            assertFalse(
                "Port sourceReference (${port.sourceReference}) must not contain fabricated MAPNA-SPEC string",
                port.sourceReference?.contains("MAPNA-SPEC") == true
            )
            assertNull("Port pricePerKwh must not contain bare untraceable numbers", port.pricePerKwh)
            assertTrue("Port power must be positive", (port.powerKw ?: 0.0) > 0.0)
            assertNotNull("Port connector type must not be null", port.connectorType)
        }
    }
}
