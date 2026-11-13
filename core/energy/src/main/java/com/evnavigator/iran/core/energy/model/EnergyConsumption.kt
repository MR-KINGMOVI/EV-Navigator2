package com.evnavigator.iran.core.energy.model

import kotlinx.serialization.Serializable

@Serializable
data class EnergyConsumption(
    val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val distanceKm: Double,
    val energyUsedWh: Double,
    val averageSpeedKmh: Double,
    val roadType: RoadType,
    val weatherConditions: WeatherConditions,
    val elevationChangeM: Double,
    val chargingSessionId: String? = null
)