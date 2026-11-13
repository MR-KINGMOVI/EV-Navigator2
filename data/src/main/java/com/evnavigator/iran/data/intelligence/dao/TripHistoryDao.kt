package com.evnavigator.iran.data.intelligence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evnavigator.iran.data.intelligence.entity.TripHistorySummaryEntity

@Dao
interface TripHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: TripHistorySummaryEntity)

    @Query("SELECT * FROM trip_history_summaries WHERE vehicleId = :vehicleId ORDER BY startEpochMs DESC")
    suspend fun getSummariesForVehicle(vehicleId: String): List<TripHistorySummaryEntity>

    @Query("SELECT * FROM trip_history_summaries ORDER BY startEpochMs DESC")
    suspend fun getAllSummaries(): List<TripHistorySummaryEntity>

    @Query("DELETE FROM trip_history_summaries WHERE vehicleId = :vehicleId")
    suspend fun deleteSummariesForVehicle(vehicleId: String)

    @Query("DELETE FROM trip_history_summaries WHERE startEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query("DELETE FROM trip_history_summaries")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM trip_history_summaries WHERE vehicleId = :vehicleId")
    suspend fun countSummariesForVehicle(vehicleId: String): Int
}
