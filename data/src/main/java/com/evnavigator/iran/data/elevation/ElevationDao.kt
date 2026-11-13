package com.evnavigator.iran.data.elevation.dao

import androidx.room.*
import com.evnavigator.iran.data.elevation.ElevationDataset
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ElevationDataset queries.
 *
 * Provides efficient queries for offline elevation dataset management,
 * including version selection, installation state, and coverage queries.
 */
@Dao
interface ElevationDao {

    /**
     * Get all elevation datasets.
     *
     * @return Flow of all elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets")
    fun getAllElevationDatasets(): Flow<List<ElevationDataset>>

    /**
     * Get a specific elevation dataset by ID.
     *
     * @param id The unique identifier for the dataset
     * @return The ElevationDataset or null if not found
     */
    @Query("SELECT * FROM elevation_datasets WHERE id = :id LIMIT 1")
    suspend fun getElevationDatasetById(id: String): ElevationDataset?

    /**
     * Get all elevation datasets with a specific source.
     *
     * @param source The source of the data
     * @return List of matching elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE source = :source")
    suspend fun getElevationDatasetsBySource(source: String): List<ElevationDataset>

    /**
     * Get all elevation datasets with a specific coverage type.
     *
     * @param coverageType The type of coverage
     * @return List of matching elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE coverageType = :coverageType")
    suspend fun getElevationDatasetsByCoverageType(coverageType: String): List<ElevationDataset>

    /**
     * Get all elevation datasets with a specific installation state.
     *
     * @param installationState The installation state to filter by
     * @return List of matching elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE installationState = :installationState")
    suspend fun getElevationDatasetsByInstallationState(installationState: String): List<ElevationDataset>

    /**
     * Get all elevation datasets with a specific coverage level.
     *
     * @param coverageLevel The coverage level to filter by
     * @return List of matching elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE coverageLevel = :coverageLevel")
    suspend fun getElevationDatasetsByCoverageLevel(coverageLevel: String): List<ElevationDataset>

    /**
     * Get the most recent elevation dataset for a given source.
     *
     * @param source The data source identifier
     * @return Most recent dataset or null if not found
     */
    @Query("SELECT * FROM elevation_datasets WHERE source = :source ORDER BY datasetVersion DESC LIMIT 1")
    suspend fun getLatestElevationDatasetBySource(source: String): ElevationDataset?

    /**
     * Get all elevation datasets that match a specific version.
     *
     * @param datasetVersion The dataset version to filter by
     * @return List of matching elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE datasetVersion = :datasetVersion")
    suspend fun getElevationDatasetsByVersion(datasetVersion: String): List<ElevationDataset>

    /**
     * Get all active elevation datasets.
     *
     * @return Flow of active elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE activeVersion IS NOT NULL")
    fun getActiveElevationDatasets(): Flow<List<ElevationDataset>>

    /**
     * Get all installed elevation datasets.
     *
     * @return Flow of installed elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE installationState = 'INSTALLED'")
    fun getInstalledElevationDatasets(): Flow<List<ElevationDataset>>

    /**
     * Insert a new elevation dataset into the database.
     *
     * @param elevationDataset The entity to insert
     * @return The generated ID for the inserted row
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElevationDataset(elevationDataset: ElevationDataset): Long

    /**
     * Insert multiple elevation datasets into the database.
     *
     * @param elevationDatasets List of entities to insert
     * @return The generated IDs for the inserted rows
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElevationDatasets(elevationDatasets: List<ElevationDataset>)

    /**
     * Update an existing elevation dataset.
     *
     * @param elevationDataset The entity to update
     * @return Number of affected rows
     */
    @Update
    suspend fun updateElevationDataset(elevationDataset: ElevationDataset): Int

    /**
     * Delete a specific elevation dataset by ID.
     *
     * @param id The unique identifier for the dataset
     * @return Number of affected rows
     */
    @Query("DELETE FROM elevation_datasets WHERE id = :id")
    suspend fun deleteElevationDatasetById(id: String): Int

    /**
     * Delete all elevation datasets with a specific source.
     *
     * @param source The data source identifier
     * @return Number of affected rows
     */
    @Query("DELETE FROM elevation_datasets WHERE source = :source")
    suspend fun deleteElevationDatasetsBySource(source: String): Int

    /**
     * Get all elevation datasets that match a specific coverage.
     *
     * @param coverage The geographic coverage area identifier
     * @return List of matching elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE coverage = :coverage")
    suspend fun getElevationDatasetsByCoverage(coverage: String): List<ElevationDataset>

    @Query("SELECT * FROM elevation_datasets WHERE boundingBox IS NOT NULL")
    suspend fun getAllElevationDatasetsWithBoundingBox(): List<ElevationDataset>

    /**
     * Get all elevation datasets within a specific bounding box.
     *
     * @param minLat Minimum latitude
     * @param minLng Minimum longitude
     * @param maxLat Maximum latitude  
     * @param maxLng Maximum longitude
     * @return List of matching elevation datasets
     */
    suspend fun getElevationDatasetsInBoundingBox(
        minLat: Double,
        minLng: Double, 
        maxLat: Double,
        maxLng: Double
    ): List<ElevationDataset> {
        if (minLat > maxLat || minLng > maxLng) return emptyList()
        return getAllElevationDatasetsWithBoundingBox().filter { dataset ->
            val parts = dataset.boundingBox?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }
            if (parts != null && parts.size >= 4) {
                val bMinLat = parts[0]
                val bMinLng = parts[1]
                val bMaxLat = parts[2]
                val bMaxLng = parts[3]
                bMaxLat >= minLat && bMinLat <= maxLat && bMaxLng >= minLng && bMinLng <= maxLng
            } else {
                true
            }
        }
    }

    /**
     * Get elevation datasets by dataset version.
     *
     * @param datasetVersion The dataset version to filter by
     * @return List of matching elevation datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE datasetVersion = :datasetVersion")
    suspend fun getElevationDatasetsByDatasetVersion(datasetVersion: String): List<ElevationDataset>

    /**
     * Get all unique sources in the database.
     *
     * @return Flow of distinct source strings
     */
    @Query("SELECT DISTINCT(source) FROM elevation_datasets")
    fun getAllSources(): Flow<List<String>> 

    /**
     * Count total number of elevation datasets.
     *
     * @return Total count
     */
    @Query("SELECT COUNT(*) FROM elevation_datasets")
    suspend fun countElevationDatasets(): Int

    /**
     * Get all elevation datasets by a specific source type.
     *
     * @param sourceType The source type to filter by
     * @return List of matching datasets
     */
    @Query("SELECT * FROM elevation_datasets WHERE sourceType = :sourceType")
    suspend fun getElevationDatasetsBySourceType(sourceType: String): List<ElevationDataset>
}