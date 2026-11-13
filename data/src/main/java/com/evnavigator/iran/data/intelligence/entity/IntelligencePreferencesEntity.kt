package com.evnavigator.iran.data.intelligence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted local user preferences and privacy controls for Phase 14 intelligence.
 */
@Entity(tableName = "intelligence_preferences")
data class IntelligencePreferencesEntity(
    @PrimaryKey
    val userId: String = "local_user",
    val isPersonalizationEnabled: Boolean = false,
    val isTripHistoryLoggingEnabled: Boolean = true,
    val minReserveSocPercent: Double = 10.0,
    val preferredOptimizationObjective: String = "BALANCED",
    val maxHistoryRetentionDays: Int = 30,
    val showDiagnosticExplanations: Boolean = true
)
