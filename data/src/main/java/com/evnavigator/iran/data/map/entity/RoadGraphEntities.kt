package com.evnavigator.iran.data.map.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Road type classification for offline routing graph.
 */
enum class RoadType {
    UNKNOWN,
    HIGHWAY,
    MAIN_ROAD,
    SECONDARY_ROAD,
    LOCAL_STREET,
    SERVICE_ROAD,
    PATH,
    FERRY,
    RAILWAY,
    AIRPORT
}

/**
 * Type of road restriction for offline routing graph.
 */
enum class RestrictionType {
    UNKNOWN,
    NO_ENTRY,
    NO_RIGHT_TURN,
    NO_LEFT_TURN,
    NO_U_TURN,
    NO_THROUGH_TRAFFIC,
    NO_OVERTAKING,
    NO_OVERTAKING_BY_TRUCK,
    NO_STOPPING,
    NO_PARKING,
    NO_PARKING_DURING_HOURS,
    MIN_SPEED,
    MAX_SPEED,
    WEIGHT_LIMIT,
    HEIGHT_LIMIT,
    LENGTH_LIMIT,
    WIDTH_LIMIT,
    TOLL,
    CONSTRUCTION,
    ROAD_CLOSED,
    ONE_WAY,
    TOWING_REQUIRED,
    TRUCK_ONLY,
    PEDESTRIAN_ONLY,
    BICYCLE_ONLY,
    EMERGENCY_VEHICLES_ONLY,
    ACCESS_CONTROLLED
}

/**
 * Road node entity for offline routing graph.
 *
 * Represents a node in the road network graph with geographic coordinates
 * and connectivity information.
 */
@Entity(
    tableName = "road_nodes",
    indices = [
        Index(value = ["roadNetworkId"]),
        Index(value = ["id"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["roadNetworkId", "id"]),
        Index(value = ["roadNetworkId", "latitude", "longitude"])
    ]
)
data class RoadNodeEntity(
    @PrimaryKey
    val id: String,

    /**
     * ID of the road network dataset this node belongs to
     */
    val roadNetworkId: String,

    /**
     * Latitude coordinate
     */
    val latitude: Double,

    /**
     * Longitude coordinate
     */
    val longitude: Double,

    /**
     * Elevation in meters (null if unknown)
     */
    val elevation: Double? = null,

    /**
     * Timestamp when this node was last accessed
     */
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Road segment entity for offline routing graph.
 *
 * Represents a directed edge in the road network graph with attributes
 * needed for routing calculations.
 */
@Entity(
    tableName = "road_segments",
    indices = [
        Index(value = ["roadNetworkId"]),
        Index(value = ["id"]),
        Index(value = ["fromNodeId"]),
        Index(value = ["toNodeId"]),
        Index(value = ["roadNetworkId", "id"]),
        Index(value = ["roadNetworkId", "fromNodeId"]),
        Index(value = ["roadNetworkId", "toNodeId"]),
        Index(value = ["roadNetworkId", "fromNodeId", "toNodeId"]),
        Index(value = ["roadNetworkId", "roadType"]),
        Index(value = ["roadNetworkId", "isOneWay"]),
        Index(value = ["roadNetworkId", "speedLimit"]),
        Index(value = ["roadNetworkId", "accessRestriction"]),
        Index(value = ["roadNetworkId", "turnRestriction"])
    ]
)
data class RoadSegmentEntity(
    @PrimaryKey
    val id: String,

    /**
     * ID of the road network dataset this segment belongs to
     */
    val roadNetworkId: String,

    /**
     * ID of the source node
     */
    val fromNodeId: String,

    /**
     * ID of the destination node
     */
    val toNodeId: String,

    /**
     * Distance in meters
     */
    val distance: Double,

    /**
     * Road type (HIGHWAY, MAIN_ROAD, SECONDARY_ROAD, LOCAL_STREET, etc.)
     */
    val roadType: String,

    /**
     * Speed limit in km/h (null if unknown)
     */
    val speedLimit: Double? = null,

    /**
     * Whether this is a one-way road
     */
    val isOneWay: Boolean = false,

    /**
     * Access restriction flags
     */
    val accessRestriction: Int = 0,

    /**
     * Turn restriction flags (bitmask)
     */
    val turnRestriction: Int = 0,

    /**
     * Timestamp when this segment was last accessed
     */
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Road restriction entity for offline routing graph.
 *
 * Represents specific restrictions on road usage such as turn prohibitions,
 * access limitations, or temporary closures.
 */
@Entity(
    tableName = "road_restrictions",
    indices = [
        Index(value = ["roadNetworkId"]),
        Index(value = ["id"]),
        Index(value = ["roadSegmentId"]),
        Index(value = ["fromNodeId"]),
        Index(value = ["toNodeId"]),
        Index(value = ["restrictionType"]),
        Index(value = ["roadNetworkId", "id"]),
        Index(value = ["roadNetworkId", "roadSegmentId"]),
        Index(value = ["roadNetworkId", "fromNodeId"]),
        Index(value = ["roadNetworkId", "toNodeId"]),
        Index(value = ["roadNetworkId", "restrictionType"])
    ]
)
data class RoadRestrictionEntity(
    @PrimaryKey
    val id: String,

    /**
     * ID of the road network dataset this restriction belongs to
     */
    val roadNetworkId: String,

    /**
     * ID of the road segment this restriction applies to (null if node-based)
     */
    val roadSegmentId: String? = null,

    /**
     * ID of the source node (for turn restrictions)
     */
    val fromNodeId: String? = null,

    /**
     * ID of the destination node (for turn restrictions)
     */
    val toNodeId: String? = null,

    /**
     * Type of restriction
     */
    val restrictionType: String,

    /**
     * Whether this restriction is currently active
     */
    val isActive: Boolean = true,

    /**
     * Timestamp when this restriction was added
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp when this restriction expires (null if permanent)
     */
    val expiresAt: Long? = null,

    /**
     * Reason for the restriction (null if not specified)
     */
    val reason: String? = null,

    /**
     * Timestamp when this restriction was last accessed
     */
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Road attribute entity for offline routing graph.
 *
 * Stores additional attributes for road segments that don't fit in the main segment entity.
 */
@Entity(
    tableName = "road_attributes",
    indices = [
        Index(value = ["roadNetworkId"]),
        Index(value = ["roadSegmentId"]),
        Index(value = ["roadNetworkId", "roadSegmentId"])
    ]
)
data class RoadAttributeEntity(
    @PrimaryKey
    val id: String,

    /**
     * ID of the road network dataset this attribute belongs to
     */
    val roadNetworkId: String,

    /**
     * ID of the road segment this attribute belongs to
     */
    val roadSegmentId: String,

    /**
     * Attribute key
     */
    val key: String,

    /**
     * Attribute value
     */
    val value: String,

    /**
     * Timestamp when this attribute was last accessed
     */
    val lastAccessedAt: Long = System.currentTimeMillis()
)