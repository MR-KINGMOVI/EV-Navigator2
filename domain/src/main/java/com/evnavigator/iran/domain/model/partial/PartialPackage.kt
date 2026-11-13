package com.evnavigator.iran.domain.model.partial

/**
 * Partial package semantics for incomplete or partially available datasets.
 * 
 * This class represents datasets that are not fully available or complete.
 * Partial packages are never treated as complete and must be explicitly
 * handled by consumers with degradation awareness.
 * 
 * @property datasetId Dataset identifier
 * @property version Dataset version
 * @property type Dataset type
 * @property coverageType Type of partial coverage
 * @property missingComponents List of missing components
 * @property degradedReason Human-readable reason for degradation
 * @property partialPercentage Percentage of data available (0-100)
 * @property isComplete Whether the package is complete (should always be false for PartialPackage)
 * @property isOffline Whether the package is only available offline
 * @property isStale Whether the package data is stale
 * @property isInvalid Whether the package data is invalid
 */
data class PartialPackage(
    val datasetId: String,
    val version: String,
    val type: PartialPackageType,
    val coverageType: CoverageType,
    val missingComponents: List<String>,
    val degradedReason: String,
    val partialPercentage: Int,
    val isComplete: Boolean = false,
    val isOffline: Boolean = true,
    val isStale: Boolean = false,
    val isInvalid: Boolean = false
) {
    /**
     * Checks if this partial package can be used for a specific operation.
     * 
     * @param operationType Type of operation
     * @return True if the operation can be performed with this partial package
     */
    fun canSupportOperation(operationType: OperationType): Boolean {
        return when (operationType) {
            OperationType.ROUTING -> canSupportRouting()
            OperationType.ELEVATION -> canSupportElevation()
            OperationType.CHARGING -> canSupportCharging()
            OperationType.VEHICLE -> canSupportVehicle()
            OperationType.WEATHER -> canSupportWeather()
            OperationType.TRAFFIC -> canSupportTraffic()
            OperationType.SEARCH -> canSupportSearch()
            OperationType.TRIP_PLANNING -> canSupportTripPlanning()
            OperationType.CHARGING_PLANNING -> canSupportChargingPlanning()
            OperationType.NAVIGATION -> canSupportNavigation()
        }
    }

    /**
     * Checks if routing can be performed with this partial package.
     */
    fun canSupportRouting(): Boolean {
        return when (coverageType) {
            CoverageType.GEOGRAPHIC,
            CoverageType.PARTIAL_GEOGRAPHIC -> missingComponents.none { it == "routing_graph" }
            CoverageType.ROAD_NETWORK,
            CoverageType.PARTIAL_ROAD_NETWORK -> missingComponents.none { it == "routing_graph" }
            CoverageType.ROUTING_GRAPH,
            CoverageType.PARTIAL_ROUTING_GRAPH -> true
            else -> false
        }
    }

    /**
     * Checks if elevation can be performed with this partial package.
     */
    fun canSupportElevation(): Boolean {
        return when (coverageType) {
            CoverageType.GEOGRAPHIC -> missingComponents.none { it == "elevation" }
            CoverageType.ELEVATION -> true
            CoverageType.PARTIAL_ELEVATION -> true
            else -> false
        }
    }

    /**
     * Checks if charging lookup can be performed with this partial package.
     */
    fun canSupportCharging(): Boolean {
        return when (coverageType) {
            CoverageType.GEOGRAPHIC -> missingComponents.none { it == "charging_infrastructure" }
            CoverageType.CHARGING_INFRASTRUCTURE -> true
            CoverageType.PARTIAL_CHARGING -> true
            else -> false
        }
    }

    /**
     * Checks if vehicle lookup can be performed with this partial package.
     */
    fun canSupportVehicle(): Boolean {
        return when (coverageType) {
            CoverageType.VEHICLE_DATABASE -> true
            CoverageType.PARTIAL_VEHICLE -> true
            else -> false
        }
    }

    /**
     * Checks if weather can be performed with this partial package.
     */
    fun canSupportWeather(): Boolean {
        return when (coverageType) {
            CoverageType.WEATHER_CACHE -> true
            CoverageType.PARTIAL_WEATHER -> true
            else -> false
        }
    }

    /**
     * Checks if traffic can be performed with this partial package.
     */
    fun canSupportTraffic(): Boolean {
        return when (coverageType) {
            CoverageType.TRAFFIC_CACHE -> true
            CoverageType.PARTIAL_TRAFFIC -> true
            else -> false
        }
    }

    /**
     * Checks if search can be performed with this partial package.
     */
    fun canSupportSearch(): Boolean {
        return when (coverageType) {
            CoverageType.GEOGRAPHIC -> missingComponents.none { it == "places" }
            CoverageType.PLACES -> true
            CoverageType.PARTIAL_PLACES -> true
            else -> false
        }
    }

    /**
     * Checks if trip planning can be performed with this partial package.
     */
    fun canSupportTripPlanning(): Boolean {
        return when (coverageType) {
            CoverageType.GEOGRAPHIC,
            CoverageType.PARTIAL_GEOGRAPHIC -> !(missingComponents.contains("routing_graph") && missingComponents.contains("elevation")) && !missingComponents.contains("road_network")
            CoverageType.ROAD_NETWORK,
            CoverageType.PARTIAL_ROAD_NETWORK -> missingComponents.none { it == "routing_graph" }
            CoverageType.ROUTING_GRAPH,
            CoverageType.PARTIAL_ROUTING_GRAPH -> missingComponents.none { it == "routing_graph" }
            else -> false
        }
    }

    /**
     * Checks if charging planning can be performed with this partial package.
     */
    fun canSupportChargingPlanning(): Boolean {
        return when (coverageType) {
            CoverageType.GEOGRAPHIC,
            CoverageType.PARTIAL_GEOGRAPHIC -> missingComponents.none { it == "charging_infrastructure" }
            CoverageType.CHARGING_INFRASTRUCTURE,
            CoverageType.PARTIAL_CHARGING -> missingComponents.none { it == "charging_infrastructure" }
            else -> false
        }
    }

    /**
     * Checks if navigation can be performed with this partial package.
     */
    fun canSupportNavigation(): Boolean {
        return when (coverageType) {
            CoverageType.GEOGRAPHIC,
            CoverageType.PARTIAL_GEOGRAPHIC -> !(missingComponents.contains("routing_graph") && missingComponents.contains("elevation")) && !missingComponents.contains("road_network")
            CoverageType.ROAD_NETWORK,
            CoverageType.PARTIAL_ROAD_NETWORK -> missingComponents.none { it == "routing_graph" }
            CoverageType.ROUTING_GRAPH,
            CoverageType.PARTIAL_ROUTING_GRAPH -> missingComponents.none { it == "routing_graph" }
            else -> false
        }
    }

    /**
     * Gets the degradation level for a specific operation.
     * 
     * @param operationType Type of operation
     * @return Degradation level
     */
    fun getDegradationLevel(operationType: OperationType): DegradationLevel {
        if (!canSupportOperation(operationType)) {
            return DegradationLevel.UNSUPPORTED
        }

        if (isComplete) {
            return DegradationLevel.FULL
        }

        if (missingComponents.isNotEmpty()) {
            return DegradationLevel.DEGRADED
        }

        return when (coverageType) {
            CoverageType.PARTIAL_ROAD_NETWORK,
            CoverageType.PARTIAL_ROUTING_GRAPH,
            CoverageType.PARTIAL_ELEVATION,
            CoverageType.PARTIAL_CHARGING,
            CoverageType.PARTIAL_VEHICLE,
            CoverageType.PARTIAL_WEATHER,
            CoverageType.PARTIAL_TRAFFIC,
            CoverageType.PARTIAL_PLACES -> DegradationLevel.PARTIAL
            else -> DegradationLevel.DEGRADED
        }
    }

    /**
     * Gets the missing components for a specific operation.
     * 
     * @param operationType Type of operation
     * @return List of missing components
     */
    fun getMissingComponentsForOperation(operationType: OperationType): List<String> {
        return when (operationType) {
            OperationType.ROUTING -> missingComponents.filter { it in listOf("road_network", "routing_graph") }
            OperationType.ELEVATION -> missingComponents.filter { it == "elevation" }
            OperationType.CHARGING -> missingComponents.filter { it == "charging_infrastructure" }
            OperationType.VEHICLE -> missingComponents.filter { it == "vehicle_database" }
            OperationType.WEATHER -> missingComponents.filter { it == "weather_cache" }
            OperationType.TRAFFIC -> missingComponents.filter { it == "traffic_cache" }
            OperationType.SEARCH -> missingComponents.filter { it == "places" }
            OperationType.TRIP_PLANNING -> missingComponents.filter { it in listOf("road_network", "routing_graph", "elevation") }
            OperationType.CHARGING_PLANNING -> missingComponents.filter { it in listOf("charging_infrastructure", "road_network") }
            OperationType.NAVIGATION -> missingComponents.filter { it in listOf("road_network", "routing_graph", "elevation") }
        }
    }

    /**
     * Converts this partial package to a map for serialization.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "datasetId" to datasetId,
            "version" to version,
            "type" to type.name,
            "coverageType" to coverageType.name,
            "missingComponents" to missingComponents,
            "degradedReason" to degradedReason,
            "partialPercentage" to partialPercentage,
            "isComplete" to isComplete,
            "isOffline" to isOffline,
            "isStale" to isStale,
            "isInvalid" to isInvalid
        )
    }

    companion object {
        /**
         * Creates a PartialPackage from a map (for deserialization).
         */
        fun fromMap(map: Map<String, Any?>): PartialPackage {
            val datasetId = map["datasetId"] as String
            val version = map["version"] as String
            val type = PartialPackageType.valueOf(map["type"] as String)
            val coverageType = CoverageType.valueOf(map["coverageType"] as String)
            val missingComponents = (map["missingComponents"] as? List<*>)?.map { it as String } ?: emptyList()
            val degradedReason = map["degradedReason"] as String
            val partialPercentage = (map["partialPercentage"] as? Number)?.toInt() ?: 0
            val isComplete = map["isComplete"] as? Boolean ?: false
            val isOffline = map["isOffline"] as? Boolean ?: true
            val isStale = map["isStale"] as? Boolean ?: false
            val isInvalid = map["isInvalid"] as? Boolean ?: false

            return PartialPackage(
                datasetId = datasetId,
                version = version,
                type = type,
                coverageType = coverageType,
                missingComponents = missingComponents,
                degradedReason = degradedReason,
                partialPercentage = partialPercentage,
                isComplete = isComplete,
                isOffline = isOffline,
                isStale = isStale,
                isInvalid = isInvalid
            )
        }
    }
}

/**
 * Partial package types.
 */
enum class PartialPackageType {
    MAP_DATA,
    ROAD_NETWORK,
    ROUTING_GRAPH,
    ROAD_ATTRIBUTES,
    ROAD_RESTRICTIONS,
    ELEVATION,
    ROAD_PROFILE,
    CHARGING_INFRASTRUCTURE,
    CHARGING_CONNECTORS,
    VEHICLE_DATABASE,
    ENERGY_PARAMETERS,
    WEATHER_CACHE,
    TRAFFIC_CACHE,
    FAVORITES,
    RECENT_DESTINATIONS,
    TRIP_DATA,
    NAVIGATION_RECOVERY_STATE,
    APPLICATION_METADATA,
    PLACES
}

/**
 * Coverage types for partial packages.
 */
enum class CoverageType {
    GEOGRAPHIC,
    ROAD_NETWORK,
    ROUTING_GRAPH,
    ELEVATION,
    CHARGING_INFRASTRUCTURE,
    VEHICLE_DATABASE,
    WEATHER_CACHE,
    TRAFFIC_CACHE,
    PLACES,
    PARTIAL_GEOGRAPHIC,
    PARTIAL_ROAD_NETWORK,
    PARTIAL_ROUTING_GRAPH,
    PARTIAL_ELEVATION,
    PARTIAL_CHARGING,
    PARTIAL_VEHICLE,
    PARTIAL_WEATHER,
    PARTIAL_TRAFFIC,
    PARTIAL_PLACES,
    UNKNOWN
}

/**
 * Operation types that can be supported by partial packages.
 */
enum class OperationType {
    ROUTING,
    ELEVATION,
    CHARGING,
    VEHICLE,
    WEATHER,
    TRAFFIC,
    SEARCH,
    TRIP_PLANNING,
    CHARGING_PLANNING,
    NAVIGATION
}

/**
 * Degradation levels for partial packages.
 */
enum class DegradationLevel {
    FULL,
    DEGRADED,
    PARTIAL,
    UNSUPPORTED
}
