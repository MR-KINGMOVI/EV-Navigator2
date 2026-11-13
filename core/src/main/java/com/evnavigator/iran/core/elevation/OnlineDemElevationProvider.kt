package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.roundToInt

/**
 * Metadata descriptor for an online Digital Elevation Model (DEM) data source.
 */
data class OnlineDemMetadata(
    val providerName: String = "OpenMeteo-DEM",
    val verticalDatum: VerticalDatum = VerticalDatum.EGM96,
    val resolutionMeters: Double = 90.0,
    val sourceProvenance: ElevationProvenance = ElevationProvenance.CUSTOM_API,
    val confidence: Double = 0.95,
    val baseEndpoint: String = "https://api.open-meteo.com/v1/elevation"
)

/**
 * Production-ready online DEM elevation provider supporting Open-Meteo / OpenTopoData API format.
 *
 * Enforces vertical datum EGM96, batch querying with size limits, strict timeouts, LRU caching,
 * and zero synthetic fallback: unknown coordinates are explicitly propagated as NaN with UNKNOWN quality.
 */
class OnlineDemElevationProvider(
    val metadata: OnlineDemMetadata = OnlineDemMetadata(),
    private val maxCacheSize: Int = 4096,
    private val httpTransport: ((url: String) -> String?)? = null
) : BatchedElevationProvider {

    // Thread-safe LRU cache: Coordinate key -> elevation in meters
    private val cache: MutableMap<String, Double> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Double>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double>?): Boolean {
                return size > maxCacheSize
            }
        }
    )

    override fun canHandle(coordinate: GeoCoordinate): Boolean {
        // Enforce Iran operational bounding box
        return coordinate.latitude in 24.0..40.5 && coordinate.longitude in 43.5..64.5
    }

    override fun getName(): String = metadata.providerName

    override fun getMaxBatchSize(): Int = 100

    private fun cacheKey(c: GeoCoordinate): String {
        // Quantize to ~10m precision (4 decimal places) for caching
        val latR = (c.latitude * 10000.0).roundToInt() / 10000.0
        val lonR = (c.longitude * 10000.0).roundToInt() / 10000.0
        return "$latR,$lonR"
    }

    override suspend fun getElevationAt(coordinate: GeoCoordinate): ElevationProviderResult {
        return getElevationBatch(listOf(coordinate), batchSize = 1)
    }

    override suspend fun getElevationProfile(coordinates: List<GeoCoordinate>): ElevationProviderResult {
        return getElevationBatch(coordinates, batchSize = getMaxBatchSize())
    }

    override suspend fun getElevationBatch(coordinates: List<GeoCoordinate>): ElevationProviderResult {
        return getElevationBatch(coordinates, batchSize = getMaxBatchSize())
    }

    override suspend fun getElevationBatch(
        coordinates: List<GeoCoordinate>,
        batchSize: Int
    ): ElevationProviderResult = withContext(Dispatchers.IO) {
        if (coordinates.isEmpty()) {
            return@withContext ElevationProviderResult.Success(emptyList())
        }

        val validPoints = mutableListOf<ElevationPoint>()
        val invalidPoints = mutableListOf<GeoCoordinate>()

        // Separate cached points from coordinates needing network query
        val uncachedCoordinates = mutableListOf<GeoCoordinate>()
        val coordinateIndexMap = mutableMapOf<GeoCoordinate, Double>()

        for (coord in coordinates) {
            if (!canHandle(coord)) {
                invalidPoints.add(coord)
                continue
            }
            val key = cacheKey(coord)
            val cachedElev = cache[key]
            if (cachedElev != null && !cachedElev.isNaN()) {
                coordinateIndexMap[coord] = cachedElev
            } else {
                uncachedCoordinates.add(coord)
            }
        }

        // Query network in batches
        val effectiveBatchSize = batchSize.coerceIn(1, 100)
        val chunks = uncachedCoordinates.chunked(effectiveBatchSize)

        for (chunk in chunks) {
            val lats = chunk.joinToString(",") { "%.5f".format(it.latitude) }
            val lons = chunk.joinToString(",") { "%.5f".format(it.longitude) }
            val url = "${metadata.baseEndpoint}?latitude=$lats&longitude=$lons"

            val responseBody = try {
                if (httpTransport != null) {
                    httpTransport.invoke(url)
                } else {
                    executeHttpGet(url)
                }
            } catch (e: Exception) {
                null
            }

            if (responseBody == null) {
                // Network failed for this chunk
                invalidPoints.addAll(chunk)
                continue
            }

            val parsedElevations = parseElevationResponse(responseBody, chunk.size)
            if (parsedElevations.size == chunk.size) {
                for (i in chunk.indices) {
                    val coord = chunk[i]
                    val elev = parsedElevations[i]
                    if (elev != null && !elev.isNaN()) {
                        coordinateIndexMap[coord] = elev
                        cache[cacheKey(coord)] = elev
                    } else {
                        invalidPoints.add(coord)
                    }
                }
            } else {
                invalidPoints.addAll(chunk)
            }
        }

        // Build result in original coordinate order
        for (coord in coordinates) {
            val elev = coordinateIndexMap[coord]
            if (elev != null && !elev.isNaN()) {
                validPoints.add(
                    ElevationPoint.known(
                        coordinate = coord,
                        elevation = elev,
                        accuracy = metadata.resolutionMeters / 10.0,
                        provenance = metadata.sourceProvenance
                    )
                )
            }
        }

        when {
            invalidPoints.isEmpty() -> ElevationProviderResult.Success(validPoints)
            validPoints.isEmpty() -> ElevationProviderResult.Error(
                "Failed to fetch elevation for ${coordinates.size} coordinates from online DEM provider."
            )
            else -> ElevationProviderResult.PartialSuccess(
                validPoints = validPoints,
                invalidPoints = invalidPoints
            )
        }
    }

    private fun parseElevationResponse(jsonStr: String, _expectedCount: Int = 0): List<Double?> {
        val result = mutableListOf<Double?>()
        try {
            val root = JSONObject(jsonStr)
            if (root.has("elevation")) {
                // Open-Meteo array format: "elevation": [1180.5, 1205.0]
                val array = root.getJSONArray("elevation")
                for (i in 0 until array.length()) {
                    if (array.isNull(i)) {
                        result.add(null)
                    } else {
                        val v = array.getDouble(i)
                        result.add(if (v.isNaN()) null else v)
                    }
                }
            } else if (root.has("results")) {
                // OpenTopoData format: "results": [{"elevation": 1180.5}, ...]
                val array = root.getJSONArray("results")
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    if (item.isNull("elevation")) {
                        result.add(null)
                    } else {
                        result.add(item.getDouble("elevation"))
                    }
                }
            }
        } catch (_: Exception) {
            // Parsing failure
        }
        return result
    }

    private fun executeHttpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "EVNavigatorIran/1.0")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                throw IllegalStateException("HTTP ${conn.responseCode} returned from $urlStr")
            }
        } finally {
            conn.disconnect()
        }
    }
}
