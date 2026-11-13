package com.evnavigator.iran.data.charging.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.core.charging.AccessType

/**
 * Type converter for AccessType.
 */
class AccessTypeConverter {
    @TypeConverter
    fun fromAccessType(type: AccessType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toAccessType(typeName: String?): AccessType? {
        return typeName?.let { AccessType.valueOf(it) }
    }
}