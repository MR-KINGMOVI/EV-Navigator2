package com.evnavigator.iran.data.vehicle.dao

import androidx.room.*
import com.evnavigator.iran.data.vehicle.VehicleSpecEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for VehicleSpecEntity queries.
 *
 * Provides efficient queries for offline vehicle specification management,
 * including version selection, installation state, and compatibility queries.
 */
@Dao
interface VehicleSpecDao {

    /**
     * Get all vehicle specifications.
     *
     * @return Flow of all vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs")
    fun getAllVehicleSpecs(): Flow<List<VehicleSpecEntity>>

    /**
     * Get a specific vehicle specification by ID.
     *
     * @param id The unique identifier for the vehicle spec
     * @return The VehicleSpecEntity or null if not found
     */
    @Query("SELECT * FROM vehicle_specs WHERE id = :id LIMIT 1")
    suspend fun getVehicleSpecById(id: String): VehicleSpecEntity?

    /**
     * Get all vehicle specifications with a specific make.
     *
     * @param make The vehicle make (e.g., "Tesla", "Nissan")  
     * @return List of matching vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs WHERE make = :make")
    suspend fun getVehicleSpecsByMake(make: String): List<VehicleSpecEntity>

    /**
     * Get all vehicle specifications with a specific model.
     *
     * @param model The vehicle model (e.g., "Model 3", "Leaf") 
     * @return List of matching vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs WHERE model = :model")
    suspend fun getVehicleSpecsByModel(model: String): List<VehicleSpecEntity>

    /**
     * Get all vehicle specifications with a specific market segment.
     *
     * @param marketSegment The market segment to filter by
     * @return List of matching vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs WHERE marketSegment = :marketSegment")
    suspend fun getVehicleSpecsByMarketSegment(marketSegment: String): List<VehicleSpecEntity>

    /**
     * Get all vehicle specifications with a specific installation state.
     *
     * @param installationState The installation state to filter by
     * @return List of matching vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs WHERE installationState = :installationState")
    suspend fun getVehicleSpecsByInstallationState(installationState: String): List<VehicleSpecEntity>

    /**
     * Get the most recent vehicle specification for a given make/model.
     *
     * @param make The vehicle make
     * @param model The vehicle model  
     * @return Most recent vehicle spec or null if not found
     */
    @Query("SELECT * FROM vehicle_specs WHERE make = :make AND model = :model ORDER BY year DESC, version DESC LIMIT 1")
    suspend fun getLatestVehicleSpecByMakeModel(make: String, model: String): VehicleSpecEntity?

    /**
     * Get all vehicle specifications for a specific year.
     *
     * @param year The manufacturing year
     * @return List of matching vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs WHERE year = :year")
    suspend fun getVehicleSpecsByYear(year: Int): List<VehicleSpecEntity>

    /**
     * Get all active vehicle specifications.
     *
     * @return Flow of active vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs WHERE installationState = 'ACTIVE'")
    fun getActiveVehicleSpecs(): Flow<List<VehicleSpecEntity>>

    /**
     * Insert a new vehicle specification into the database.
     *
     * @param vehicleSpec The entity to insert
     * @return The generated ID for the inserted row
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicleSpec(vehicleSpec: VehicleSpecEntity): Long

    /**
     * Insert multiple vehicle specifications into the database.
     *
     * @param vehicleSpecs List of entities to insert
     * @return The generated IDs for the inserted rows
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicleSpecs(vehicleSpecs: List<VehicleSpecEntity>)

    /**
     * Update an existing vehicle specification.
     *
     * @param vehicleSpec The entity to update
     * @return Number of affected rows
     */
    @Update
    suspend fun updateVehicleSpec(vehicleSpec: VehicleSpecEntity): Int

    /**
     * Delete a specific vehicle specification by ID.
     *
     * @param id The unique identifier for the vehicle spec
     * @return Number of deleted rows
     */
    @Query("DELETE FROM vehicle_specs WHERE id = :id")
    suspend fun deleteVehicleSpecById(id: String): Int

    /**
     * Delete all vehicle specifications with a specific make.
     *
     * @param make The vehicle make identifier
     * @return Number of affected rows
     */
    @Query("DELETE FROM vehicle_specs WHERE make = :make")
    suspend fun deleteVehicleSpecsByMake(make: String): Int

    /**
     * Get all unique makes in the database.
     *
     * @return Flow of distinct make strings
     */
    @Query("SELECT DISTINCT(make) FROM vehicle_specs")
    fun getAllMakes(): Flow<List<String>>

    /**
     * Count total number of vehicle specifications.
     *
     * @return Total count
     */
    @Query("SELECT COUNT(*) FROM vehicle_specs")
    suspend fun countVehicleSpecs(): Int

    /**
     * Get all vehicle specs by a specific source type.
     *
     * @param sourceType The source type to filter by
     * @return List of matching vehicle specifications
     */
    @Query("SELECT * FROM vehicle_specs WHERE source = :source")
    suspend fun getVehicleSpecsBySourceType(source: String): List<VehicleSpecEntity>
}