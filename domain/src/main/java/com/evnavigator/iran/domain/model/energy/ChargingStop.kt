package com.evnavigator.iran.domain.model.energy

import kotlinx.serialization.Serializable

@Serializable
data class ChargingStop(
    val id: String,
    val timestamp: Long,
    val location: Location,
    val chargerId: String,
    val chargingDurationMinutes: Double,
    val energyAddedWh: Double,
    val startSocPercent: Double,
    val endSocPercent: Double,
    val chargingCost: Double
)