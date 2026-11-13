package com.evnavigator.iran.domain.intelligence.config

import com.evnavigator.iran.domain.intelligence.routing.RouteOptimizationObjective
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RiskTolerance {
    CONSERVATIVE, // Requires >= 15% reserve, verified chargers with backups
    BALANCED,     // Requires >= 10% reserve, standard trade-offs
    AGGRESSIVE    // Allows 5% reserve, prioritizes speed over redundancy
}

/**
 * Validated, reactive intelligence configuration model.
 */
data class IntelligenceUserConfiguration(
    val minReserveSocPercent: Double = 10.0,
    val optimizationObjective: RouteOptimizationObjective = RouteOptimizationObjective.BALANCED,
    val riskTolerance: RiskTolerance = RiskTolerance.BALANCED,
    val isPersonalizationEnabled: Boolean = false,
    val isTripHistoryLoggingEnabled: Boolean = true,
    val historyRetentionDays: Int = 30,
    val preferOfflineDatasets: Boolean = false,
    val showDiagnosticExplanations: Boolean = true
) {
    init {
        require(minReserveSocPercent in 5.0..30.0) {
            "Minimum reserve SOC must be between 5.0% and 30.0%, was $minReserveSocPercent"
        }
        require(historyRetentionDays in 1..365) {
            "History retention days must be between 1 and 365, was $historyRetentionDays"
        }
    }
}

/**
 * Manager providing reactive state flow and real-time propagation of configuration changes.
 */
class IntelligenceConfigurationManager(
    initialConfig: IntelligenceUserConfiguration = IntelligenceUserConfiguration()
) {
    private val _configState = MutableStateFlow(initialConfig)
    val configState: StateFlow<IntelligenceUserConfiguration> = _configState.asStateFlow()

    fun updateConfiguration(newConfig: IntelligenceUserConfiguration) {
        _configState.value = newConfig
    }

    fun updateReserveSoc(reservePercent: Double) {
        val current = _configState.value
        _configState.value = current.copy(minReserveSocPercent = reservePercent)
    }

    fun updateOptimizationObjective(objective: RouteOptimizationObjective) {
        val current = _configState.value
        _configState.value = current.copy(optimizationObjective = objective)
    }

    fun updateRiskTolerance(tolerance: RiskTolerance) {
        val current = _configState.value
        val adjustedReserve = when (tolerance) {
            RiskTolerance.CONSERVATIVE -> current.minReserveSocPercent.coerceAtLeast(15.0)
            RiskTolerance.BALANCED -> 10.0
            RiskTolerance.AGGRESSIVE -> 5.0
        }
        _configState.value = current.copy(
            riskTolerance = tolerance,
            minReserveSocPercent = adjustedReserve
        )
    }

    fun setPersonalizationEnabled(enabled: Boolean) {
        val current = _configState.value
        _configState.value = current.copy(isPersonalizationEnabled = enabled)
    }

    fun setTripHistoryLoggingEnabled(enabled: Boolean) {
        val current = _configState.value
        _configState.value = current.copy(isTripHistoryLoggingEnabled = enabled)
    }

    fun resetToDefaults() {
        _configState.value = IntelligenceUserConfiguration()
    }
}
