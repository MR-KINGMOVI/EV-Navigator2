package com.evnavigator.iran.data.charging.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.core.charging.SourceType

/**
 * Type converter for SourceType.
 */
class SourceTypeConverter {
    @TypeConverter
    fun fromSourceType(type: SourceType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toSourceType(typeName: String?): SourceType? {
        return typeName?.let { SourceType.valueOf(it) }
    }
}