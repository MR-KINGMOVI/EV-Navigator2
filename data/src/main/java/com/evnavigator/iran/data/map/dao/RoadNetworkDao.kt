package com.evnavigator.iran.data.map.dao

import androidx.room.*
import com.evnavigator.iran.data.map.entity.RoadNetworkEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for RoadNetworkEntity queries.
 *
 * Provides efficient queries for offline map/routing dataset management,
 * including version selection, installation state, and coverage queries.
 */
@Dao
interface RoadNetworkDao {

    /**
     * Get all road network datasets.
     *
     * @return Flow of all road network datasets
     */
    @Query("SELECT * FROM road_networks")
    fun getAllRoadNetworks(): Flow<List<RoadNetworkEntity>>

    /**
     * Get a specific road network dataset by ID.
     *
     * @param id The unique identifier for the dataset
     * @return The RoadNetworkEntity or null if not found
     */
    @Query("SELECT * FROM road_networks WHERE id = :id LIMIT 1")
    suspend fun getRoadNetworkById(id: String): RoadNetworkEntity?

    /**
     * Get all road network datasets with a specific source.
     *
     * @param source The source of the data
     * @return List of matching road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE source = :source")
    suspend fun getRoadNetworksBySource(source: String): List<RoadNetworkEntity>

    /**
     * Get all road network datasets with a specific coverage type.
     *
     * @param coverageType The type of coverage
     * @return List of matching road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE coverageType = :coverageType")
    suspend fun getRoadNetworksByCoverageType(coverageType: String): List<RoadNetworkEntity>

    /**
     * Get all road network datasets with a specific installation state.
     *
     * @param installationState The installation state to filter by
     * @return List of matching road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE installationState = :installationState")
    suspend fun getRoadNetworksByInstallationState(installationState: String): List<RoadNetworkEntity>

    /**
     * Get all road network datasets with a specific coverage level.
     *
     * @param coverageLevel The coverage level to filter by
     * @return List of matching road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE coverageLevel = :coverageLevel")
    suspend fun getRoadNetworksByCoverageLevel(coverageLevel: String): List<RoadNetworkEntity>

    /**
     * Get all active road network datasets.
     *
     * @return Flow of active road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE activeVersion IS NOT NULL")
    fun getActiveRoadNetworks(): Flow<List<RoadNetworkEntity>>

    /**
     * Get the most recent road network dataset for a given source.
     *
     * @param source The data source identifier
     * @return Most recent dataset or null if not found
     */
    @Query("SELECT * FROM road_networks WHERE source = :source ORDER BY datasetVersion DESC LIMIT 1")
    suspend fun getLatestRoadNetworkBySource(source: String): RoadNetworkEntity?

    /**
     * Get all road network datasets that match a specific version.
     *
     * @param datasetVersion The dataset version to filter by
     * @return List of matching road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE datasetVersion = :datasetVersion")
    suspend fun getRoadNetworksByVersion(datasetVersion: String): List<RoadNetworkEntity>

    /**
     * Get all road network datasets that are currently active.
     *
     * @return Flow of active road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE installationState = 'ACTIVE'")
    fun getInstalledRoadNetworks(): Flow<List<RoadNetworkEntity>>

    /**
     * Insert a new road network dataset into the database.
     *
     * @param roadNetwork The entity to insert
     * @return The generated ID for the inserted row
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoadNetwork(roadNetwork: RoadNetworkEntity): Long

    /**
     * Insert multiple road network datasets into the database.
     *
     * @param roadNetworks List of entities to insert
     * @return The generated IDs for the inserted rows
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoadNetworks(roadNetworks: List<RoadNetworkEntity>)

    /**
     * Update an existing road network dataset.
     *
     * @param roadNetwork The entity to update
     * @return Number of affected rows
     */
    @Update
    suspend fun updateRoadNetwork(roadNetwork: RoadNetworkEntity): Int

    /**
     * Delete a specific road network dataset by ID.
     *
     * @param id The unique identifier for the dataset
     * @return Number of affected rows
     */
    @Query("DELETE FROM road_networks WHERE id = :id")
    suspend fun deleteRoadNetworkById(id: String): Int

    /**
     * Delete all road network datasets with a specific source.
     *
     * @param source The data source identifier
     * @return Number of affected rows
     */
    @Query("DELETE FROM road_networks WHERE source = :source")
    suspend fun deleteRoadNetworksBySource(source: String): Int

    /**
     * Get all road network datasets that match a specific coverage.
     *
     * @param coverage The geographic coverage area identifier
     * @return List of matching road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE coverage = :coverage")
    suspend fun getRoadNetworksByCoverage(coverage: String): List<RoadNetworkEntity>

    @Query("SELECT * FROM road_networks WHERE boundingBox IS NOT NULL")
    suspend fun getAllRoadNetworksWithBoundingBox(): List<RoadNetworkEntity>

    /**
     * Get all road network datasets that are within a specific bounding box.
     *
     * @param minLat Minimum latitude
     * @param minLng Minimum longitude
     * @param maxLat Maximum latitude  
     * @param maxLng Maximum longitude
     * @return List of matching road network datasets
     */
    suspend fun getRoadNetworksInBoundingBox(
        minLat: Double,
        minLng: Double, 
        maxLat: Double,
        maxLng: Double
    ): List<RoadNetworkEntity> {
        if (minLat > maxLat || minLng > maxLng) return emptyList()
        return getAllRoadNetworksWithBoundingBox().filter { network ->
            val parts = network.boundingBox?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }
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
     * Get road network datasets by dataset version.
     *
     * @param datasetVersion The dataset version to filter by
     * @return List of matching road network datasets
     */
    @Query("SELECT * FROM road_networks WHERE datasetVersion = :datasetVersion")
    suspend fun getRoadNetworksByDatasetVersion(datasetVersion: String): List<RoadNetworkEntity>

    /**
     * Get all unique sources in the database.
     *
     * @return Flow of distinct source strings
     */
    @Query("SELECT DISTINCT(source) FROM road_networks")
    fun getAllSources(): Flow<List<String>>

    /**
     * Count total number of road network datasets.
     *
     * @return Total count
     */
    @Query("SELECT COUNT(*) FROM road_networks")
    suspend fun countRoadNetworks(): Int

    /**
     * Get all road networks by a specific source type.
     *
     * @param sourceType The source type to filter by
     * @return List of matching datasets
     */
    @Query("SELECT * FROM road_networks WHERE sourceType = :sourceType")
    suspend fun getRoadNetworksBySourceType(sourceType: String): List<RoadNetworkEntity>
}