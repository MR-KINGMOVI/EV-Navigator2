package com.evnavigator.iran.data.intelligence.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Privacy-preserving aggregate trip summary for energy calibration.
 * Stores only macro-level metrics; strictly avoids tracking granular GPS breadcrumbs.
 */
@Entity(
    tableName = "trip_history_summaries",
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["startEpochMs"])
    ]
)
data class TripHistorySummaryEntity(
    @PrimaryKey
    val tripId: String,
    val vehicleId: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val distanceMeters: Double,
    val predictedEnergyWh: Double,
    val measuredEnergyWh: Double,
    val averageSpeedKmh: Double,
    val ambientTempCelsius: Double,
    val isVerifiedTelemetry: Boolean,
    val modelCalibrationVersion: Int = 1
)
