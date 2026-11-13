package com.evnavigator.iran.data.intelligence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evnavigator.iran.data.intelligence.entity.IntelligencePreferencesEntity

@Dao
interface IntelligencePreferencesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(preferences: IntelligencePreferencesEntity)

    @Query("SELECT * FROM intelligence_preferences WHERE userId = :userId LIMIT 1")
    suspend fun getPreferences(userId: String = "local_user"): IntelligencePreferencesEntity?

    @Query("DELETE FROM intelligence_preferences WHERE userId = :userId")
    suspend fun clearPreferences(userId: String = "local_user")
}
