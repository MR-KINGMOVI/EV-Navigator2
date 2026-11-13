package com.evnavigator.iran.domain.model.trip

import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * V12.5 Extracted Collaborator: Domain value models for [TripPlanningEngine].
 */
data class BatteryStateEstimate(
    val coordinate: GeoCoordinate,
    val stateOfChargePercent: Double,
    val remainingRangeKm: Double,
    val energyConsumedKwh: Double,
    val estimatedArrivalTimestamp: Long? = null
)

data class TripChargingRequirements(
    val minBatteryLevelPercent: Double = 15.0,
    val targetBatteryLevelPercent: Double = 80.0,
    val requiredEnergyKwh: Double = 0.0,
    val estimatedChargingStops: Int = 0
)

data class EnvironmentImpact(
    val co2SavedKg: Double = 0.0,
    val treeEquivalent: Double = 0.0,
    val energyEfficiencyScore: Double = 1.0
)

data class TripChargingInfrastructure(
    val availableStationsCount: Int = 0,
    val fastChargersCount: Int = 0,
    val averagePowerKw: Double = 0.0
)

data class TripWeatherImpact(
    val temperatureCelsius: Double = 25.0,
    val windSpeedKmh: Double = 0.0,
    val precipitation: Boolean = false,
    val rangeImpactPercent: Double = 0.0
)

data class TripTrafficImpact(
    val delayMinutes: Int = 0,
    val congestionLevel: Double = 0.0,
    val extraEnergyConsumedKwh: Double = 0.0
)
