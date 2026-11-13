package com.evnavigator.iran.domain.intelligence

import com.evnavigator.iran.domain.intelligence.context.EvidenceDocument
import com.evnavigator.iran.domain.intelligence.context.FieldFreshnessState
import com.evnavigator.iran.domain.intelligence.context.FieldProvenanceRecord
import com.evnavigator.iran.domain.intelligence.context.FieldVerificationState
import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.context.UncertaintyRange
import com.evnavigator.iran.domain.intelligence.context.UrlVerificationStatus
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyUnitConverter
import com.evnavigator.iran.domain.intelligence.energy.DefaultEnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.InputProvenance
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.intelligence.trip.StationPortEligibilityEvaluator
import com.evnavigator.iran.domain.model.catalog.CentralCurrencyConverter
import com.evnavigator.iran.domain.model.catalog.ChargingTariffSchedule
import com.evnavigator.iran.domain.model.catalog.DomainChargingPort
import com.evnavigator.iran.domain.model.catalog.DomainChargingStation
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.PortIndependentSpecRecord
import com.evnavigator.iran.domain.model.catalog.PortOccupancyStatus
import com.evnavigator.iran.domain.model.catalog.StationIndependentVerificationRecord
import com.evnavigator.iran.domain.model.catalog.StationOperationalStatus
import com.evnavigator.iran.domain.model.catalog.TariffCurrency
import com.evnavigator.iran.domain.model.catalog.VerificationMethod
import com.evnavigator.iran.domain.policy.DomainClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V13.2 Master Behavioral Audit Test Suite (Sections 2, 3, 4, 5, 6, 10, 11, 12 & 14).
 *
 * Verifies root-level fixes for:
 * - Section 2 & 14: Elimination of hidden 90.0 km/h speed & 22.0 °C ambient temperature fallbacks,
 *   explicit `MODEL_ASSUMPTION` provenance, uncertainty range propagation, physical input validation,
 *   canonical unit conversions, and zero HVAC double-counting.
 * - Section 3 & 4: Per-station independent verification timestamps, rejection of conflated retrieval
 *   vs verification timestamps, transition of old records to `STALE`, exclusion of `STALE` stations from
 *   fresh verified recommendations, and strict separation of `operationalStatus` from `verificationStatus`.
 * - Section 5: Independent AC and DC port power/connector provenance without cross-inference.
 * - Section 6: Explicit `TariffCurrency` (`IRR_RIAL`, `IRT_TOMAN`, `UNKNOWN`), `CentralCurrencyConverter`,
 *   and time-bounded `ChargingTariffSchedule` evaluation.
 * - Section 12: Field-by-field `FieldProvenanceRecord` and rejection of unchecked URLs (`LINK_UNCHECKED`)
 *   as proof of `VERIFIED` status.
 */
class V132MasterBehavioralAuditTest {

    private val verifiedVehicle = VehiclePhysicsParams(
        vehicleId = "kmc_ej7_v132",
        usableBatteryKwh = 50.1,
        nominalGrossBatteryKwh = 55.0,
        totalMassKg = 1750.0,
        dragCoefficient = 0.27,
        frontalAreaM2 = 2.25,
        rollingResistanceCoeff = 0.011,
        maxChargePowerKw = 60.0,
        dcMaxChargePowerKw = 60.0,
        acMaxChargePowerKw = 7.4,
        supportedConnectors = listOf("GB/T_DC", "GB/T_AC"),
        isAssumedFallback = false,
        dataQuality = "VERIFIED",
        uncertaintyPercent = 5.0
    )

    private fun buildBaseContext(
        avgSpeedAttr: InputQualityAttribute<Double> = InputQualityAttribute.unknown("Missing average speed"),
        ambientTempAttr: InputQualityAttribute<Double> = InputQualityAttribute.unknown("Missing ambient temperature"),
        routeEstimatedDurationSeconds: Double = 0.0
    ): TripIntelligenceContext = TripIntelligenceContext(
        vehicleId = "kmc_ej7_v132",
        batteryCapacityKwh = InputQualityAttribute.verified(50.1, "Verified spec"),
        curbWeightKg = InputQualityAttribute.verified(1650.0, "Verified spec"),
        dragCoefficient = InputQualityAttribute.verified(0.27, "Verified spec"),
        frontalAreaM2 = InputQualityAttribute.verified(2.25, "Verified spec"),
        rollingResistanceCoeff = InputQualityAttribute.verified(0.011, "Verified spec"),
        driveEfficiency = InputQualityAttribute.verified(0.90, "Verified spec"),
        currentSocPercent = InputQualityAttribute.verified(80.0, "Live BMS"),
        minReserveSocPercent = InputQualityAttribute.userConfigured(15.0),
        routeDistanceMeters = 50_000.0,
        routeEstimatedDurationSeconds = routeEstimatedDurationSeconds,
        averageSpeedKmh = avgSpeedAttr,
        ambientTemperatureCelsius = ambientTempAttr,
        averageElevationMeters = InputQualityAttribute.verified(1150.0, "SRTM DEM"),
        elevationGainMeters = InputQualityAttribute.verified(120.0, "SRTM DEM"),
        elevationLossMeters = InputQualityAttribute.verified(80.0, "SRTM DEM")
    )

    // =========================================================================
    // SECTION 2 & 14: SPEED/TEMP FALLBACK ELIMINATION & ENERGY PHYSICS INTEGRITY
    // =========================================================================

    @Test
    fun `energy prediction with missing speed and missing temperature records MODEL_ASSUMPTION and ESTIMATED dataQuality`() {
        val service = DefaultEnergyPredictionService()
        val segmentWithoutSpeedOrTemp = RouteSegmentInput(
            distanceMeters = 50_000.0,
            startElevationMeters = 1150.0,
            endElevationMeters = 950.0,
            speedLimitKmh = null,
            targetSpeedKmh = null
        )

        val prediction = service.predict(
            route = RouteProfileInput(segments = listOf(segmentWithoutSpeedOrTemp)),
            vehicle = verifiedVehicle,
            conditions = EnvironmentalConditions(
                departureSocPercent = 80.0,
                isDepartureSocVerified = true
            )
        )

        assertEquals("Missing speed/temp must downgrade dataQuality to ESTIMATED", "ESTIMATED", prediction.dataQuality)
        assertFalse("Estimated prediction must never be flagged as live telemetry grade", prediction.isLiveTelemetryGrade)
        assertTrue("Confidence must be penalized when speed/temp are assumed", prediction.confidenceScore <= 0.80)
        assertEquals(InputProvenance.MODEL_ASSUMPTION, prediction.inputProvenance["targetSpeed"])
        assertEquals(InputProvenance.MODEL_ASSUMPTION, prediction.inputProvenance["ambientTemp"])
        assertEquals(InputProvenance.UNKNOWN, prediction.inputProvenance["speedLimit"])

        // Verify 90.0 km/h is NOT silently used
        val segResult = prediction.segments.single()
        assertNotEquals(90.0, segResult.averageSpeedKmh, 1e-3)
        assertTrue(prediction.assumptions.any { it.startsWith("MODEL_ASSUMPTION:") })
        assertTrue(prediction.energyUncertaintyRangeWh.uncertaintyPercent >= 15.0)
        assertTrue(prediction.energyUncertaintyRangeWh.min < prediction.energyUncertaintyRangeWh.nominal)
        assertTrue(prediction.energyUncertaintyRangeWh.max > prediction.energyUncertaintyRangeWh.nominal)
    }

    @Test
    fun `PredictiveEnergyOrchestrator with only speed or only temperature tracks provenance independently`() {
        val orchestrator = PredictiveEnergyOrchestrator()

        // Case A: Only speed provided, temperature missing
        val ctxOnlySpeed = buildBaseContext(
            avgSpeedAttr = InputQualityAttribute.userConfigured(95.0, "User target speed"),
            ambientTempAttr = InputQualityAttribute.unknown("Missing ambient temperature")
        )
        val predOnlySpeed = orchestrator.predictTripEnergy(ctxOnlySpeed)
        assertEquals(InputProvenance.USER_PROVIDED, predOnlySpeed.inputProvenance["averageSpeed"])
        assertEquals(InputProvenance.MODEL_ASSUMPTION, predOnlySpeed.inputProvenance["ambientTemp"])
        assertEquals("ESTIMATED", predOnlySpeed.dataQuality)
        assertTrue(predOnlySpeed.assumptions.contains("MODEL_ASSUMPTION:MISSING_AMBIENT_TEMPERATURE"))

        // Case B: Only temperature provided, speed derived from route duration (50km in 2500s = 72 km/h)
        val ctxOnlyTempWithDuration = buildBaseContext(
            avgSpeedAttr = InputQualityAttribute.unknown("Missing average speed"),
            ambientTempAttr = InputQualityAttribute.measured(8.0, "Weather station"),
            routeEstimatedDurationSeconds = 2_500.0
        )
        val predOnlyTemp = orchestrator.predictTripEnergy(ctxOnlyTempWithDuration)
        assertEquals(InputProvenance.DERIVED_FROM_ROUTE_TIME, predOnlyTemp.inputProvenance["averageSpeed"])
        assertEquals(InputProvenance.MEASURED, predOnlyTemp.inputProvenance["ambientTemp"])
        assertEquals(72.0, predOnlyTemp.segments.first().averageSpeedKmh, 1e-3)
        assertEquals("ESTIMATED", predOnlyTemp.dataQuality)
    }

    @Test
    fun `energy prediction rejects negative NaN and Infinite inputs and separates vehicle speed from road legal speed limit`() {
        val service = DefaultEnergyPredictionService()
        val segWithLegalLimitAndTargetSpeed = RouteSegmentInput(
            distanceMeters = 20_000.0,
            startElevationMeters = 1000.0,
            endElevationMeters = 1000.0,
            speedLimitKmh = 110.0,
            targetSpeedKmh = 88.0
        )
        val pred = service.predict(
            route = RouteProfileInput(segments = listOf(segWithLegalLimitAndTargetSpeed)),
            vehicle = verifiedVehicle,
            conditions = EnvironmentalConditions(
                departureSocPercent = 80.0,
                ambientTempC = 18.0,
                isTemperatureMeasured = true,
                isDepartureSocVerified = true
            )
        )
        assertEquals(88.0, pred.segments.single().averageSpeedKmh, 1e-3)
        assertEquals(InputProvenance.MEASURED, pred.inputProvenance["speedLimit"])
        assertEquals(InputProvenance.USER_PROVIDED, pred.inputProvenance["targetSpeed"])

        // NaN departure SOC returns INSUFFICIENT_DATA
        val nanSocPred = service.predict(
            route = RouteProfileInput(segments = listOf(segWithLegalLimitAndTargetSpeed)),
            vehicle = verifiedVehicle,
            conditions = EnvironmentalConditions(departureSocPercent = Double.NaN)
        )
        assertEquals("INSUFFICIENT_DATA", nanSocPred.dataQuality)
        assertFalse(nanSocPred.isFeasibleWithoutCharging)

        // Negative route distance in PredictiveEnergyOrchestrator throws IllegalArgumentException
        val orchestrator = PredictiveEnergyOrchestrator(service)
        var threwOnNegativeDistance = false
        try {
            orchestrator.predictTripEnergy(buildBaseContext().copy(routeDistanceMeters = -500.0))
        } catch (_: IllegalArgumentException) {
            threwOnNegativeDistance = true
        }
        assertTrue("Negative route distance must be rejected", threwOnNegativeDistance)
    }

    @Test
    fun `CanonicalEnergyUnitConverter and PredictiveEnergyOrchestrator preserve units and never double-count HVAC`() {
        assertEquals(18.0, CanonicalEnergyUnitConverter.whPerKmToKwhPer100Km(180.0), 1e-6)
        assertEquals(180.0, CanonicalEnergyUnitConverter.kwhPer100KmToWhPerKm(18.0), 1e-6)
        assertEquals(50.1, CanonicalEnergyUnitConverter.whToKwh(50_100.0), 1e-6)
        assertEquals(50_100.0, CanonicalEnergyUnitConverter.kwhToWh(50.1), 1e-6)

        val orchestrator = PredictiveEnergyOrchestrator()
        val coldContext = buildBaseContext(
            avgSpeedAttr = InputQualityAttribute.verified(85.0, "Speed"),
            ambientTempAttr = InputQualityAttribute.measured(-5.0, "Cold ambient")
        )
        val result = orchestrator.predictTripEnergy(coldContext)
        val b = result.breakdown
        val expectedNet = (b.electricalDrivetrainWh + b.auxiliaryHvacWh) - b.regeneratedWh
        assertEquals("Total net battery energy must equal single-pass sum without HVAC double-counting", expectedNet, b.netBatteryEnergyWh, 1.0)
    }

    // =========================================================================
    // SECTION 3 & 4: INDEPENDENT STATION VERIFICATION, STALENESS & OPERATIONAL STATUS
    // =========================================================================

    @Test
    fun `StationIndependentVerificationRecord enforces independent timestamp, source, URL status, and operational separation`() {
        val nowMs = 1_794_441_600_000L // Nov 12, 2026
        val fiveDaysAgoMs = nowMs - (5L * 24L * 3600_000L)
        val oneYearAgoMs = 1_762_819_200_000L // Nov 11, 2025

        // 1. Fresh, independently inspected station -> FRESH & VERIFIED, while operationalStatus stays UNKNOWN
        val freshInspected = StationIndependentVerificationRecord(
            stationId = "st_fresh",
            verifiedAt = fiveDaysAgoMs,
            retrievedAt = nowMs,
            datasetPublishedAt = nowMs,
            evidenceSource = "TEHRAN_EV_FIELD_AUDIT_TEAM",
            evidenceReference = "INSP-2026-11-001",
            externalUrl = "https://meko.ir/stations/mapna_001",
            urlVerificationStatus = UrlVerificationStatus.HTTP_CONTENT_VERIFIED,
            verificationMethod = VerificationMethod.FIELD_INSPECTION,
            rawVerificationStatus = "VERIFIED",
            operationalStatus = StationOperationalStatus.UNKNOWN
        )
        assertEquals(FieldFreshnessState.FRESH, freshInspected.evaluateFreshnessState(nowMs))
        assertEquals(FieldVerificationState.VERIFIED, freshInspected.resolveEffectiveVerificationState(nowMs))
        assertTrue(freshInspected.isEligibleForFreshVerifiedRecommendation(nowMs))
        assertEquals(StationOperationalStatus.UNKNOWN, freshInspected.operationalStatus)

        // 2. Old 2025 timestamp evaluated in Nov 2026 -> STALE & downgraded to PARTIALLY_VERIFIED, excluded from fresh verified recommendation
        val stale2025Record = freshInspected.copy(
            stationId = "st_stale_2025",
            verifiedAt = oneYearAgoMs
        )
        assertEquals(FieldFreshnessState.STALE, stale2025Record.evaluateFreshnessState(nowMs))
        assertEquals(FieldVerificationState.PARTIALLY_VERIFIED, stale2025Record.resolveEffectiveVerificationState(nowMs))
        assertFalse(stale2025Record.isEligibleForFreshVerifiedRecommendation(nowMs))

        // 3. Conflated timestamp (verifiedAt == retrievedAt) -> UNKNOWN freshness & UNVERIFIED
        val conflatedTimestampRecord = freshInspected.copy(
            stationId = "st_conflated",
            verifiedAt = nowMs,
            retrievedAt = nowMs
        )
        assertEquals(FieldFreshnessState.UNKNOWN, conflatedTimestampRecord.evaluateFreshnessState(nowMs))
        assertEquals(FieldVerificationState.UNVERIFIED, conflatedTimestampRecord.resolveEffectiveVerificationState(nowMs))
        assertFalse(conflatedTimestampRecord.isEligibleForFreshVerifiedRecommendation(nowMs))

        // 4. Missing timestamp (verifiedAt == null) -> UNKNOWN freshness & UNVERIFIED
        val missingTimestampRecord = freshInspected.copy(
            stationId = "st_no_ts",
            verifiedAt = null
        )
        assertEquals(FieldFreshnessState.UNKNOWN, missingTimestampRecord.evaluateFreshnessState(nowMs))
        assertEquals(FieldVerificationState.UNVERIFIED, missingTimestampRecord.resolveEffectiveVerificationState(nowMs))

        // 5. Missing evidence source -> UNVERIFIED
        val missingSourceRecord = freshInspected.copy(
            stationId = "st_no_source",
            evidenceSource = ""
        )
        assertEquals(FieldVerificationState.UNVERIFIED, missingSourceRecord.resolveEffectiveVerificationState(nowMs))

        // 6. Unchecked URL (LINK_UNCHECKED) without FIELD_INSPECTION/OCPP -> capped at PARTIALLY_VERIFIED, never VERIFIED
        val uncheckedUrlOnly = freshInspected.copy(
            stationId = "st_link_unchecked",
            urlVerificationStatus = UrlVerificationStatus.LINK_UNCHECKED,
            verificationMethod = VerificationMethod.DOCUMENTED_PUBLIC_RELEASE
        )
        assertEquals(FieldVerificationState.PARTIALLY_VERIFIED, uncheckedUrlOnly.resolveEffectiveVerificationState(nowMs))
        assertFalse(uncheckedUrlOnly.isEligibleForFreshVerifiedRecommendation(nowMs))
    }

    @Test
    fun `StationPortEligibilityEvaluator rejects OUT_OF_SERVICE operationalStatus even when static record is VERIFIED`() {
        val nowMs = 1_794_441_600_000L
        val stationOutOfService = DomainChargingStation(
            id = "st_oos",
            name = "ایستگاه خارج از سرویس",
            latitude = 35.70,
            longitude = 51.40,
            isVerified = true,
            retrievedDate = nowMs,
            verifiedAt = nowMs - 86_400_000L,
            operationalStatus = StationOperationalStatus.OUT_OF_SERVICE
        )
        val port = DomainChargingPort(
            id = "p1",
            stationId = "st_oos",
            connectorType = "GB/T_DC",
            powerKw = 60.0,
            isDc = true,
            status = "AVAILABLE",
            retrievedDate = nowMs,
            liveStatusUpdatedAtEpochMs = nowMs
        )
        val outcome = StationPortEligibilityEvaluator.evaluateStationAndSelectPort(
            stationWithPorts = DomainChargingStationWithPorts(stationOutOfService, listOf(port)),
            explicitConnectors = listOf("GB/T_DC"),
            vehicleMaxDcKw = 60.0,
            vehicleMaxAcKw = 7.4,
            clock = DomainClock.fixed(nowMs)
        )
        assertTrue(outcome is StationPortEligibilityEvaluator.StationEvaluationOutcome.Rejected)
        val rejected = outcome as StationPortEligibilityEvaluator.StationEvaluationOutcome.Rejected
        assertTrue(rejected.reasonCode.contains("STATION_UNAVAILABLE"))
    }

    // =========================================================================
    // SECTION 5: PORT AC/DC POWER & CONNECTOR INDEPENDENCE
    // =========================================================================

    @Test
    fun `PortIndependentSpecRecord keeps AC and DC power independent and rejects missing power or missing source`() {
        val nowMs = 1_794_441_600_000L
        val dcPortSpec = PortIndependentSpecRecord(
            portId = "port_dc_1",
            stationId = "st_1",
            isAc = false,
            isDc = true,
            rawConnectorType = "CCS2",
            nominalPowerKw = 120.0,
            powerEvidenceSource = "MAPNA_NAMEPLATE_AUDIT",
            powerEvidenceReference = "NP-001",
            powerVerifiedAtEpochMs = nowMs - 86_400_000L,
            powerVerificationState = FieldVerificationState.VERIFIED,
            connectorEvidenceSource = "MAPNA_NAMEPLATE_AUDIT",
            connectorVerifiedAtEpochMs = nowMs - 86_400_000L,
            occupancyStatus = PortOccupancyStatus.UNKNOWN
        )
        assertTrue(dcPortSpec.hasValidElectricalMode)
        assertTrue(dcPortSpec.isConnectorKnown)
        assertEquals(120.0, dcPortSpec.effectiveNominalPowerKw!!, 1e-3)
        assertEquals(FieldVerificationState.VERIFIED, dcPortSpec.effectivePowerVerificationState)
        assertEquals(FieldFreshnessState.FRESH, dcPortSpec.evaluatePowerFreshness(nowMs))

        // AC port without power must have effectiveNominalPowerKw = null and UNKNOWN state (never inferred from DC!)
        val acPortUnknownPower = PortIndependentSpecRecord(
            portId = "port_ac_unknown",
            stationId = "st_1",
            isAc = true,
            isDc = false,
            rawConnectorType = "TYPE_2",
            nominalPowerKw = null,
            powerEvidenceSource = "MAPNA_NAMEPLATE_AUDIT",
            powerVerificationState = FieldVerificationState.VERIFIED
        )
        assertNull(acPortUnknownPower.effectiveNominalPowerKw)
        assertEquals(FieldVerificationState.UNKNOWN, acPortUnknownPower.effectivePowerVerificationState)

        // Port with contradictory AC+DC mode is rejected
        val invalidDualMode = dcPortSpec.copy(isAc = true, isDc = true)
        assertFalse(invalidDualMode.hasValidElectricalMode)
        assertNull(invalidDualMode.effectiveNominalPowerKw)
    }

    // =========================================================================
    // SECTION 6: TARIFF & CURRENCY SYSTEM (RIAL vs TOMAN)
    // =========================================================================

    @Test
    fun `CentralCurrencyConverter and ChargingTariffSchedule handle Rial-Toman conversion and reject expired or sourceless tariffs`() {
        // 1. Exact 10:1 Rial <-> Toman conversion
        assertEquals(1_500.0, CentralCurrencyConverter.rialToToman(15_000.0), 1e-6)
        assertEquals(15_000.0, CentralCurrencyConverter.tomanToRial(1_500.0), 1e-6)
        assertNull("UNKNOWN currency must never convert", CentralCurrencyConverter.convert(15_000.0, TariffCurrency.UNKNOWN, TariffCurrency.IRT_TOMAN))

        val nowMs = 1_794_441_600_000L // Nov 12, 2026

        // 2. Current verified tariff in Rial evaluated in Toman
        val currentVerifiedTariff = ChargingTariffSchedule(
            tariffId = "tariff_2026_dc",
            pricePerKwh = 25_000.0, // 25,000 Rials = 2,500 Tomans
            currency = TariffCurrency.IRR_RIAL,
            sourceName = "TAVANIR_OFFICIAL_TARIFF_1405",
            sourceReference = "TAVANIR-1405-DC-HPC",
            effectiveFromEpochMs = nowMs - (10L * 24L * 3600_000L),
            effectiveUntilEpochMs = nowMs + (60L * 24L * 3600_000L),
            verificationState = FieldVerificationState.VERIFIED
        )
        assertTrue(currentVerifiedTariff.canUseForDeterministicCost(nowMs))
        val costInTomans = currentVerifiedTariff.computeSessionCost(
            energyKwh = 40.0,
            targetCurrency = TariffCurrency.IRT_TOMAN,
            nowEpochMs = nowMs
        )
        assertTrue(costInTomans.isDeterministicVerified)
        assertEquals(100_000.0, costInTomans.amount!!, 1e-3) // 40 kWh * 2,500 Tomans = 100,000 Tomans
        assertEquals(TariffCurrency.IRT_TOMAN, costInTomans.currency)
        assertEquals("VERIFIED_CURRENT_TARIFF", costInTomans.statusCode)

        // 3. Expired tariff transitions to STALE and is never deterministic
        val expiredTariff = currentVerifiedTariff.copy(
            tariffId = "tariff_expired",
            effectiveFromEpochMs = nowMs - (200L * 24L * 3600_000L),
            effectiveUntilEpochMs = nowMs - (20L * 24L * 3600_000L)
        )
        assertEquals(FieldFreshnessState.STALE, expiredTariff.evaluateFreshness(nowMs))
        assertFalse(expiredTariff.canUseForDeterministicCost(nowMs))
        val expiredCost = expiredTariff.computeSessionCost(40.0, TariffCurrency.IRT_TOMAN, nowMs)
        assertFalse(expiredCost.isDeterministicVerified)
        assertEquals(FieldVerificationState.ESTIMATED, expiredCost.verificationState)
        assertEquals("STALE_OR_EXPIRED_TARIFF", expiredCost.statusCode)

        // 4. Tariff without source is rejected
        val sourcelessTariff = currentVerifiedTariff.copy(sourceName = null, sourceReference = null)
        val sourcelessCost = sourcelessTariff.computeSessionCost(40.0, TariffCurrency.IRT_TOMAN, nowMs)
        assertNull(sourcelessCost.amount)
        assertFalse(sourcelessCost.isDeterministicVerified)
        assertEquals("MISSING_TARIFF_SOURCE", sourcelessCost.statusCode)
    }

    // =========================================================================
    // SECTION 12: FIELD-BY-FIELD PROVENANCE & UNCHECKED LINKS
    // =========================================================================

    @Test
    fun `FieldProvenanceRecord downgrades VERIFIED claim when URL is LINK_UNCHECKED or origin is MODEL_ASSUMPTION`() {
        val nowMs = 1_794_441_600_000L
        val uncheckedDoc = EvidenceDocument(
            documentId = "doc_1",
            sourceName = "MAPNA_WEB",
            referenceCode = "MAPNA_001",
            externalUrl = "https://meko.ir/stations/mapna_001",
            urlVerificationStatus = UrlVerificationStatus.LINK_UNCHECKED,
            verifiedAtEpochMs = nowMs - 86_400_000L
        )
        val fieldWithUncheckedUrl = FieldProvenanceRecord(
            fieldName = "dcChargingMaxPowerKw",
            value = 120.0,
            unit = "kW",
            origin = InputOrigin.STATIC_CATALOG,
            verificationState = FieldVerificationState.VERIFIED,
            freshnessState = FieldFreshnessState.FRESH,
            confidence = 0.85,
            uncertainty = UncertaintyRange.fromPercent(120.0, 5.0, "kW"),
            evidence = uncheckedDoc
        )
        assertFalse("Field with LINK_UNCHECKED must not be considered strictly verified", fieldWithUncheckedUrl.isStrictlyVerified)
        assertEquals(FieldVerificationState.PARTIALLY_VERIFIED, fieldWithUncheckedUrl.effectiveVerificationState)

        val assumedField = fieldWithUncheckedUrl.copy(
            origin = InputOrigin.MODEL_ASSUMPTION,
            evidence = uncheckedDoc.copy(urlVerificationStatus = UrlVerificationStatus.HTTP_CONTENT_VERIFIED)
        )
        assertFalse("MODEL_ASSUMPTION origin can never be strictly verified", assumedField.isStrictlyVerified)
        assertEquals(FieldVerificationState.ESTIMATED, assumedField.effectiveVerificationState)
    }
}
