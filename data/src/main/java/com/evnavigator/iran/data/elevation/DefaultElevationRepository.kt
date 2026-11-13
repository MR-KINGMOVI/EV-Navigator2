package com.evnavigator.iran.data.elevation

import com.evnavigator.iran.data.elevation.dao.ElevationDao
import kotlinx.coroutines.flow.Flow

/**
 * Default implementation of [ElevationRepository].
 *
 * This repository provides a clean interface for data access and manipulation
 * of elevation-related information, abstracting away the underlying data source.
 */
class DefaultElevationRepository(
    private val elevationDao: ElevationDao
) : ElevationRepository {

    override fun getAllElevationDatasets(): Flow<List<ElevationDataset>> {
        return elevationDao.getAllElevationDatasets()
    }

    override suspend fun getElevationDatasetById(id: String): ElevationDataset? {
        return elevationDao.getElevationDatasetById(id)
    }

    override suspend fun getElevationDatasetsBySource(source: String): List<ElevationDataset> {
        return elevationDao.getElevationDatasetsBySource(source)
    }

    override suspend fun getLatestElevationDatasetBySource(source: String): ElevationDataset? {
        return elevationDao.getLatestElevationDatasetBySource(source)
    }

    override suspend fun insertElevationDataset(elevationDataset: ElevationDataset): Long {
        return elevationDao.insertElevationDataset(elevationDataset)
    }

    override suspend fun updateElevationDataset(elevationDataset: ElevationDataset): Int {
        return elevationDao.updateElevationDataset(elevationDataset)
    }

    override suspend fun deleteElevationDatasetById(id: String): Int {
        return elevationDao.deleteElevationDatasetById(id)
    }

    override fun getInstalledElevationDatasets(): Flow<List<ElevationDataset>> {
        return elevationDao.getInstalledElevationDatasets()
    }

    override suspend fun countElevationDatasets(): Int {
        return elevationDao.countElevationDatasets()
    }

    override suspend fun getElevationDatasetsBySourceType(sourceType: String): List<ElevationDataset> {
        return elevationDao.getElevationDatasetsBySourceType(sourceType)
    }
}