package com.evnavigator.iran.core.charging

import java.util.*

/**
 * Represents a time range for opening hours.
 *
 * This model defines a specific time period within a day (e.g., 9:00 AM to 5:00 PM).
 */
data class HourRange(
    /**
     * Start time in 24-hour format (0-23)
     */
    val startHour: Int,
    
    /**
     * Start minute (0-59)
     */
    val startMinute: Int = 0,
    
    /**
     * End time in 24-hour format (0-23)
     */
    val endHour: Int,
    
    /**
     * End minute (0-59)
     */
    val endMinute: Int = 0
) {
    constructor(startHour: Int, endHour: Int) : this(startHour = startHour, startMinute = 0, endHour = endHour, endMinute = 0) {
        require(startHour <= endHour) { "Start hour cannot be after end hour" }
    }


    init {
        require(startHour in 0..23) { "Start hour must be between 0 and 23" }
        require(startMinute in 0..59) { "Start minute must be between 0 and 59" }
        require(endHour in 0..23) { "End hour must be between 0 and 23" }
        require(endMinute in 0..59) { "End minute must be between 0 and 59" }
        
if (startHour > endHour) {
            require(startHour in 20..23 && endHour in 0..8) {
                "Start hour cannot be after end hour"
            }
        }
    }

    /**
     * Checks if a given time falls within this hour range
     */
    fun containsTime(hour: Int, minute: Int): Boolean {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        
        return when {
            startHour < endHour -> {
                // Normal case: start time before end time
                (hour > startHour || (hour == startHour && minute >= startMinute)) &&
                (hour < endHour || (hour == endHour && minute <= endMinute))
            }
            startHour == endHour -> {
                // Same hour case
                hour == startHour && 
                minute >= startMinute && 
                minute <= endMinute
            }
            else -> {
                // Cross-midnight case: end time before start time (e.g., 22:00 to 06:00)
                (hour > startHour || (hour == startHour && minute >= startMinute)) ||
                (hour < endHour || (hour == endHour && minute <= endMinute))
            }
        }
    }

    /**
     * Gets the duration in minutes
     */
    val durationMinutes: Int
        get() {
            val startTotalMinutes = startHour * 60 + startMinute
            val endTotalMinutes = endHour * 60 + endMinute
            
            return if (startHour < endHour || (startHour == endHour && startMinute <= endMinute)) {
                // Normal case
                endTotalMinutes - startTotalMinutes
            } else {
                // Cross-midnight case: 22:00 to 06:00 = 8 hours
                (24 * 60 - startTotalMinutes) + endTotalMinutes
            }
        }

    companion object {
        /**
         * Creates a standard business hours range (9 AM to 5 PM)
         */
        fun createBusinessHours(): HourRange {
            return HourRange(9, 0, 17, 0)
        }
    }
}

/**
 * Represents exceptional opening hours (e.g., holidays, maintenance periods).
 */
data class ExceptionalHour(
    /**
     * Date for which these hours apply
     */
    val date: Date,
    
    /**
     * Hour range for this exceptional day
     */
    val hourRange: HourRange,
    
    /**
     * Optional description of why these hours apply
     */
    val reason: String? = null
) {
    init {
        require(date.time > 0) { "Date must be valid" }
    }
}

/**
 * Main model for opening hours information at a charging station.
 *
 * This model supports:
 * - Regular daily opening hours
 * - 24/7 operation flag
 * - Exceptional hours (holidays, maintenance)
 * - Proper timezone handling considerations
 *
 * Note: Timezone handling is left to the application layer since this model
 * should focus on the data representation rather than timezone conversions.
 */
data class OpeningHours(
    /**
     * Regular daily opening hours (Monday through Sunday)
     */
    val dailyHours: Map<DayOfWeek, HourRange?>? = null,
    
    /**
     * Whether station is open 24 hours a day, 7 days a week
     */
    val is247: Boolean = false,
    
    /**
     * Exceptional opening hours for specific dates (e.g., holidays)
     */
    val exceptionalHours: Set<ExceptionalHour>? = null,

    /**
     * Source of this opening hours information (e.g., "ChargePoint", "Shell")
     */
    val source: String? = null,

    /**
     * Reference ID from source system
     */
    val sourceReference: String? = null,

    /**
     * When this information was retrieved
     */
    val retrievedDate: Long? = null,

    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double = 0.5,

    /**
     * Validation status of this information
     */
    val validationStatus: ValidationStatus = ValidationStatus.UNKNOWN
) {
    constructor(
        regularHours: Map<DayOfWeek, HourRange?>?,
        exceptionalHours: Collection<ExceptionalHour>? = null
    ) : this(
        dailyHours = regularHours,
        exceptionalHours = exceptionalHours?.toSet()
    )

    init {
        // Validate confidence level
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        
        // If daily hours are provided, they should not be empty
        dailyHours?.let {
            require(it.isNotEmpty()) { "Daily hours cannot be empty if provided" }
        }
        
        // If exceptional hours are provided, they should not be empty
        exceptionalHours?.let {
            require(it.isNotEmpty()) { "Exceptional hours cannot be empty if provided" }
        }
        
        // If is247 is true, dailyHours should be null or all null
        if (is247) {
            dailyHours?.let { hours ->
                require(hours.values.all { it == null }) { 
                    "If 24/7 is true, daily hours should be null or all null" 
                }
            }
        }
    }

    /**
     * Checks if the station is open at a specific time
     */
    fun isOpenAt(date: Date, hour: Int, minute: Int): Boolean {
        // If 24/7, always open
        if (is247) return true
        
        // Check exceptional hours first (more specific)
        exceptionalHours?.let { exceptions ->
            val exception = exceptions.find { 
                it.date == date 
            }
            exception?.let {
                return it.hourRange.containsTime(hour, minute)
            }
        }
        
        // Check regular daily hours
        dailyHours?.let { hours ->
            val dayOfWeek = Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK)
            val day = when (dayOfWeek) {
                Calendar.MONDAY -> DayOfWeek.MONDAY
                Calendar.TUESDAY -> DayOfWeek.TUESDAY
                Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
                Calendar.THURSDAY -> DayOfWeek.THURSDAY
                Calendar.FRIDAY -> DayOfWeek.FRIDAY
                Calendar.SATURDAY -> DayOfWeek.SATURDAY
                Calendar.SUNDAY -> DayOfWeek.SUNDAY
                else -> throw IllegalArgumentException("Invalid day of week")
            }
            
            hours[day]?.let { range ->
                return range.containsTime(hour, minute)
            }
        }
        
        // Default to closed if no information available
        return false
    }

    /**
     * Gets the opening hours for a specific day of the week
     */
    fun getHoursForDay(day: DayOfWeek): HourRange? {
        return dailyHours?.get(day)
    }

    companion object {
        /**
         * Creates 24/7 opening hours
         */
        fun create247(): OpeningHours {
            return OpeningHours(is247 = true)
        }
        
        /**
         * Creates regular business hours (Monday-Friday 9 AM to 5 PM)
         */
        fun createBusinessHours(): OpeningHours {
            val businessHours = mapOf(
                DayOfWeek.MONDAY to HourRange(9, 0, 17, 0),
                DayOfWeek.TUESDAY to HourRange(9, 0, 17, 0),
                DayOfWeek.WEDNESDAY to HourRange(9, 0, 17, 0),
                DayOfWeek.THURSDAY to HourRange(9, 0, 17, 0),
                DayOfWeek.FRIDAY to HourRange(9, 0, 17, 0),
                DayOfWeek.SATURDAY to null,
                DayOfWeek.SUNDAY to null
            )
            
            return OpeningHours(dailyHours = businessHours)
        }
    }
}

/**
 * Enum representing the days of the week for opening hours.
 */
enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}