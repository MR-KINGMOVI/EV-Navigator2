package com.evnavigator.iran.data.charging.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.core.charging.DayOfWeek

/**
 * Type converter for DayOfWeek.
 */
class DayOfWeekConverter {
    @TypeConverter
    fun fromDayOfWeek(day: DayOfWeek?): String? {
        return day?.name
    }

    @TypeConverter
    fun toDayOfWeek(dayName: String?): DayOfWeek? {
        return dayName?.let { DayOfWeek.valueOf(it) }
    }
}