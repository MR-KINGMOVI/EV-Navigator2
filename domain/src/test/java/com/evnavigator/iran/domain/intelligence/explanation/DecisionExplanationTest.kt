package com.evnavigator.iran.domain.intelligence.explanation

import org.junit.Assert.*
import org.junit.Test

class DecisionExplanationTest {

    @Test
    fun testConnectorMismatchExplanationIsVerifiedFact() {
        val exp = ExplanationFactory.stationRejected(
            stationId = "st_1",
            stationName = "Tehran Ray Station",
            reasonCode = ReasonCodes.CHARGER_CONNECTOR_MISMATCH,
            details = "GB/T required vs CCS2 offered"
        )

        assertEquals(ExplanationCategory.STATION_REJECTION, exp.category)
        assertEquals(ReasonCodes.CHARGER_CONNECTOR_MISMATCH, exp.reasonCode)
        assertEquals("Physical connector mismatch is a verified fact", EvidenceType.VERIFIED_FACT, exp.evidenceType)
        assertTrue(exp.summaryEn.contains("does not support your vehicle's connector"))
        assertTrue(exp.summaryFa.contains("پشتیبانی نمی‌کند"))
    }

    @Test
    fun testRouteSelectionExplanation() {
        val exp = ExplanationFactory.routeSelected(
            routeId = "route_fast",
            reasonCode = ReasonCodes.ROUTE_FASTER_TIME,
            timeSavedMin = 25,
            energySavedKwh = null
        )

        assertEquals(ExplanationCategory.ROUTE_SELECTION, exp.category)
        assertEquals(ReasonCodes.ROUTE_FASTER_TIME, exp.reasonCode)
        assertEquals(EvidenceType.PHYSICAL_ESTIMATE, exp.evidenceType)
        assertTrue(exp.summaryEn.contains("saved ~25 min"))
        assertTrue(exp.summaryFa.contains("25 دقیقه صرفه‌جویی"))
    }

    @Test
    fun testConfidenceLimitationNeverClaimsVerifiedFact() {
        val exp = ExplanationFactory.confidenceLimitation(
            reasonCode = ReasonCodes.UNCERTAINTY_UNVERIFIED_VEHICLE_SPECS,
            field = "dragCoefficient"
        )

        assertEquals(ExplanationCategory.CONFIDENCE_LIMITATION, exp.category)
        assertEquals(ReasonCodes.UNCERTAINTY_UNVERIFIED_VEHICLE_SPECS, exp.reasonCode)
        assertNotEquals("Unverified spec must never claim to be VERIFIED_FACT", EvidenceType.VERIFIED_FACT, exp.evidenceType)
        assertEquals(EvidenceType.ASSUMED_FALLBACK, exp.evidenceType)
        assertTrue(exp.summaryEn.contains("dragCoefficient"))
        assertTrue(exp.summaryFa.contains("محافظه‌کارانه"))
    }
}
