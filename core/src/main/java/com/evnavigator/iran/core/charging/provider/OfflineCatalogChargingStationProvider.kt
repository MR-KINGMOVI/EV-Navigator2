package com.evnavigator.iran.core.charging.provider

import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.charging.Freshness
import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Offline catalog charging station provider.
 * Serves verified Iranian charging stations without network connectivity.
 */
class OfflineCatalogChargingStationProvider(
    private val stationSupplier: () -> List<ChargingStation>
) : ChargingStationProvider {

    override val providerType: ChargingSourceProviderType = ChargingSourceProviderType.OFFLINE_CATALOG

    override suspend fun queryStations(query: ProviderStationQuery): ChargingProviderResult {
        val allStations = stationSupplier()
        val now = System.currentTimeMillis()

        val filtered = allStations.filter { station ->
            val stationCoord = GeoCoordinate(station.location.latitude, station.location.longitude)
            val distance = query.center.distanceTo(stationCoord)

            if (distance > query.radiusMeters) return@filter false
            if (query.requireOperationalOnly && !station.isOperational) return@filter false

            if (query.minPowerKw != null) {
                val maxKw = station.ports.maxOfOrNull { it.power ?: 0.0 } ?: 0.0
                if (maxKw < query.minPowerKw) return@filter false
            }

            if (!query.connectorTypes.isNullOrEmpty()) {
                val hasMatchingConnector = station.ports.any { port ->
                    query.connectorTypes.contains(port.connectorType)
                }
                if (!hasMatchingConnector) return@filter false
            }

            true
        }

        return ChargingProviderResult(
            stations = filtered,
            providerType = providerType,
            freshness = Freshness(
                timestamp = now,
                source = "OfflineCatalog",
                ageMillis = 0,
                confidence = 0.85,
                version = "2024.2",
                isFresh = true
            ),
            warnings = if (filtered.isEmpty()) listOf("No stations found within search radius in offline catalog") else emptyList()
        )
    }

    override suspend fun getStationById(id: String): ChargingStation? {
        return stationSupplier().firstOrNull { it.id == id }
    }
}
