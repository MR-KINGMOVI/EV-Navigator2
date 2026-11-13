package com.evnavigator.iran.core.weather

/**
 * Data freshness classification for environmental telemetry.
 */
enum class Freshness {
    FRESH,
    STALE_BUT_USABLE,
    EXPIRED,
    INVALID,
    CACHED,
    UNKNOWN
}
