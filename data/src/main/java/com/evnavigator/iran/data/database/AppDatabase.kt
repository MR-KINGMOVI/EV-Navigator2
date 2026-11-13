package com.evnavigator.iran.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.evnavigator.iran.data.charging.dao.ChargingStationDao
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.elevation.dao.ElevationDao
import com.evnavigator.iran.data.map.dao.RoadGraphDao
import com.evnavigator.iran.data.map.dao.RoadNetworkDao
import com.evnavigator.iran.data.map.entity.RoadAttributeEntity
import com.evnavigator.iran.data.map.entity.RoadNetworkEntity
import com.evnavigator.iran.data.map.entity.RoadNodeEntity
import com.evnavigator.iran.data.map.entity.RoadRestrictionEntity
import com.evnavigator.iran.data.map.entity.RoadSegmentEntity
import com.evnavigator.iran.data.vehicle.dao.VehicleDao
import com.evnavigator.iran.data.vehicle.dao.VehicleSpecDao
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity

/**
 * Main Room database for EV Navigator Iran.
 *
 * Persists:
 * - Charging stations and ports
 * - Vehicle specifications and profiles
 * - Road networks, nodes, segments, restrictions, and attributes
 */
@Database(
    entities = [
        ChargingStationEntity::class,
        ChargingPortEntity::class,
        VehicleEntity::class,
        RoadNetworkEntity::class,
        RoadNodeEntity::class,
        RoadSegmentEntity::class,
        RoadRestrictionEntity::class,
        RoadAttributeEntity::class,
        com.evnavigator.iran.data.intelligence.entity.TripHistorySummaryEntity::class,
        com.evnavigator.iran.data.intelligence.entity.IntelligencePreferencesEntity::class,
        com.evnavigator.iran.data.elevation.ElevationDataset::class,
        com.evnavigator.iran.data.vehicle.VehicleSpecEntity::class,
        com.evnavigator.iran.data.journey.ActiveJourneyEntity::class,
        com.evnavigator.iran.data.cache.EnvironmentCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(
    // Charging Converters
    com.evnavigator.iran.data.charging.converters.ChargingStationIdConverter::class,
    com.evnavigator.iran.data.charging.converters.ConnectorTypeConverter::class,
    com.evnavigator.iran.data.charging.converters.AccessTypeConverter::class,
    com.evnavigator.iran.data.charging.converters.SourceTypeConverter::class,
    com.evnavigator.iran.data.charging.converters.ValidationStatusConverter::class,
    com.evnavigator.iran.data.charging.converters.DayOfWeekConverter::class,
    // Vehicle Converters
    com.evnavigator.iran.data.vehicle.converters.VehicleTypeConverters::class,
    // Map Converters
    com.evnavigator.iran.data.map.converters.CoverageLevelConverter::class,
    com.evnavigator.iran.data.map.converters.CoverageTypeConverter::class,
    com.evnavigator.iran.data.map.converters.InstallationStateConverter::class,
    com.evnavigator.iran.data.map.converters.RestrictionTypeConverter::class,
    com.evnavigator.iran.data.map.converters.RoadRestrictionVersionConverter::class,
    com.evnavigator.iran.data.map.converters.RoadTypeConverter::class,
    com.evnavigator.iran.data.map.converters.SourceTypeConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chargingStationDao(): ChargingStationDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun roadNetworkDao(): RoadNetworkDao
    abstract fun roadGraphDao(): RoadGraphDao
    abstract fun elevationDao(): ElevationDao
    abstract fun vehicleSpecDao(): VehicleSpecDao
    abstract fun tripHistoryDao(): com.evnavigator.iran.data.intelligence.dao.TripHistoryDao
    abstract fun intelligencePreferencesDao(): com.evnavigator.iran.data.intelligence.dao.IntelligencePreferencesDao
    abstract fun activeJourneyDao(): com.evnavigator.iran.data.journey.ActiveJourneyDao
    abstract fun environmentCacheDao(): com.evnavigator.iran.data.cache.EnvironmentCacheDao

    companion object {
        private const val DATABASE_NAME = "ev_navigator_iran.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `environment_cache` (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `cacheType` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_environment_cache_cacheType` ON `environment_cache` (`cacheType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_environment_cache_expiresAt` ON `environment_cache` (`expiresAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_environment_cache_latitude_longitude` ON `environment_cache` (`latitude`, `longitude`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
