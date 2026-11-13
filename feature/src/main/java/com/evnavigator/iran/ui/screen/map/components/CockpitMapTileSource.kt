package com.evnavigator.iran.ui.screen.map.components

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * High-Definition Automotive Map Tile Sources for EV Navigator Iran.
 *
 * Engineered with high-availability, unblocked global CDN mirrors for Iran & Middle East:
 * 1. CartoDarkMatter (CockpitDarkObsidian): Fast CDN-backed dark cartography, perfect for EV cockpits.
 * 2. CartoVoyager: Crisp, high-contrast daylight road cartography.
 * 3. OpenStreetMap: Standard OSM Mapnik.
 * 4. GoogleSatellite: Hybrid photorealistic satellite imagery.
 * 5. GoogleRoadmap: Google Maps vector cartography.
 */
object CockpitMapTileSource {

    /**
     * CartoDB Dark Matter - Ultra-fast, CDN-cached dark tiles (unblocked in Iran).
     */
    val CartoDarkMatter = object : OnlineTileSourceBase(
        "CartoDarkMatter",
        0,
        20,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
            "https://d.basemaps.cartocdn.com/dark_all/"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$x/$y.png"
        }
    }

    /**
     * CartoDB Voyager - Crisp street road network with highway labeling (unblocked in Iran).
     */
    val CartoVoyager = object : OnlineTileSourceBase(
        "CartoVoyager",
        0,
        20,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$x/$y.png"
        }
    }

    // Default Dark Cockpit source points to CartoDarkMatter for instant high-speed loading
    val DarkMatter = CartoDarkMatter
    val CockpitDarkObsidian = CartoDarkMatter

    /**
     * Standard OpenStreetMap Mapnik
     */
    val OpenStreetMap = TileSourceFactory.MAPNIK

    /**
     * Google Roadmap - Persian/English navigation cartography
     */
    val GoogleRoadmap = object : OnlineTileSourceBase(
        "GoogleRoadmap",
        0,
        21,
        256,
        ".png",
        arrayOf(
            "https://mt0.google.com/vt/lyrs=m&hl=fa",
            "https://mt1.google.com/vt/lyrs=m&hl=fa",
            "https://mt2.google.com/vt/lyrs=m&hl=fa",
            "https://mt3.google.com/vt/lyrs=m&hl=fa"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl&x=$x&y=$y&z=$zoom"
        }
    }

    /**
     * Google Satellite Hybrid - Photorealistic earth imagery with overlaid road geometries.
     */
    val GoogleSatellite = object : OnlineTileSourceBase(
        "GoogleSatellite",
        0,
        20,
        256,
        ".jpg",
        arrayOf(
            "https://mt0.google.com/vt/lyrs=y&hl=fa",
            "https://mt1.google.com/vt/lyrs=y&hl=fa",
            "https://mt2.google.com/vt/lyrs=y&hl=fa",
            "https://mt3.google.com/vt/lyrs=y&hl=fa"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl&x=$x&y=$y&z=$zoom"
        }
    }

    /**
     * Google Terrain - Shaded elevation relief for mountain passes.
     */
    val GoogleTerrain = object : OnlineTileSourceBase(
        "GoogleTerrain",
        0,
        18,
        256,
        ".jpg",
        arrayOf(
            "https://mt0.google.com/vt/lyrs=p&hl=fa",
            "https://mt1.google.com/vt/lyrs=p&hl=fa",
            "https://mt2.google.com/vt/lyrs=p&hl=fa",
            "https://mt3.google.com/vt/lyrs=p&hl=fa"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl&x=$x&y=$y&z=$zoom"
        }
    }
}
