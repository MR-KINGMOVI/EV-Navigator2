package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import com.evnavigator.iran.core.energy.model.Efficiency

/**
 * Utility class for handling unknown values in a semantically correct way.
 *
 * This ensures that "unknown" values are not confused with zero or default values.
 */
object UnknownValueSemantics {
    
    /**
     * Represents an unknown value that cannot be confused with valid zero values.
     * 
     * Usage: Instead of `val batteryCapacity: Double? = null` which could be ambiguous,
     * use `val batteryCapacity: Double? = UnknownValueSemantics.unknownDouble()`
     */
    
    /**
     * Creates an explicitly unknown double value
     * This is different from 0.0 and helps distinguish it in calculations
     */
    fun unknownDouble(): Double? = null
    
    /**
     * Creates an explicitly unknown integer value
     */
    fun unknownInt(): Int? = null
    
    /**
     * Creates an explicitly unknown string value
     */
    fun unknownString(): String? = null
    
    /**
     * Creates an explicitly unknown mass value
     */
    fun unknownMass(): Mass? = null
    
    /**
     * Creates an explicitly unknown energy value
     */
    fun unknownEnergy(): Energy? = null
    
    /**
     * Creates an explicitly unknown power value
     */
    fun unknownPower(): Power? = null
    
    /**
     * Creates an explicitly unknown efficiency value
     */
    fun unknownEfficiency(): Efficiency? = null
    
    /**
     * Checks if a value represents an unknown value
     */
    fun isUnknown(value: Double?): Boolean {
        return value == null
    }
    
    /**
     * Checks if a value represents an unknown mass
     */
    fun isUnknown(value: Mass?): Boolean {
        return value == null
    }
    
    /**
     * Checks if a value represents an unknown energy
     */
    fun isUnknown(value: Energy?): Boolean {
        return value == null
    }
    
    /**
     * Checks if a value represents an unknown power
     */
    fun isUnknown(value: Power?): Boolean {
        return value == null
    }
    
    /**
     * Checks if a value represents an unknown efficiency
     */
    fun isUnknown(value: Efficiency?): Boolean {
        return value == null
    }
    
    /**
     * Provides a safe default for unknown values while maintaining semantic clarity
     */
    fun safeDefault(value: Double?, defaultValue: Double): Double {
        return value ?: defaultValue
    }
    
    /**
     * Provides a safe default for unknown mass values
     */
    fun safeDefault(value: Mass?, defaultValue: Mass): Mass {
        return value ?: defaultValue
    }
    
    /**
     * Provides a safe default for unknown energy values
     */
    fun safeDefault(value: Energy?, defaultValue: Energy): Energy {
        return value ?: defaultValue
    }
    
    /**
     * Provides a safe default for unknown power values
     */
    fun safeDefault(value: Power?, defaultValue: Power): Power {
        return value ?: defaultValue
    }
    
    /**
     * Provides a safe default for unknown efficiency values
     */
    fun safeDefault(value: Efficiency?, defaultValue: Efficiency): Efficiency {
        return value ?: defaultValue
    }
}

/**
 * Sealed class to represent explicitly unknown vehicle data
 * This provides compile-time guarantees that unknown values are handled properly
 */
sealed class UnknownVehicleData<T> {
    /**
     * Represents an actual value (not unknown)
     */
    data class Known<T>(val value: T) : UnknownVehicleData<T>()
    
    /**
     * Represents an explicitly unknown value
     */
    class Unknown<T> : UnknownVehicleData<T>()
    
    companion object {
        fun <T> known(value: T): UnknownVehicleData<T> = Known(value)
        fun <T> unknown(): UnknownVehicleData<T> = Unknown()
    }
}

/**
 * Specific sealed classes for different vehicle data types to provide compile-time safety
 */
sealed class UnknownDouble {
    data class Known(val value: Double) : UnknownDouble()
    object Unknown : UnknownDouble()
    
    companion object {
        fun known(value: Double): UnknownDouble = Known(value)
        fun unknown(): UnknownDouble = Unknown
    }
}

sealed class UnknownInt {
    data class Known(val value: Int) : UnknownInt()
    object Unknown : UnknownInt()
    
    companion object {
        fun known(value: Int): UnknownInt = Known(value)
        fun unknown(): UnknownInt = Unknown
    }
}

sealed class UnknownString {
    data class Known(val value: String) : UnknownString()
    object Unknown : UnknownString()
    
    companion object {
        fun known(value: String): UnknownString = Known(value)
        fun unknown(): UnknownString = Unknown
    }
}