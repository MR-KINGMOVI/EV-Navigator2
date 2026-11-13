package com.evnavigator.iran.core.routing.importer

import com.evnavigator.iran.core.routing.engine.CorridorEdge
import com.evnavigator.iran.core.routing.engine.CorridorNode
import com.evnavigator.iran.core.routing.engine.IranOfflineRoadCorridors
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.TurnRestriction
import com.evnavigator.iran.core.routing.spatial.SpatialNodeIndex
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

/**
 * Central OSM Road Graph Dataset Manager (Items 2, 3, 20, 21).
 *
 * Connects [OsmPbfGraphImporter] directly to the active routing engine ([IranRealRoadRoutingEngine]
 * and [DefaultRoutingRepository]).
 *
 * Supports full atomic dataset lifecycle:
 * - Staging import from `.osm.pbf`, `.osm` XML, or `.evrn` binary streams
 * - Structural & topological validation before activation
 * - SHA-256 checksum & version metadata tracking ([OsmDatasetMetadata])
 * - Atomic activation and instant rollback to previous healthy dataset on failure
 * - Fallback to the national trunk corridor skeleton ([IranOfflineRoadCorridors]) only when no
 *   regional OSM dataset is active.
 */
object OsmDatasetManager {

    enum class DatasetOriginType {
        OSM_PBF_IMPORTED,
        OSM_XML_IMPORTED,
        EVRN_BINARY_IMPORTED,
        FALLBACK_CORRIDOR_SKELETON
    }

    data class OsmDatasetMetadata(
        val datasetId: String,
        val extractionDateIso: String,
        val osmVersion: String,
        val coverageRegion: String,
        val sourceUrlOrPath: String,
        val nodeCount: Int,
        val wayCount: Int,
        val edgeCount: Int,
        val relationCount: Int,
        val schemaVersion: Int,
        val sha256Checksum: String,
        val originType: DatasetOriginType,
        val minLatitude: Double,
        val maxLatitude: Double,
        val minLongitude: Double,
        val maxLongitude: Double
    )

    data class ActiveRoutingDataset(
        val metadata: OsmDatasetMetadata,
        val nodes: List<CorridorNode>,
        val edges: List<CorridorEdge>,
        val turnRestrictions: List<TurnRestriction>,
        val roadGraph: RoadGraph,
        val spatialIndex: SpatialNodeIndex
    )

    private val lock = Any()
    private val importer = OsmPbfGraphImporter()

    @Volatile
    private var activeImportedDataset: ActiveRoutingDataset? = null

    @Volatile
    private var previousHealthyDataset: ActiveRoutingDataset? = null

    private val fallbackSkeletonDataset: ActiveRoutingDataset by lazy {
        buildFallbackSkeletonDataset()
    }

    /**
     * Returns the currently active dataset: prioritizes any validated imported OSM PBF/XML/EVRN
     * dataset, falling back to the national highway skeleton only when no OSM dataset has been loaded.
     */
    fun getActiveDataset(): ActiveRoutingDataset {
        return activeImportedDataset ?: fallbackSkeletonDataset
    }

    fun isUsingImportedOsmDataset(): Boolean = activeImportedDataset != null

    /**
     * Imports, validates, checksums, and atomically activates an `.osm.pbf` binary dataset.
     */
    fun importAndActivateOsmPbf(
        inputStream: InputStream,
        datasetId: String = "osm_pbf_${System.currentTimeMillis()}",
        coverageRegion: String = "Iran",
        sourceUrlOrPath: String = "local://osm.pbf",
        expectedSha256: String? = null
    ): Result<OsmDatasetMetadata> = synchronized(lock) {
        return try {
            val rawBytes = inputStream.readBytes()
            val actualSha256 = sha256Hex(rawBytes)
            if (!expectedSha256.isNullOrBlank()) {
                require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    "Dataset SHA-256 mismatch: expected $expectedSha256, got $actualSha256"
                }
            }

            val parsed = importer.importOsmPbf(ByteArrayInputStream(rawBytes))
            activateValidatedDataset(
                parsed = parsed,
                datasetId = datasetId,
                coverageRegion = coverageRegion,
                sourceUrlOrPath = sourceUrlOrPath,
                sha256 = actualSha256,
                originType = DatasetOriginType.OSM_PBF_IMPORTED
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports, validates, checksums, and atomically activates an `.osm` XML dataset.
     */
    fun importAndActivateOsmXml(
        inputStream: InputStream,
        datasetId: String = "osm_xml_${System.currentTimeMillis()}",
        coverageRegion: String = "Iran",
        sourceUrlOrPath: String = "local://map.osm",
        expectedSha256: String? = null
    ): Result<OsmDatasetMetadata> = synchronized(lock) {
        return try {
            val rawBytes = inputStream.readBytes()
            val actualSha256 = sha256Hex(rawBytes)
            if (!expectedSha256.isNullOrBlank()) {
                require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    "Dataset SHA-256 mismatch: expected $expectedSha256, got $actualSha256"
                }
            }

            val parsed = importer.importOsmXml(ByteArrayInputStream(rawBytes))
            activateValidatedDataset(
                parsed = parsed,
                datasetId = datasetId,
                coverageRegion = coverageRegion,
                sourceUrlOrPath = sourceUrlOrPath,
                sha256 = actualSha256,
                originType = DatasetOriginType.OSM_XML_IMPORTED
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports, validates, checksums, and atomically activates an `EVRN` binary road graph dataset.
     */
    fun importAndActivateBinaryGraph(
        inputStream: InputStream,
        datasetId: String = "evrn_${System.currentTimeMillis()}",
        coverageRegion: String = "Iran",
        sourceUrlOrPath: String = "local://road_graph.evrn",
        expectedSha256: String? = null
    ): Result<OsmDatasetMetadata> = synchronized(lock) {
        return try {
            val rawBytes = inputStream.readBytes()
            val actualSha256 = sha256Hex(rawBytes)
            if (!expectedSha256.isNullOrBlank()) {
                require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    "Dataset SHA-256 mismatch: expected $expectedSha256, got $actualSha256"
                }
            }

            val parsed = importer.importBinaryGraph(ByteArrayInputStream(rawBytes))
            activateValidatedDataset(
                parsed = parsed,
                datasetId = datasetId,
                coverageRegion = coverageRegion,
                sourceUrlOrPath = sourceUrlOrPath,
                sha256 = actualSha256,
                originType = DatasetOriginType.EVRN_BINARY_IMPORTED
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rolls back to the previous healthy dataset if an update needs to be reverted (Item 20).
     */
    fun rollbackToPreviousDataset(): Boolean = synchronized(lock) {
        val prev = previousHealthyDataset ?: return false
        activeImportedDataset = prev
        previousHealthyDataset = null
        return true
    }

    /**
     * Clears any imported custom dataset and restores the fallback national highway skeleton.
     */
    fun resetToFallbackCorridors() = synchronized(lock) {
        previousHealthyDataset = activeImportedDataset
        activeImportedDataset = null
    }

    private fun activateValidatedDataset(
        parsed: OsmPbfGraphImporter.ImportedCorridorDataset,
        datasetId: String,
        coverageRegion: String,
        sourceUrlOrPath: String,
        sha256: String,
        originType: DatasetOriginType
    ): Result<OsmDatasetMetadata> {
        require(parsed.nodes.size >= 2 && parsed.edges.isNotEmpty()) {
            "Imported OSM dataset failed topological validation: requires >= 2 nodes and >= 1 routable edge"
        }

        val spatialIndex = SpatialNodeIndex(
            roadNodes = parsed.roadGraph.nodes.values,
            corridorNodes = parsed.nodes,
            corridorEdges = parsed.edges
        )
        val bounds = spatialIndex.coverageBounds
            ?: throw IllegalStateException("Imported dataset has empty geographic bounds")

        val metadata = OsmDatasetMetadata(
            datasetId = datasetId,
            extractionDateIso = Instant.now().toString(),
            osmVersion = "0.6",
            coverageRegion = coverageRegion,
            sourceUrlOrPath = sourceUrlOrPath,
            nodeCount = parsed.nodes.size,
            wayCount = parsed.edges.mapNotNull { it.osmWayId }.distinct().size.coerceAtLeast(parsed.edges.size),
            edgeCount = parsed.edges.size,
            relationCount = parsed.turnRestrictions.size,
            schemaVersion = 2,
            sha256Checksum = sha256,
            originType = originType,
            minLatitude = bounds.minLatitude,
            maxLatitude = bounds.maxLatitude,
            minLongitude = bounds.minLongitude,
            maxLongitude = bounds.maxLongitude
        )

        val newActive = ActiveRoutingDataset(
            metadata = metadata,
            nodes = parsed.nodes,
            edges = parsed.edges,
            turnRestrictions = parsed.turnRestrictions,
            roadGraph = parsed.roadGraph,
            spatialIndex = spatialIndex
        )

        previousHealthyDataset = activeImportedDataset ?: fallbackSkeletonDataset
        activeImportedDataset = newActive
        return Result.success(metadata)
    }

    private fun buildFallbackSkeletonDataset(): ActiveRoutingDataset {
        val graph = IranOfflineRoadCorridors.toRoadGraph()
        val nodes = IranOfflineRoadCorridors.nodes
        val edges = IranOfflineRoadCorridors.edges
        val index = SpatialNodeIndex(
            roadNodes = graph.nodes.values,
            corridorNodes = nodes,
            corridorEdges = edges
        )
        val bounds = index.coverageBounds ?: SpatialNodeIndex.CoverageBoundingBox(25.0, 40.0, 44.0, 63.5)

        val meta = OsmDatasetMetadata(
            datasetId = "iran_national_corridor_skeleton_v9",
            extractionDateIso = "2026-01-01T00:00:00Z",
            osmVersion = "0.6-corridor-fallback",
            coverageRegion = "Iran National Intercity Highway Skeleton (Fallback Only)",
            sourceUrlOrPath = "embedded://IranOfflineRoadCorridors",
            nodeCount = nodes.size,
            wayCount = edges.size,
            edgeCount = edges.size,
            relationCount = graph.turnRestrictions.size,
            schemaVersion = 2,
            sha256Checksum = "fallback_corridor_skeleton",
            originType = DatasetOriginType.FALLBACK_CORRIDOR_SKELETON,
            minLatitude = bounds.minLatitude,
            maxLatitude = bounds.maxLatitude,
            minLongitude = bounds.minLongitude,
            maxLongitude = bounds.maxLongitude
        )

        return ActiveRoutingDataset(
            metadata = meta,
            nodes = nodes,
            edges = edges,
            turnRestrictions = graph.turnRestrictions,
            roadGraph = graph,
            spatialIndex = index
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
