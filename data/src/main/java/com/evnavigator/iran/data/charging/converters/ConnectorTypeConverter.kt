package com.evnavigator.iran.data.charging.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.core.charging.ConnectorType

/**
 * Type converter for ConnectorType.
 */
class ConnectorTypeConverter {
    @TypeConverter
    fun fromConnectorType(type: ConnectorType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toConnectorType(typeName: String?): ConnectorType? {
        return typeName?.let { ConnectorType.valueOf(it) }
    }
}