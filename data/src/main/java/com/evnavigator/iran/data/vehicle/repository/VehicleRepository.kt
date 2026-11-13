package com.evnavigator.iran.data.vehicle.repository

import com.evnavigator.iran.core.vehicle.Vehicle
import com.evnavigator.iran.core.vehicle.VehicleId
import com.evnavigator.iran.data.vehicle.dao.VehicleDao
import com.evnavigator.iran.data.vehicle.mapper.VehicleMapper.toDomainModel
import com.evnavigator.iran.data.vehicle.mapper.VehicleMapper.toEntity

/**
 * Repository for vehicle data operations.
 *
 * Provides a clean interface between the domain layer and the data layer,
 * handling all vehicle persistence logic.
 */
class VehicleRepository(
    private val vehicleDao: VehicleDao
) : com.evnavigator.iran.core.vehicle.VehicleRepository {
    
    /**
     * Gets a vehicle by its unique ID
     */
    override suspend fun getVehicleById(id: String): Vehicle? {
        return vehicleDao.getVehicleById(id)?.toDomainModel()
    }
    
    /**
     * Gets all vehicles in the database
     */
    override suspend fun getAllVehicles(): List<Vehicle> {
        return vehicleDao.getAllVehicles().map { it.toDomainModel() }
    }
    
    /**
     * Gets vehicles by manufacturer
     */
    override suspend fun getVehiclesByManufacturer(manufacturer: String): List<Vehicle> {
        return vehicleDao.getVehiclesByManufacturer(manufacturer).mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Gets vehicles by model
     */
    override suspend fun getVehiclesByModel(model: String): List<Vehicle> {
        return vehicleDao.getVehiclesByModel(model).mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Gets vehicles by powertrain type
     */
    override suspend fun getVehiclesByPowertrain(powertrainType: String): List<Vehicle> {
        return vehicleDao.getVehiclesByPowertrain(powertrainType).mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Gets vehicles by market
     */
    override suspend fun getVehiclesByMarket(market: String): List<Vehicle> {
        return vehicleDao.getVehiclesByMarket(market).mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Gets vehicles by model year
     */
    override suspend fun getVehiclesByYear(modelYear: Int): List<Vehicle> {
        return vehicleDao.getVehiclesByYear(modelYear).mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Gets vehicles by trim
     */
    override suspend fun getVehiclesByTrim(trim: String): List<Vehicle> {
        return vehicleDao.getVehiclesByTrim(trim).mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Searches vehicles by name or model (case-insensitive)
     */
    override suspend fun searchVehicles(query: String): List<Vehicle> {
        return vehicleDao.searchVehicles(query).mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Gets vehicle variants for a specific identity
     */
    override suspend fun getVehicleVariants(
        manufacturer: String,
        model: String,
        generation: String,
        modelYear: Int
    ): List<Vehicle> {
        return vehicleDao.getVehicleVariants(manufacturer, model, generation, modelYear)
            .mapNotNull { getVehicleById(it.id) }
    }
    
    /**
     * Gets the default vehicle (if one exists)
     */
    override suspend fun getDefaultVehicle(): Vehicle? {
        val defaultProjection = vehicleDao.getDefaultVehicle() ?: return null
        return getVehicleById(defaultProjection.id)
    }
    
    /**
     * Inserts or updates a vehicle
     */
    override suspend fun insertVehicle(vehicle: Vehicle) {
        val entity = vehicle.toEntity()
        vehicleDao.insertVehicle(entity)
    }
    
    /**
     * Inserts or updates multiple vehicles
     */
    override suspend fun insertVehicles(vehicles: List<Vehicle>) {
        val entities = vehicles.map { it.toEntity() }
        vehicleDao.insertVehicles(entities)
    }
    
    /**
     * Updates a vehicle
     */
    override suspend fun updateVehicle(vehicle: Vehicle) {
        val entity = vehicle.toEntity()
        vehicleDao.updateVehicle(entity)
    }
    
    /**
     * Deletes a vehicle by ID
     */
    override suspend fun deleteVehicleById(id: String) {
        vehicleDao.deleteVehicleById(id)
    }
    
    /**
     * Deletes all vehicles
     */
    override suspend fun deleteAllVehicles() {
        vehicleDao.deleteAllVehicles()
    }
    
    /**
     * Gets count of all vehicles
     */
    override suspend fun getVehicleCount(): Int {
        return vehicleDao.countVehicles()
    }
    
    /**
     * Gets a vehicle by its VehicleId (complete identity)
     */
    override suspend fun getVehicleByFullIdentity(vehicleId: VehicleId): Vehicle? {
        return getVehicleById(vehicleId.id)
    }
    
    /**
     * Resolves conflicting vehicle specifications for the same identity
     */
    override suspend fun resolveConflictingVehicles(vehicles: List<Vehicle>): Vehicle? {
        return vehicles.firstOrNull()
    }
}
