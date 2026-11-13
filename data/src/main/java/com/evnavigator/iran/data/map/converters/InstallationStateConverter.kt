package com.evnavigator.iran.data.map.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.domain.model.DatasetInstallationState

typealias InstallationState = DatasetInstallationState

/**
 * Type converter for DatasetInstallationState enum.
 */
class InstallationStateConverter {
    @TypeConverter
    fun toDatabaseValue(value: DatasetInstallationState): String {
        return value.name
    }

    @TypeConverter
    fun fromDatabaseValue(value: String): DatasetInstallationState {
        return try {
            DatasetInstallationState.valueOf(value)
        } catch (e: IllegalArgumentException) {
            DatasetInstallationState.NOT_INSTALLED
        }
    }

    companion object {
        @JvmStatic
        fun toDatabaseValueStatic(value: DatasetInstallationState): String {
            return value.name
        }

        @JvmStatic
        fun fromDatabaseValueStatic(value: String): DatasetInstallationState {
            return try {
                DatasetInstallationState.valueOf(value)
            } catch (e: IllegalArgumentException) {
                DatasetInstallationState.NOT_INSTALLED
            }
        }
    }
}
