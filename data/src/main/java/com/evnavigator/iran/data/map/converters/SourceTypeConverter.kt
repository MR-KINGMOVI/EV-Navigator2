package com.evnavigator.iran.data.map.converters

import androidx.room.TypeConverter

/**
 * Type converter for SourceType enum.
 */
class SourceTypeConverter {
    @TypeConverter
    fun toDatabaseValue(value: SourceType?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromDatabaseValue(value: String?): SourceType? {
        return value?.let {
            try {
                SourceType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                SourceType.UNKNOWN
            }
        }
    }

    companion object {
        @JvmStatic
        fun toDbValue(value: SourceType): String = value.name

        @JvmStatic
        fun fromDbValue(value: String): SourceType = try {
            SourceType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SourceType.UNKNOWN
        }
    }
}

/**
 * Source type for road network data.
 */
enum class SourceType {
    /**
     * Network provider (e.g., HERE, Google, Mapbox).
     */
    NETWORK_PROVIDER,

    /**
     * Government source.
     */
    GOVERNMENT,

    /**
     * Open source.
     */
    OPEN_SOURCE,

    /**
     * User-generated.
     */
    USER_GENERATED,

    /**
     * Third-party commercial.
     */
    COMMERCIAL,

    /**
     * Unknown source.
     */
    UNKNOWN
}
