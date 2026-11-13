package com.evnavigator.iran.data.charging.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.core.charging.ValidationStatus

/**
 * Type converter for ValidationStatus.
 */
class ValidationStatusConverter {
    @TypeConverter
    fun fromValidationStatus(status: ValidationStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toValidationStatus(statusName: String?): ValidationStatus? {
        return statusName?.let {
            try {
                ValidationStatus.valueOf(it)
            } catch (_: Exception) {
                ValidationStatus.UNKNOWN
            }
        }
    }
}
