package com.evnavigator.iran.domain.policy

/**
 * Injectable clock abstraction for deterministic TTL and freshness evaluation across `:domain`.
 * Prevents hidden calls to `System.currentTimeMillis()` inside business rules and enables
 * exact unit testing of stale, future-skewed, and zero/negative timestamps.
 */
fun interface DomainClock {
    fun nowEpochMs(): Long

    companion object {
        val SYSTEM: DomainClock = DomainClock { System.currentTimeMillis() }

        fun fixed(epochMs: Long): DomainClock = DomainClock { epochMs }
    }
}
