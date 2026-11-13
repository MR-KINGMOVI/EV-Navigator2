package com.evnavigator.iran.data.di

import android.content.Context
import com.evnavigator.iran.data.charging.dao.ChargingStationDao
import com.evnavigator.iran.data.database.AppDatabase
import com.evnavigator.iran.data.download.OfflineMapPackageManager
import com.evnavigator.iran.data.map.dao.RoadGraphDao
import com.evnavigator.iran.data.map.dao.RoadNetworkDao
import com.evnavigator.iran.data.repository.*
import com.evnavigator.iran.data.vehicle.dao.VehicleDao
import com.evnavigator.iran.data.vehicle.repository.ActiveVehicleRepository
import com.evnavigator.iran.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val databaseInitScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val database = AppDatabase.getInstance(context)
        databaseInitScope.launch {
            try {
                com.evnavigator.iran.data.seed.DatabaseSeeder.seedIfEmpty(database)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DatabaseModule", "Failed to seed initial EV Navigator database", e)
            }
        }
        return database
    }

    @Provides
    fun provideChargingStationDao(database: AppDatabase): ChargingStationDao {
        return database.chargingStationDao()
    }

    @Provides
    fun provideVehicleDao(database: AppDatabase): VehicleDao {
        return database.vehicleDao()
    }

    @Provides
    fun provideRoadNetworkDao(database: AppDatabase): RoadNetworkDao {
        return database.roadNetworkDao()
    }

    @Provides
    fun provideRoadGraphDao(database: AppDatabase): RoadGraphDao {
        return database.roadGraphDao()
    }

    @Provides
    fun provideElevationDao(database: AppDatabase): com.evnavigator.iran.data.elevation.dao.ElevationDao {
        return database.elevationDao()
    }

    @Provides
    fun provideVehicleSpecDao(database: AppDatabase): com.evnavigator.iran.data.vehicle.dao.VehicleSpecDao {
        return database.vehicleSpecDao()
    }

    @Provides
    fun provideActiveJourneyDao(database: AppDatabase): com.evnavigator.iran.data.journey.ActiveJourneyDao {
        return database.activeJourneyDao()
    }

    @Provides
    fun provideEnvironmentCacheDao(database: AppDatabase): com.evnavigator.iran.data.cache.EnvironmentCacheDao {
        return database.environmentCacheDao()
    }

    @Provides
    @Singleton
    fun provideElevationRepository(
        elevationDao: com.evnavigator.iran.data.elevation.dao.ElevationDao
    ): com.evnavigator.iran.data.elevation.ElevationRepository {
        return com.evnavigator.iran.data.elevation.DefaultElevationRepository(elevationDao)
    }

    @Provides
    @Singleton
    fun provideVehicleSpecRepository(
        vehicleSpecDao: com.evnavigator.iran.data.vehicle.dao.VehicleSpecDao
    ): com.evnavigator.iran.data.vehicle.VehicleSpecRepository {
        return com.evnavigator.iran.data.vehicle.DefaultVehicleSpecRepository(vehicleSpecDao)
    }

    @Provides
    fun provideTripHistoryDao(database: AppDatabase): com.evnavigator.iran.data.intelligence.dao.TripHistoryDao {
        return database.tripHistoryDao()
    }

    @Provides
    fun provideIntelligencePreferencesDao(database: AppDatabase): com.evnavigator.iran.data.intelligence.dao.IntelligencePreferencesDao {
        return database.intelligencePreferencesDao()
    }

    @Provides
    @Singleton
    fun provideTripHistoryPrivacyRepository(
        tripHistoryDao: com.evnavigator.iran.data.intelligence.dao.TripHistoryDao,
        preferencesDao: com.evnavigator.iran.data.intelligence.dao.IntelligencePreferencesDao
    ): com.evnavigator.iran.data.intelligence.repository.TripHistoryPrivacyRepository {
        return com.evnavigator.iran.data.intelligence.repository.TripHistoryPrivacyRepository(
            tripHistoryDao,
            preferencesDao
        )
    }

    // =========================================================================
    // V11 Canonical Domain Repository Bindings
    // =========================================================================

    @Provides
    @Singleton
    fun provideVehicleCatalogRepository(
        vehicleDao: VehicleDao
    ): VehicleCatalogRepository {
        return DefaultVehicleCatalogRepository(vehicleDao)
    }

    @Provides
    @Singleton
    fun provideActiveVehicleStateRepository(
        activeVehicleRepository: ActiveVehicleRepository
    ): ActiveVehicleStateRepository {
        return DefaultActiveVehicleStateRepository(activeVehicleRepository)
    }

    @Provides
    @Singleton
    fun provideChargingStationCatalogRepository(
        chargingStationDao: ChargingStationDao
    ): ChargingStationCatalogRepository {
        return DefaultChargingStationCatalogRepository(chargingStationDao)
    }

    @Provides
    @Singleton
    fun provideActiveJourneyStateRepository(
        activeJourneyDao: com.evnavigator.iran.data.journey.ActiveJourneyDao
    ): ActiveJourneyStateRepository {
        return DefaultActiveJourneyStateRepository(activeJourneyDao)
    }

    @Provides
    @Singleton
    fun provideTripHistoryRepository(
        tripHistoryDao: com.evnavigator.iran.data.intelligence.dao.TripHistoryDao
    ): TripHistoryRepository {
        return DefaultTripHistoryRepository(tripHistoryDao)
    }

    @Provides
    @Singleton
    fun provideOfflinePackageRepository(
        packageManager: OfflineMapPackageManager
    ): OfflinePackageRepository {
        return packageManager
    }

    @Provides
    @Singleton
    fun provideEnvironmentCacheStore(
        environmentCacheDao: com.evnavigator.iran.data.cache.EnvironmentCacheDao
    ): EnvironmentCacheStore {
        return RoomEnvironmentCacheStore(environmentCacheDao)
    }
}
