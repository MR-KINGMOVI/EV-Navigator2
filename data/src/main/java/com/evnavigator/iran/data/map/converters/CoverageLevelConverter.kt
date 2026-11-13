package com.evnavigator.iran.data.map.converters

import androidx.room.TypeConverter

/**
 * Type converter for CoverageLevel enum.
 */
class CoverageLevelConverter {
    @TypeConverter
    fun toDbValue(value: CoverageLevel?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromDatabaseValue(value: String?): CoverageLevel? {
        return value?.let {
            try {
                CoverageLevel.valueOf(it)
            } catch (e: IllegalArgumentException) {
                CoverageLevel.NONE
            }
        }
    }
}

/**
 * Coverage level for road network data.
 */
enum class CoverageLevel {
    FULL,
    PARTIAL,
    NONE,
    UNKNOWN
}
