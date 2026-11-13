package com.evnavigator.iran.domain.model.cache

/**
 * Composite cache key ensuring strict dataset version isolation and preventing cache poisoning.
 *
 * @param datasetVersion Version identifier of the dataset context
 * @param key Unique key within the dataset version scope
 */
data class CacheKey(
    val datasetVersion: String,
    val key: String,
    val region: String? = null
)
