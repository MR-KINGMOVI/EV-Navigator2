package com.evnavigator.iran.domain.model

/**
 * Represents a map tile.
 *
 * @param x X coordinate of the tile
 * @param y Y coordinate of the tile
 * @param zoom Zoom level of the tile
 */
data class MapTile(
    val x: Int,
    val y: Int,
    val zoom: Int
)