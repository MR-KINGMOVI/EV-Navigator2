package com.evnavigator.iran.data.charging.repository

import com.evnavigator.iran.core.charging.ChargingStationId
import com.evnavigator.iran.core.charging.StationMetadata
import com.evnavigator.iran.core.charging.StationLocation
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.dao.ChargingStationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Repository for charging station data access.
 *
 * This repository provides a clean interface for accessing and managing
 * charging station data from the local database while maintaining separation
 * between domain logic and data persistence.
 */
class ChargingStationRepository(
    private val chargingStationDao: ChargingStationDao
) {

    /**
     * Insert or update a charging station.
     */
    suspend fun insertStation(station: ChargingStationEntity) {
        chargingStationDao.insertStation(station)
    }

    /**
     * Insert or update multiple charging stations.
     */
    suspend fun insertStations(stations: List<ChargingStationEntity>) {
        chargingStationDao.insertStations(stations)
    }

    /**
     * Update a charging station.
     */
    suspend fun updateStation(station: ChargingStationEntity) {
        chargingStationDao.updateStation(station)
    }

    /**
     * Delete a charging station by ID.
     */
    suspend fun deleteStationById(id: String) {
        chargingStationDao.deleteStationById(id)
    }

    /**
     * Get a charging station by its internal ID.
     */
    suspend fun getStationById(id: String): ChargingStationEntity? {
        return chargingStationDao.getStationById(id)
    }

    /**
     * Get a charging station by external ID.
     */
    suspend fun getStationByExternalId(externalId: String): ChargingStationEntity? {
        return chargingStationDao.getStationByExternalId(externalId)
    }

    /**
     * Get all charging stations.
     */
    fun getAllStations(): Flow<List<ChargingStationEntity>> {
        val primaryFlow = try { chargingStationDao.getAllStations() } catch (e: Exception) { null }
        val fallbackFlow = primaryFlow ?: try { chargingStationDao.getAllChargingStations() } catch (e: Exception) { null }
        return fallbackFlow ?: flowOf(emptyList())
    }

    private fun calculateHaversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371.0 * c
    }

    /**
     * Get stations near a given coordinate within a radius in kilometers.
     * Uses bounding-box prefiltering combined with exact Haversine distance filtering and radial sorting.
     */
    fun getStationsNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 10.0
    ): Flow<List<ChargingStationEntity>> {
        if (radiusKm <= 0.0 || radiusKm.isNaN() || radiusKm.isInfinite() ||
            latitude.isNaN() || longitude.isNaN() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return flowOf(emptyList())
        }
        val cosLat = Math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val latDelta = radiusKm / 111.32
        val lngDelta = radiusKm / (111.32 * cosLat)
        
        val minLat = (latitude - latDelta).coerceIn(-90.0, 90.0)
        val maxLat = (latitude + latDelta).coerceIn(-90.0, 90.0)
        val minLng = (longitude - lngDelta).coerceIn(-180.0, 180.0)
        val maxLng = (longitude + lngDelta).coerceIn(-180.0, 180.0)
        
        return chargingStationDao.getStationsNearby(minLat, maxLat, minLng, maxLng).map { stations ->
            stations
                .map { station -> station to calculateHaversineKm(latitude, longitude, station.latitude, station.longitude) }
                .filter { it.second <= radiusKm }
                .sortedBy { it.second }
                .map { it.first }
        }
    }

    /**
     * Get stations near a given coordinate within a radius with accurate distance filtering.
     */
    fun getStationsNearbyWithDistanceFilter(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 10.0
    ): Flow<List<ChargingStationEntity>> {
        return getStationsNearbyOptimized(latitude, longitude, radiusKm)
    }

    /**
     * Get stations near a given coordinate within a radius using optimized spatial queries.
     * Uses bounding-box prefiltering combined with exact Haversine distance filtering and ascending radial sorting.
     */
    fun getStationsNearbyOptimized(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 10.0
    ): Flow<List<ChargingStationEntity>> {
        if (radiusKm <= 0.0 || radiusKm.isNaN() || radiusKm.isInfinite() ||
            latitude.isNaN() || longitude.isNaN() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return flowOf(emptyList())
        }
        val cosLat = Math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val latDelta = radiusKm / 111.32
        val lngDelta = radiusKm / (111.32 * cosLat)

        val minLat = (latitude - latDelta).coerceIn(-90.0, 90.0)
        val maxLat = (latitude + latDelta).coerceIn(-90.0, 90.0)
        val minLng = (longitude - lngDelta).coerceIn(-180.0, 180.0)
        val maxLng = (longitude + lngDelta).coerceIn(-180.0, 180.0)

        return chargingStationDao.getStationsNearby(minLat, maxLat, minLng, maxLng).map { stations ->
            stations
                .map { station -> station to calculateHaversineKm(latitude, longitude, station.latitude, station.longitude) }
                .filter { it.second <= radiusKm }
                .sortedBy { it.second }
                .map { it.first }
        }
    }

    /**
     * Get stations by connector type.
     */
    fun getStationsByConnectorType(connectorType: String): Flow<List<ChargingStationEntity>> {
        return chargingStationDao.getStationsByConnectorType(connectorType)
    }

    /**
     * Get stations by minimum power capability.
     */
    fun getStationsByMinimumPower(minPowerKw: Double): Flow<List<ChargingStationEntity>> {
        return chargingStationDao.getStationsByMinimumPower(minPowerKw)
    }

    /**
     * Get stations by operator name.
     */
    fun getStationsByOperator(operatorName: String): Flow<List<ChargingStationEntity>> {
        return chargingStationDao.getStationsByOperator(operatorName)
    }

    /**
     * Get stations by province.
     */
    fun getStationsByProvince(province: String): Flow<List<ChargingStationEntity>> {
        return chargingStationDao.getStationsByProvince(province)
    }

    /**
     * Get stations by city.
     */
    fun getStationsByCity(city: String): Flow<List<ChargingStationEntity>> {
        return chargingStationDao.getStationsByCity(city)
    }

    /**
     * Insert or update a charging port.
     */
    suspend fun insertPort(port: ChargingPortEntity) {
        chargingStationDao.insertPort(port)
    }

    /**
     * Insert or update multiple charging ports.
     */
    suspend fun insertPorts(ports: List<ChargingPortEntity>) {
        chargingStationDao.insertPorts(ports)
    }

    /**
     * Update a charging port.
     */
    suspend fun updatePort(port: ChargingPortEntity) {
        chargingStationDao.updatePort(port)
    }

    /**
     * Delete a charging port by ID.
     */
    suspend fun deletePortById(id: String) {
        chargingStationDao.deletePortById(id)
    }

    /**
     * Get all ports for a specific station.
     */
    fun getPortsForStation(stationId: String): Flow<List<ChargingPortEntity>> {
        return chargingStationDao.getPortsForStation(stationId)
    }

    /**
     * Get a specific port by ID.
     */
    suspend fun getPortById(id: String): ChargingPortEntity? {
        return chargingStationDao.getPortById(id)
    }

    /**
     * Get a station with its associated ports.
     */
    suspend fun getStationWithPorts(stationId: String): com.evnavigator.iran.data.charging.entity.ChargingStationWithPortsEntity? {
        return chargingStationDao.getStationWithPorts(stationId)
    }

    /**
     * Get all stations with their associated ports.
     */
    fun getAllStationsWithPorts(): Flow<List<com.evnavigator.iran.data.charging.entity.ChargingStationWithPortsEntity>> {
        return chargingStationDao.getAllStationsWithPorts()
    }
}