package com.evnavigator.iran.domain.model

/**
 * Represents a road grade as a percentage.
 *
 * @param value Road grade in percentage (-100 to 100)
 */
data class RoadGrade(private val value: Double) {
    init {
        require(value >= -100.0 && value <= 100.0) {
            "Road grade must be between -100 and 100 percent, got: $value"
        }
    }

    /**
     * Gets the road grade value in percentage.
     */
    fun value(): Double = value

    companion object {
        /**
         * Creates a RoadGrade from percentage.
         */
        fun ofPercentage(value: Double): RoadGrade {
            return RoadGrade(value)
        }
    }
}