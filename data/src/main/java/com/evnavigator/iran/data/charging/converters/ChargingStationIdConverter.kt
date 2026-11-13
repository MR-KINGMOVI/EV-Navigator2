package com.evnavigator.iran.data.charging.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.core.charging.ChargingStationId

/**
 * Type converter for ChargingStationId.
 */
class ChargingStationIdConverter {
    @TypeConverter
    fun fromChargingStationId(id: ChargingStationId?): String? {
        return id?.id
    }

    @TypeConverter
    fun toChargingStationId(idString: String?): ChargingStationId? {
        return idString?.let { ChargingStationId.fromString(it) }
    }
}