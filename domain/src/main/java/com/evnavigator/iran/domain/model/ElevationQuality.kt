package com.evnavigator.iran.domain.model

/**
 * Represents the quality of elevation data.
 */
enum class ElevationQuality {
    /**
     * Data quality is unknown.
     */
    UNKNOWN,

    /**
     * Data is estimated.
     */
    ESTIMATED,

    /**
     * Data is measured.
     */
    MEASURED,

    /**
     * Data has high confidence.
     */
    HIGH_CONFIDENCE,
    
    LOW,
    MEDIUM,
    HIGH
}