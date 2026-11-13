package com.evnavigator.iran.core.energy.model

import kotlinx.serialization.Serializable

@Serializable
data class EnergyEfficiencyMetrics(
    val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val averageConsumptionWhPer100Km: Double,
    val estimatedRangeKm: Double,
    val energyConsumptionTrend: ConsumptionTrend,
    val efficiencyScore: Double
)

enum class ConsumptionTrend {
    IMPROVING,
    DECREASING,
    STABLE
}