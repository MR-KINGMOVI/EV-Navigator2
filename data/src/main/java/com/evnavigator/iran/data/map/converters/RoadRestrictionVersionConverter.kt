package com.evnavigator.iran.data.map.converters

import androidx.room.TypeConverter

/**
 * Type converter for RoadRestrictionVersion enum.
 */
class RoadRestrictionVersionConverter {
    @TypeConverter
    fun toDatabaseValue(value: RoadRestrictionVersion?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromDatabaseValue(value: String?): RoadRestrictionVersion? {
        return value?.let {
            try {
                RoadRestrictionVersion.valueOf(it)
            } catch (e: IllegalArgumentException) {
                RoadRestrictionVersion.UNKNOWN
            }
        }
    }
}

/**
 * Version of road restrictions.
 */
enum class RoadRestrictionVersion {
    NONE,
    BASIC,
    ADVANCED,
    FULL,
    UNKNOWN
}
