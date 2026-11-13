package com.evnavigator.iran.domain.model

/**
 * Represents a map style.
 *
 * @param id Unique identifier for the style
 * @param name Human-readable name of the style
 * @param url URL to fetch the style definition
 */
data class MapStyle(
    val id: String,
    val name: String,
    val url: String
)