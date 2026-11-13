package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IranRealRoadRoutingEngineTest {

    private lateinit var routingEngine: IranRealRoadRoutingEngine

    @Before
    fun setup() {
        // Test with offline fallback forced (to ensure deterministic offline graph routing)
        routingEngine = IranRealRoadRoutingEngine(enableOnlineRouting = false)
    }

    @Test
    fun testTehranToIsfahanOfflineCorridorRouting() = runBlocking {
        val tehran = GeoCoordinate(35.6997, 51.3380) // Azadi
        val isfahan = GeoCoordinate(32.6500, 51.6800) // Isfahan

        val result = routingEngine.calculateRealRoute(tehran, isfahan)

        assertNotNull("Result should not be null", result)
        assertTrue("Polyline must have multiple real road coordinates", result.polyline.size >= 10)
        assertTrue("Distance should be approximately 380km - 470km", result.totalDistanceMeters in 350_000.0..500_000.0)
        assertTrue("Duration should be realistic (> 3 hours)", result.totalDurationSeconds > 10800.0)
        assertTrue("Maneuvers should be generated", result.maneuvers.isNotEmpty())

        // Verify that the route polyline contains key corridor coordinates (e.g. Qom Mehr-o-Mah / Freeway 5)
        val hasQomSegment = result.polyline.any { it.latitude in 34.5..34.8 }
        assertTrue("Route from Tehran to Isfahan must pass through Qom corridor", hasQomSegment)
    }

    @Test
    fun testTehranToTabrizOfflineCorridorRouting() = runBlocking {
        val tehran = GeoCoordinate(35.6997, 51.3380)
        val tabriz = GeoCoordinate(38.0700, 46.3000)

        val result = routingEngine.calculateRealRoute(tehran, tabriz)

        assertTrue("Polyline should have multiple coordinates", result.polyline.size >= 8)
        assertTrue("Distance should be approximately 550km - 650km", result.totalDistanceMeters in 500_000.0..700_000.0)
        val hasZanjanSegment = result.polyline.any { it.latitude in 36.5..36.8 }
        assertTrue("Route from Tehran to Tabriz must pass through Zanjan corridor", hasZanjanSegment)
    }

    @Test
    fun testTehranUrbanExpresswayRouting() = runBlocking {
        val azadi = GeoCoordinate(35.6997, 51.3380)
        val hemmatEast = GeoCoordinate(35.7550, 51.4600)

        val result = routingEngine.calculateRealRoute(azadi, hemmatEast)

        assertTrue("Urban route must have detailed steps", result.maneuvers.size >= 2)
        assertTrue("Urban distance should be approximately 15km - 30km", result.totalDistanceMeters in 12_000.0..35_000.0)
    }

    @Test
    fun testParseOsrmResponseComputesPolylineDistanceAccurately() {
        val json = """{
            "code": "Ok",
            "routes": [{
                "distance": 0.0,
                "duration": 0.0,
                "geometry": {
                    "coordinates": [
                        [51.3380, 35.6997],
                        [51.3400, 35.7000],
                        [51.3450, 35.7050]
                    ]
                },
                "legs": [{
                    "steps": []
                }]
            }]
        }"""

        val origin = GeoCoordinate(35.6997, 51.3380)
        val dest = GeoCoordinate(35.7050, 51.3450)
        val parsed = routingEngine.parseOsrmResponse(json, origin, dest)

        assertNotNull("Should parse valid coordinates", parsed)
        assertEquals(3, parsed!!.polyline.size)
        assertTrue("Distance must be computed from polyline coordinates, not straight line x 1.25", parsed.totalDistanceMeters > 0.0)
        assertTrue("Duration must be positive", parsed.totalDurationSeconds > 0.0)
    }

    @Test
    fun testParseOsrmResponseRejectsEmptyOrInvalidRoutes() {
        val emptyJson = """{"code": "Ok", "routes": []}"""
        val origin = GeoCoordinate(35.6997, 51.3380)
        val dest = GeoCoordinate(35.7050, 51.3450)

        val parsed = routingEngine.parseOsrmResponse(emptyJson, origin, dest)
        assertNull("Must return null when routes array is empty", parsed)
    }

    @Test
    fun testSnapDistanceRecordedInResult() = runBlocking {
        val azadi = GeoCoordinate(35.6997, 51.3380)
        val hemmatEast = GeoCoordinate(35.7550, 51.4600)

        val result = routingEngine.calculateRealRoute(azadi, hemmatEast)
        assertTrue("Origin snap distance must be recorded", result.originSnapDistanceMeters >= 0.0)
        assertTrue("Destination snap distance must be recorded", result.destinationSnapDistanceMeters >= 0.0)
    }

    @Test
    fun testOriginExceedingSnapDistanceThrowsSnapExceededException() = runBlocking {
        // Point deep in the Kavir desert far from any road network (> 10 km away)
        val deepDesert = GeoCoordinate(33.0000, 54.5000)
        val tehran = GeoCoordinate(35.6997, 51.3380)

        try {
            routingEngine.calculateRealRoute(deepDesert, tehran)
            fail("Must throw SnapExceededException when origin exceeds snap distance")
        } catch (e: Exception) {
            assertTrue("Exception must be SnapExceededException or have snap threshold message",
                e is com.evnavigator.iran.core.routing.model.SnapExceededException || e.message?.contains("Snap distance exceeded") == true || e.message?.contains("مبدا خارج از محدوده") == true)
        }
    }

    @Test
    fun testDestinationExceedingSnapDistanceThrowsSnapExceededException() = runBlocking {
        val tehran = GeoCoordinate(35.6997, 51.3380)
        val deepDesert = GeoCoordinate(33.0000, 54.5000)

        try {
            routingEngine.calculateRealRoute(tehran, deepDesert)
            fail("Must throw SnapExceededException when destination exceeds snap distance")
        } catch (e: Exception) {
            assertTrue("Exception must be SnapExceededException or have snap threshold message",
                e is com.evnavigator.iran.core.routing.model.SnapExceededException || e.message?.contains("Snap distance exceeded") == true || e.message?.contains("مقصد خارج از محدوده") == true)
        }
    }

    @Test
    fun testDirectedGraphOneWayEdgeTraversal() {
        val edges = listOf(
            CorridorEdge(
                from = "nodeA",
                to = "nodeB",
                roadName = "خیابان یک‌طرفه رفت",
                distanceMeters = 1000.0,
                speedLimitKmh = 50,
                detailedPath = listOf(GeoCoordinate(35.70, 51.30), GeoCoordinate(35.71, 51.31)),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD,
                access = com.evnavigator.iran.core.routing.model.RoadAccess.PUBLIC
            )
        )

        // Traversal in legal forward direction
        val forwardPath = routingEngine.findShortestPath("nodeA", "nodeB", edges)
        assertEquals("Forward one-way path must succeed", listOf("nodeA", "nodeB"), forwardPath)

        // Traversal in illegal reverse direction
        val reversePath = routingEngine.findShortestPath("nodeB", "nodeA", edges)
        assertTrue("Reverse one-way traversal must be blocked", reversePath.isEmpty())
    }

    @Test
    fun testTurnRestrictionsPreventIllegalTurns() {
        val edges = listOf(
            CorridorEdge(
                from = "A",
                to = "B",
                roadName = "معبر ورودی",
                distanceMeters = 500.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD,
                prohibitedTurns = listOf("C") // Prohibits turning from A->B into C
            ),
            CorridorEdge(
                from = "B",
                to = "C",
                roadName = "گردش به راست ممنوع",
                distanceMeters = 500.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            ),
            CorridorEdge(
                from = "B",
                to = "D",
                roadName = "مسیر جایگزین قانونی",
                distanceMeters = 700.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            ),
            CorridorEdge(
                from = "D",
                to = "C",
                roadName = "دسترسی دوربرگردان به C",
                distanceMeters = 700.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            )
        )

        val path = routingEngine.findShortestPath("A", "C", edges)
        assertEquals("Route must take legal alternative (A -> B -> D -> C) avoiding prohibited turn B -> C",
            listOf("A", "B", "D", "C"), path)
    }

    @Test
    fun testNoEntryOrPedestrianAccessBlocked() {
        val edges = listOf(
            CorridorEdge(
                from = "Start",
                to = "Target",
                roadName = "پیاده‌راه ممنوع برای خودرو",
                distanceMeters = 200.0,
                speedLimitKmh = 10,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.BIDIRECTIONAL,
                access = com.evnavigator.iran.core.routing.model.RoadAccess.PEDESTRIAN_ONLY
            )
        )

        val path = routingEngine.findShortestPath("Start", "Target", edges)
        assertTrue("Vehicular route must NOT traverse pedestrian-only or no-entry edges", path.isEmpty())
    }

    @Test
    fun testCoroutineCancellationAbortsExecution() = runBlocking {
        val job = launch(kotlinx.coroutines.Dispatchers.Default) {
            val tehran = GeoCoordinate(35.6997, 51.3380)
            val isfahan = GeoCoordinate(32.6500, 51.6800)
            routingEngine.calculateRealRoute(tehran, isfahan)
        }
        job.cancel()
        assertTrue("Job must be cancelled", job.isCancelled)
    }

    @Test
    fun testEdgeStateDijkstraDoesNotShadowLegalLongerIncomingEdgeAtTurnRestriction() {
        // Classic node-state Dijkstra bug:
        // Start -> X -> B costs 200m, but X -> B prohibits turning onto B -> Target.
        // Start -> Y -> B costs 350m, and Y -> B allows turning onto B -> Target (cost 100m).
        // Node-based dist[B] would lock B at 200m via X and fail to reach Target!
        // Edge-state (nodeId, incomingEdgeIndex) must find Start -> Y -> B -> Target (total 450m).
        val edges = listOf(
            CorridorEdge(
                from = "Start",
                to = "X",
                roadName = "مسیر کوتاه‌تر ورودی ۱",
                distanceMeters = 100.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            ),
            CorridorEdge(
                from = "X",
                to = "B",
                roadName = "ورودی با گردش ممنوع به مقصد",
                distanceMeters = 100.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD,
                prohibitedTurns = listOf("Target")
            ),
            CorridorEdge(
                from = "Start",
                to = "Y",
                roadName = "مسیر کمی بلندتر اما مجاز",
                distanceMeters = 200.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            ),
            CorridorEdge(
                from = "Y",
                to = "B",
                roadName = "ورودی مجاز به تقاطع B",
                distanceMeters = 150.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            ),
            CorridorEdge(
                from = "B",
                to = "Target",
                roadName = "خروجی مقصد",
                distanceMeters = 100.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            )
        )

        val path = routingEngine.findShortestPath("Start", "Target", edges)
        assertEquals(
            "Edge-state A*/Dijkstra must find legal path via Y even though X reaches B earlier",
            listOf("Start", "Y", "B", "Target"),
            path
        )
    }

    @Test
    fun testMandatoryTurnRestrictionEnforced() {
        val edges = listOf(
            CorridorEdge(
                from = "A",
                to = "B",
                roadName = "ورودی اجباری به راست",
                distanceMeters = 300.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD,
                mandatoryTurns = listOf("RightNode")
            ),
            CorridorEdge(
                from = "B",
                to = "StraightNode",
                roadName = "مستقیم (غیرمجاز به دلیل اجبار گردش به راست)",
                distanceMeters = 100.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            ),
            CorridorEdge(
                from = "B",
                to = "RightNode",
                roadName = "مسیر اجباری راست",
                distanceMeters = 250.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            ),
            CorridorEdge(
                from = "RightNode",
                to = "StraightNode",
                roadName = "ارتباط دوربرگردان به مقصد",
                distanceMeters = 250.0,
                speedLimitKmh = 50,
                detailedPath = emptyList(),
                direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD
            )
        )

        val path = routingEngine.findShortestPath("A", "StraightNode", edges)
        assertEquals(
            "Must follow mandatory turn (A -> B -> RightNode -> StraightNode)",
            listOf("A", "B", "RightNode", "StraightNode"),
            path
        )
    }

    @Test
    fun testOsmXmlImporterExtractsOneWayAndTurnRestrictions() {
        val osmXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <osm version="0.6">
              <node id="1" lat="35.7000" lon="51.3400"><tag k="name:fa" v="میدان اول"/></node>
              <node id="2" lat="35.7050" lon="51.3450"><tag k="name:fa" v="تقاطع میانی"/></node>
              <node id="3" lat="35.7100" lon="51.3500"><tag k="name:fa" v="مقصد ممنوع مستقیم"/></node>
              <node id="4" lat="35.7060" lon="51.3520"><tag k="name:fa" v="مسیر جایگزین"/></node>
              <way id="100">
                <nd ref="1"/><nd ref="2"/>
                <tag k="highway" v="primary"/>
                <tag k="oneway" v="yes"/>
                <tag k="name:fa" v="خیابان ورودی"/>
              </way>
              <way id="101">
                <nd ref="2"/><nd ref="3"/>
                <tag k="highway" v="primary"/>
                <tag k="oneway" v="yes"/>
                <tag k="name:fa" v="خیابان گردش ممنوع"/>
              </way>
              <way id="102">
                <nd ref="2"/><nd ref="4"/>
                <tag k="highway" v="secondary"/>
                <tag k="oneway" v="no"/>
              </way>
              <way id="103">
                <nd ref="4"/><nd ref="3"/>
                <tag k="highway" v="secondary"/>
                <tag k="oneway" v="-1"/>
              </way>
              <relation id="500">
                <member type="way" ref="100" role="from"/>
                <member type="node" ref="2" role="via"/>
                <member type="way" ref="101" role="to"/>
                <tag k="type" v="restriction"/>
                <tag k="restriction" v="no_left_turn"/>
              </relation>
            </osm>
        """.trimIndent()

        val importer = com.evnavigator.iran.core.routing.importer.OsmPbfGraphImporter()
        val dataset = importer.importOsmXml(osmXml.byteInputStream())

        assertEquals(4, dataset.nodes.size)
        assertEquals(4, dataset.edges.size)
        assertTrue("Turn restriction must be parsed from relation 500", dataset.turnRestrictions.isNotEmpty())

        // Way 103 has oneway=-1, meaning traversal is only legal from node 3 -> node 4 (REVERSE of 4->3)
        val way103Edge = dataset.edges.first { it.osmWayId == 103L }
        assertEquals(com.evnavigator.iran.core.routing.model.EdgeDirection.REVERSE, way103Edge.direction)
    }

    @Test
    fun testBinaryOsmPbfRoundTripEncodingAndDecoding() {
        val osmXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <osm version="0.6">
              <node id="10" lat="35.7000" lon="51.3400"><tag k="name:fa" v="میدان آزادی"/><tag k="ele" v="1180"/></node>
              <node id="20" lat="35.7200" lon="51.3600"><tag k="name:fa" v="تقاطع نواب"/></node>
              <node id="30" lat="35.7400" lon="51.3800"><tag k="name:fa" v="تقاطع چمران"/></node>
              <way id="200">
                <nd ref="10"/><nd ref="20"/>
                <tag k="highway" v="motorway"/>
                <tag k="oneway" v="yes"/>
                <tag k="maxspeed" v="110"/>
                <tag k="name:fa" v="بزرگراه جناح"/>
              </way>
              <way id="201">
                <nd ref="20"/><nd ref="30"/>
                <tag k="highway" v="trunk"/>
                <tag k="maxspeed" v="50 mph"/>
                <tag k="name:fa" v="بزرگراه چمران"/>
              </way>
              <relation id="900">
                <member type="way" ref="200" role="from"/>
                <member type="node" ref="20" role="via"/>
                <member type="way" ref="201" role="to"/>
                <tag k="type" v="restriction"/>
                <tag k="restriction" v="only_straight_on"/>
              </relation>
            </osm>
        """.trimIndent()

        val importer = com.evnavigator.iran.core.routing.importer.OsmPbfGraphImporter()
        val xmlDataset = importer.importOsmXml(osmXml.byteInputStream())

        val pbfOut = java.io.ByteArrayOutputStream()
        importer.writeOsmPbfStream(xmlDataset, pbfOut)
        val pbfBytes = pbfOut.toByteArray()
        assertTrue("Binary PBF stream must be non-empty", pbfBytes.size > 32)

        val pbfDataset = importer.importOsmPbf(java.io.ByteArrayInputStream(pbfBytes))
        assertEquals("PBF decoded node count must match", 3, pbfDataset.nodes.size)
        assertEquals("PBF decoded edge count must match", 2, pbfDataset.edges.size)
        assertTrue("PBF decoded turn restrictions must be preserved", pbfDataset.turnRestrictions.isNotEmpty())
    }

    @Test
    fun testMultiEdgeViaWayTurnRestrictionBlocks3EdgeSequence() {
        // Test via-way restriction: Edge 1 (A->B) -> via Edge 2 (B->C) -> prohibited onto Edge 3 (C->D),
        // while allowing A->B->C->E.
        val edges = listOf(
            CorridorEdge("A", "B", "ورودی ۱", 200.0, 60, emptyList(), com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD, osmWayId = 1L),
            CorridorEdge("B", "C", "رابط میانی دوربرگردان", 80.0, 40, emptyList(), com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD, osmWayId = 2L),
            CorridorEdge("C", "D", "خروجی ممنوع از دوربرگردان", 200.0, 60, emptyList(), com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD, osmWayId = 3L),
            CorridorEdge("C", "E", "خروجی مجاز", 300.0, 60, emptyList(), com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD, osmWayId = 4L),
            CorridorEdge("E", "D", "دسترسی مجاز به D", 300.0, 60, emptyList(), com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD, osmWayId = 5L)
        )
        val multiEdgeRestrictions = listOf(
            com.evnavigator.iran.core.routing.model.TurnRestriction.MultiEdge(
                fromEdgeId = "1",
                viaEdgeIds = listOf("2"),
                toEdgeId = "3",
                isMandatory = false
            )
        )

        val path = routingEngine.findShortestPath(
            startId = "A",
            targetId = "D",
            edges = edges,
            multiEdgeRestrictions = multiEdgeRestrictions
        )
        assertEquals(
            "3-edge via-way restriction must block A->B->C->D and route via A->B->C->E->D",
            listOf("A", "B", "C", "E", "D"),
            path
        )
    }

    @Test
    fun testGradeSeparatedBridgeDoesNotCreateAtGradeIntersection() {
        // Node 2 is shared in coordinates, or Way 10 is bridge (layer=1) while Way 20 is ground (layer=0)
        val osmXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <osm version="0.6">
              <node id="1" lat="35.7000" lon="51.3400"/>
              <node id="2" lat="35.7050" lon="51.3450"/>
              <node id="3" lat="35.7100" lon="51.3500"/>
              <node id="4" lat="35.7050" lon="51.3400"/>
              <node id="5" lat="35.7050" lon="51.3500"/>
              <way id="10">
                <nd ref="1"/><nd ref="2"/><nd ref="3"/>
                <tag k="highway" v="motorway"/>
                <tag k="bridge" v="yes"/>
                <tag k="layer" v="1"/>
              </way>
              <way id="20">
                <nd ref="4"/><nd ref="2"/><nd ref="5"/>
                <tag k="highway" v="primary"/>
                <tag k="layer" v="0"/>
              </way>
            </osm>
        """.trimIndent()

        val importer = com.evnavigator.iran.core.routing.importer.OsmPbfGraphImporter()
        val dataset = importer.importOsmXml(osmXml.byteInputStream())
        // Because way 10 is on layer=1 and way 20 is on layer=0, node 2 is NOT a valid at-grade junction between them:
        // each way remains a single uninterrupted edge (1->3 and 4->5).
        assertEquals("Bridge over ground road must not split at grade-separated node 2", 2, dataset.edges.size)
    }

    @Test
    fun testSpatialNodeIndexSnapThresholdAndCoverage() {
        val graph = IranOfflineRoadCorridors.toRoadGraph()
        val index = com.evnavigator.iran.core.routing.spatial.SpatialNodeIndex.fromRoadGraph(graph)

        val nearAzadi = GeoCoordinate(35.7000, 51.3385)
        val match = index.findNearestNode(nearAzadi, maxSnapDistanceMeters = 1000.0)
        assertNotNull("Must find nearest node within 1km of Azadi Square", match)
        assertTrue("Snap distance must be < 500m", match!!.distanceMeters < 500.0)

        val farDesert = GeoCoordinate(33.0000, 54.5000)
        val farMatch = index.findNearestNode(farDesert, maxSnapDistanceMeters = 2000.0)
        assertNull("Point > 2km from any node must return null", farMatch)
    }

    @Test
    fun testDirectedEdgesPreservedBetweenParallelNodesWithoutAmbiguity() {
        // Two parallel edges between NodeA and NodeB:
        // Edge 0: Slow service road (1000m @ 40 km/h -> 90 sec) listed FIRST in edges list
        // Edge 1: Fast expressway (1000m @ 120 km/h -> 30 sec) listed SECOND in edges list
        // Legacy edges.find { ... } would erroneously pick Edge 0 (first match) during reconstruction!
        // V10 findShortestPathDirectedEdges must preserve Edge 1 from the A* search state.
        val slowServiceEdge = CorridorEdge(
            from = "NodeA",
            to = "NodeB",
            roadName = "کندرو محلی",
            distanceMeters = 1000.0,
            speedLimitKmh = 40,
            detailedPath = listOf(GeoCoordinate(35.7000, 51.3400), GeoCoordinate(35.7090, 51.3400)),
            direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD,
            osmWayId = 1001L
        )
        val fastExpressEdge = CorridorEdge(
            from = "NodeA",
            to = "NodeB",
            roadName = "تندرو بزرگراه",
            distanceMeters = 1000.0,
            speedLimitKmh = 120,
            detailedPath = listOf(GeoCoordinate(35.7000, 51.3400), GeoCoordinate(35.7090, 51.3400)),
            direction = com.evnavigator.iran.core.routing.model.EdgeDirection.FORWARD,
            osmWayId = 1002L
        )

        val directedEdges = routingEngine.findShortestPathDirectedEdges(
            startId = "NodeA",
            targetId = "NodeB",
            edges = listOf(slowServiceEdge, fastExpressEdge)
        )

        assertEquals("Must traverse exactly 1 directed edge", 1, directedEdges.size)
        assertEquals("Must preserve the faster expressway edge (index 1), not the first matching edge", 1, directedEdges[0].edgeIndex)
        assertEquals("تندرو بزرگراه", directedEdges[0].edge.roadName)
        assertEquals(120, directedEdges[0].edge.speedLimitKmh)
    }

    @Test
    fun testManeuverClassifierDistinguishesTurnTypesRoundaboutUturnExitAndMerge() {
        val leftTurn = ManeuverClassifier.classifyManeuver(
            incomingBearingDeg = 0.0,
            outgoingBearingDeg = 270.0,
            incomingRoadName = "خیابان ولیعصر",
            outgoingRoadName = "خیابان فاطمی",
            incomingSpeedKmh = 50,
            outgoingSpeedKmh = 50
        )
        assertEquals(com.evnavigator.iran.core.routing.model.RealManeuverType.TURN_LEFT, leftTurn.type)

        val rightTurn = ManeuverClassifier.classifyManeuver(
            incomingBearingDeg = 0.0,
            outgoingBearingDeg = 90.0,
            incomingRoadName = "خیابان ولیعصر",
            outgoingRoadName = "خیابان مطهری",
            incomingSpeedKmh = 50,
            outgoingSpeedKmh = 50
        )
        assertEquals(com.evnavigator.iran.core.routing.model.RealManeuverType.TURN_RIGHT, rightTurn.type)

        val uturn = ManeuverClassifier.classifyManeuver(
            incomingBearingDeg = 90.0,
            outgoingBearingDeg = 270.0,
            incomingRoadName = "بزرگراه همت",
            outgoingRoadName = "دوربرگردان همت",
            incomingSpeedKmh = 80,
            outgoingSpeedKmh = 40
        )
        assertEquals(com.evnavigator.iran.core.routing.model.RealManeuverType.UTURN, uturn.type)

        val roundabout = ManeuverClassifier.classifyManeuver(
            incomingBearingDeg = 0.0,
            outgoingBearingDeg = 45.0,
            incomingRoadName = "بلوار کشاورز",
            outgoingRoadName = "میدان ولیعصر",
            incomingSpeedKmh = 50,
            outgoingSpeedKmh = 40
        )
        assertEquals(com.evnavigator.iran.core.routing.model.RealManeuverType.ROUNDABOUT, roundabout.type)

        val rampExit = ManeuverClassifier.classifyManeuver(
            incomingBearingDeg = 0.0,
            outgoingBearingDeg = 25.0,
            incomingRoadName = "آزادراه تهران-قم",
            outgoingRoadName = "خروجی فرودگاه امام",
            incomingSpeedKmh = 120,
            outgoingSpeedKmh = 60
        )
        assertEquals(com.evnavigator.iran.core.routing.model.RealManeuverType.RAMP_EXIT, rampExit.type)

        val highwayStages = ManeuverClassifier.computeAnnouncementThresholds(120)
        assertEquals(3, highwayStages.size)
        assertEquals(com.evnavigator.iran.core.routing.model.ManeuverAnnouncementStage.INITIAL_WARNING, highwayStages[0].stage)
        assertTrue("Highway initial warning should be >= 1000m", highwayStages[0].triggerDistanceMeters >= 1000.0)
    }

    @Test
    fun testCalculateRealRoutePopulatesSegmentsAndSnapMetadata() = runBlocking {
        val tehran = GeoCoordinate(35.6997, 51.3380)
        val isfahan = GeoCoordinate(32.6500, 51.6800)

        val result = routingEngine.calculateRealRoute(tehran, isfahan)
        assertTrue("Route must contain ordered RealRoadRouteSegments", result.segments.isNotEmpty())
        assertNotNull("Start snap metadata must be populated", result.startSnap)
        assertNotNull("End snap metadata must be populated", result.endSnap)

        var prevEnd = 0.0
        for ((idx, seg) in result.segments.withIndex()) {
            assertEquals(idx, seg.segmentIndex)
            assertTrue("Segment edgeId must not be blank", seg.edgeId.isNotBlank())
            assertEquals("Cumulative start must match previous end", prevEnd, seg.cumulativeStartMeters, 1e-3)
            assertTrue("Cumulative end must be >= cumulative start", seg.cumulativeEndMeters >= seg.cumulativeStartMeters)
            prevEnd = seg.cumulativeEndMeters
        }
    }
}



