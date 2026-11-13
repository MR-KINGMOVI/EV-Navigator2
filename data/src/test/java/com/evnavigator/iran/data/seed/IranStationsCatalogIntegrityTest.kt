package com.evnavigator.iran.data.seed

import com.evnavigator.iran.data.mapper.toDomainPort
import com.evnavigator.iran.data.mapper.toDomainStation
import com.evnavigator.iran.domain.intelligence.context.FieldFreshnessState
import com.evnavigator.iran.domain.intelligence.context.FieldVerificationState
import com.evnavigator.iran.domain.intelligence.context.UrlVerificationStatus
import com.evnavigator.iran.domain.model.catalog.TariffCurrency
import com.evnavigator.iran.domain.model.catalog.VerificationMethod
import com.evnavigator.iran.domain.policy.DomainClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V13.2 Data Trust, Independent Verification & Tariff Provenance Test Suite for [IranStationsCatalog].
 *
 * Verifies V13.2 Sections 3, 4, 5, 6 & 12:
 * 1. Global `LAST_OPERATOR_RELEASE_VERIFIED_AT_EPOCH_MS` is eliminated; each station in
 *    [IranStationsCatalog.INDEPENDENT_STATION_VERIFICATION_REGISTRY] has a unique `verifiedAtEpochMs`
 *    and a unique `evidenceSource` / `evidenceReference`.
 * 2. In late 2026 (`snapshotTime = 1_794_441_600_000L`), historical 2025 operator release records
 *    exceed the 30-day TTL (`STALE`) and have `LINK_UNCHECKED` URLs, so 0 stations in the static
 *    offline catalog falsely claim fresh `VERIFIED` status (`10` transition to `STALE` / `PARTIALLY_VERIFIED`).
 * 3. When a fresh independent field inspection (`FIELD_INSPECTION` + `HTTP_CONTENT_VERIFIED` within 30 days)
 *    is provided, only that station becomes `VERIFIED` and `FRESH`.
 * 4. `retrievedDate` (`catalogGeneratedAtEpochMs`) is never conflated with `verifiedAt`.
 * 5. Bare `pricePerKwh` numbers are removed (`null`) on static seed ports and replaced with
 *    currency-explicit [com.evnavigator.iran.domain.model.catalog.ChargingTariffSchedule] records (`IRR_RIAL`).
 */
class IranStationsCatalogIntegrityTest {

    @Test
    fun `full catalog contains 195 stations and historical 2025 verifications transition to STALE and PARTIALLY_VERIFIED in 2026`() {
        val nov2026EpochMs = 1_794_441_600_000L // 2026-11-12T00:00:00Z
        val (allStations, allPorts) = IranStationsCatalog.getAllStationsAndPorts(
            catalogGeneratedAtEpochMs = nov2026EpochMs,
            includeSeedCandidates = true
        )
        val (prodStations, prodPorts) = IranStationsCatalog.getProductionStationsAndPorts(
            catalogGeneratedAtEpochMs = nov2026EpochMs
        )

        assertEquals(195, allStations.size)
        assertTrue(allPorts.size >= 195)
        assertEquals(93, prodStations.size)
        assertTrue(prodPorts.isNotEmpty())

        // In Nov 2026 without live OCPP feed or fresh <30d field inspection, 0 static stations claim fresh VERIFIED
        val verifiedCount = allStations.count { it.validationStatus == "VERIFIED" }
        val partiallyVerifiedCount = allStations.count { it.validationStatus == "PARTIALLY_VERIFIED" }
        val unverifiedCount = allStations.count { it.validationStatus == "UNVERIFIED" }
        assertEquals("No offline seed station with 2025 timestamp or LINK_UNCHECKED may claim fresh VERIFIED in Nov 2026", 0, verifiedCount)
        assertEquals(93, partiallyVerifiedCount)
        assertEquals(102, unverifiedCount)

        // All 10 historical Mapna release stations have unique independent timestamps and evaluate to STALE in Nov 2026
        val registryTimestamps = IranStationsCatalog.INDEPENDENT_STATION_VERIFICATION_REGISTRY.values.map { it.verifiedAtEpochMs }
        assertEquals("Every station in the audit registry must have a distinct timestamp", 10, registryTimestamps.toSet().size)

        val staleStations = allStations.map { it.toDomainStation() }.filter { st ->
            st.verifiedAt != null && st.toIndependentVerificationRecord().evaluateFreshnessState(nov2026EpochMs) == FieldFreshnessState.STALE
        }
        assertEquals(10, staleStations.size)
        for (staleSt in staleStations) {
            assertFalse(
                "Stale station ${staleSt.id} must never be eligible for fresh verified recommendation",
                staleSt.isEligibleForFreshVerifiedRecommendation(DomainClock.fixed(nov2026EpochMs))
            )
        }
    }

    @Test
    fun `station with fresh independent FIELD_INSPECTION and HTTP_CONTENT_VERIFIED within 30 days is VERIFIED and FRESH`() {
        val nov2026EpochMs = 1_794_441_600_000L
        val freshInspectionTime = nov2026EpochMs - (5L * 24L * 3600_000L) // 5 days ago
        val override = mapOf(
            "station_tehran_milad" to IranStationsCatalog.IndependentStationAuditEntry(
                stationId = "station_tehran_milad",
                externalRecordId = "MAPNA_001",
                verifiedAtEpochMs = freshInspectionTime,
                evidenceSource = "TEHRAN_FIELD_ENGINEERING_TEAM_2026",
                evidenceReference = "FIELD-INSP-2026-11-MILAD-001",
                verificationMethod = VerificationMethod.FIELD_INSPECTION,
                urlVerificationStatus = UrlVerificationStatus.HTTP_CONTENT_VERIFIED
            )
        )
        val (prodStations, _) = IranStationsCatalog.getProductionStationsAndPorts(
            catalogGeneratedAtEpochMs = nov2026EpochMs,
            freshVerificationOverrides = override
        )
        val milad = prodStations.first { it.id == "station_tehran_milad" }.toDomainStation()
        assertEquals("VERIFIED", milad.validationStatus)
        assertEquals(freshInspectionTime, milad.verifiedAt)
        assertNotEquals(milad.retrievedDate, milad.verifiedAt)
        assertTrue(milad.isEligibleForFreshVerifiedRecommendation(DomainClock.fixed(nov2026EpochMs)))
        val record = milad.toIndependentVerificationRecord()
        assertEquals(FieldFreshnessState.FRESH, record.evaluateFreshnessState(nov2026EpochMs))
        assertEquals(FieldVerificationState.VERIFIED, record.resolveEffectiveVerificationState(nov2026EpochMs))
    }

    @Test
    fun `ports do not carry bare untraceable pricePerKwh numbers and map to currency-explicit IRR_RIAL tariff schedules`() {
        val nov2026EpochMs = 1_794_441_600_000L
        val (_, allPorts) = IranStationsCatalog.getAllStationsAndPorts(catalogGeneratedAtEpochMs = nov2026EpochMs)
        for (portEntity in allPorts) {
            assertNull("Bare pricePerKwh on ChargingPortEntity must be null in V13.2", portEntity.pricePerKwh)
            val domainPort = portEntity.toDomainPort()
            val tariff = domainPort.tariffSchedule
            assertNotNull("Every mapped DomainChargingPort must carry a structured ChargingTariffSchedule", tariff)
            assertEquals(TariffCurrency.IRR_RIAL, tariff!!.currency)
            assertEquals(FieldFreshnessState.STALE, tariff.evaluateFreshness(nov2026EpochMs))
            assertFalse("Expired 1404 tariff schedule must not be used for deterministic verified cost in late 2026", tariff.canUseForDeterministicCost(nov2026EpochMs))
        }
    }

    @Test
    fun `all station coordinates are valid and within Iran geographic bounds`() {
        val (stations, _) = IranStationsCatalog.getAllStationsAndPorts()
        for (station in stations) {
            assertTrue(station.latitude.isFinite() && station.latitude in 24.0..40.5)
            assertTrue(station.longitude.isFinite() && station.longitude in 43.5..64.5)
            assertTrue(station.confidence in 0.0..IranStationsCatalog.OFFLINE_MAX_CONFIDENCE)
            assertEquals(IranStationsCatalog.CATALOG_DATASET_VERSION, station.datasetVersion)
        }
    }

    @Test
    fun `deduplicateAndMergeStations merges duplicate station entities while preserving external URL and ports`() {
        val (prodStations, prodPorts) = IranStationsCatalog.getProductionStationsAndPorts(
            catalogGeneratedAtEpochMs = 1_794_441_600_000L
        )
        val milad = prodStations.first { it.id == "station_tehran_milad" }
        val miladPorts = prodPorts.filter { it.stationId == milad.id }
        val duplicateSurvey = milad.copy(
            id = "station_tehran_milad_survey_dup",
            validationStatus = "UNVERIFIED",
            sourceUrl = null,
            verifiedAt = null,
            confidence = 0.35
        )
        val (mergedStations, mergedPorts) = IranStationsCatalog.deduplicateAndMergeStations(
            stations = listOf(duplicateSurvey, milad),
            ports = miladPorts
        )
        assertEquals(1, mergedStations.size)
        val survivor = mergedStations.first()
        assertEquals("https://meko.ir/stations/mapna_001", survivor.sourceUrl)
        assertNotNull(survivor.verifiedAt)
        assertTrue(mergedPorts.isNotEmpty())
    }
}
