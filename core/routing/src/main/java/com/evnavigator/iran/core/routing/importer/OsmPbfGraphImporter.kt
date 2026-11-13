package com.evnavigator.iran.core.routing.importer

import com.evnavigator.iran.core.routing.CorridorEdge
import com.evnavigator.iran.core.routing.CorridorNode
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.InflaterInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Production OSM Road Network Graph Importer for EV Navigator Iran (V12.5 Decomposed).
 *
 * Capabilities:
 * 1. Authentic `.osm.pbf` binary reader (`BlobHeader` + `Blob` raw/zlib + `OSMHeader` + `PrimitiveBlock`
 *    `StringTable`, `DenseNodes`, `Node`, `Way`, and `Relation`) via [ProtoWireReader].
 * 2. Authentic `.osm.pbf` binary writer ([writeOsmPbfStream]) via [OsmPbfStreamEncoder].
 * 3. Streaming SAX `.osm` XML parser ([importOsmXml]) with XXE protection and bounded memory.
 * 4. Hardened `EVRN` binary road network parser ([importBinaryGraph]) with strict invariants.
 * 5. Grade-separated topological graph construction ([buildDatasetFromRecords]) via [OsmTopologyGraphBuilder].
 */
class OsmPbfGraphImporter(
    private val maxNodesLimit: Int = 2_000_000,
    private val maxWaysLimit: Int = 600_000,
    private val maxRelationsLimit: Int = 100_000,
    private val maxBlobBytes: Int = 64 * 1024 * 1024
) {

    /**
     * Generates a synthetic test fixture representing key Iranian national corridors.
     */
    fun generateIranNationalCorridorDataset(): Pair<List<CorridorNode>, List<CorridorEdge>> {
        val dataset = OsmNationalCorridorSeedGenerator.generateDataset(this)
        return Pair(dataset.nodes, dataset.edges)
    }

    /**
     * Parses a real OpenStreetMap `.osm.pbf` binary stream (`BlobHeader` + `Blob` + `PrimitiveBlock`).
     */
    fun importOsmPbf(inputStream: InputStream): ImportedCorridorDataset {
        val dis = DataInputStream(BufferedInputStream(inputStream))
        val nodes = LinkedHashMap<Long, OsmNodeRecord>()
        val ways = ArrayList<OsmWayRecord>()
        val relations = ArrayList<OsmRelationRecord>()
        var sawHeaderOrData = false

        while (true) {
            val headerLen = try {
                dis.readInt()
            } catch (_: EOFException) {
                break
            }
            require(headerLen in 1..65_536) {
                "Invalid OSM PBF BlobHeader length: $headerLen (expected 1..65536)"
            }
            val headerBytes = ByteArray(headerLen)
            dis.readFully(headerBytes)
            val blobHeader = parseBlobHeader(headerBytes)
            require(blobHeader.datasize in 1..maxBlobBytes) {
                "Invalid OSM PBF Blob datasize: ${blobHeader.datasize}"
            }

            val blobBytes = ByteArray(blobHeader.datasize)
            dis.readFully(blobBytes)
            val rawBlockBytes = decodeBlobPayload(blobBytes)

            when (blobHeader.type) {
                "OSMHeader" -> {
                    sawHeaderOrData = true
                    validateHeaderBlock(rawBlockBytes)
                }
                "OSMData" -> {
                    sawHeaderOrData = true
                    parsePrimitiveBlock(rawBlockBytes, nodes, ways, relations)
                }
                else -> throw IllegalArgumentException("Unsupported OSM PBF blob type: '${blobHeader.type}'")
            }
        }

        require(sawHeaderOrData && nodes.isNotEmpty()) {
            "OSM PBF stream contained no valid OSMData nodes"
        }
        return buildDatasetFromRecords(nodes, ways, relations)
    }

    private data class PbfBlobHeader(val type: String, val datasize: Int)

    private fun parseBlobHeader(bytes: ByteArray): PbfBlobHeader {
        val reader = ProtoWireReader(bytes)
        var type = ""
        var datasize = -1
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> type = reader.readString()
                3 -> datasize = reader.readVarint32()
                else -> reader.skipField(wireType)
            }
        }
        require(type.isNotBlank() && datasize > 0) { "Malformed PBF BlobHeader (type='$type', datasize=$datasize)" }
        return PbfBlobHeader(type, datasize)
    }

    private fun decodeBlobPayload(bytes: ByteArray): ByteArray {
        val reader = ProtoWireReader(bytes)
        var rawBytes: ByteArray? = null
        var rawSize = -1
        var zlibBytes: ByteArray? = null

        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> rawBytes = reader.readBytes()
                2 -> rawSize = reader.readVarint32()
                3 -> zlibBytes = reader.readBytes()
                else -> reader.skipField(wireType)
            }
        }

        if (rawBytes != null) {
            require(rawBytes.size <= maxBlobBytes) { "Raw PBF blob exceeds max size" }
            return rawBytes
        }
        val compressed = zlibBytes ?: throw IllegalArgumentException("OSM PBF Blob missing both raw and zlib_data payload")
        require(rawSize in 0..maxBlobBytes) { "Invalid OSM PBF uncompressed raw_size: $rawSize" }

        val out = ByteArrayOutputStream(if (rawSize > 0) rawSize else compressed.size * 2)
        InflaterInputStream(compressed.inputStream()).use { inflater ->
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = inflater.read(buf)
                if (n < 0) break
                total += n
                require(total <= maxBlobBytes) { "Decompressed PBF blob exceeds maximum allowed size ($maxBlobBytes)" }
                out.write(buf, 0, n)
            }
        }
        val result = out.toByteArray()
        if (rawSize > 0) {
            require(result.size == rawSize) {
                "OSM PBF zlib decompression size mismatch: expected $rawSize bytes, got ${result.size}"
            }
        }
        return result
    }

    private fun validateHeaderBlock(bytes: ByteArray) {
        val reader = ProtoWireReader(bytes)
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val wireType = tag and 0x07
            reader.skipField(wireType)
        }
    }

    private fun parsePrimitiveBlock(
        bytes: ByteArray,
        nodesOut: MutableMap<Long, OsmNodeRecord>,
        waysOut: MutableList<OsmWayRecord>,
        relationsOut: MutableList<OsmRelationRecord>
    ) {
        val reader = ProtoWireReader(bytes)
        var stringTable: List<String> = emptyList()
        val primitiveGroupPayloads = ArrayList<ByteArray>()
        var granularity = 100L
        var latOffset = 0L
        var lonOffset = 0L

        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> stringTable = parseStringTable(reader.readBytes())
                2 -> primitiveGroupPayloads.add(reader.readBytes())
                17 -> granularity = reader.readVarint64()
                19 -> latOffset = reader.readVarint64()
                20 -> lonOffset = reader.readVarint64()
                else -> reader.skipField(wireType)
            }
        }

        for (groupBytes in primitiveGroupPayloads) {
            parsePrimitiveGroup(
                bytes = groupBytes,
                stringTable = stringTable,
                granularity = granularity,
                latOffset = latOffset,
                lonOffset = lonOffset,
                nodesOut = nodesOut,
                waysOut = waysOut,
                relationsOut = relationsOut
            )
        }
    }

    private fun parseStringTable(bytes: ByteArray): List<String> {
        val reader = ProtoWireReader(bytes)
        val strings = ArrayList<String>()
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            if (fieldNumber == 1) {
                strings.add(reader.readString())
            } else {
                reader.skipField(wireType)
            }
        }
        return strings
    }

    private fun parsePrimitiveGroup(
        bytes: ByteArray,
        stringTable: List<String>,
        granularity: Long,
        latOffset: Long,
        lonOffset: Long,
        nodesOut: MutableMap<Long, OsmNodeRecord>,
        waysOut: MutableList<OsmWayRecord>,
        relationsOut: MutableList<OsmRelationRecord>
    ) {
        val reader = ProtoWireReader(bytes)
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> {
                    val node = parseSinglePbfNode(reader.readBytes(), stringTable, granularity, latOffset, lonOffset)
                    require(nodesOut.size < maxNodesLimit) { "Node count limit exceeded ($maxNodesLimit)" }
                    nodesOut[node.id] = node
                }
                2 -> parseDenseNodes(reader.readBytes(), stringTable, granularity, latOffset, lonOffset, nodesOut)
                3 -> {
                    val way = parsePbfWay(reader.readBytes(), stringTable)
                    if (way != null && way.nodeIds.size >= 2 && way.tags.containsKey("highway")) {
                        require(waysOut.size < maxWaysLimit) { "Way count limit exceeded ($maxWaysLimit)" }
                        waysOut.add(way)
                    }
                }
                4 -> {
                    val rel = parsePbfRelation(reader.readBytes(), stringTable)
                    if (rel != null && (rel.tags["type"]?.startsWith("restriction") == true || rel.tags.containsKey("restriction"))) {
                        require(relationsOut.size < maxRelationsLimit) { "Relation count limit exceeded ($maxRelationsLimit)" }
                        relationsOut.add(rel)
                    }
                }
                else -> reader.skipField(wireType)
            }
        }
    }

    private fun parseSinglePbfNode(
        bytes: ByteArray,
        stringTable: List<String>,
        granularity: Long,
        latOffset: Long,
        lonOffset: Long
    ): OsmNodeRecord {
        val reader = ProtoWireReader(bytes)
        var id = 0L
        var rawLat = 0L
        var rawLon = 0L
        val keys = ArrayList<Int>()
        val vals = ArrayList<Int>()

        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> id = reader.readSInt64()
                2 -> if (wireType == 2) keys.addAll(reader.readPackedUInt32()) else keys.add(reader.readVarint32())
                3 -> if (wireType == 2) vals.addAll(reader.readPackedUInt32()) else vals.add(reader.readVarint32())
                8 -> rawLat = reader.readSInt64()
                9 -> rawLon = reader.readSInt64()
                else -> reader.skipField(wireType)
            }
        }

        val lat = 1e-9 * (latOffset + granularity * rawLat)
        val lon = 1e-9 * (lonOffset + granularity * rawLon)
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Invalid PBF node coordinates ($lat, $lon) on node $id" }

        var ele: Double? = null
        var name: String? = null
        val count = minOf(keys.size, vals.size)
        for (i in 0 until count) {
            val k = stringTable.getOrNull(keys[i]) ?: continue
            val v = stringTable.getOrNull(vals[i]) ?: continue
            if (k == "ele") ele = v.toDoubleOrNull()
            if (k == "name:fa" || (k == "name" && name == null)) name = v
        }
        return OsmNodeRecord(id, lat, lon, ele, name)
    }

    private fun parseDenseNodes(
        bytes: ByteArray,
        stringTable: List<String>,
        granularity: Long,
        latOffset: Long,
        lonOffset: Long,
        nodesOut: MutableMap<Long, OsmNodeRecord>
    ) {
        val reader = ProtoWireReader(bytes)
        var deltaIds = LongArray(0)
        var deltaLats = LongArray(0)
        var deltaLons = LongArray(0)
        var keysVals = IntArray(0)

        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> deltaIds = reader.readPackedSInt64()
                8 -> deltaLats = reader.readPackedSInt64()
                9 -> deltaLons = reader.readPackedSInt64()
                10 -> keysVals = reader.readPackedInt32()
                else -> reader.skipField(wireType)
            }
        }

        require(deltaIds.size == deltaLats.size && deltaIds.size == deltaLons.size) {
            "DenseNodes array length mismatch: ids=${deltaIds.size}, lats=${deltaLats.size}, lons=${deltaLons.size}"
        }

        var runningId = 0L
        var runningLat = 0L
        var runningLon = 0L
        var kvIdx = 0

        for (i in deltaIds.indices) {
            runningId += deltaIds[i]
            runningLat += deltaLats[i]
            runningLon += deltaLons[i]

            var ele: Double? = null
            var name: String? = null
            while (kvIdx < keysVals.size) {
                val kId = keysVals[kvIdx++]
                if (kId == 0) break
                if (kvIdx >= keysVals.size) break
                val vId = keysVals[kvIdx++]
                val k = stringTable.getOrNull(kId) ?: continue
                val v = stringTable.getOrNull(vId) ?: continue
                if (k == "ele") ele = v.toDoubleOrNull()
                if (k == "name:fa" || (k == "name" && name == null)) name = v
            }

            val lat = 1e-9 * (latOffset + granularity * runningLat)
            val lon = 1e-9 * (lonOffset + granularity * runningLon)
            require(lat in -90.0..90.0 && lon in -180.0..180.0) {
                "Invalid PBF DenseNode coordinate ($lat, $lon) for node $runningId"
            }
            require(nodesOut.size < maxNodesLimit) { "Node count limit exceeded ($maxNodesLimit)" }
            nodesOut[runningId] = OsmNodeRecord(runningId, lat, lon, ele, name)
        }
    }

    private fun parsePbfWay(bytes: ByteArray, stringTable: List<String>): OsmWayRecord? {
        val reader = ProtoWireReader(bytes)
        var id = 0L
        val keys = ArrayList<Int>()
        val vals = ArrayList<Int>()
        var deltaRefs = LongArray(0)

        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> id = reader.readVarint64()
                2 -> if (wireType == 2) keys.addAll(reader.readPackedUInt32()) else keys.add(reader.readVarint32())
                3 -> if (wireType == 2) vals.addAll(reader.readPackedUInt32()) else vals.add(reader.readVarint32())
                8 -> deltaRefs = reader.readPackedSInt64()
                else -> reader.skipField(wireType)
            }
        }

        if (deltaRefs.isEmpty()) return null
        val nodeIds = ArrayList<Long>(deltaRefs.size)
        var runningRef = 0L
        for (d in deltaRefs) {
            runningRef += d
            nodeIds.add(runningRef)
        }

        val tags = HashMap<String, String>()
        val tagCount = minOf(keys.size, vals.size)
        for (i in 0 until tagCount) {
            val k = stringTable.getOrNull(keys[i]) ?: continue
            val v = stringTable.getOrNull(vals[i]) ?: continue
            tags[k] = v
        }
        return OsmWayRecord(id, nodeIds, tags)
    }

    private fun parsePbfRelation(bytes: ByteArray, stringTable: List<String>): OsmRelationRecord? {
        val reader = ProtoWireReader(bytes)
        var id = 0L
        val keys = ArrayList<Int>()
        val vals = ArrayList<Int>()
        val rolesSid = ArrayList<Int>()
        var deltaMemIds = LongArray(0)
        val types = ArrayList<Int>()

        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            when (fieldNumber) {
                1 -> id = reader.readVarint64()
                2 -> if (wireType == 2) keys.addAll(reader.readPackedUInt32()) else keys.add(reader.readVarint32())
                3 -> if (wireType == 2) vals.addAll(reader.readPackedUInt32()) else vals.add(reader.readVarint32())
                8 -> if (wireType == 2) rolesSid.addAll(reader.readPackedInt32().toList()) else rolesSid.add(reader.readVarint32())
                9 -> deltaMemIds = reader.readPackedSInt64()
                10 -> if (wireType == 2) types.addAll(reader.readPackedInt32().toList()) else types.add(reader.readVarint32())
                else -> reader.skipField(wireType)
            }
        }

        val memberCount = minOf(deltaMemIds.size, minOf(rolesSid.size, types.size))
        val members = ArrayList<OsmRelationMember>(memberCount)
        var runningMemId = 0L
        for (i in 0 until memberCount) {
            runningMemId += deltaMemIds[i]
            val role = stringTable.getOrNull(rolesSid[i]) ?: ""
            val typeStr = when (types[i]) {
                0 -> "node"
                1 -> "way"
                2 -> "relation"
                else -> "unknown"
            }
            members.add(OsmRelationMember(typeStr, runningMemId, role))
        }

        val tags = HashMap<String, String>()
        val tagCount = minOf(keys.size, vals.size)
        for (i in 0 until tagCount) {
            val k = stringTable.getOrNull(keys[i]) ?: continue
            val v = stringTable.getOrNull(vals[i]) ?: continue
            tags[k] = v
        }
        return OsmRelationRecord(id, members, tags)
    }

    fun writeOsmPbfStream(dataset: ImportedCorridorDataset, outputStream: OutputStream) =
        OsmPbfStreamEncoder.writeDatasetStream(dataset, outputStream)

    fun writeOsmPbfStream(
        nodes: List<OsmNodeRecord>,
        ways: List<OsmWayRecord>,
        relations: List<OsmRelationRecord>,
        outputStream: OutputStream
    ) = OsmPbfStreamEncoder.writeRecordsStream(nodes, ways, relations, outputStream)

    fun importOsmXml(inputStream: InputStream): ImportedCorridorDataset {
        val saxFactory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

        val nodes = LinkedHashMap<Long, OsmNodeRecord>()
        val ways = ArrayList<OsmWayRecord>()
        val relations = ArrayList<OsmRelationRecord>()

        val handler = object : DefaultHandler() {
            private var currentElementType: String? = null
            private var currentId: Long = 0L
            private var currentLat: Double = 0.0
            private var currentLon: Double = 0.0
            private var currentNodeEle: Double? = null
            private var currentNodeName: String? = null
            private val currentWayNds = ArrayList<Long>()
            private val currentTags = HashMap<String, String>()
            private val currentMembers = ArrayList<OsmRelationMember>()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val name = qName ?: localName ?: return
                val attrs = attributes ?: return
                when (name) {
                    "node" -> {
                        val id = attrs.getValue("id")?.toLongOrNull()
                            ?: throw IllegalArgumentException("Missing or invalid node id")
                        val lat = attrs.getValue("lat")?.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Missing or invalid node lat for $id")
                        val lon = attrs.getValue("lon")?.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Missing or invalid node lon for $id")
                        require(lat in -90.0..90.0 && lon in -180.0..180.0) {
                            "Out-of-bounds coordinate ($lat, $lon) on node $id"
                        }
                        currentElementType = "node"
                        currentId = id
                        currentLat = lat
                        currentLon = lon
                        currentNodeEle = null
                        currentNodeName = null
                    }
                    "way" -> {
                        val id = attrs.getValue("id")?.toLongOrNull()
                            ?: throw IllegalArgumentException("Missing or invalid way id")
                        currentElementType = "way"
                        currentId = id
                        currentWayNds.clear()
                        currentTags.clear()
                    }
                    "relation" -> {
                        val id = attrs.getValue("id")?.toLongOrNull()
                            ?: throw IllegalArgumentException("Missing or invalid relation id")
                        currentElementType = "relation"
                        currentId = id
                        currentMembers.clear()
                        currentTags.clear()
                    }
                    "nd" -> {
                        if (currentElementType == "way") {
                            val ref = attrs.getValue("ref")?.toLongOrNull()
                            if (ref != null) currentWayNds.add(ref)
                        }
                    }
                    "member" -> {
                        if (currentElementType == "relation") {
                            val type = attrs.getValue("type") ?: ""
                            val ref = attrs.getValue("ref")?.toLongOrNull() ?: return
                            val role = attrs.getValue("role") ?: ""
                            currentMembers.add(OsmRelationMember(type, ref, role))
                        }
                    }
                    "tag" -> {
                        val k = attrs.getValue("k") ?: return
                        val v = attrs.getValue("v") ?: return
                        when (currentElementType) {
                            "node" -> {
                                if (k == "ele") currentNodeEle = v.toDoubleOrNull()
                                if (k == "name:fa" || (k == "name" && currentNodeName == null)) currentNodeName = v
                            }
                            "way", "relation" -> {
                                currentTags[k] = v
                            }
                        }
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val name = qName ?: localName ?: return
                when (name) {
                    "node" -> {
                        if (currentElementType == "node") {
                            require(nodes.size < maxNodesLimit) { "Node limit exceeded ($maxNodesLimit)" }
                            nodes[currentId] = OsmNodeRecord(
                                id = currentId,
                                latitude = currentLat,
                                longitude = currentLon,
                                elevationMeters = currentNodeEle,
                                name = currentNodeName
                            )
                            currentElementType = null
                        }
                    }
                    "way" -> {
                        if (currentElementType == "way") {
                            if (currentWayNds.size >= 2 && currentTags.containsKey("highway")) {
                                require(ways.size < maxWaysLimit) { "Way limit exceeded ($maxWaysLimit)" }
                                ways.add(OsmWayRecord(currentId, ArrayList(currentWayNds), HashMap(currentTags)))
                            }
                            currentElementType = null
                        }
                    }
                    "relation" -> {
                        if (currentElementType == "relation") {
                            if (currentTags["type"]?.startsWith("restriction") == true || currentTags.containsKey("restriction")) {
                                require(relations.size < maxRelationsLimit) { "Relation limit exceeded ($maxRelationsLimit)" }
                                relations.add(OsmRelationRecord(currentId, ArrayList(currentMembers), HashMap(currentTags)))
                            }
                            currentElementType = null
                        }
                    }
                }
            }
        }

        saxFactory.newSAXParser().parse(inputStream, handler)
        return buildDatasetFromRecords(nodes, ways, relations)
    }

    fun importBinaryGraph(inputStream: InputStream): ImportedCorridorDataset {
        val dis = DataInputStream(BufferedInputStream(inputStream))
        val magic = ByteArray(4)
        dis.readFully(magic)
        val header = String(magic, Charsets.US_ASCII)
        require(header == "EVRN") { "Invalid binary road network header: $header (expected EVRN)" }

        val version = dis.readInt()
        require(version in 1..2) { "Unsupported EVRN binary graph version: $version" }

        val nodeCount = dis.readInt()
        require(nodeCount in 1..maxNodesLimit) { "Invalid EVRN nodeCount: $nodeCount (must be in 1..$maxNodesLimit)" }

        val nodes = LinkedHashMap<Long, OsmNodeRecord>(minOf(nodeCount, 65_536))
        for (i in 0 until nodeCount) {
            val id = dis.readLong()
            require(id > 0L && !nodes.containsKey(id)) { "Invalid or duplicate node ID in EVRN: $id" }
            val lat = dis.readDouble()
            val lon = dis.readDouble()
            require(!lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                "Out-of-bounds coordinate ($lat, $lon) for node $id in EVRN"
            }
            val ele = dis.readDouble().let { if (it.isNaN()) null else it }
            require(ele == null || ele in -500.0..9000.0) { "Invalid elevation $ele for node $id" }
            val name = dis.readUTF().ifBlank { null }
            nodes[id] = OsmNodeRecord(id, lat, lon, ele, name)
        }

        val wayCount = dis.readInt()
        require(wayCount in 0..maxWaysLimit) { "Invalid EVRN wayCount: $wayCount" }
        val seenWayIds = HashSet<Long>(minOf(wayCount, 65_536))
        val ways = ArrayList<OsmWayRecord>(minOf(wayCount, 65_536))

        for (i in 0 until wayCount) {
            val wayId = dis.readLong()
            require(wayId > 0L && seenWayIds.add(wayId)) { "Invalid or duplicate way ID in EVRN: $wayId" }
            val ndCount = dis.readInt()
            require(ndCount in 2..50_000) { "Invalid EVRN way node count $ndCount on way $wayId" }
            val nds = ArrayList<Long>(ndCount)
            for (n in 0 until ndCount) {
                val refId = dis.readLong()
                require(nodes.containsKey(refId)) { "Way $wayId references non-existent node $refId" }
                nds.add(refId)
            }
            val tagCount = dis.readInt()
            require(tagCount in 0..500) { "Invalid EVRN tag count $tagCount on way $wayId" }
            val tags = HashMap<String, String>(tagCount)
            for (t in 0 until tagCount) {
                tags[dis.readUTF()] = dis.readUTF()
            }
            ways.add(OsmWayRecord(wayId, nds, tags))
        }

        val relCount = dis.readInt()
        require(relCount in 0..maxRelationsLimit) { "Invalid EVRN relationCount: $relCount" }
        val relations = ArrayList<OsmRelationRecord>(minOf(relCount, 16_384))
        for (i in 0 until relCount) {
            val relId = dis.readLong()
            val memCount = dis.readInt()
            require(memCount in 0..5_000) { "Invalid EVRN member count $memCount on relation $relId" }
            val members = ArrayList<OsmRelationMember>(memCount)
            for (m in 0 until memCount) {
                members.add(OsmRelationMember(dis.readUTF(), dis.readLong(), dis.readUTF()))
            }
            val tagCount = dis.readInt()
            require(tagCount in 0..500) { "Invalid EVRN tag count $tagCount on relation $relId" }
            val tags = HashMap<String, String>(tagCount)
            for (t in 0 until tagCount) {
                tags[dis.readUTF()] = dis.readUTF()
            }
            relations.add(OsmRelationRecord(relId, members, tags))
        }

        return buildDatasetFromRecords(nodes, ways, relations)
    }

    fun buildDatasetFromRecords(
        nodes: Map<Long, OsmNodeRecord>,
        ways: List<OsmWayRecord>,
        relations: List<OsmRelationRecord>
    ): ImportedCorridorDataset = OsmTopologyGraphBuilder.buildDatasetFromRecords(nodes, ways, relations)

    fun parseRoadClass(highway: String?) = OsmRoadTagParser.parseRoadClass(highway)

    fun parseRoadAccess(tags: Map<String, String>, roadClass: com.evnavigator.iran.core.routing.model.RoadClass) =
        OsmRoadTagParser.parseRoadAccess(tags, roadClass)

    fun parseEdgeDirection(tags: Map<String, String>, roadClass: com.evnavigator.iran.core.routing.model.RoadClass) =
        OsmRoadTagParser.parseEdgeDirection(tags, roadClass)

    fun parseSpeedLimitKmh(tags: Map<String, String>, roadClass: com.evnavigator.iran.core.routing.model.RoadClass) =
        OsmRoadTagParser.parseSpeedLimitKmh(tags, roadClass)
}
