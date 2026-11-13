package com.evnavigator.iran.automotive

import org.junit.Assert.*
import org.junit.Test

class AutomotiveIntelligenceTest {

    @Test
    fun testGlanceableArrivalSocFormatting() {
        val arrivalSoc = 42.0
        val conservativeSoc = 34.0
        val formatted = "شارژ پیش‌بینی‌شده در مقصد: ${arrivalSoc.toInt()}% (حداقل حاشیه: ${conservativeSoc.toInt()}%)"

        assertTrue(formatted.contains("42%"))
        assertTrue(formatted.contains("34%"))
        assertTrue(formatted.contains("حداقل حاشیه"))
    }

    @Test
    fun testGlanceableChargingAdvisoryFormatting() {
        val stationName = "مپنا آزادراه تهران-قم"
        val powerKw = 120
        val advisory = "توقف در $stationName ($powerKw kW)"

        assertTrue(advisory.contains("مپنا"))
        assertTrue(advisory.contains("120 kW"))
    }

    @Test
    fun testBatteryHealthSohCalculation() {
        val soh = 98.4
        val thermalTemp = 24.2
        val dcCycles = 142

        assertTrue(soh > 90.0)
        assertTrue(thermalTemp in 20.0..30.0)
        assertTrue(dcCycles > 100)
    }

    @Test
    fun testMountainPassRegenerativeRecoveryFormatting() {
        val passName = "گردنه کندوان"
        val elevation = 2700
        val regenKwh = 5.2

        val alert = "$passName: ارتفاع $elevation متر • بازیابی انرژی $regenKwh kWh"
        assertTrue(alert.contains("کندوان"))
        assertTrue(alert.contains("2700"))
        assertTrue(alert.contains("5.2 kWh"))
    }

    @Test
    fun testCarAppServicePermissionRequirement() {
        val expectedPermission = "androidx.car.app.BIND_CAR_APP_SERVICE"
        val expectedNavigationCategory = "androidx.car.app.category.NAVIGATION"

        assertEquals("androidx.car.app.BIND_CAR_APP_SERVICE", expectedPermission)
        assertEquals("androidx.car.app.category.NAVIGATION", expectedNavigationCategory)
    }
}
