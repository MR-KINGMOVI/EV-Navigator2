package com.evnavigator.iran.data.map.dao

import androidx.room.*
import com.evnavigator.iran.data.map.entity.RoadAttributeEntity
import com.evnavigator.iran.data.map.entity.RoadNodeEntity
import com.evnavigator.iran.data.map.entity.RoadRestrictionEntity
import com.evnavigator.iran.data.map.entity.RoadSegmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for offline road network routing graph.
 *
 * Provides high-performance querying for nodes, segments, restrictions,
 * and attributes used by the offline routing engine.
 */
@Dao
interface RoadGraphDao {

    // ========================================================================
    // Road Node Queries
    // ========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: RoadNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<RoadNodeEntity>)

    @Query("SELECT * FROM road_nodes WHERE id = :nodeId AND roadNetworkId = :roadNetworkId")
    suspend fun getNodeById(nodeId: String, roadNetworkId: String): RoadNodeEntity?

    @Query("SELECT * FROM road_nodes WHERE roadNetworkId = :roadNetworkId")
    suspend fun getNodesByNetwork(roadNetworkId: String): List<RoadNodeEntity>

    @Query("""
        SELECT * FROM road_nodes 
        WHERE roadNetworkId = :roadNetworkId 
          AND latitude BETWEEN :minLat AND :maxLat 
          AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getNodesInBoundingBox(
        roadNetworkId: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<RoadNodeEntity>

    @Query("SELECT COUNT(*) FROM road_nodes WHERE roadNetworkId = :roadNetworkId")
    suspend fun getNodeCount(roadNetworkId: String): Int

    @Query("DELETE FROM road_nodes WHERE roadNetworkId = :roadNetworkId")
    suspend fun deleteNodesByNetwork(roadNetworkId: String)

    // ========================================================================
    // Road Segment Queries
    // ========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: RoadSegmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<RoadSegmentEntity>)

    @Query("SELECT * FROM road_segments WHERE id = :segmentId AND roadNetworkId = :roadNetworkId")
    suspend fun getSegmentById(segmentId: String, roadNetworkId: String): RoadSegmentEntity?

    @Query("SELECT * FROM road_segments WHERE roadNetworkId = :roadNetworkId")
    suspend fun getSegmentsByNetwork(roadNetworkId: String): List<RoadSegmentEntity>

    @Query("SELECT * FROM road_segments WHERE fromNodeId = :fromNodeId AND roadNetworkId = :roadNetworkId")
    suspend fun getOutgoingSegments(fromNodeId: String, roadNetworkId: String): List<RoadSegmentEntity>

    @Query("SELECT * FROM road_segments WHERE toNodeId = :toNodeId AND roadNetworkId = :roadNetworkId")
    suspend fun getIncomingSegments(toNodeId: String, roadNetworkId: String): List<RoadSegmentEntity>

    @Query("""
        SELECT * FROM road_segments 
        WHERE fromNodeId = :fromNodeId 
          AND toNodeId = :toNodeId 
          AND roadNetworkId = :roadNetworkId
    """)
    suspend fun getSegmentsBetween(
        fromNodeId: String,
        toNodeId: String,
        roadNetworkId: String
    ): List<RoadSegmentEntity>

    @Query("SELECT * FROM road_segments WHERE roadNetworkId = :roadNetworkId AND roadType = :roadType")
    suspend fun getSegmentsByRoadType(roadNetworkId: String, roadType: String): List<RoadSegmentEntity>

    @Query("SELECT COUNT(*) FROM road_segments WHERE roadNetworkId = :roadNetworkId")
    suspend fun getSegmentCount(roadNetworkId: String): Int

    @Query("DELETE FROM road_segments WHERE roadNetworkId = :roadNetworkId")
    suspend fun deleteSegmentsByNetwork(roadNetworkId: String)

    // ========================================================================
    // Road Restriction Queries
    // ========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestriction(restriction: RoadRestrictionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestrictions(restrictions: List<RoadRestrictionEntity>)

    @Query("SELECT * FROM road_restrictions WHERE roadNetworkId = :roadNetworkId AND isActive = 1")
    suspend fun getActiveRestrictions(roadNetworkId: String): List<RoadRestrictionEntity>

    @Query("""
        SELECT * FROM road_restrictions 
        WHERE roadSegmentId = :segmentId 
          AND roadNetworkId = :roadNetworkId 
          AND isActive = 1
    """)
    suspend fun getRestrictionsForSegment(segmentId: String, roadNetworkId: String): List<RoadRestrictionEntity>

    @Query("""
        SELECT * FROM road_restrictions 
        WHERE fromNodeId = :fromNodeId 
          AND toNodeId = :toNodeId 
          AND roadNetworkId = :roadNetworkId 
          AND isActive = 1
    """)
    suspend fun getTurnRestrictions(
        fromNodeId: String,
        toNodeId: String,
        roadNetworkId: String
    ): List<RoadRestrictionEntity>

    @Query("DELETE FROM road_restrictions WHERE roadNetworkId = :roadNetworkId")
    suspend fun deleteRestrictionsByNetwork(roadNetworkId: String)

    // ========================================================================
    // Road Attribute Queries
    // ========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttribute(attribute: RoadAttributeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttributes(attributes: List<RoadAttributeEntity>)

    @Query("SELECT * FROM road_attributes WHERE roadSegmentId = :segmentId AND roadNetworkId = :roadNetworkId")
    suspend fun getAttributesForSegment(segmentId: String, roadNetworkId: String): List<RoadAttributeEntity>

    @Query("DELETE FROM road_attributes WHERE roadNetworkId = :roadNetworkId")
    suspend fun deleteAttributesByNetwork(roadNetworkId: String)

    // ========================================================================
    // Maintenance Queries
    // ========================================================================

    @Transaction
    suspend fun clearNetworkGraph(roadNetworkId: String) {
        deleteAttributesByNetwork(roadNetworkId)
        deleteRestrictionsByNetwork(roadNetworkId)
        deleteSegmentsByNetwork(roadNetworkId)
        deleteNodesByNetwork(roadNetworkId)
    }
}
