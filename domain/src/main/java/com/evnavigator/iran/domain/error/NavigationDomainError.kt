package com.evnavigator.iran.domain.error

/**
 * Typed domain error taxonomy for navigation, routing, energy, charging,
 * vehicle selection, providers, persistence, and permissions.
 *
 * Replaces unstructured Exception / String error propagation across modules.
 */
sealed class NavigationDomainError(
    open val code: String,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    data class RoutingFailed(
        val reason: String,
        override val cause: Throwable? = null
    ) : NavigationDomainError(
        code = "ROUTING_FAILED",
        message = reason,
        cause = cause
    )

    data class SnapFailed(
        val pointLabel: String,
        val latitude: Double,
        val longitude: Double,
        val snapDistanceMeters: Double,
        val maxAllowedMeters: Double
    ) : NavigationDomainError(
        code = "SNAP_FAILED",
        message = "نقطه $pointLabel در فاصله ${snapDistanceMeters.toInt()} متری از جاده قرار دارد (حداکثر مجاز: ${maxAllowedMeters.toInt()} متر)."
    )

    data class OffRoute(
        val lateralDeviationMeters: Double,
        val headingMismatchDegrees: Double,
        val consecutiveSamples: Int
    ) : NavigationDomainError(
        code = "OFF_ROUTE",
        message = "انحراف از مسیر تشخیص داده شد (${lateralDeviationMeters.toInt()} متر، $consecutiveSamples نمونه متوالی)."
    )

    data class NoVehicleSelected(
        val requestedVehicleId: String? = null
    ) : NavigationDomainError(
        code = "NO_VEHICLE_SELECTED",
        message = if (requestedVehicleId.isNullOrBlank()) {
            "هیچ خودروی فعالی انتخاب نشده است. لطفاً ابتدا خودروی خود را از گاراژ انتخاب کنید."
        } else {
            "خودروی انتخاب‌شده ($requestedVehicleId) در پایگاه داده یافت نشد."
        }
    )

    data class EnergyModelFailed(
        val reason: String,
        override val cause: Throwable? = null
    ) : NavigationDomainError(
        code = "ENERGY_MODEL_FAILED",
        message = reason,
        cause = cause
    )

    data class ChargingStationUnavailable(
        val stationId: String?,
        val reason: String
    ) : NavigationDomainError(
        code = "CHARGING_STATION_UNAVAILABLE",
        message = reason
    )

    data class WeatherProviderFailed(
        val reason: String,
        override val cause: Throwable? = null
    ) : NavigationDomainError(
        code = "WEATHER_PROVIDER_FAILED",
        message = reason,
        cause = cause
    )

    data class TrafficProviderFailed(
        val reason: String,
        override val cause: Throwable? = null
    ) : NavigationDomainError(
        code = "TRAFFIC_PROVIDER_FAILED",
        message = reason,
        cause = cause
    )

    data class DatabaseCorrupted(
        val tableOrEntity: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : NavigationDomainError(
        code = "DATABASE_CORRUPTED",
        message = "خطا در یکپارچگی داده ($tableOrEntity): $reason",
        cause = cause
    )

    data class PermissionDenied(
        val permissionName: String,
        val reason: String = "مجوز دسترسی به موقعیت مکانی ($permissionName) صادر نشده است."
    ) : NavigationDomainError(
        code = "PERMISSION_DENIED",
        message = reason
    )
}
