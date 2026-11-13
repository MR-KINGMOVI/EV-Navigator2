package com.evnavigator.iran.domain.intelligence.calibration

import kotlin.math.abs

/**
 * A recorded observation comparing predicted vs physically measured trip energy.
 */
data class TripEnergyObservation(
    val tripId: String,
    val vehicleId: String,
    val distanceMeters: Double,
    val predictedWh: Double,
    val measuredWh: Double,
    val averageSpeedKmh: Double,
    val ambientTempCelsius: Double,
    val timestampEpochMs: Long,
    val isVerifiedTelemetry: Boolean = true
) {
    init {
        require(distanceMeters > 0) { "Distance must be positive." }
        require(predictedWh > 0) { "Predicted energy must be positive." }
        require(measuredWh > 0) { "Measured energy must be positive." }
    }

    /** Ratio of actual measured energy over predicted energy */
    val consumptionRatio: Double get() = measuredWh / predictedWh
}

/**
 * Safe, bounded calibration state for a specific vehicle.
 */
data class VehicleCalibrationState(
    val vehicleId: String,
    val correctionMultiplier: Double,
    val eligibleSampleCount: Int,
    val isCalibrated: Boolean,
    val lastUpdatedEpochMs: Long,
    val excludedOutlierCount: Int = 0
) {
    companion object {
        fun defaultFor(vehicleId: String): VehicleCalibrationState =
            VehicleCalibrationState(
                vehicleId = vehicleId,
                correctionMultiplier = 1.0,
                eligibleSampleCount = 0,
                isCalibrated = false,
                lastUpdatedEpochMs = System.currentTimeMillis()
            )
    }
}

/**
 * Adaptive calibration engine that computes safe, bounded efficiency adjustments
 * from actual historical trip measurements without altering canonical vehicle specs.
 */
class EnergyCalibrationEngine(
    private val minSamplesThreshold: Int = 3,
    private val minTripDistanceMeters: Double = 5_000.0, // Ignore micro-trips < 5km
    private val minMultiplier: Double = 0.80, // Max 20% more efficient
    private val maxMultiplier: Double = 1.25  // Max 25% higher consumption
) {
    private val vehicleObservations = mutableMapOf<String, MutableList<TripEnergyObservation>>()
    private val vehicleCalibrations = mutableMapOf<String, VehicleCalibrationState>()

    /**
     * Ingest a new trip observation for a vehicle.
     */
    fun recordObservation(observation: TripEnergyObservation): VehicleCalibrationState {
        // Enforce telemetry verification and minimum distance
        if (!observation.isVerifiedTelemetry || observation.distanceMeters < minTripDistanceMeters) {
            return getCalibration(observation.vehicleId)
        }

        val list = vehicleObservations.getOrPut(observation.vehicleId) { mutableListOf() }
        // Avoid duplicate trip IDs
        if (list.none { it.tripId == observation.tripId }) {
            list.add(observation)
        }

        return recalculate(observation.vehicleId)
    }

    /**
     * Retrieve the current calibration state for a vehicle.
     */
    fun getCalibration(vehicleId: String): VehicleCalibrationState {
        return vehicleCalibrations[vehicleId] ?: VehicleCalibrationState.defaultFor(vehicleId)
    }

    /**
     * Clear all recorded observations and reset calibration for a vehicle.
     */
    fun resetCalibration(vehicleId: String) {
        vehicleObservations.remove(vehicleId)
        vehicleCalibrations.remove(vehicleId)
    }

    /**
     * Apply calibration multiplier to a baseline predicted energy consumption.
     */
    fun applyCalibration(baselineWh: Double, vehicleId: String): Double {
        val state = getCalibration(vehicleId)
        return baselineWh * state.correctionMultiplier
    }

    private fun recalculate(vehicleId: String): VehicleCalibrationState {
        val samples = vehicleObservations[vehicleId] ?: return VehicleCalibrationState.defaultFor(vehicleId)

        if (samples.size < minSamplesThreshold) {
            val state = VehicleCalibrationState(
                vehicleId = vehicleId,
                correctionMultiplier = 1.0,
                eligibleSampleCount = samples.size,
                isCalibrated = false,
                lastUpdatedEpochMs = System.currentTimeMillis()
            )
            vehicleCalibrations[vehicleId] = state
            return state
        }

        // Outlier rejection using median absolute deviation
        val ratios = samples.map { it.consumptionRatio }.sorted()
        val medianRatio = ratios[ratios.size / 2]

        // Reject extreme outliers that deviate by > 40% from median (e.g. towing trailers or open windows)
        val filteredRatios = ratios.filter { abs(it - medianRatio) <= 0.40 }
        val outlierCount = ratios.size - filteredRatios.size

        if (filteredRatios.size < minSamplesThreshold) {
            val state = VehicleCalibrationState(
                vehicleId = vehicleId,
                correctionMultiplier = 1.0,
                eligibleSampleCount = filteredRatios.size,
                isCalibrated = false,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                excludedOutlierCount = outlierCount
            )
            vehicleCalibrations[vehicleId] = state
            return state
        }

        // Robust average of inlier ratios
        val rawMultiplier = filteredRatios.average()

        // Apply strict physical bounds to prevent unstable corrections
        val boundedMultiplier = rawMultiplier.coerceIn(minMultiplier, maxMultiplier)

        val state = VehicleCalibrationState(
            vehicleId = vehicleId,
            correctionMultiplier = boundedMultiplier,
            eligibleSampleCount = filteredRatios.size,
            isCalibrated = true,
            lastUpdatedEpochMs = System.currentTimeMillis(),
            excludedOutlierCount = outlierCount
        )
        vehicleCalibrations[vehicleId] = state
        return state
    }
}
