package com.evnavigator.iran.data.seed

import androidx.room.withTransaction
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.database.AppDatabase
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Production database seeder that initializes the Room database on first run
 * with verified vehicle specifications and production-eligible Iranian charging stations.
 *
 * V12.6 P0-4 Invariant:
 * By default (`includeSeedCandidates = false`), only production-eligible stations
 * (`VERIFIED` and `PARTIALLY_VERIFIED`) from [IranStationsCatalog.getProductionStationsAndPorts]
 * are seeded into the production Room database; unverified corridor survey seed candidates
 * are excluded from production builds unless explicitly requested for survey/diagnostic mode.
 */
object DatabaseSeeder {

    private val seedMutex = Mutex()

    suspend fun seedIfEmpty(
        database: AppDatabase,
        includeSeedCandidates: Boolean = false
    ) = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            database.withTransaction {
                val vehicleCount = try {
                    database.vehicleDao().countVehicles()
                } catch (e: Exception) {
                    0
                }

                if (vehicleCount < 25) {
                    database.vehicleDao().insertVehicles(getInitialVehicles())
                }

                val stationCount = try {
                    database.chargingStationDao().countStations()
                } catch (e: Exception) {
                    0
                }

                val (stations, ports) = getInitialStationsAndPorts(includeSeedCandidates = includeSeedCandidates)
                if (stationCount < stations.size) {
                    database.chargingStationDao().insertStations(stations)
                    for (port in ports) {
                        database.chargingStationDao().insertPort(port)
                    }
                }
            }
        }
    }

    fun getInitialVehicles(): List<VehicleEntity> {
        val catalogVehicles = IranVehiclesCatalog.getAllVehicles()
        val fleetVehicles = IranFleetVehiclesCatalog.getFleetVehicles()
        return (catalogVehicles + fleetVehicles).distinctBy { it.id }
    }

    fun getInitialStationsAndPorts(
        includeSeedCandidates: Boolean = false
    ): Pair<List<ChargingStationEntity>, List<ChargingPortEntity>> {
        return if (includeSeedCandidates) {
            IranStationsCatalog.getAllStationsAndPorts(includeSeedCandidates = true)
        } else {
            IranStationsCatalog.getProductionStationsAndPorts()
        }
    }
}
