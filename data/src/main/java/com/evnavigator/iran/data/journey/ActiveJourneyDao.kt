package com.evnavigator.iran.data.journey

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActiveJourneyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveJourney(journey: ActiveJourneyEntity)

    @Query("SELECT * FROM active_journey WHERE journeyId = :id")
    suspend fun getActiveJourney(id: String): ActiveJourneyEntity?

    @Query("SELECT * FROM active_journey ORDER BY lastUpdatedEpochMs DESC LIMIT 1")
    suspend fun getLatestActiveJourney(): ActiveJourneyEntity?

    @Query("DELETE FROM active_journey WHERE journeyId = :id")
    suspend fun deleteJourney(id: String)

    @Query("DELETE FROM active_journey")
    suspend fun clearAll()
}
