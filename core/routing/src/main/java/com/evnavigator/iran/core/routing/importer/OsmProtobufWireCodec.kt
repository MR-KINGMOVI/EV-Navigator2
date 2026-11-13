package com.evnavigator.iran.core.routing.importer

import com.evnavigator.iran.core.routing.model.EdgeDirection
import com.evnavigator.iran.core.routing.model.RoadClass
import com.evnavigator.iran.core.routing.model.TurnRestriction
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream
import kotlin.math.roundToInt

data class OsmNodeRecord(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val name: String? = null
)

data class OsmWayRecord(
    val id: Long,
    val nodeIds: List<Long>,
    val tags: Map<String, String>
)

data class OsmRelationMember(
    val type: String,
    val ref: Long,
    val role: String
)

data class OsmRelationRecord(
    val id: Long,
    val members: List<OsmRelationMember>,
    val tags: Map<String, String>
)

/**
 * V12.5 Extracted Collaborator: Lightweight Protobuf Wire Reader & Writer + PBF Stream Encoder
 * for `.osm.pbf` binary framing (`OSMHeader` + `OSMData` `PrimitiveBlock`).
 */
internal class ProtoWireReader(private val buf: ByteArray) {
    private var pos = 0

    fun isAtEnd(): Boolean = pos >= buf.size

    fun readTag(): Int = readVarint32()

    fun readVarint32(): Int {
        var result = 0
        var shift = 0
        while (shift < 32) {
            if (pos >= buf.size) throw EOFException("Unexpected EOF in PBF varint32")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Malformed PBF varint32")
    }

    fun readVarint64(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (pos >= buf.size) throw EOFException("Unexpected EOF in PBF varint64")
            val b = buf[pos++].toLong()
            result = result or ((b and 0x7FL) shl shift)
            if ((b and 0x80L) == 0L) return result
            shift += 7
        }
        throw IllegalArgumentException("Malformed PBF varint64")
    }

    fun readSInt64(): Long {
        val raw = readVarint64()
        return (raw ushr 1) xor -(raw and 1L)
    }

    fun readBytes(): ByteArray {
        val len = readVarint32()
        require(len >= 0 && pos + len <= buf.size) { "Invalid PBF length-delimited field size: $len" }
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun readPackedUInt32(): List<Int> {
        val len = readVarint32()
        require(len >= 0 && pos + len <= buf.size) { "Invalid packed uint32 length: $len" }
        val end = pos + len
        val list = ArrayList<Int>()
        while (pos < end) {
            list.add(readVarint32())
        }
        return list
    }

    fun readPackedInt32(): IntArray {
        val len = readVarint32()
        require(len >= 0 && pos + len <= buf.size) { "Invalid packed int32 length: $len" }
        val end = pos + len
        val list = ArrayList<Int>()
        while (pos < end) {
            list.add(readVarint32())
        }
        return list.toIntArray()
    }

    fun readPackedSInt64(): LongArray {
        val len = readVarint32()
        require(len >= 0 && pos + len <= buf.size) { "Invalid packed sint64 length: $len" }
        val end = pos + len
        val list = ArrayList<Long>()
        while (pos < end) {
            list.add(readSInt64())
        }
        return list.toLongArray()
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarint64()
            1 -> {
                require(pos + 8 <= buf.size) { "Unexpected EOF skipping 64-bit field" }
                pos += 8
            }
            2 -> {
                val len = readVarint32()
                require(len >= 0 && pos + len <= buf.size) { "Unexpected EOF skipping bytes field" }
                pos += len
            }
            5 -> {
                require(pos + 4 <= buf.size) { "Unexpected EOF skipping 32-bit field" }
                pos += 4
            }
            else -> throw IllegalArgumentException("Unsupported Protobuf wireType: $wireType")
        }
    }
}

internal class ProtoWireWriter {
    private val out = ByteArrayOutputStream()

    fun writeTag(fieldNumber: Int, wireType: Int) {
        writeRawVarint32((fieldNumber shl 3) or wireType)
    }

    fun writeVarint32(fieldNumber: Int, value: Int) {
        writeTag(fieldNumber, 0)
        writeRawVarint32(value)
    }

    fun writeVarint64(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, 0)
        writeRawVarint64(value)
    }

    fun writeBytes(fieldNumber: Int, bytes: ByteArray) {
        writeTag(fieldNumber, 2)
        writeRawVarint32(bytes.size)
        out.write(bytes)
    }

    fun writeString(fieldNumber: Int, str: String) {
        writeBytes(fieldNumber, str.toByteArray(Charsets.UTF_8))
    }

    fun writePackedInt32(fieldNumber: Int, values: IntArray) {
        val packed = ByteArrayOutputStream()
        for (v in values) {
            var u = v
            while ((u and 0x7F.inv()) != 0) {
                packed.write((u and 0x7F) or 0x80)
                u = u ushr 7
            }
            packed.write(u and 0x7F)
        }
        writeBytes(fieldNumber, packed.toByteArray())
    }

    fun writePackedSInt64(fieldNumber: Int, values: LongArray) {
        val packed = ByteArrayOutputStream()
        for (v in values) {
            var zz = (v shl 1) xor (v shr 63)
            while ((zz and 0x7FL.inv()) != 0L) {
                packed.write(((zz.toInt() and 0x7F) or 0x80))
                zz = zz ushr 7
            }
            packed.write(zz.toInt() and 0x7F)
        }
        writeBytes(fieldNumber, packed.toByteArray())
    }

    private fun writeRawVarint32(value: Int) {
        var u = value
        while ((u and 0x7F.inv()) != 0) {
            out.write((u and 0x7F) or 0x80)
            u = u ushr 7
        }
        out.write(u and 0x7F)
    }

    private fun writeRawVarint64(value: Long) {
        var u = value
        while ((u and 0x7FL.inv()) != 0L) {
            out.write(((u.toInt() and 0x7F) or 0x80))
            u = u ushr 7
        }
        out.write(u.toInt() and 0x7F)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

internal object OsmPbfStreamEncoder {

    fun writeDatasetStream(dataset: ImportedCorridorDataset, outputStream: OutputStream) {
        val nodeRecords = dataset.nodes.mapIndexed { idx, cn ->
            val numericId = cn.id.removePrefix("osm_node_").toLongOrNull() ?: (idx + 1).toLong()
            OsmNodeRecord(
                id = numericId,
                latitude = cn.coordinate.latitude,
                longitude = cn.coordinate.longitude,
                elevationMeters = cn.elevationMeters,
                name = cn.name
            )
        }
        val nodeIdLookup = dataset.nodes.mapIndexed { idx, cn ->
            cn.id to (cn.id.removePrefix("osm_node_").toLongOrNull() ?: (idx + 1).toLong())
        }.toMap()

        val wayRecords = dataset.edges.mapIndexed { idx, edge ->
            val wayId = edge.osmWayId ?: (idx + 100).toLong()
            val fromNid = nodeIdLookup[edge.from] ?: 1L
            val toNid = nodeIdLookup[edge.to] ?: 2L
            val highwayStr = when (edge.roadClass) {
                RoadClass.MOTORWAY -> "motorway"
                RoadClass.TRUNK_ROAD -> "trunk"
                RoadClass.PRIMARY_ROAD -> "primary"
                RoadClass.SECONDARY_ROAD -> "secondary"
                RoadClass.LOCAL_ROAD -> "residential"
                RoadClass.SERVICE_ROAD -> "service"
                RoadClass.PEDESTRIAN_PATH -> "pedestrian"
                RoadClass.CYCLE_PATH -> "cycleway"
            }
            val onewayStr = when (edge.direction) {
                EdgeDirection.FORWARD -> "yes"
                EdgeDirection.REVERSE -> "-1"
                EdgeDirection.BIDIRECTIONAL -> "no"
            }
            OsmWayRecord(
                id = wayId,
                nodeIds = listOf(fromNid, toNid),
                tags = mapOf(
                    "highway" to highwayStr,
                    "oneway" to onewayStr,
                    "maxspeed" to edge.speedLimitKmh.toString(),
                    "name:fa" to edge.roadName
                )
            )
        }

        val edgeIdToWayId = dataset.edges.mapIndexed { idx, edge ->
            "edge_${idx + 1}" to (edge.osmWayId ?: (idx + 100).toLong())
        }.toMap()
        val edgeIdToViaNodeId = dataset.edges.mapIndexed { idx, edge ->
            "edge_${idx + 1}" to (nodeIdLookup[edge.to] ?: 1L)
        }.toMap()

        val relationRecords = dataset.turnRestrictions.mapIndexedNotNull { idx, tr ->
            when (tr) {
                is TurnRestriction.Prohibited -> {
                    val fWay = edgeIdToWayId[tr.fromEdgeId] ?: return@mapIndexedNotNull null
                    val tWay = edgeIdToWayId[tr.toEdgeId] ?: return@mapIndexedNotNull null
                    val vNode = edgeIdToViaNodeId[tr.fromEdgeId] ?: return@mapIndexedNotNull null
                    OsmRelationRecord(
                        id = (idx + 1000).toLong(),
                        members = listOf(
                            OsmRelationMember("way", fWay, "from"),
                            OsmRelationMember("node", vNode, "via"),
                            OsmRelationMember("way", tWay, "to")
                        ),
                        tags = mapOf("type" to "restriction", "restriction" to "no_left_turn")
                    )
                }
                is TurnRestriction.Mandatory -> {
                    val fWay = edgeIdToWayId[tr.fromEdgeId] ?: return@mapIndexedNotNull null
                    val tWay = edgeIdToWayId[tr.toEdgeId] ?: return@mapIndexedNotNull null
                    val vNode = edgeIdToViaNodeId[tr.fromEdgeId] ?: return@mapIndexedNotNull null
                    OsmRelationRecord(
                        id = (idx + 1000).toLong(),
                        members = listOf(
                            OsmRelationMember("way", fWay, "from"),
                            OsmRelationMember("node", vNode, "via"),
                            OsmRelationMember("way", tWay, "to")
                        ),
                        tags = mapOf("type" to "restriction", "restriction" to "only_straight_on")
                    )
                }
                else -> null
            }
        }

        writeRecordsStream(nodeRecords, wayRecords, relationRecords, outputStream)
    }

    fun writeRecordsStream(
        nodes: List<OsmNodeRecord>,
        ways: List<OsmWayRecord>,
        relations: List<OsmRelationRecord>,
        outputStream: OutputStream
    ) {
        val dos = DataOutputStream(outputStream)

        val headerBlockBytes = ProtoWireWriter().apply {
            writeString(1, "OsmSchema-V0.6")
            writeString(2, "DenseNodes")
        }.toByteArray()
        writePbfFraming(dos, "OSMHeader", headerBlockBytes, compress = true)

        val stringToId = LinkedHashMap<String, Int>()
        stringToId[""] = 0
        fun intern(s: String): Int = stringToId.getOrPut(s) { stringToId.size }

        for (n in nodes) {
            if (n.elevationMeters != null) {
                intern("ele")
                intern(n.elevationMeters.toString())
            }
            if (!n.name.isNullOrBlank()) {
                intern("name:fa")
                intern(n.name)
            }
        }
        for (w in ways) {
            for ((k, v) in w.tags) {
                intern(k)
                intern(v)
            }
        }
        for (r in relations) {
            for (m in r.members) intern(m.role)
            for ((k, v) in r.tags) {
                intern(k)
                intern(v)
            }
        }

        val stringTableBytes = ProtoWireWriter().apply {
            for (s in stringToId.keys) {
                writeString(1, s)
            }
        }.toByteArray()

        val denseGroupWriter = ProtoWireWriter()
        if (nodes.isNotEmpty()) {
            val deltaIds = LongArray(nodes.size)
            val deltaLats = LongArray(nodes.size)
            val deltaLons = LongArray(nodes.size)
            val keysValsList = ArrayList<Int>()

            var prevId = 0L
            var prevLat = 0L
            var prevLon = 0L
            for (i in nodes.indices) {
                val n = nodes[i]
                val scaledLat = (n.latitude * 1e7).roundToInt().toLong()
                val scaledLon = (n.longitude * 1e7).roundToInt().toLong()
                deltaIds[i] = n.id - prevId
                deltaLats[i] = scaledLat - prevLat
                deltaLons[i] = scaledLon - prevLon
                prevId = n.id
                prevLat = scaledLat
                prevLon = scaledLon

                if (n.elevationMeters != null) {
                    keysValsList.add(intern("ele"))
                    keysValsList.add(intern(n.elevationMeters.toString()))
                }
                if (!n.name.isNullOrBlank()) {
                    keysValsList.add(intern("name:fa"))
                    keysValsList.add(intern(n.name))
                }
                keysValsList.add(0)
            }

            val denseBytes = ProtoWireWriter().apply {
                writePackedSInt64(1, deltaIds)
                writePackedSInt64(8, deltaLats)
                writePackedSInt64(9, deltaLons)
                writePackedInt32(10, keysValsList.toIntArray())
            }.toByteArray()
            denseGroupWriter.writeBytes(2, denseBytes)
        }

        for (w in ways) {
            val kList = IntArray(w.tags.size)
            val vList = IntArray(w.tags.size)
            var ti = 0
            for ((k, v) in w.tags) {
                kList[ti] = intern(k)
                vList[ti] = intern(v)
                ti++
            }
            val deltaRefs = LongArray(w.nodeIds.size)
            var prevRef = 0L
            for (i in w.nodeIds.indices) {
                deltaRefs[i] = w.nodeIds[i] - prevRef
                prevRef = w.nodeIds[i]
            }
            val wayBytes = ProtoWireWriter().apply {
                writeVarint64(1, w.id)
                writePackedInt32(2, kList)
                writePackedInt32(3, vList)
                writePackedSInt64(8, deltaRefs)
            }.toByteArray()
            denseGroupWriter.writeBytes(3, wayBytes)
        }

        for (r in relations) {
            val kList = IntArray(r.tags.size)
            val vList = IntArray(r.tags.size)
            var ti = 0
            for ((k, v) in r.tags) {
                kList[ti] = intern(k)
                vList[ti] = intern(v)
                ti++
            }
            val roles = IntArray(r.members.size)
            val deltaMem = LongArray(r.members.size)
            val types = IntArray(r.members.size)
            var prevMem = 0L
            for (i in r.members.indices) {
                val m = r.members[i]
                roles[i] = intern(m.role)
                deltaMem[i] = m.ref - prevMem
                prevMem = m.ref
                types[i] = when (m.type) {
                    "node" -> 0
                    "way" -> 1
                    else -> 2
                }
            }
            val relBytes = ProtoWireWriter().apply {
                writeVarint64(1, r.id)
                writePackedInt32(2, kList)
                writePackedInt32(3, vList)
                writePackedInt32(8, roles)
                writePackedSInt64(9, deltaMem)
                writePackedInt32(10, types)
            }.toByteArray()
            denseGroupWriter.writeBytes(4, relBytes)
        }

        val primitiveBlockBytes = ProtoWireWriter().apply {
            writeBytes(1, stringTableBytes)
            writeBytes(2, denseGroupWriter.toByteArray())
            writeVarint64(17, 100L)
        }.toByteArray()

        writePbfFraming(dos, "OSMData", primitiveBlockBytes, compress = true)
        dos.flush()
    }

    private fun writePbfFraming(dos: DataOutputStream, type: String, rawPayload: ByteArray, compress: Boolean) {
        val blobBytes = ProtoWireWriter().apply {
            if (compress) {
                val compressedOut = ByteArrayOutputStream()
                DeflaterOutputStream(compressedOut).use { it.write(rawPayload) }
                writeVarint32(2, rawPayload.size)
                writeBytes(3, compressedOut.toByteArray())
            } else {
                writeBytes(1, rawPayload)
                writeVarint32(2, rawPayload.size)
            }
        }.toByteArray()

        val headerBytes = ProtoWireWriter().apply {
            writeString(1, type)
            writeVarint32(3, blobBytes.size)
        }.toByteArray()

        dos.writeInt(headerBytes.size)
        dos.write(headerBytes)
        dos.write(blobBytes)
    }
}
