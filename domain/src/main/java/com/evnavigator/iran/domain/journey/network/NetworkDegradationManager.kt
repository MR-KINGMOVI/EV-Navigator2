package com.evnavigator.iran.domain.journey.network

/**
 * Operational mode of the EV journey navigation engine.
 */
enum class JourneyConnectivityMode {
    ONLINE_FULL,
    OFFLINE_DEGRADED,
    OFFLINE_STALE_DATA
}

/**
 * Manages graceful network degradation, cache preservation, and honest freshness reporting.
 */
class NetworkDegradationManager {

    fun determineMode(
        isNetworkConnected: Boolean,
        lastOnlineDataEpochMs: Long,
        currentEpochMs: Long = System.currentTimeMillis()
    ): JourneyConnectivityMode {
        if (isNetworkConnected) {
            return JourneyConnectivityMode.ONLINE_FULL
        }

        val ageMs = currentEpochMs - lastOnlineDataEpochMs
        return if (ageMs < 30 * 60 * 1000L) { // Less than 30 min offline
            JourneyConnectivityMode.OFFLINE_DEGRADED
        } else {
            JourneyConnectivityMode.OFFLINE_STALE_DATA
        }
    }

    fun getDegradationNotice(mode: JourneyConnectivityMode): String? {
        return when (mode) {
            JourneyConnectivityMode.ONLINE_FULL -> null
            JourneyConnectivityMode.OFFLINE_DEGRADED ->
                "حالت آفلاین: مسیر و محاسبات انرژی با داده‌های محلی ذخیره‌شده ادامه دارد. وضعیت زنده شارژرها در دسترس نیست."
            JourneyConnectivityMode.OFFLINE_STALE_DATA ->
                "هشدار آفلاین: داده‌های شبکه منقضی شده‌اند. لطفاً قبل از مراجعه به ایستگاه شارژ، از در دسترس بودن آن اطمینان حاصل فرمایید."
        }
    }
}
