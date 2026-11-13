package com.evnavigator.iran.data.intelligence.repository

import com.evnavigator.iran.data.intelligence.dao.IntelligencePreferencesDao
import com.evnavigator.iran.data.intelligence.dao.TripHistoryDao
import com.evnavigator.iran.data.intelligence.entity.IntelligencePreferencesEntity
import com.evnavigator.iran.data.intelligence.entity.TripHistorySummaryEntity

/**
 * Repository orchestrating trip history persistence with strict local privacy protections.
 */
class TripHistoryPrivacyRepository(
    private val tripHistoryDao: TripHistoryDao,
    private val preferencesDao: IntelligencePreferencesDao
) {

    suspend fun getPreferences(): IntelligencePreferencesEntity {
        return preferencesDao.getPreferences() ?: IntelligencePreferencesEntity().also {
            preferencesDao.savePreferences(it)
        }
    }

    suspend fun updatePreferences(preferences: IntelligencePreferencesEntity) {
        preferencesDao.savePreferences(preferences)
    }

    suspend fun recordTripSummary(summary: TripHistorySummaryEntity): Boolean {
        val prefs = getPreferences()
        if (!prefs.isTripHistoryLoggingEnabled) {
            // Strictly enforce user opt-out: no persistence when logging is disabled
            return false
        }

        // Auto-prune trips older than user-configured retention period
        val retentionPeriodMs = prefs.maxHistoryRetentionDays * 24 * 3600 * 1000L
        val cutoffMs = System.currentTimeMillis() - retentionPeriodMs
        tripHistoryDao.deleteOlderThan(cutoffMs)

        tripHistoryDao.insertSummary(summary)
        return true
    }

    suspend fun getHistoryForVehicle(vehicleId: String): List<TripHistorySummaryEntity> {
        val prefs = getPreferences()
        if (!prefs.isTripHistoryLoggingEnabled) return emptyList()
        return tripHistoryDao.getSummariesForVehicle(vehicleId)
    }

    suspend fun clearHistoryForVehicle(vehicleId: String) {
        tripHistoryDao.deleteSummariesForVehicle(vehicleId)
    }

    suspend fun deleteAllHistory() {
        tripHistoryDao.clearAll()
    }
}
