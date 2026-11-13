package com.evnavigator.iran.core.charging.provider

import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.charging.Freshness
import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Charging station provider for Mapna Group / MEKO EV charging network in Iran.
 * Interfaces with Mapna telemetry and API endpoints.
 */
class MapnaChargingStationProvider(
    private val remoteApiCaller: (suspend (query: ProviderStationQuery) -> List<ChargingStation>)? = null,
    private val stationSupplier: (() -> List<ChargingStation>)? = null
) : ChargingStationProvider {

    override val providerType: ChargingSourceProviderType = ChargingSourceProviderType.MAPNA_MEKO

    override suspend fun queryStations(query: ProviderStationQuery): ChargingProviderResult {
        val now = System.currentTimeMillis()
        val stations = try {
            if (remoteApiCaller != null) {
                remoteApiCaller.invoke(query)
            } else if (stationSupplier != null) {
                stationSupplier.invoke().filter { station ->
                    val coord = GeoCoordinate(station.location.latitude, station.location.longitude)
                    query.center.distanceTo(coord) <= query.radiusMeters
                }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        return ChargingProviderResult(
            stations = stations,
            providerType = providerType,
            freshness = Freshness(
                timestamp = now,
                source = "MAPNA_MEKO_API",
                ageMillis = 0,
                confidence = 0.90,
                version = "2024.1",
                isFresh = true
            ),
            warnings = if (stations.isEmpty()) listOf("Mapna MEKO endpoint returned zero matching stations") else emptyList()
        )
    }

    override suspend fun getStationById(id: String): ChargingStation? {
        return stationSupplier?.invoke()?.firstOrNull { it.id == id }
    }
}
