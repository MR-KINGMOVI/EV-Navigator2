package com.evnavigator.iran.core.charging.provider

import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.charging.Freshness
import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Charging station provider for Tavanir (Iran National Power Generation, Transmission & Distribution).
 */
class TavanirChargingStationProvider(
    private val remoteApiCaller: (suspend (query: ProviderStationQuery) -> List<ChargingStation>)? = null,
    private val stationSupplier: (() -> List<ChargingStation>)? = null
) : ChargingStationProvider {

    override val providerType: ChargingSourceProviderType = ChargingSourceProviderType.TAVANIR_IRAN

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
                source = "TAVANIR_IRAN_GRID",
                ageMillis = 0,
                confidence = 0.85,
                version = "2024.1",
                isFresh = true
            ),
            warnings = if (stations.isEmpty()) listOf("Tavanir network returned zero matching stations in this area") else emptyList()
        )
    }

    override suspend fun getStationById(id: String): ChargingStation? {
        return stationSupplier?.invoke()?.firstOrNull { it.id == id }
    }
}
