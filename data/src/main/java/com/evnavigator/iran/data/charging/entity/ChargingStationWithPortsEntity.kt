package com.evnavigator.iran.data.charging.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room database entity for charging stations with their associated ports.
 *
 * This entity combines station and port data for efficient querying of
 * stations along with their complete charging infrastructure information.
 */
data class ChargingStationWithPortsEntity(
    /**
     * The charging station record
     */
    @Embedded
    val station: ChargingStationEntity,

    /**
     * List of charging ports associated with this station
     */
    @Relation(
        parentColumn = "id",
        entityColumn = "stationId"
    )
    val ports: List<ChargingPortEntity>
)