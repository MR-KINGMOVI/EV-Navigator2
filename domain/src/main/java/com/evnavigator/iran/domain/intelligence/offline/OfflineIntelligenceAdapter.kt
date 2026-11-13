package com.evnavigator.iran.domain.intelligence.offline

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.IntelligenceCoverageStatus

/**
 * Status of local offline datasets available on the device.
 */
data class OfflineDatasetAvailability(
    val hasMapRoutingDataset: Boolean,
    val mapDatasetVersion: String? = null,
    val hasElevationDataset: Boolean,
    val elevationDatasetVersion: String? = null,
    val hasChargingRegistryDataset: Boolean,
    val chargingDatasetVersion: String? = null
)

/**
 * Cached environmental data item with timestamp.
 */
data class CachedEnvironmentSnapshot(
    val temperatureCelsius: Double?,
    val windSpeedMps: Double?,
    val timestampEpochMs: Long
)

/**
 * Policy and adapter for offline execution of Phase 14 intelligence.
 * Ensures graceful degradation without fabricating live status or hiding missing data.
 */
class OfflineIntelligenceAdapter(
    private val maxWeatherCacheValidityMs: Long = 4 * 60 * 60 * 1000L // 4 hours
) {

    /**
     * Adapt environmental and dataset inputs into explicit InputQualityAttributes
     * based on network and offline package availability.
     */
    fun resolveEnvironmentalInputs(
        isOnline: Boolean,
        cachedWeather: CachedEnvironmentSnapshot?,
        currentEpochMs: Long = System.currentTimeMillis()
    ): Pair<InputQualityAttribute<Double>, InputQualityAttribute<Double>> {
        if (isOnline && cachedWeather != null && (currentEpochMs - cachedWeather.timestampEpochMs < 30 * 60 * 1000L)) {
            // Fresh online data
            val temp = cachedWeather.temperatureCelsius?.let {
                InputQualityAttribute.measured(it, cachedWeather.timestampEpochMs)
            } ?: InputQualityAttribute.modelAssumption(
                com.evnavigator.iran.domain.policy.EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS,
                "MODEL_ASSUMPTION: Live temp missing; ISA reference 15°C used"
            )

            val wind = cachedWeather.windSpeedMps?.let {
                InputQualityAttribute.measured(it, cachedWeather.timestampEpochMs)
            } ?: InputQualityAttribute.modelAssumption(0.0, "MODEL_ASSUMPTION: Live wind missing; calm wind assumed")

            return Pair(temp, wind)
        }

        // Offline mode
        if (cachedWeather != null) {
            val ageMs = currentEpochMs - cachedWeather.timestampEpochMs
            val isStale = ageMs > maxWeatherCacheValidityMs

            val tempAttr = if (cachedWeather.temperatureCelsius != null) {
                InputQualityAttribute(
                    value = cachedWeather.temperatureCelsius,
                    origin = InputOrigin.ESTIMATED_MODEL,
                    timestampEpochMs = cachedWeather.timestampEpochMs,
                    confidence = if (isStale) 0.40 else 0.75,
                    isStale = isStale,
                    assumptionNote = if (isStale) "Cached weather observation is stale (> 4h old)." else "Using recent offline cached weather."
                )
            } else {
                InputQualityAttribute.modelAssumption(
                    com.evnavigator.iran.domain.policy.EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS,
                    "MODEL_ASSUMPTION: Offline with no cached temp; ISA reference 15°C assumed",
                    confidence = 0.45
                )
            }

            val windAttr = if (cachedWeather.windSpeedMps != null) {
                InputQualityAttribute(
                    value = cachedWeather.windSpeedMps,
                    origin = InputOrigin.ESTIMATED_MODEL,
                    timestampEpochMs = cachedWeather.timestampEpochMs,
                    confidence = if (isStale) 0.40 else 0.70,
                    isStale = isStale,
                    assumptionNote = if (isStale) "Cached wind data is stale." else "Using cached wind."
                )
            } else {
                InputQualityAttribute.assumed(0.0, "Offline with no cached wind; assumed 0 m/s", confidence = 0.50)
            }

            return Pair(tempAttr, windAttr)
        }

        // Completely offline with zero cache
        val defaultTemp = InputQualityAttribute.assumed(20.0, "Offline with zero cached data; assumed standard 20°C", confidence = 0.40)
        val defaultWind = InputQualityAttribute.assumed(0.0, "Offline with zero cached data; assumed calm 0 m/s", confidence = 0.40)
        return Pair(defaultTemp, defaultWind)
    }

    /**
     * Determine coverage status based on offline packages.
     */
    fun determineCoverageStatus(datasets: OfflineDatasetAvailability): IntelligenceCoverageStatus {
        return when {
            !datasets.hasMapRoutingDataset -> IntelligenceCoverageStatus.UNAVAILABLE
            !datasets.hasElevationDataset -> IntelligenceCoverageStatus.ELEVATION_MISSING
            else -> IntelligenceCoverageStatus.FULL_COVERAGE
        }
    }
}
