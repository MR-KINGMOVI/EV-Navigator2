package com.evnavigator.iran.domain.model.energy

import kotlinx.serialization.Serializable

@Serializable
data class EnergyResult(
    val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val totalEnergyConsumedWh: Double,
    val totalDistanceKm: Double,
    val averageConsumptionWhPer100Km: Double,
    val estimatedRangeKm: Double,
    val chargingStops: List<ChargingStop>,
    val energyConsumptionHistory: List<EnergyConsumption>
)