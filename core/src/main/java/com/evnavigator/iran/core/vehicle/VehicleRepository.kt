package com.evnavigator.iran.core.vehicle

/**
 * Repository interface for vehicle data operations.
 */
interface VehicleRepository {
    suspend fun getVehicleById(id: String): Vehicle?
    suspend fun getAllVehicles(): List<Vehicle>
    suspend fun getVehiclesByManufacturer(manufacturer: String): List<Vehicle>
    suspend fun getVehiclesByModel(model: String): List<Vehicle>
    suspend fun getVehiclesByPowertrain(powertrainType: String): List<Vehicle>
    suspend fun getVehiclesByMarket(market: String): List<Vehicle>
    suspend fun getVehiclesByYear(modelYear: Int): List<Vehicle>
    suspend fun getVehiclesByTrim(trim: String): List<Vehicle>
    suspend fun searchVehicles(query: String): List<Vehicle>
    suspend fun getVehicleVariants(
        manufacturer: String,
        model: String,
        generation: String,
        modelYear: Int
    ): List<Vehicle>
    suspend fun getDefaultVehicle(): Vehicle?
    suspend fun insertVehicle(vehicle: Vehicle)
    suspend fun insertVehicles(vehicles: List<Vehicle>)
    suspend fun updateVehicle(vehicle: Vehicle)
    suspend fun deleteVehicleById(id: String)
    suspend fun deleteAllVehicles()
    suspend fun getVehicleCount(): Int
    suspend fun getVehicleByFullIdentity(vehicleId: VehicleId): Vehicle?
    suspend fun resolveConflictingVehicles(vehicles: List<Vehicle>): Vehicle?
}
