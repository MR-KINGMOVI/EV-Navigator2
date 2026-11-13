package com.evnavigator.iran.data.elevation

import com.evnavigator.iran.data.elevation.dao.ElevationDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing elevation datasets.
 *
 * This repository provides a clean interface for data access and manipulation
 * of elevation-related information, abstracting away the underlying data source.
 */
interface ElevationRepository {
    
    /**
     * Get all elevation datasets.
     *
     * @return Flow of all elevation datasets
     */
    fun getAllElevationDatasets(): Flow<List<ElevationDataset>>

    /**
     * Get a specific elevation dataset by ID.
     *
     * @param id The unique identifier for the dataset
     * @return ElevationDataset or null if not found
     */
    suspend fun getElevationDatasetById(id: String): ElevationDataset?

    /**
     * Get all elevation datasets with a specific source.
     *
     * @param source The data source identifier
     * @return List of matching elevation datasets
     */
    suspend fun getElevationDatasetsBySource(source: String): List<ElevationDataset>

    /**
     * Get the most recent elevation dataset for a given source.
     *
     * @param source The data source identifier
     * @return Most recent dataset or null if not found
     */
    suspend fun getLatestElevationDatasetBySource(source: String): ElevationDataset?

    /**
     * Insert a new elevation dataset into the database.
     *
     * @param elevationDataset The entity to insert
     * @return The generated ID for the inserted row
     */
    suspend fun insertElevationDataset(elevationDataset: ElevationDataset): Long

    /**
     * Update an existing elevation dataset.
     *
     * @param elevationDataset The entity to update
     * @return Number of affected rows
     */
    suspend fun updateElevationDataset(elevationDataset: ElevationDataset): Int

    /**
     * Delete a specific elevation dataset by ID.
     *
     * @param id The unique identifier for the dataset
     * @return Number of deleted rows
     */
    suspend fun deleteElevationDatasetById(id: String): Int

    /**
     * Get all installed elevation datasets.
     *
     * @return Flow of installed elevation datasets
     */
    fun getInstalledElevationDatasets(): Flow<List<ElevationDataset>>

    /**
     * Count total number of elevation datasets.
     *
     * @return Total count
     */
    suspend fun countElevationDatasets(): Int

    /**
     * Get all elevation datasets by a specific source type.
     *
     * @param sourceType The source type to filter by
     * @return List of matching datasets
     */
    suspend fun getElevationDatasetsBySourceType(sourceType: String): List<ElevationDataset>
}