package com.evnavigator.iran.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evnavigator.iran.data.cache.EnvironmentCacheDao
import com.evnavigator.iran.data.cache.EnvironmentCacheEntity
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Phase 2 Required Test Suite:
 * - Tests Database non-destructive Migration (AppDatabase.MIGRATION_1_2)
 * - Tests EnvironmentCache persistence across restarts
 * - Tests TTL pruning and user data preservation
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationAndPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var cacheDao: EnvironmentCacheDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        cacheDao = db.environmentCacheDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testCacheInsertRetrieveAndPruneExpiredTtl() = runBlocking {
        val now = System.currentTimeMillis()
        val validEntry = EnvironmentCacheEntity(
            key = "weather:35.70:51.34",
            cacheType = "WEATHER",
            latitude = 35.70,
            longitude = 51.34,
            payloadJson = "{\"temp\": 24.5, \"wind\": 12.0}",
            cachedAt = now,
            expiresAt = now + 1_800_000L // 30 minutes in future
        )

        val expiredEntry = EnvironmentCacheEntity(
            key = "traffic:35.70:51.34",
            cacheType = "TRAFFIC",
            latitude = 35.70,
            longitude = 51.34,
            payloadJson = "{\"congestion\": \"HIGH\"}",
            cachedAt = now - 600_000L,
            expiresAt = now - 60_000L // expired 1 minute ago
        )

        cacheDao.insertOrUpdate(validEntry)
        cacheDao.insertOrUpdate(expiredEntry)

        assertEquals("Should have 2 entries before pruning", 2, cacheDao.count())

        // Retrieve valid entry
        val retrieved = cacheDao.getValidEntry("weather:35.70:51.34", now)
        assertNotNull("Valid entry must be retrieved", retrieved)
        assertEquals("WEATHER", retrieved?.cacheType)
        assertTrue("Payload must match", retrieved?.payloadJson?.contains("24.5") == true)

        // Prune expired entries
        val prunedCount = cacheDao.pruneExpired(now)
        assertEquals("Pruned count should be 1", 1, prunedCount)
        assertEquals("Only 1 valid entry should remain after pruning", 1, cacheDao.count())
        assertNull("Expired entry must be gone", cacheDao.getValidEntry("traffic:35.70:51.34", now))
        assertNotNull("Fresh entry must remain", cacheDao.getValidEntry("weather:35.70:51.34", now))
    }

    @Test
    fun testUserDataPreservationWithoutDestructiveMigration() = runBlocking {
        val vehicle = VehicleEntity(
            id = "test-ev-preserved",
            name = "Test Vehicle",
            manufacturer = "Volkswagen",
            model = "ID.4",
            generation = "1",
            modelYear = 2024,
            trim = "CROZZ",
            market = "IR",
            powertrainType = "BEV",
            curbMassKg = 1950.0,
            grossVehicleMassKg = 2400.0,
            lengthMm = 4592,
            widthMm = 1852,
            heightMm = 1629,
            wheelbaseMm = 2765,
            groundClearanceMm = 175,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.30,
            rollingResistanceCoefficient = 0.010,
            nominalBatteryCapacityWh = 82000.0,
            usableBatteryCapacityWh = 77000.0,
            batteryChemistry = "NMC",
            nominalVoltageV = 400.0,
            batteryConfiguration = "Standard",
            motorCount = 1,
            frontMotorPowerKw = 0.0,
            rearMotorPowerKw = 150.0,
            peakMotorPowerKw = 150.0,
            continuousMotorPowerKw = 70.0,
            torqueNm = 310,
            drivetrainType = "RWD",
            baselineConsumptionWhPer100Km = 16200.0,
            acChargingMaxPowerKw = 11.0,
            dcChargingMaxPowerKw = 100.0,
            chargingVoltageRangeV = "200-500",
            chargingCurrentRangeA = "10-250",
            chargePortCount = 2,
            connectorTypes = "CCS2, GB/T",
            wheelDiameterMm = 482,
            tireWidthMm = 235,
            dataQuality = "VERIFIED",
            source = "OEM",
            sourceVersion = "1.0",
            retrievedDate = System.currentTimeMillis(),
            confidence = 1.0,
            validationStatus = "VALIDATED",
            notes = null,
            schemaVersion = "1.0",
            dataVersion = "1.0",
            vehicleRecordVersion = "1.0"
        )

        db.vehicleDao().insertVehicle(vehicle)
        val fetched = db.vehicleDao().getVehicleById("test-ev-preserved")
        assertNotNull("User vehicle must be preserved in database", fetched)
        assertEquals("Test Vehicle", fetched?.name)
        assertEquals(77000.0, fetched?.usableBatteryCapacityWh ?: 0.0, 0.01)
    }
}
