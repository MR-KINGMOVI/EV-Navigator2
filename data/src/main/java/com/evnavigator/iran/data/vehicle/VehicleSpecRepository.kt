package com.evnavigator.iran.data.vehicle

import com.evnavigator.iran.data.vehicle.dao.VehicleSpecDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing vehicle specifications.
 *
 * This repository provides a clean interface for data access and manipulation
 * of vehicle-related information, abstracting away the underlying data source.
 */
interface VehicleSpecRepository {
    
    /**
     * Get all vehicle specifications.
     *
     * @return Flow of all vehicle specifications
     */
    fun getAllVehicleSpecs(): Flow<List<VehicleSpecEntity>>

    /**
     * Get a specific vehicle specification by ID.
     *
     * @param id The unique identifier for the vehicle spec
     * @return VehicleSpecEntity or null if not found
     */
    suspend fun getVehicleSpecById(id: String): VehicleSpecEntity?

    /**
     * Get all vehicle specifications with a specific make.
     *
     * @param make The vehicle make (e.g., "Tesla", "Nissan")  
     * @return List of matching vehicle specifications
     */
    suspend fun getVehicleSpecsByMake(make: String): List<VehicleSpecEntity>

    /**
     * Get all vehicle specifications with a specific model.
     *
     * @param model The vehicle model (e.g., "Model 3", "Leaf") 
     * @return List of matching vehicle specifications
     */
    suspend fun getVehicleSpecsByModel(model: String): List<VehicleSpecEntity>

    /**
     * Get the most recent vehicle specification for a given make/model.
     *
     * @param make The vehicle make
     * @param model The vehicle model  
     * @return Most recent vehicle spec or null if not found
     */
    suspend fun getLatestVehicleSpecByMakeModel(make: String, model: String): VehicleSpecEntity?

    /**
     * Get all vehicle specifications for a specific year.
     *
     * @param year The manufacturing year
     * @return List of matching vehicle specifications
     */
    suspend fun getVehicleSpecsByYear(year: Int): List<VehicleSpecEntity>

    /**
     * Insert a new vehicle specification into the database.
     *
     * @param vehicleSpec The entity to insert
     * @return The generated ID for the inserted row
     */
    suspend fun insertVehicleSpec(vehicleSpec: VehicleSpecEntity): Long

    /**
     * Update an existing vehicle specification.
     *
     * @param vehicleSpec The entity to update
     * @return Number of affected rows
     */
    suspend fun updateVehicleSpec(vehicleSpec: VehicleSpecEntity): Int

    /**
     * Delete a specific vehicle specification by ID.
     *
     * @param id The unique identifier for the vehicle spec
     * @return Number of deleted rows
     */
    suspend fun deleteVehicleSpecById(id: String): Int

    /**
     * Get all active vehicle specifications.
     *
     * @return Flow of active vehicle specifications
     */
    fun getActiveVehicleSpecs(): Flow<List<VehicleSpecEntity>>

    /**
     * Count total number of vehicle specifications.
     *
     * @return Total count
     */
    suspend fun countVehicleSpecs(): Int

    /**
     * Get all vehicle specs by a specific source type.
     *
     * @param sourceType The source type to filter by
     * @return List of matching vehicle specifications
     */
    suspend fun getVehicleSpecsBySourceType(sourceType: String): List<VehicleSpecEntity>
}