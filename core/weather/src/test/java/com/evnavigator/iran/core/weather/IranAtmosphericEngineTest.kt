package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class IranAtmosphericEngineTest {

    @Test
    fun `rangeReductionPercent is continuous across temperature boundaries`() {
        val f12 = IranAtmosphericEngine.rangeReductionPercent(12.0)
        assertEquals(8.4, f12, 0.001)

        assertEquals(32.0, IranAtmosphericEngine.rangeReductionPercent(-10.0), 0.001)
        assertEquals(22.0, IranAtmosphericEngine.rangeReductionPercent(0.0), 0.001)
        assertEquals(12.0, IranAtmosphericEngine.rangeReductionPercent(10.0), 0.001)
        assertEquals(3.0, IranAtmosphericEngine.rangeReductionPercent(15.0), 0.001)
        assertEquals(3.0, IranAtmosphericEngine.rangeReductionPercent(28.0), 0.001)
        assertEquals(10.0, IranAtmosphericEngine.rangeReductionPercent(40.0), 0.001)
        assertEquals(20.0, IranAtmosphericEngine.rangeReductionPercent(45.0), 0.001)

        assertEquals(32.0, IranAtmosphericEngine.rangeReductionPercent(-25.0), 0.001)
        assertEquals(20.0, IranAtmosphericEngine.rangeReductionPercent(55.0), 0.001)
        assertEquals(10.0, IranAtmosphericEngine.rangeReductionPercent(Double.NaN), 0.001)

        var t = -15.0
        while (t <= 50.0) {
            val delta = abs(IranAtmosphericEngine.rangeReductionPercent(t + 0.01) - IranAtmosphericEngine.rangeReductionPercent(t))
            assertTrue(delta < 0.05, "Discontinuity detected at t=$t (delta=$delta)")
            t += 0.25
        }
    }

    @Test
    fun `classifyClimateZone accurately classifies Iranian cities`() {
        val bandarAbbas = IranAtmosphericEngine.classifyClimateZone(latitude = 27.18, longitude = 56.27, elevationMeters = 9.0)
        assertEquals(IranAtmosphericEngine.IranClimateZone.PERSIAN_GULF_HYPER_ARID_HOT, bandarAbbas)

        val rasht = IranAtmosphericEngine.classifyClimateZone(latitude = 37.28, longitude = 49.58, elevationMeters = 5.0)
        assertEquals(IranAtmosphericEngine.IranClimateZone.CASPIAN_HUMID_SUBTROPICAL, rasht)

        val tehran = IranAtmosphericEngine.classifyClimateZone(latitude = 35.70, longitude = 51.34, elevationMeters = 1200.0)
        assertEquals(IranAtmosphericEngine.IranClimateZone.CENTRAL_PLATEAU_SEMI_ARID, tehran)

        val shiraz = IranAtmosphericEngine.classifyClimateZone(latitude = 29.63, longitude = 52.54, elevationMeters = 1540.0)
        assertEquals(IranAtmosphericEngine.IranClimateZone.CENTRAL_PLATEAU_SEMI_ARID, shiraz)

        val kerman = IranAtmosphericEngine.classifyClimateZone(latitude = 30.28, longitude = 57.08, elevationMeters = 1750.0)
        assertEquals(IranAtmosphericEngine.IranClimateZone.CENTRAL_PLATEAU_SEMI_ARID, kerman)

        val tabriz = IranAtmosphericEngine.classifyClimateZone(latitude = 38.07, longitude = 46.30, elevationMeters = 1360.0)
        assertEquals(IranAtmosphericEngine.IranClimateZone.ZAGROS_ALBORZ_ALPINE, tabriz)
    }

    @Test
    fun `computeWindComponentsKmh honors METEOROLOGICAL_FROM vs VECTOR_TO conventions`() {
        // Vehicle heading North (0 deg), wind 30 km/h FROM North (0 deg) => +30 km/h headwind
        val (headFromNorth, crossFromNorth) = IranAtmosphericEngine.computeWindComponentsKmh(
            windSpeedKmh = 30.0,
            windDirectionDegrees = 0.0,
            vehicleHeadingDegrees = 0.0,
            convention = IranAtmosphericEngine.WindDirectionConvention.METEOROLOGICAL_FROM
        )
        assertEquals(30.0, headFromNorth, 1e-4)
        assertEquals(0.0, crossFromNorth, 1e-4)

        // Vehicle heading North (0 deg), wind 30 km/h FROM South (180 deg) => -30 km/h tailwind
        val (tailFromSouth, _) = IranAtmosphericEngine.computeWindComponentsKmh(
            windSpeedKmh = 30.0,
            windDirectionDegrees = 180.0,
            vehicleHeadingDegrees = 0.0,
            convention = IranAtmosphericEngine.WindDirectionConvention.METEOROLOGICAL_FROM
        )
        assertEquals(-30.0, tailFromSouth, 1e-4)

        // VECTOR_TO: wind vector moving TOWARD North (0 deg) while vehicle drives North (0 deg) => -30 km/h tailwind
        val (vectorTailwind, _) = IranAtmosphericEngine.computeWindComponentsKmh(
            windSpeedKmh = 30.0,
            windDirectionDegrees = 0.0,
            vehicleHeadingDegrees = 0.0,
            convention = IranAtmosphericEngine.WindDirectionConvention.VECTOR_TO
        )
        assertEquals(-30.0, vectorTailwind, 1e-4)
    }

    @Test
    fun `evaluateAtmosphericImpact does not fabricate elevation when elevationMeters is null`() {
        val state = IranAtmosphericEngine.evaluateAtmosphericImpact(
            coordinate = GeoCoordinate(35.70, 51.34),
            elevationMeters = null,
            temperatureCelsius = 5.0,
            relativeHumidityPercent = 40.0,
            windSpeedKmh = 20.0,
            windDirectionDegrees = 0.0,
            vehicleHeadingDegrees = 0.0,
            vehicleSpeedKmh = 100.0
        )
        assertNull(state.elevationMeters)
        assertFalse(state.isElevationVerified)
        assertTrue(state.advisoriesFa.any { it.contains("ارتفاع دقیق مسیر نامشخص") })
    }
}
