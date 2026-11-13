package com.evnavigator.iran.core.routing.model

/**
 * Enumeration of pathfinding algorithms for routing
 */
enum class PathfindingAlgorithm {
    A_STAR,
    DIJKSTRA,
    BIDIRECTIONAL_Dijkstra,
    YENS_K_SHORTEST_PATHS,
    YENS_K_SHORTEST_PATHS_WITH_ELEVATION,
    FLOYD_WARSHALL,
    BELLMAN_FORD
}