package com.evnavigator.iran.data.map.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.data.map.entity.RoadType

/**
 * Room TypeConverter for RoadType enum.
 */
class RoadTypeConverter {
    @TypeConverter
    fun toDatabaseValue(value: RoadType?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromDatabaseValue(value: String?): RoadType? {
        return value?.let {
            try {
                RoadType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                RoadType.UNKNOWN
            }
        }
    }
}
