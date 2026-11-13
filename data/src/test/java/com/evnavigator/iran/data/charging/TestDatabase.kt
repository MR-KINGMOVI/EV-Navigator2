package com.evnavigator.iran.data.charging

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.dao.ChargingStationDao
import com.evnavigator.iran.data.charging.converters.*

/**
 * Test database for charging station DAO tests.
 */
@Database(
    entities = [ChargingStationEntity::class, ChargingPortEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    ChargingStationIdConverter::class,
    ConnectorTypeConverter::class,
    AccessTypeConverter::class,
    SourceTypeConverter::class,
    ValidationStatusConverter::class,
    DayOfWeekConverter::class,
    com.evnavigator.iran.data.vehicle.converters.VehicleTypeConverters::class
)
abstract class TestDatabase : RoomDatabase() {
    abstract fun chargingStationDao(): ChargingStationDao
}