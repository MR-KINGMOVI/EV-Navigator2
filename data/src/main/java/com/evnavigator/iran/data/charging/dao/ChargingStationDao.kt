package com.evnavigator.iran.data.charging.dao

import androidx.room.*
import com.evnavigator.iran.data.charging.dto.NearbyStationProjection
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationWithPortsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for charging stations and associated ports.
 * Provides complete CRUD operations, spatial queries, and dataset management.
 */
@Dao
interface ChargingStationDao {

    // ============================================================================
    // Basic CRUD Operations & Queries for Charging Stations
    // ============================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: ChargingStationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<ChargingStationEntity>)

    @Update
    suspend fun updateStation(station: ChargingStationEntity): Int

    @Delete
    suspend fun deleteStation(station: ChargingStationEntity): Int

    @Query("DELETE FROM charging_stations WHERE id = :id")
    suspend fun deleteStationById(id: String): Int

    @Query("DELETE FROM charging_stations WHERE source = :source")
    suspend fun deleteChargingStationsBySource(source: String): Int

    @Query("DELETE FROM charging_stations")
    suspend fun deleteAllStations()

    @Query("SELECT * FROM charging_stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: String): ChargingStationEntity?

    @Query("SELECT * FROM charging_stations WHERE id = :id LIMIT 1")
    suspend fun getChargingStationById(id: String): ChargingStationEntity?

    @Query("SELECT * FROM charging_stations WHERE externalId = :externalId LIMIT 1")
    suspend fun getStationByExternalId(externalId: String): ChargingStationEntity?

    @Query("SELECT * FROM charging_stations WHERE id = :stationId LIMIT 1")
    suspend fun getChargingStationByStationId(stationId: String): ChargingStationEntity?

    @Query("SELECT * FROM charging_stations")
    fun getAllStations(): Flow<List<ChargingStationEntity>>

    @Query("SELECT * FROM charging_stations")
    fun getAllChargingStations(): Flow<List<ChargingStationEntity>>

    @Query("SELECT * FROM charging_stations WHERE source = :source")
    suspend fun getChargingStationsBySource(source: String): List<ChargingStationEntity>

    @Query("SELECT * FROM charging_stations WHERE sourceType = :sourceType")
    suspend fun getChargingStationsBySourceType(sourceType: String): List<ChargingStationEntity>

    @Query("SELECT * FROM charging_stations WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng")
    fun getStationsNearby(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): Flow<List<ChargingStationEntity>>

    @Query("SELECT DISTINCT cs.* FROM charging_stations cs JOIN charging_ports cp ON cs.id = cp.stationId WHERE cp.connectorType LIKE '%' || :connectorType || '%'")
    fun getStationsByConnectorType(connectorType: String): Flow<List<ChargingStationEntity>>

    @Query("SELECT DISTINCT cs.* FROM charging_stations cs JOIN charging_ports cp ON cs.id = cp.stationId WHERE cp.powerKw >= :minPowerKw")
    fun getStationsByMinimumPower(minPowerKw: Double): Flow<List<ChargingStationEntity>>

    @Query("SELECT * FROM charging_stations WHERE operatorName = :operatorName")
    fun getStationsByOperator(operatorName: String): Flow<List<ChargingStationEntity>>

    @Query("SELECT * FROM charging_stations WHERE province = :province")
    fun getStationsByProvince(province: String): Flow<List<ChargingStationEntity>>

    @Query("SELECT * FROM charging_stations WHERE city = :city")
    fun getStationsByCity(city: String): Flow<List<ChargingStationEntity>>

    // ============================================================================
    // Port Operations & Station-With-Ports Relations
    // ============================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPort(port: ChargingPortEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPorts(ports: List<ChargingPortEntity>)

    @Update
    suspend fun updatePort(port: ChargingPortEntity): Int

    @Delete
    suspend fun deletePort(port: ChargingPortEntity): Int

    @Query("DELETE FROM charging_ports WHERE id = :id")
    suspend fun deletePortById(id: String)

    @Query("DELETE FROM charging_ports WHERE stationId = :stationId")
    suspend fun deletePortsForStation(stationId: String)

    @Query("SELECT * FROM charging_ports WHERE id = :id LIMIT 1")
    suspend fun getPortById(id: String): ChargingPortEntity?

    @Query("SELECT * FROM charging_ports WHERE stationId = :stationId")
    fun getPortsForStation(stationId: String): Flow<List<ChargingPortEntity>>

    @Transaction
    @Query("SELECT * FROM charging_stations WHERE id = :id LIMIT 1")
    suspend fun getStationWithPorts(id: String): ChargingStationWithPortsEntity?

    @Transaction
    @Query("SELECT * FROM charging_stations")
    fun getAllStationsWithPorts(): Flow<List<ChargingStationWithPortsEntity>>

    // ============================================================================
    // Spatial & Projection Queries for Performance Optimization
    // ============================================================================

    @Query("""
        SELECT 
            cs.id, cs.canonicalName, cs.latitude, cs.longitude, cs.operatorName, cs.province, cs.city,
            cs.accessType, COUNT(cp.id) AS connectorCount, COALESCE(MAX(cp.powerKw), 0.0) AS powerKw, cs.confidence, cs.validationStatus, cs.retrievedDate
        FROM charging_stations cs
        LEFT JOIN charging_ports cp ON cs.id = cp.stationId
        WHERE cs.latitude BETWEEN :minLat AND :maxLat 
          AND cs.longitude BETWEEN :minLng AND :maxLng
        GROUP BY cs.id
    """)
    fun getStationsInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<NearbyStationProjection>>

    @Query("""
        SELECT 
            cs.id, cs.canonicalName, cs.latitude, cs.longitude, cs.operatorName, cs.province, cs.city,
            cs.accessType, COUNT(cp.id) AS connectorCount, COALESCE(MAX(cp.powerKw), 0.0) AS powerKw, cs.confidence, cs.validationStatus, cs.retrievedDate
        FROM charging_stations cs
        LEFT JOIN charging_ports cp ON cs.id = cp.stationId
        WHERE cs.latitude BETWEEN :minLat AND :maxLat 
          AND cs.longitude BETWEEN :minLng AND :maxLng
          AND (:connectorType IS NULL OR cs.id IN (
              SELECT DISTINCT cs2.id FROM charging_stations cs2
              JOIN charging_ports cp2 ON cs2.id = cp2.stationId
              WHERE cp2.connectorType = :connectorType
          ))
          AND (:minPowerKw IS NULL OR cs.id IN (
              SELECT DISTINCT cs3.id FROM charging_stations cs3
              JOIN charging_ports cp3 ON cs3.id = cp3.stationId
              WHERE cp3.powerKw >= :minPowerKw
          ))
          AND (:province IS NULL OR cs.province = :province)
          AND (:city IS NULL OR cs.city = :city)
        GROUP BY cs.id
    """)
    fun getStationsInBoundingBoxWithFilters(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        connectorType: String? = null,
        minPowerKw: Double? = null,
        province: String? = null,
        city: String? = null
    ): Flow<List<NearbyStationProjection>>

    @Query("""
        SELECT 
            cs.id, cs.canonicalName, cs.latitude, cs.longitude, cs.operatorName, cs.province, cs.city,
            cs.accessType, COUNT(cp.id) AS connectorCount, COALESCE(MAX(cp.powerKw), 0.0) AS powerKw, cs.confidence, cs.validationStatus, cs.retrievedDate
        FROM charging_stations cs
        LEFT JOIN charging_ports cp ON cs.id = cp.stationId
        WHERE cs.province = :province
          AND cs.latitude BETWEEN :minLat AND :maxLat 
          AND cs.longitude BETWEEN :minLng AND :maxLng
        GROUP BY cs.id
    """)
    fun getStationsInProvinceBoundingBox(
        province: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<NearbyStationProjection>>

    @Query("""
        SELECT 
            cs.id, cs.canonicalName, cs.latitude, cs.longitude, cs.operatorName, cs.province, cs.city,
            cs.accessType, COUNT(cp.id) AS connectorCount, COALESCE(MAX(cp.powerKw), 0.0) AS powerKw, cs.confidence, cs.validationStatus, cs.retrievedDate
        FROM charging_stations cs
        LEFT JOIN charging_ports cp ON cs.id = cp.stationId
        WHERE cs.city = :city
          AND cs.latitude BETWEEN :minLat AND :maxLat 
          AND cs.longitude BETWEEN :minLng AND :maxLng
        GROUP BY cs.id
    """)
    fun getStationsInCityBoundingBox(
        city: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<NearbyStationProjection>>

    @Query("""
        SELECT 
            cs.id, cs.canonicalName, cs.latitude, cs.longitude, cs.operatorName, cs.province, cs.city,
            cs.accessType, COUNT(cp.id) AS connectorCount, COALESCE(MAX(cp.powerKw), 0.0) AS powerKw, cs.confidence, cs.validationStatus, cs.retrievedDate
        FROM charging_stations cs
        LEFT JOIN charging_ports cp ON cs.id = cp.stationId
        WHERE cs.latitude BETWEEN :minLat AND :maxLat 
          AND cs.longitude BETWEEN :minLng AND :maxLng
        GROUP BY cs.id
    """)
    fun getStationsNearbyOptimized(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<NearbyStationProjection>>

    // ============================================================================
    // Administrative & Dataset Maintenance Queries
    // ============================================================================

    @Query("SELECT COUNT(*) FROM charging_stations")
    suspend fun countStations(): Int

    @Query("SELECT COUNT(*) FROM charging_stations WHERE source = :source")
    suspend fun countStationsBySource(source: String): Int

    @Query("SELECT COUNT(*) FROM charging_stations WHERE confidence >= :minConfidence")
    suspend fun countStationsByConfidence(minConfidence: Double): Int

    @Query("SELECT COUNT(*) FROM charging_stations WHERE validationStatus = :validationStatus")
    suspend fun countStationsByValidationStatus(validationStatus: String): Int

    @Query("SELECT COUNT(*) FROM charging_stations WHERE (datasetVersion = :datasetVersion OR version = :datasetVersion) AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng")
    suspend fun countStationsByDatasetVersionAndBoundingBox(
        datasetVersion: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Int

    @Query("DELETE FROM charging_stations WHERE datasetVersion = :datasetVersion OR version = :datasetVersion")
    suspend fun deleteStationsByDatasetVersion(datasetVersion: String): Int

    @Query("DELETE FROM charging_stations WHERE confidence < :minConfidence")
    suspend fun deleteStationsBelowConfidence(minConfidence: Double): Int

    @Query("UPDATE charging_stations SET validationStatus = :validationStatus WHERE id = :id")
    suspend fun updateValidationStatus(id: String, validationStatus: String): Int

    @Query("UPDATE charging_stations SET confidence = :confidence WHERE id = :id")
    suspend fun updateConfidence(id: String, confidence: Double): Int

    @Query("UPDATE charging_stations SET retrievedDate = :retrievedDate WHERE id = :id")
    suspend fun updateRetrievedDate(id: String, retrievedDate: Long): Int

    @Query("SELECT DISTINCT source FROM charging_stations")
    fun getAllSources(): Flow<List<String>>

    @Query("SELECT DISTINCT province FROM charging_stations WHERE province IS NOT NULL")
    fun getAllProvinces(): Flow<List<String>>

    @Query("SELECT DISTINCT city FROM charging_stations WHERE province = :province AND city IS NOT NULL")
    fun getCitiesByProvince(province: String): Flow<List<String>>

    

    @Query("""
        SELECT * FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND datasetVersion = :datasetVersion
    """)
    fun getStationsByDatasetVersionBoundingBox(
        datasetVersion: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): kotlinx.coroutines.flow.Flow<List<ChargingStationEntity>>

    @Query("""
        SELECT * FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND source = :source
        AND sourceType = :sourceType
    """)
    fun getStationsBySourceBoundingBox(
        source: String,
        sourceType: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): kotlinx.coroutines.flow.Flow<List<ChargingStationEntity>>

    @Query("""
        SELECT * FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND validationStatus = :validationStatus
    """)
    fun getStationsByValidationStatusBoundingBox(
        validationStatus: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): kotlinx.coroutines.flow.Flow<List<ChargingStationEntity>>

    @Query("""
        SELECT * FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND confidence >= :minConfidence
    """)
    fun getStationsByConfidenceBoundingBox(
        minConfidence: Double,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): kotlinx.coroutines.flow.Flow<List<ChargingStationEntity>>

    @Query("""
        SELECT COUNT(*) FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun countStationsInBoundingBox(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Int

    @Query("""
        SELECT COUNT(*) FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND province = :province
    """)
    suspend fun countStationsInProvinceBoundingBox(
        province: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Int

    @Query("""
        SELECT COUNT(*) FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND city = :city
    """)
    suspend fun countStationsInCityBoundingBox(
        city: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Int

    @Query("""
        SELECT COUNT(*) FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND datasetVersion = :datasetVersion
    """)
    suspend fun countStationsByDatasetVersionBoundingBox(
        datasetVersion: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Int

    @Query("""
        SELECT COUNT(*) FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND source = :source
        AND sourceType = :sourceType
    """)
    suspend fun countStationsBySourceBoundingBox(
        source: String,
        sourceType: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Int

    @Query("""
        SELECT COUNT(*) FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND validationStatus = :validationStatus
    """)
    suspend fun countStationsByValidationStatusBoundingBox(
        validationStatus: String,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Int

    @Query("""
        SELECT COUNT(*) FROM charging_stations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        AND confidence >= :minConfidence
    """)
    suspend fun countStationsByConfidenceBoundingBox(
        minConfidence: Double,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Int

}
