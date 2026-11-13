package com.evnavigator.iran.core.charging.provider

import com.evnavigator.iran.core.charging.ChargingPort
import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.Freshness
import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Types of charging station data providers.
 */
enum class ChargingSourceProviderType {
    MAPNA_MEKO,
    OPEN_CHARGE_MAP,
    TAVANIR_IRAN,
    OFFLINE_CATALOG,
    COMPOSITE
}

/**
 * Real dynamic port operational status.
 * Strictly separates static existence from live operational telemetry.
 */
enum class DynamicPortStatus {
    AVAILABLE,
    OCCUPIED,
    OUT_OF_SERVICE,
    UNKNOWN,
    STALE,
    UNVERIFIED
}

/**
 * Query parameters for spatial and technical charging station discovery.
 */
data class ProviderStationQuery(
    val center: GeoCoordinate,
    val radiusMeters: Double,
    val minPowerKw: Double? = null,
    val connectorTypes: Set<ConnectorType>? = null,
    val requireOperationalOnly: Boolean = true
)

/**
 * Standardized result returned by charging station providers.
 */
data class ChargingProviderResult(
    val stations: List<ChargingStation>,
    val providerType: ChargingSourceProviderType,
    val freshness: Freshness,
    val warnings: List<String> = emptyList()
)

/**
 * Standard interface for multi-source charging station providers.
 */
interface ChargingStationProvider {
    val providerType: ChargingSourceProviderType

    /**
     * Queries stations within geographic radius satisfying filter constraints.
     */
    suspend fun queryStations(query: ProviderStationQuery): ChargingProviderResult

    /**
     * Retrieves station by unique identifier.
     */
    suspend fun getStationById(id: String): ChargingStation?
}
