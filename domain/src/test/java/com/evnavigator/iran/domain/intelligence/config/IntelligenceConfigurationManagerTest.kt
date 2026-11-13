package com.evnavigator.iran.domain.intelligence.config

import com.evnavigator.iran.domain.intelligence.routing.RouteOptimizationObjective
import org.junit.Assert.*
import org.junit.Test

class IntelligenceConfigurationManagerTest {

    private val manager = IntelligenceConfigurationManager()

    @Test
    fun testDefaultConfiguration() {
        val config = manager.configState.value
        assertEquals(10.0, config.minReserveSocPercent, 0.001)
        assertEquals(RouteOptimizationObjective.BALANCED, config.optimizationObjective)
        assertEquals(RiskTolerance.BALANCED, config.riskTolerance)
        assertFalse(config.isPersonalizationEnabled)
        assertTrue(config.isTripHistoryLoggingEnabled)
        assertEquals(30, config.historyRetentionDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidReserveSocTooLowFails() {
        IntelligenceUserConfiguration(minReserveSocPercent = 2.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidReserveSocTooHighFails() {
        IntelligenceUserConfiguration(minReserveSocPercent = 45.0)
    }

    @Test
    fun testReactiveStateUpdates() {
        manager.updateOptimizationObjective(RouteOptimizationObjective.MINIMUM_ENERGY)
        assertEquals(RouteOptimizationObjective.MINIMUM_ENERGY, manager.configState.value.optimizationObjective)

        manager.setPersonalizationEnabled(true)
        assertTrue(manager.configState.value.isPersonalizationEnabled)
    }

    @Test
    fun testConservativeRiskToleranceEnforcesHigherReserve() {
        manager.updateRiskTolerance(RiskTolerance.CONSERVATIVE)
        assertEquals(RiskTolerance.CONSERVATIVE, manager.configState.value.riskTolerance)
        assertTrue(manager.configState.value.minReserveSocPercent >= 15.0)
    }

    @Test
    fun testResetToDefaults() {
        manager.updateOptimizationObjective(RouteOptimizationObjective.MINIMUM_TIME)
        manager.setPersonalizationEnabled(true)
        manager.resetToDefaults()

        assertEquals(RouteOptimizationObjective.BALANCED, manager.configState.value.optimizationObjective)
        assertFalse(manager.configState.value.isPersonalizationEnabled)
    }
}
