package com.evnavigator.iran.core.charging.provider

import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.charging.Freshness
import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Composite charging station provider that queries multiple sources (online Mapna, OCM, Tavanir)
 * and falls back to offline catalog, performing spatial and ID deduplication.
 */
class CompositeChargingStationProvider(
    private val providers: List<ChargingStationProvider>,
    private val offlineFallback: OfflineCatalogChargingStationProvider? = null
) : ChargingStationProvider {

    override val providerType: ChargingSourceProviderType = ChargingSourceProviderType.COMPOSITE

    override suspend fun queryStations(query: ProviderStationQuery): ChargingProviderResult {
        val collectedStations = mutableListOf<ChargingStation>()
        val warnings = mutableListOf<String>()
        val now = System.currentTimeMillis()

        for (provider in providers) {
            try {
                val res = provider.queryStations(query)
                collectedStations.addAll(res.stations)
                warnings.addAll(res.warnings)
            } catch (e: Exception) {
                warnings.add("Provider ${provider.providerType} failed: ${e.message}")
            }
        }

        // If online providers returned nothing and offline fallback is available, query offline
        if (collectedStations.isEmpty() && offlineFallback != null) {
            val fallbackRes = offlineFallback.queryStations(query)
            collectedStations.addAll(fallbackRes.stations)
            warnings.addAll(fallbackRes.warnings)
            warnings.add("All online providers unavailable; fell back to offline catalog.")
        }

        // Deduplicate stations by ID and proximity (< 50m)
        val deduplicated = mutableListOf<ChargingStation>()
        for (station in collectedStations) {
            val stationCoord = GeoCoordinate(station.location.latitude, station.location.longitude)
            val isDuplicate = deduplicated.any { existing ->
                existing.id == station.id ||
                (!existing.location.address.isNullOrBlank() && existing.location.address == station.location.address) ||
                GeoCoordinate(existing.location.latitude, existing.location.longitude).distanceTo(stationCoord) < 50.0
            }
            if (!isDuplicate) {
                deduplicated.add(station)
            }
        }

        return ChargingProviderResult(
            stations = deduplicated,
            providerType = providerType,
            freshness = Freshness(
                timestamp = now,
                source = "CompositeProvider",
                ageMillis = 0,
                confidence = if (deduplicated.isNotEmpty()) 0.88 else 0.50,
                version = "1.0",
                isFresh = true
            ),
            warnings = warnings
        )
    }

    override suspend fun getStationById(id: String): ChargingStation? {
        for (provider in providers) {
            val s = provider.getStationById(id)
            if (s != null) return s
        }
        return offlineFallback?.getStationById(id)
    }
}
