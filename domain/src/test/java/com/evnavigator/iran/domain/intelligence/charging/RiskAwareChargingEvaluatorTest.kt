package com.evnavigator.iran.domain.intelligence.charging

import org.junit.Assert.*
import org.junit.Test

class RiskAwareChargingEvaluatorTest {

    private val evaluator = RiskAwareChargingEvaluator()

    private fun createStation(
        connectors: List<String> = listOf("CCS2"),
        maxPowerKw: Double = 120.0,
        liveEpochMs: Long? = System.currentTimeMillis(),
        isAvailable: Boolean? = true
    ): CandidateStation {
        return CandidateStation(
            stationId = "mapna_tehran_qom_km45",
            stationName = "Mapna Highway Fast Charger Km 45",
            compatibleConnectors = connectors,
            maxPowerKw = maxPowerKw,
            detourDistanceMeters = 800.0,
            detourDurationSeconds = 120.0,
            distanceAlongRouteMeters = 45_000.0,
            liveStatusLastUpdatedEpochMs = liveEpochMs,
            isReportedAvailable = isAvailable
        )
    }

    @Test
    fun testIncompatibleConnectorRejected() {
        val station = createStation(connectors = listOf("CHAdeMO"))
        val eval = evaluator.evaluateStation(
            candidate = station,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 100.0,
            nominalArrivalSocPercent = 40.0,
            socUncertaintyDeltaPercent = 5.0
        )

        assertFalse("Incompatible connector must not be compatible", eval.isConnectorCompatible)
        assertFalse("Incompatible station cannot be safely reachable", eval.isSafelyReachable)
        assertTrue(eval.recommendationSummary.contains("REJECTED: Incompatible connector"))
    }

    @Test
    fun testMathematicalReachabilityVsSafeReachability() {
        val station = createStation()
        // Nominal is 8%, but uncertainty is ±3%, and minimum reserve is 10%
        // Conservative worst-case arrival is 5%, which is < 10%
        val eval = evaluator.evaluateStation(
            candidate = station,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 100.0,
            nominalArrivalSocPercent = 8.0,
            socUncertaintyDeltaPercent = 3.0,
            minReserveSocPercent = 10.0
        )

        assertTrue("Arrival SOC 8% > 0 is mathematically reachable", eval.isMathematicallyReachable)
        assertFalse("Conservative arrival 5% < 10% is NOT safely reachable", eval.isSafelyReachable)
        assertTrue(eval.riskFactors.any { it.contains("Insufficient safety reserve") })
    }

    @Test
    fun testUnknownLiveStatusNeverClaimsOperational() {
        val stationWithoutLiveStatus = createStation(liveEpochMs = null, isAvailable = null)
        val eval = evaluator.evaluateStation(
            candidate = stationWithoutLiveStatus,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 100.0,
            nominalArrivalSocPercent = 35.0,
            socUncertaintyDeltaPercent = 5.0
        )

        assertEquals(
            "Must be classified as STATIC_VERIFIED_LIVE_UNKNOWN",
            OperationalConfidence.STATIC_VERIFIED_LIVE_UNKNOWN,
            eval.operationalConfidence
        )
        assertTrue(eval.riskFactors.any { it.contains("real-time availability is unconfirmed") })
        assertTrue(eval.recommendationSummary.contains("live status unknown"))
    }

    @Test
    fun testNoBackupStationsTriggersSinglePointOfFailureRisk() {
        val station = createStation()
        val eval = evaluator.evaluateStation(
            candidate = station,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 100.0,
            nominalArrivalSocPercent = 25.0,
            socUncertaintyDeltaPercent = 4.0,
            otherReachableStationsCount = 0
        )

        assertTrue(eval.riskFactors.any { it.contains("Single point of failure") })
    }

    @Test
    fun testVerifiedStationWithBackupsRecommended() {
        val station = createStation()
        val eval = evaluator.evaluateStation(
            candidate = station,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 100.0,
            nominalArrivalSocPercent = 30.0,
            socUncertaintyDeltaPercent = 3.0,
            otherReachableStationsCount = 2
        )

        assertTrue(eval.isSafelyReachable)
        assertEquals(OperationalConfidence.CONFIRMED_OPERATIONAL_LIVE, eval.operationalConfidence)
        assertTrue(eval.recommendationSummary.contains("RECOMMENDED"))
    }
}
