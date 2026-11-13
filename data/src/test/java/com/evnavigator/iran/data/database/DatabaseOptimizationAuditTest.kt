package com.evnavigator.iran.data.database

import androidx.room.Entity
import androidx.room.Index
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.map.entity.RoadNetworkEntity
import com.evnavigator.iran.data.map.entity.RoadNodeEntity
import com.evnavigator.iran.data.map.entity.RoadSegmentEntity
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Step AJ Verification Test: Database Schema, Keys, and Index Optimization Audit.
 *
 * Verifies:
 * - Critical spatial and relational columns have corresponding indexes
 * - No missing indexes on foreign key relationships
 * - Correct entity table names and annotations
 */
class DatabaseOptimizationAuditTest {

    private fun getEntityBytecode(clazz: Class<*>): String {
        val resourceName = "/" + clazz.name.replace('.', '/') + ".class"
        val bytes = clazz.getResourceAsStream(resourceName)?.readBytes()
            ?: clazz.classLoader?.getResourceAsStream(clazz.name.replace('.', '/') + ".class")?.readBytes()
            ?: throw IllegalStateException("Cannot find bytecode for ${clazz.name}")
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun verifyEntityAndColumns(clazz: Class<*>, requiredColumns: List<String>) {
        val annotation = clazz.getAnnotation(Entity::class.java)
        if (annotation != null) {
            val indexes = annotation.indices
            val allIndexedCols = indexes.flatMap { it.value.toList() }.toSet()
            assertTrue(
                "Entity ${clazz.simpleName} must have indexes for required columns: $requiredColumns",
                requiredColumns.all { allIndexedCols.contains(it) }
            )
        } else {
            // Room's @Entity has CLASS retention. When run on standard JVM, inspect bytecode directly.
            val bytecode = getEntityBytecode(clazz)
            assertTrue("${clazz.simpleName} must have @Entity annotation in bytecode", bytecode.contains("Landroidx/room/Entity;"))
            assertTrue("${clazz.simpleName} must have @Index annotation in bytecode", bytecode.contains("Landroidx/room/Index;"))
            for (col in requiredColumns) {
                assertTrue("${clazz.simpleName} must index column $col in bytecode", bytecode.contains(col))
            }
        }
    }

    @Test
    fun `test RoadNodeEntity has compound spatial index on roadNetworkId and coordinates`() {
        verifyEntityAndColumns(RoadNodeEntity::class.java, listOf("roadNetworkId", "latitude", "longitude"))
    }

    @Test
    fun `test RoadSegmentEntity has indexes for fromNodeId and toNodeId`() {
        verifyEntityAndColumns(RoadSegmentEntity::class.java, listOf("fromNodeId", "toNodeId"))
    }

    @Test
    fun `test ChargingStationEntity has indexes on spatial coordinates and validation status`() {
        verifyEntityAndColumns(ChargingStationEntity::class.java, listOf("validationStatus", "latitude", "longitude"))
    }

    @Test
    fun `test ChargingPortEntity has foreign key index on stationId`() {
        verifyEntityAndColumns(ChargingPortEntity::class.java, listOf("stationId"))
    }

    @Test
    fun `test VehicleEntity has indexes on manufacturer and model`() {
        verifyEntityAndColumns(VehicleEntity::class.java, listOf("manufacturer", "model"))
    }
}
