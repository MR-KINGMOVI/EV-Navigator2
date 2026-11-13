package com.evnavigator.iran.domain.model.energy

import kotlinx.serialization.Serializable

@Serializable
data class EnergyConsumptionHistory(
    val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val consumptionData: List<EnergyConsumption>
)