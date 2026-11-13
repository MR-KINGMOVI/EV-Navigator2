package com.evnavigator.iran.data.vehicle.dao

import androidx.room.*
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for vehicle entities.
 * Optimized with projection DTOs for efficient querying.
 */

// ============================================================================
// PROJECTION DTOs for efficient querying
// ============================================================================

/**
 * Projection DTO for vehicle list queries.
 * Contains fields needed for vehicle lists and basic filtering.
 */
data class VehicleListProjection(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val generation: String,
    val modelYear: Int,
    val trim: String,
    val market: String,
    val powertrainType: String,
    val curbMassKg: Double,
    val nominalBatteryCapacityWh: Double,
    val usableBatteryCapacityWh: Double,
    val motorCount: Int,
    val peakMotorPowerKw: Double?,
    val continuousMotorPowerKw: Double?,
    val acChargingMaxPowerKw: Double?,
    val dcChargingMaxPowerKw: Double?,
    val chargePortCount: Int,
    val connectorTypes: String,
    val confidence: Double,
    val validationStatus: String,
    val retrievedDate: Long,
    val updatedAt: Long
)

/**
 * Projection DTO for vehicle detail queries.
 * Contains all essential fields for vehicle details.
 */
data class VehicleDetailProjection(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val generation: String,
    val modelYear: Int,
    val trim: String,
    val market: String,
    val powertrainType: String,
    val curbMassKg: Double,
    val grossVehicleMassKg: Double?,
    val lengthMm: Int?,
    val widthMm: Int?,
    val heightMm: Int?,
    val wheelbaseMm: Int?,
    val groundClearanceMm: Int?,
    val dragCoefficient: Double?,
    val frontalAreaM2: Double?,
    val rollingResistanceCoefficient: Double?,
    val nominalBatteryCapacityWh: Double,
    val usableBatteryCapacityWh: Double,
    val batteryChemistry: String?,
    val nominalVoltageV: Double?,
    val batteryConfiguration: String?,
    val motorCount: Int,
    val frontMotorPowerKw: Double?,
    val rearMotorPowerKw: Double?,
    val peakMotorPowerKw: Double?,
    val continuousMotorPowerKw: Double?,
    val torqueNm: Int?,
    val drivetrainType: String,
    val baselineConsumptionWhPer100Km: Double?,
    val acChargingMaxPowerKw: Double?,
    val dcChargingMaxPowerKw: Double?,
    val chargingVoltageRangeV: String?,
    val chargingCurrentRangeA: String?,
    val chargePortCount: Int,
    val connectorTypes: String,
    val wheelDiameterMm: Int?,
    val tireWidthMm: Int?,
    val dataQuality: String,
    val source: String,
    val sourceVersion: String?,
    val retrievedDate: Long,
    val confidence: Double,
    val validationStatus: String,
    val notes: String?,
    val schemaVersion: String,
    val dataVersion: String,
    val vehicleRecordVersion: String,
    val createdAt: Long,
    val updatedAt: Long
)

// ============================================================================
@Dao
interface VehicleDao {

// Basic CRUD Operations
// ============================================================================

/**
 * Gets a vehicle by its unique ID
 */
@Query("SELECT * FROM vehicles WHERE id = :id")
suspend fun getVehicleById(id: String): VehicleEntity?

/**
 * Gets all vehicles
 */
@Query("SELECT * FROM vehicles")
suspend fun getAllVehicles(): List<VehicleEntity>

/**
 * Observes all vehicles reactively so UI updates immediately when DatabaseSeeder completes
 */
@Query("SELECT * FROM vehicles")
fun getAllVehiclesFlow(): Flow<List<VehicleEntity>>

/**
 * Gets vehicles by manufacturer
 * Uses projection for efficient data transfer.
 * Uses index on manufacturer for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE manufacturer = :manufacturer
""")
suspend fun getVehiclesByManufacturer(manufacturer: String): List<VehicleListProjection>

/**
 * Gets vehicles by model
 * Uses projection for efficient data transfer.
 * Uses index on model for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE model = :model
""")
suspend fun getVehiclesByModel(model: String): List<VehicleListProjection>

/**
 * Gets vehicles by powertrain type
 * Uses projection for efficient data transfer.
 * Uses index on powertrainType for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE powertrainType = :powertrainType
""")
suspend fun getVehiclesByPowertrain(powertrainType: String): List<VehicleListProjection>

/**
 * Gets vehicles by market
 * Uses projection for efficient data transfer.
 * Uses index on market for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE market = :market
""")
suspend fun getVehiclesByMarket(market: String): List<VehicleListProjection>

/**
 * Gets vehicles by model year
 * Uses projection for efficient data transfer.
 * Uses index on modelYear for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE modelYear = :modelYear
""")
suspend fun getVehiclesByYear(modelYear: Int): List<VehicleListProjection>

/**
 * Gets vehicles by trim
 * Uses projection for efficient data transfer.
 * Uses index on trim for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE trim = :trim
""")
suspend fun getVehiclesByTrim(trim: String): List<VehicleListProjection>

/**
 * Search vehicles by name or model (case-insensitive)
 * Uses projection for efficient data transfer.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE LOWER(name) LIKE LOWER(:query) OR LOWER(model) LIKE LOWER(:query)
""")
suspend fun searchVehicles(query: String): List<VehicleListProjection>

/**
 * Gets vehicle variants for a specific identity
 * Uses projection for efficient data transfer.
 * Uses composite index (manufacturer, model, generation, modelYear) for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE manufacturer = :manufacturer AND model = :model AND generation = :generation AND modelYear = :modelYear
""")
suspend fun getVehicleVariants(
    manufacturer: String,
    model: String,
    generation: String,
    modelYear: Int
): List<VehicleListProjection>

/**
 * Gets default vehicle (if one exists)
 * Uses projection for efficient data transfer.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE name = 'Default Vehicle' LIMIT 1
""")
suspend fun getDefaultVehicle(): VehicleListProjection?

/**
 * Gets vehicles by manufacturer and model
 * Uses projection for efficient data transfer.
 * Uses composite index (manufacturer, model) for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE manufacturer = :manufacturer AND model = :model
""")
suspend fun getVehiclesByManufacturerAndModel(manufacturer: String, model: String): List<VehicleListProjection>

/**
 * Gets vehicles by powertrain type and market
 * Uses projection for efficient data transfer.
 * Uses composite index (powertrainType, market) for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE powertrainType = :powertrainType AND market = :market
""")
suspend fun getVehiclesByPowertrainAndMarket(powertrainType: String, market: String): List<VehicleListProjection>

/**
 * Gets vehicles by model year and market
 * Uses projection for efficient data transfer.
 * Uses composite index (modelYear, market) for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE modelYear = :modelYear AND market = :market
""")
suspend fun getVehiclesByYearAndMarket(modelYear: Int, market: String): List<VehicleListProjection>

/**
 * Gets vehicles by manufacturer and model year
 * Uses projection for efficient data transfer.
 * Uses composite index (manufacturer, modelYear) for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE manufacturer = :manufacturer AND modelYear = :modelYear
""")
suspend fun getVehiclesByManufacturerAndYear(manufacturer: String, modelYear: Int): List<VehicleListProjection>

/**
 * Gets vehicles by trim and model year
 * Uses projection for efficient data transfer.
 * Uses composite index (trim, modelYear) for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE trim = :trim AND modelYear = :modelYear
""")
suspend fun getVehiclesByTrimAndYear(trim: String, modelYear: Int): List<VehicleListProjection>

/**
 * Gets vehicles by schema version
 * Uses projection for efficient data transfer.
 * Uses index on schemaVersion for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE schemaVersion = :schemaVersion
""")
suspend fun getVehiclesBySchemaVersion(schemaVersion: String): List<VehicleListProjection>

/**
 * Gets vehicles by data version
 * Uses projection for efficient data transfer.
 * Uses index on dataVersion for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE dataVersion = :dataVersion
""")
suspend fun getVehiclesByDataVersion(dataVersion: String): List<VehicleListProjection>

/**
 * Gets vehicles by source
 * Uses projection for efficient data transfer.
 * Uses index on source for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE source = :source
""")
suspend fun getVehiclesBySource(source: String): List<VehicleListProjection>

/**
 * Gets vehicles by source version
 * Uses projection for efficient data transfer.
 * Uses index on sourceVersion for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE sourceVersion = :sourceVersion
""")
suspend fun getVehiclesBySourceVersion(sourceVersion: String): List<VehicleListProjection>

/**
 * Gets vehicles by confidence threshold
 * Uses projection for efficient data transfer.
 * Uses index on confidence for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE confidence >= :minConfidence
""")
suspend fun getVehiclesByConfidence(minConfidence: Double): List<VehicleListProjection>

/**
 * Gets vehicles by validation status
 * Uses projection for efficient data transfer.
 * Uses index on validationStatus for efficient filtering.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        motorCount, peakMotorPowerKw, continuousMotorPowerKw, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargePortCount, connectorTypes, confidence,
        validationStatus, retrievedDate, updatedAt
    FROM vehicles
    WHERE validationStatus = :validationStatus
""")
suspend fun getVehiclesByValidationStatus(validationStatus: String): List<VehicleListProjection>

/**
 * Inserts or replaces a vehicle entity
 */
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertVehicle(vehicle: VehicleEntity)

/**
 * Inserts or replaces multiple vehicle entities
 */
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertVehicles(vehicles: List<VehicleEntity>)

/**
 * Updates a vehicle entity
 */
@Update
suspend fun updateVehicle(vehicle: VehicleEntity)

/**
 * Deletes a vehicle entity
 */
@Delete
suspend fun deleteVehicle(vehicle: VehicleEntity)

/**
 * Deletes all vehicles
 */
@Query("DELETE FROM vehicles")
suspend fun deleteAllVehicles()

/**
 * Deletes a vehicle by ID
 */
@Query("DELETE FROM vehicles WHERE id = :id")
suspend fun deleteVehicleById(id: String)

/**
 * Updates the timestamp for a vehicle
 */
@Query("UPDATE vehicles SET updatedAt = :timestamp WHERE id = :id")
suspend fun updateVehicleTimestamp(id: String, timestamp: Long)

/**
 * Count total vehicles
 */
@Query("SELECT COUNT(*) FROM vehicles")
suspend fun countVehicles(): Int

/**
 * Count vehicles by manufacturer
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE manufacturer = :manufacturer")
suspend fun countVehiclesByManufacturer(manufacturer: String): Int

/**
 * Count vehicles by model
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE model = :model")
suspend fun countVehiclesByModel(model: String): Int

/**
 * Count vehicles by powertrain type
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE powertrainType = :powertrainType")
suspend fun countVehiclesByPowertrain(powertrainType: String): Int

/**
 * Count vehicles by market
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE market = :market")
suspend fun countVehiclesByMarket(market: String): Int

/**
 * Count vehicles by model year
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE modelYear = :modelYear")
suspend fun countVehiclesByYear(modelYear: Int): Int

/**
 * Count vehicles by trim
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE trim = :trim")
suspend fun countVehiclesByTrim(trim: String): Int

/**
 * Count vehicles by schema version
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE schemaVersion = :schemaVersion")
suspend fun countVehiclesBySchemaVersion(schemaVersion: String): Int

/**
 * Count vehicles by data version
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE dataVersion = :dataVersion")
suspend fun countVehiclesByDataVersion(dataVersion: String): Int

/**
 * Count vehicles by source
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE source = :source")
suspend fun countVehiclesBySource(source: String): Int

/**
 * Count vehicles by source version
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE sourceVersion = :sourceVersion")
suspend fun countVehiclesBySourceVersion(sourceVersion: String): Int

/**
 * Count vehicles by confidence threshold
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE confidence >= :minConfidence")
suspend fun countVehiclesByConfidence(minConfidence: Double): Int

/**
 * Count vehicles by validation status
 */
@Query("SELECT COUNT(*) FROM vehicles WHERE validationStatus = :validationStatus")
suspend fun countVehiclesByValidationStatus(validationStatus: String): Int

/**
 * Gets vehicle details by ID
 * Uses projection to reduce memory usage.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, grossVehicleMassKg, lengthMm, widthMm, heightMm,
        wheelbaseMm, groundClearanceMm, dragCoefficient, frontalAreaM2,
        rollingResistanceCoefficient, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        batteryChemistry, nominalVoltageV, batteryConfiguration, motorCount,
        frontMotorPowerKw, rearMotorPowerKw, peakMotorPowerKw, continuousMotorPowerKw,
        torqueNm, drivetrainType, baselineConsumptionWhPer100Km, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargingVoltageRangeV, chargingCurrentRangeA, chargePortCount,
        connectorTypes, wheelDiameterMm, tireWidthMm, dataQuality, source, sourceVersion,
        retrievedDate, confidence, validationStatus, notes, schemaVersion, dataVersion,
        vehicleRecordVersion, createdAt, updatedAt
    FROM vehicles
    WHERE id = :id
""")
suspend fun getVehicleDetail(id: String): VehicleDetailProjection?

/**
 * Gets vehicle details by name
 * Uses projection to reduce memory usage.
 */
@Query("""
    SELECT 
        id, name, manufacturer, model, generation, modelYear, trim, market,
        powertrainType, curbMassKg, grossVehicleMassKg, lengthMm, widthMm, heightMm,
        wheelbaseMm, groundClearanceMm, dragCoefficient, frontalAreaM2,
        rollingResistanceCoefficient, nominalBatteryCapacityWh, usableBatteryCapacityWh,
        batteryChemistry, nominalVoltageV, batteryConfiguration, motorCount,
        frontMotorPowerKw, rearMotorPowerKw, peakMotorPowerKw, continuousMotorPowerKw,
        torqueNm, drivetrainType, baselineConsumptionWhPer100Km, acChargingMaxPowerKw,
        dcChargingMaxPowerKw, chargingVoltageRangeV, chargingCurrentRangeA, chargePortCount,
        connectorTypes, wheelDiameterMm, tireWidthMm, dataQuality, source, sourceVersion,
        retrievedDate, confidence, validationStatus, notes, schemaVersion, dataVersion,
        vehicleRecordVersion, createdAt, updatedAt
    FROM vehicles
    WHERE name = :name
""")
suspend fun getVehicleDetailByName(name: String): VehicleDetailProjection?

}
