package com.evnavigator.iran.core.vehicle

import kotlinx.coroutines.flow.Flow

/**
 * Service for searching and filtering vehicles.
 *
 * Provides domain-level search capabilities that are independent of UI concerns.
 */
class VehicleSearchService(
    private val vehicleRepository: VehicleRepository
) {
    
    /**
     * Searches vehicles by a query string (name or model)
     */
    suspend fun searchVehicles(query: String): List<Vehicle> {
        return vehicleRepository.searchVehicles(query)
    }
    
    /**
     * Gets vehicles by manufacturer
     */
    suspend fun getVehiclesByManufacturer(manufacturer: String): List<Vehicle> {
        return vehicleRepository.getVehiclesByManufacturer(manufacturer)
    }
    
    /**
     * Gets vehicles by model
     */
    suspend fun getVehiclesByModel(model: String): List<Vehicle> {
        return vehicleRepository.getVehiclesByModel(model)
    }
    
    /**
     * Gets vehicles by powertrain type
     */
    suspend fun getVehiclesByPowertrain(powertrainType: PowertrainType): List<Vehicle> {
        return vehicleRepository.getVehiclesByPowertrain(powertrainType.name)
    }
    
    /**
     * Gets vehicles by market
     */
    suspend fun getVehiclesByMarket(market: String): List<Vehicle> {
        return vehicleRepository.getVehiclesByMarket(market)
    }
    
    /**
     * Gets vehicles by model year
     */
    suspend fun getVehiclesByYear(modelYear: Int): List<Vehicle> {
        return vehicleRepository.getVehiclesByYear(modelYear)
    }
    
    /**
     * Gets vehicles by trim
     */
    suspend fun getVehiclesByTrim(trim: String): List<Vehicle> {
        return vehicleRepository.getVehiclesByTrim(trim)
    }
    
    /**
     * Gets vehicle variants for a specific identity
     */
    suspend fun getVehicleVariants(
        manufacturer: String,
        model: String,
        generation: String,
        modelYear: Int
    ): List<Vehicle> {
        return vehicleRepository.getVehicleVariants(manufacturer, model, generation, modelYear)
    }
    
    /**
     * Gets all unique manufacturers
     */
    suspend fun getAllManufacturers(): Set<String> {
        val allVehicles = vehicleRepository.getAllVehicles()
        return allVehicles.map { it.id.manufacturer }.toSet()
    }
    
    /**
     * Gets all unique models for a manufacturer
     */
    suspend fun getModelsByManufacturer(manufacturer: String): Set<String> {
        val vehicles = vehicleRepository.getVehiclesByManufacturer(manufacturer)
        return vehicles.map { it.id.model }.toSet()
    }
    
    /**
     * Gets all unique generations for a manufacturer and model
     */
    suspend fun getGenerationsByModel(manufacturer: String, model: String): Set<String> {
        val vehicles = vehicleRepository.getVehiclesByManufacturer(manufacturer)
            .filter { it.id.model == model }
        return vehicles.map { it.id.generation }.toSet()
    }
    
    /**
     * Gets all unique trims for a manufacturer, model and generation
     */
    suspend fun getTrimsByGeneration(
        manufacturer: String,
        model: String,
        generation: String
    ): Set<String> {
        val vehicles = vehicleRepository.getVehiclesByManufacturer(manufacturer)
            .filter { it.id.model == model && it.id.generation == generation }
        return vehicles.map { it.id.trim }.toSet()
    }
    
    /**
     * Gets all unique years for a manufacturer, model and generation
     */
    suspend fun getYearsByGeneration(
        manufacturer: String,
        model: String,
        generation: String
    ): Set<Int> {
        val vehicles = vehicleRepository.getVehiclesByManufacturer(manufacturer)
            .filter { it.id.model == model && it.id.generation == generation }
        return vehicles.map { it.id.modelYear }.toSet()
    }
    
    /**
     * Gets all unique markets
     */
    suspend fun getAllMarkets(): Set<String> {
        val allVehicles = vehicleRepository.getAllVehicles()
        return allVehicles.map { it.id.market }.toSet()
    }
    
    /**
     * Gets all unique powertrain types
     */
    suspend fun getAllPowertrainTypes(): Set<PowertrainType> {
        val allVehicles = vehicleRepository.getAllVehicles()
        return allVehicles.map { it.id.powertrainType }.toSet()
    }
    
    /**
     * Searches vehicles with multiple criteria
     */
    suspend fun searchVehicles(
        manufacturer: String? = null,
        model: String? = null,
        year: Int? = null,
        trim: String? = null,
        market: String? = null,
        powertrainType: PowertrainType? = null
    ): List<Vehicle> {
        // Start with all vehicles
        var results = vehicleRepository.getAllVehicles()
        
        // Apply filters one by one
        manufacturer?.let { m -> results = results.filter { it.id.manufacturer.equals(m, ignoreCase = true) } }
        
        model?.let { m -> results = results.filter { it.id.model.equals(m, ignoreCase = true) } }
        
        year?.let { 
            results = results.filter { it.id.modelYear == year }
        }
        
        trim?.let { t -> results = results.filter { it.id.trim.equals(t, ignoreCase = true) } }
        
        market?.let { m -> results = results.filter { it.id.market.equals(m, ignoreCase = true) } }
        
        powertrainType?.let { 
            results = results.filter { it.id.powertrainType == powertrainType }
        }
        
        return results
    }
    
    /**
     * Gets a vehicle by its complete identity (manufacturer, model, generation, year, trim, market, powertrain)
     */
    suspend fun getVehicleByFullIdentity(vehicleId: VehicleId): Vehicle? {
        return vehicleRepository.getVehicleByFullIdentity(vehicleId)
    }
}