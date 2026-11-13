package com.evnavigator.iran.domain.model.energy

import kotlinx.serialization.Serializable

@Serializable
data class SOCPrediction(
    val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val predictedSOCPercent: Double,
    val confidenceInterval: Double,
    val predictionValidityTime: Long,
    val routeSegmentId: String
)