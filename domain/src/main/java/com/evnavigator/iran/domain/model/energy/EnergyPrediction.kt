package com.evnavigator.iran.domain.model.energy

import kotlinx.serialization.Serializable

@Serializable
data class EnergyPrediction(
    val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val predictedEnergyConsumptionWh: Double,
    val confidenceInterval: Double,
    val predictionValidityTime: Long,
    val routeSegmentId: String
)