package com.evnavigator.iran.core.routing.model

/**
 * Represents a turn restriction at an intersection
 */
sealed class TurnRestriction {
    /**
     * Prohibits a specific turn
     */
    data class Prohibited(
        val fromEdgeId: String,
        val toEdgeId: String
    ) : TurnRestriction()

    /**
     * Requires a specific turn (mandatory)
     */
    data class Mandatory(
        val fromEdgeId: String,
        val toEdgeId: String
    ) : TurnRestriction()

    /**
     * Via-node restriction - turn must go through a specific node
     */
    data class ViaNode(
        val fromEdgeId: String,
        val viaNodeId: String,
        val toEdgeId: String
    ) : TurnRestriction()

    /**
     * Multi-edge restriction - multiple edges cannot be used in sequence (or must be used if mandatory)
     */
    data class MultiEdge(
        val edgeIds: List<String>,
        val isMandatory: Boolean = false
    ) : TurnRestriction() {
        val edgeSequence: List<String> get() = edgeIds

        constructor(
            fromEdgeId: String,
            viaEdgeIds: List<String>,
            toEdgeId: String,
            isMandatory: Boolean = false
        ) : this(
            edgeIds = buildList {
                add(fromEdgeId)
                addAll(viaEdgeIds)
                add(toEdgeId)
            },
            isMandatory = isMandatory
        )
    }
}