package com.evnavigator.iran.core.energy.model

/**
 * Represents energy in Watt-hours (Wh)
 */
data class Energy(val wattHours: Double) {
    companion object {
        fun fromKilowattHours(kwh: Double): Energy = Energy(kwh * 1000.0)
        fun fromJoules(joules: Double): Energy = Energy(joules / 3600.0)
    }
    
    operator fun plus(other: Energy): Energy = Energy(this.wattHours + other.wattHours)
    operator fun minus(other: Energy): Energy = Energy(this.wattHours - other.wattHours)
    operator fun times(factor: Double): Energy = Energy(this.wattHours * factor)
    operator fun div(other: Energy): Double = this.wattHours / other.wattHours
    
    val value: Double get() = wattHours
    val kiloWattHours: Double get() = wattHours / 1000.0
    val joules: Double get() = wattHours * 3600.0
}

/**
 * Represents power in Watts (W)
 */
data class Power(val watts: Double) {
    val value: Double get() = watts
    val kiloWatts: Double get() = watts / 1000.0

    companion object {
        fun fromKilowatts(kw: Double): Power = Power(kw * 1000.0)
    }
    
    operator fun plus(other: Power): Power = Power(this.watts + other.watts)
    operator fun minus(other: Power): Power = Power(this.watts - other.watts)
    operator fun times(factor: Double): Power = Power(this.watts * factor)
}

/**
 * Represents distance in meters
 */
data class Distance(val meters: Double) {
    companion object {
        fun fromKilometers(km: Double): Distance = Distance(km * 1000.0)
    }
    
    operator fun plus(other: Distance): Distance = Distance(this.meters + other.meters)
    operator fun minus(other: Distance): Distance = Distance(this.meters - other.meters)
    operator fun times(factor: Double): Distance = Distance(this.meters * factor)
    operator fun div(other: Distance): Double = this.meters / other.meters
    
    val kilometers: Double get() = meters / 1000.0
}

/**
 * Represents speed in meters per second (m/s)  
 */
data class Speed(val metersPerSecond: Double) {
    companion object {
        fun fromKilometersPerHour(kmh: Double): Speed = Speed(kmh / 3.6)
        fun fromMilesPerHour(mph: Double): Speed = Speed(mph * 0.44704) // 1 mph = 0.44704 m/s
    }
    
    operator fun plus(other: Speed): Speed = Speed(this.metersPerSecond + other.metersPerSecond)
    operator fun minus(other: Speed): Speed = Speed(this.metersPerSecond - other.metersPerSecond)
    operator fun times(factor: Double): Speed = Speed(this.metersPerSecond * factor)
    
    val kilometersPerHour: Double get() = metersPerSecond * 3.6
}

/**
 * Represents mass in kilograms
 */
data class Mass(val kilograms: Double) {
    companion object {
        fun fromTons(tons: Double): Mass = Mass(tons * 1000.0)
    }
    
    operator fun plus(other: Mass): Mass = Mass(this.kilograms + other.kilograms)
    operator fun minus(other: Mass): Mass = Mass(this.kilograms - other.kilograms)
    operator fun times(factor: Double): Mass = Mass(this.kilograms * factor)
    val value: Double get() = kilograms
}

/**
 * Represents efficiency as a fraction (0.0 to 1.0)
 */
data class Efficiency(val fraction: Double) {
    companion object {
        fun fromPercentage(percent: Double): Efficiency = Efficiency(percent / 100.0)
    }
    
    init {
        require(fraction >= 0.0) { "Efficiency must be non-negative" }
    }
    
    operator fun plus(other: Efficiency): Efficiency = Efficiency(this.fraction + other.fraction)
    operator fun minus(other: Efficiency): Efficiency = Efficiency(this.fraction - other.fraction)
    operator fun times(factor: Double): Efficiency = Efficiency(this.fraction * factor)
    
    val percentage: Double get() = fraction * 100.0
}

/**
 * Represents grade as a percentage (positive for uphill, negative for downhill)
 */
data class Grade(val percent: Double) {
    operator fun plus(other: Grade): Grade = Grade(this.percent + other.percent)
    operator fun minus(other: Grade): Grade = Grade(this.percent - other.percent)
    operator fun times(factor: Double): Grade = Grade(this.percent * factor)
    
    val radians: Double get() = Math.toRadians(percent / 100.0)
}
