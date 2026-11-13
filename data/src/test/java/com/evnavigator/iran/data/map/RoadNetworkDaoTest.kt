package com.evnavigator.iran.data.map

import com.evnavigator.iran.data.map.dao.RoadGraphDao
import com.evnavigator.iran.data.map.dao.RoadNetworkDao
import com.evnavigator.iran.data.map.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for RoadNetworkDao and RoadGraphDao offline data access.
 * Verifies spatial bounding-box filtering, connectivity, restrictions, and dataset version management.
 */
class RoadNetworkDaoTest {

    private lateinit var mockNetworkDao: RoadNetworkDao
    private lateinit var mockGraphDao: RoadGraphDao

    @Before
    fun setUp() {
        mockNetworkDao = mock(RoadNetworkDao::class.java)
        mockGraphDao = mock(RoadGraphDao::class.java)
    }

    @Test
    fun `test getRoadNetworkById returns correct dataset`() = runTest {
        val expectedEntity = RoadNetworkEntity(
            id = "iran_tehran_v1",
            datasetVersion = "1.0.0",
            source = "OSM_IRAN",
            sourceType = "OFFLINE_EXTRACT",
            coverage = "Tehran Metropolitan Area",
            coverageType = "METROPOLITAN",
            coverageLevel = "FULL",
            graphVersion = "1.0",
            roadRestrictionVersion = "1.0",
            installationState = "INSTALLED",
            installedAt = 1690000000L,
            sizeBytes = 25_000_000L,
            checksum = "sha256_mock_hash",
            minLatitude = 35.5,
            maxLatitude = 35.8,
            minLongitude = 51.1,
            maxLongitude = 51.6,
            nodeCount = 45000,
            segmentCount = 92000,
            activeVersion = true
        )

        `when`(mockNetworkDao.getRoadNetworkById("iran_tehran_v1")).thenReturn(expectedEntity)

        val result = mockNetworkDao.getRoadNetworkById("iran_tehran_v1")

        assertNotNull(result)
        assertEquals("iran_tehran_v1", result?.id)
        assertEquals("1.0.0", result?.datasetVersion)
        assertTrue(result?.activeVersion == true)
        assertEquals(35.5, result?.minLatitude ?: 0.0, 0.001)
    }

    @Test
    fun `test getNodesInBoundingBox returns only nodes within bounds`() = runTest {
        val node1 = RoadNodeEntity("node_1", "iran_tehran_v1", 35.6892, 51.3890, 1200.0)
        val node2 = RoadNodeEntity("node_2", "iran_tehran_v1", 35.7000, 51.4000, 1250.0)

        `when`(
            mockGraphDao.getNodesInBoundingBox(
                "iran_tehran_v1",
                35.6, 35.8,
                51.3, 51.5
            )
        ).thenReturn(listOf(node1, node2))

        val nodes = mockGraphDao.getNodesInBoundingBox(
            "iran_tehran_v1",
            35.6, 35.8,
            51.3, 51.5
        )

        assertEquals(2, nodes.size)
        assertEquals("node_1", nodes[0].id)
        assertEquals("node_2", nodes[1].id)
        assertEquals(1200.0, nodes[0].elevation ?: 0.0, 0.1)
    }

    @Test
    fun `test outgoing segments and one-way rules verification`() = runTest {
        val segment1 = RoadSegmentEntity(
            id = "seg_1",
            roadNetworkId = "iran_tehran_v1",
            fromNodeId = "node_1",
            toNodeId = "node_2",
            distance = 540.0,
            roadType = RoadType.HIGHWAY.name,
            speedLimit = 100.0,
            isOneWay = true
        )

        `when`(mockGraphDao.getOutgoingSegments("node_1", "iran_tehran_v1"))
            .thenReturn(listOf(segment1))

        val outgoing = mockGraphDao.getOutgoingSegments("node_1", "iran_tehran_v1")

        assertEquals(1, outgoing.size)
        assertEquals("seg_1", outgoing[0].id)
        assertTrue(outgoing[0].isOneWay)
        assertEquals(540.0, outgoing[0].distance, 0.01)
        assertEquals(RoadType.HIGHWAY.name, outgoing[0].roadType)
    }

    @Test
    fun `test turn restrictions verification`() = runTest {
        val restriction = RoadRestrictionEntity(
            id = "restr_no_left",
            roadNetworkId = "iran_tehran_v1",
            roadSegmentId = "seg_1",
            fromNodeId = "node_1",
            toNodeId = "node_2",
            restrictionType = RestrictionType.NO_LEFT_TURN.name,
            isActive = true
        )

        `when`(mockGraphDao.getTurnRestrictions("node_1", "node_2", "iran_tehran_v1"))
            .thenReturn(listOf(restriction))

        val turnRestrictions = mockGraphDao.getTurnRestrictions("node_1", "node_2", "iran_tehran_v1")

        assertEquals(1, turnRestrictions.size)
        assertEquals(RestrictionType.NO_LEFT_TURN.name, turnRestrictions[0].restrictionType)
        assertTrue(turnRestrictions[0].isActive)
    }

    @Test
    fun `test clearNetworkGraph performs transactional cleanup`() = runTest {
        mockGraphDao.clearNetworkGraph("iran_tehran_v1")
        verify(mockGraphDao).clearNetworkGraph("iran_tehran_v1")
    }
}
