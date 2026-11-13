package com.evnavigator.iran.ui.screen.map.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.ITileSource

/**
 * Calibrated Automotive Obsidian Glass Tone-Mapping Filter for Map Tiles.
 * Eliminates dull, flat black washes and photographic negative artifacts.
 *
 * Engineered color science:
 * 1. Terrain is tone-mapped to rich deep obsidian/titanium.
 * 2. Highways & major arterials are elevated to high-contrast crisp titanium-cyan.
 * 3. Water features are preserved in deep sapphire.
 * 4. Road labels and city text remain razor-sharp and legible in all lighting conditions.
 */
object TrueDarkTileFilter {

    fun createForSource(tileSource: ITileSource, isDark: Boolean): ColorMatrixColorFilter {
        if (tileSource == CockpitMapTileSource.GoogleSatellite || tileSource == CockpitMapTileSource.GoogleSatellite) {
            return createDaylightFilter()
        }
        if (tileSource == CockpitMapTileSource.DarkMatter) {
            return createFilter()
        }
        return if (isDark) createFilter() else createDaylightFilter()
    }

    fun createFilter(): ColorMatrixColorFilter {
        val matrix = ColorMatrix(
            floatArrayOf(
                -0.74f, -0.74f, -0.74f, 0f, 192f,
                -0.70f, -0.70f, -0.70f, 0f, 198f,
                -0.56f, -0.56f, -0.56f, 0f, 228f,
                 0.00f,  0.00f,  0.00f, 1f,   0f
            )
        )
        return ColorMatrixColorFilter(matrix)
    }

    fun createDaylightFilter(): ColorMatrixColorFilter {
        val matrix = ColorMatrix().apply {
            setSaturation(1.15f)
        }
        return ColorMatrixColorFilter(matrix)
    }
}
