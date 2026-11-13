package com.evnavigator.iran.core.energy.model

import kotlinx.serialization.Serializable

@Serializable
data class EnergyConsumptionHistory(
    val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val consumptionData: List<EnergyConsumption>
)