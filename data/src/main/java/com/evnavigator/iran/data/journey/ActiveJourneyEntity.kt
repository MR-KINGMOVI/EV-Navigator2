package com.evnavigator.iran.data.journey

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an active or recent EV journey persisted for crash recovery.
 */
@Entity(tableName = "active_journey")
data class ActiveJourneyEntity(
    @PrimaryKey
    val journeyId: String,
    val lifecycleState: String,
    val vehicleId: String,
    val originName: String,
    val destinationName: String,
    val waypointsJson: String,
    val currentSocPercent: Double,
    val batteryCapacityKwh: Double,
    val totalDistanceMeters: Double,
    val remainingDistanceMeters: Double,
    val lastLatitude: Double,
    val lastLongitude: Double,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)
