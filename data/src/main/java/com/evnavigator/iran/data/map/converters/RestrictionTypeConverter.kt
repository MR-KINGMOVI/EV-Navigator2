package com.evnavigator.iran.data.map.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.data.map.entity.RestrictionType

/**
 * Room TypeConverter for RestrictionType enum.
 */
class RestrictionTypeConverter {
    @TypeConverter
    fun toDatabaseValue(value: RestrictionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromDatabaseValue(value: String?): RestrictionType? {
        return value?.let {
            try {
                RestrictionType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                RestrictionType.UNKNOWN
            }
        }
    }
}
