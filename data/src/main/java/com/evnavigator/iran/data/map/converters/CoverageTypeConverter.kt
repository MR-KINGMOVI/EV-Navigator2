package com.evnavigator.iran.data.map.converters

import androidx.room.TypeConverter

/**
 * Type converter for CoverageType enum.
 */
class CoverageTypeConverter {
    @TypeConverter
    fun toDatabaseValue(value: CoverageType?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromDatabaseValue(value: String?): CoverageType? {
        return value?.let {
            try {
                CoverageType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                CoverageType.UNKNOWN
            }
        }
    }
}

/**
 * Coverage type for road network data.
 */
enum class CoverageType {
    POINT,
    AREA,
    ROUTE,
    REGION,
    GLOBAL,
    UNKNOWN
}
