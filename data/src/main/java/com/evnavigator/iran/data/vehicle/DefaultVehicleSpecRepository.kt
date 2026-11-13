package com.evnavigator.iran.data.vehicle

import com.evnavigator.iran.data.vehicle.dao.VehicleSpecDao
import kotlinx.coroutines.flow.Flow

/**
 * Default implementation of [VehicleSpecRepository].
 *
 * This repository provides a clean interface for data access and manipulation
 * of vehicle-related information, abstracting away the underlying data source.
 */
class DefaultVehicleSpecRepository(
    private val vehicleSpecDao: VehicleSpecDao
) : VehicleSpecRepository {

    override fun getAllVehicleSpecs(): Flow<List<VehicleSpecEntity>> {
        return vehicleSpecDao.getAllVehicleSpecs()
    }

    override suspend fun getVehicleSpecById(id: String): VehicleSpecEntity? {
        return vehicleSpecDao.getVehicleSpecById(id)
    }

    override suspend fun getVehicleSpecsByMake(make: String): List<VehicleSpecEntity> {
        return vehicleSpecDao.getVehicleSpecsByMake(make)
    }

    override suspend fun getVehicleSpecsByModel(model: String): List<VehicleSpecEntity> {
        return vehicleSpecDao.getVehicleSpecsByModel(model)
    }

    override suspend fun getLatestVehicleSpecByMakeModel(make: String, model: String): VehicleSpecEntity? {
        return vehicleSpecDao.getLatestVehicleSpecByMakeModel(make, model)
    }

    override suspend fun getVehicleSpecsByYear(year: Int): List<VehicleSpecEntity> {
        return vehicleSpecDao.getVehicleSpecsByYear(year)
    }

    override suspend fun insertVehicleSpec(vehicleSpec: VehicleSpecEntity): Long {
        return vehicleSpecDao.insertVehicleSpec(vehicleSpec)
    }

    override suspend fun updateVehicleSpec(vehicleSpec: VehicleSpecEntity): Int {
        return vehicleSpecDao.updateVehicleSpec(vehicleSpec)
    }

    override suspend fun deleteVehicleSpecById(id: String): Int {
        return vehicleSpecDao.deleteVehicleSpecById(id)
    }

    override fun getActiveVehicleSpecs(): Flow<List<VehicleSpecEntity>> {
        return vehicleSpecDao.getActiveVehicleSpecs()
    }

    override suspend fun countVehicleSpecs(): Int {
        return vehicleSpecDao.countVehicleSpecs()
    }

    override suspend fun getVehicleSpecsBySourceType(sourceType: String): List<VehicleSpecEntity> {
        return vehicleSpecDao.getVehicleSpecsBySourceType(sourceType)
    }
}