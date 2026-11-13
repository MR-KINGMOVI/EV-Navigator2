package com.evnavigator.iran.domain.intelligence.personalization

import com.evnavigator.iran.domain.intelligence.context.DrivingMode

enum class SpeedBand {
    CITY,        // <= 50 km/h
    SUBURBAN,    // 50 - 80 km/h
    HIGHWAY,     // 80 - 110 km/h
    EXPRESSWAY;  // > 110 km/h

    companion object {
        fun fromSpeedKmh(speed: Double): SpeedBand = when {
            speed <= 50.0 -> CITY
            speed <= 80.0 -> SUBURBAN
            speed <= 110.0 -> HIGHWAY
            else -> EXPRESSWAY
        }
    }
}

enum class TemperatureBand {
    FREEZING,    // < 0°C
    COLD,        // 0 - 15°C
    MODERATE,    // 15 - 28°C
    HOT;         // > 28°C

    companion object {
        fun fromTemperatureCelsius(temp: Double): TemperatureBand = when {
            temp < 0.0 -> FREEZING
            temp < 15.0 -> COLD
            temp <= 28.0 -> MODERATE
            else -> HOT
        }
    }
}

/**
 * Aggregated efficiency statistics within a specific driving operating condition.
 */
data class OperatingConditionBucket(
    val speedBand: SpeedBand,
    val temperatureBand: TemperatureBand,
    val drivingMode: DrivingMode,
    val sampleCount: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalEnergyWh: Double = 0.0
) {
    /** Average observed energy consumption in Wh/km */
    val observedWhPerKm: Double?
        get() = if (sampleCount >= 3 && totalDistanceMeters >= 5_000.0) {
            val distanceKm = totalDistanceMeters / 1000.0
            totalEnergyWh / distanceKm
        } else null
}

/**
 * Opt-in personalized efficiency profile for a single vehicle.
 */
data class PersonalizedVehicleProfile(
    val vehicleId: String,
    val isEnabled: Boolean = false,
    val buckets: Map<String, OperatingConditionBucket> = emptyMap(),
    val totalObservedDistanceKm: Double = 0.0,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

/**
 * Manager that handles local, privacy-preserving personalized efficiency profiles.
 */
class PersonalizationManager(
    private val minBucketSamples: Int = 3,
    private val minBucketDistanceMeters: Double = 5_000.0
) {
    private val profiles = mutableMapOf<String, PersonalizedVehicleProfile>()

    fun setPersonalizationEnabled(vehicleId: String, enabled: Boolean) {
        val current = profiles[vehicleId] ?: PersonalizedVehicleProfile(vehicleId = vehicleId)
        profiles[vehicleId] = current.copy(isEnabled = enabled, lastUpdatedEpochMs = System.currentTimeMillis())
    }

    fun getProfile(vehicleId: String): PersonalizedVehicleProfile {
        return profiles[vehicleId] ?: PersonalizedVehicleProfile(vehicleId = vehicleId)
    }

    fun recordTripTelemetry(
        vehicleId: String,
        speedKmh: Double,
        tempCelsius: Double,
        drivingMode: DrivingMode,
        distanceMeters: Double,
        energyWh: Double
    ) {
        val current = profiles[vehicleId] ?: PersonalizedVehicleProfile(vehicleId = vehicleId)
        if (!current.isEnabled) return // strictly opt-in

        val speedBand = SpeedBand.fromSpeedKmh(speedKmh)
        val tempBand = TemperatureBand.fromTemperatureCelsius(tempCelsius)
        val bucketKey = "${speedBand}_${tempBand}_$drivingMode"

        val existingBucket = current.buckets[bucketKey] ?: OperatingConditionBucket(
            speedBand = speedBand,
            temperatureBand = tempBand,
            drivingMode = drivingMode
        )

        val updatedBucket = existingBucket.copy(
            sampleCount = existingBucket.sampleCount + 1,
            totalDistanceMeters = existingBucket.totalDistanceMeters + distanceMeters,
            totalEnergyWh = existingBucket.totalEnergyWh + energyWh
        )

        val updatedBuckets = current.buckets.toMutableMap()
        updatedBuckets[bucketKey] = updatedBucket

        profiles[vehicleId] = current.copy(
            buckets = updatedBuckets,
            totalObservedDistanceKm = current.totalObservedDistanceKm + (distanceMeters / 1000.0),
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    }

    /**
     * Compute personalization adjustment factor (ratio) for given conditions.
     * Returns 1.0 if disabled, no vehicle profile, or insufficient samples.
     */
    fun getEfficiencyMultiplier(
        vehicleId: String,
        speedKmh: Double,
        tempCelsius: Double,
        drivingMode: DrivingMode,
        baselineWhPerKm: Double
    ): Double {
        val profile = profiles[vehicleId] ?: return 1.0
        if (!profile.isEnabled || baselineWhPerKm <= 0.0) return 1.0

        val speedBand = SpeedBand.fromSpeedKmh(speedKmh)
        val tempBand = TemperatureBand.fromTemperatureCelsius(tempCelsius)
        val bucketKey = "${speedBand}_${tempBand}_$drivingMode"

        val bucket = profile.buckets[bucketKey] ?: return 1.0
        val observed = bucket.observedWhPerKm ?: return 1.0

        // Ratio of observed over theoretical baseline, bounded within [0.75, 1.30]
        val ratio = observed / baselineWhPerKm
        return ratio.coerceIn(0.75, 1.30)
    }

    fun resetVehicleProfile(vehicleId: String) {
        profiles.remove(vehicleId)
    }

    fun clearAllProfiles() {
        profiles.clear()
    }
}
