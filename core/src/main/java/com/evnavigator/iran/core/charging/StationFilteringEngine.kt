package com.evnavigator.iran.core.charging

/**
 * Represents various filter criteria for charging stations.
 */
data class StationFilterCriteria(
    val connectorTypes: Set<Any>? = null,
    val minPowerKw: Double? = null,
    val isAcOnly: Boolean? = null,
    val isDcOnly: Boolean? = null,
    val operator: String? = null,
    val network: String? = null,
    val accessRestriction: Any? = null,
    val province: String? = null,
    val city: String? = null,
    val verifiedOnly: Boolean? = null,
    val radiusKm: Double? = null
)

/**
 * Utility class for filtering charging stations based on various criteria.
 *
 * This engine provides deterministic filtering of charging stations to support
 * efficient querying and selection in the application.
 */
object StationFilteringEngine {
    
    /**
     * Filters a list of charging stations based on provided criteria.
     *
     * @param stations The list of stations to filter
     * @param criteria The filtering criteria to apply
     * @return A filtered list of stations that match all specified criteria
     */
    fun filterStations(
        stations: List<ChargingStation>,
        criteria: StationFilterCriteria
    ): List<ChargingStation> {
        return stations.filter { station ->
            // Apply connector type filtering
            if (criteria.connectorTypes != null && criteria.connectorTypes.isNotEmpty()) {
                val filterTypeNames = criteria.connectorTypes.map { it.toString() }.toSet()
                val stationTypeNames = station.ports.flatMap { it.connectorTypes.map { c -> c.name } }.toSet()
                if (stationTypeNames.intersect(filterTypeNames).isEmpty()) {
                    return@filter false
                }
            }
            
            // Apply minimum power filtering
            if (criteria.minPowerKw != null) {
                val maxPower = station.ports.maxOfOrNull { port ->
                    maxOf(port.acPowerKw ?: 0.0, port.dcPowerKw ?: 0.0, port.power ?: 0.0)
                } ?: 0.0
                
                if (maxPower < criteria.minPowerKw) {
                    return@filter false
                }
            }
            
            // Apply AC-only filtering
            if (criteria.isAcOnly == true) {
                if (!station.ports.any { it.isAc }) {
                    return@filter false
                }
                
                // Ensure no DC ports exist for AC-only filter
                if (station.ports.any { it.isDc }) {
                    return@filter false
                }
            }
            
            // Apply DC-only filtering
            if (criteria.isDcOnly == true) {
                if (!station.ports.any { it.isDc }) {
                    return@filter false
                }
                
                // Ensure no AC ports exist for DC-only filter
                if (station.ports.any { it.isAc }) {
                    return@filter false
                }
            }
            
            // Apply operator filtering
            if (criteria.operator != null && station.metadata.operator != criteria.operator && station.metadata.operatorName != criteria.operator) {
                return@filter false
            }
            
            // Apply network filtering
            if (criteria.network != null && station.metadata.network != criteria.network && station.metadata.networkName != criteria.network) {
                return@filter false
            }
            
            // Apply access restriction filtering
            if (criteria.accessRestriction != null) {
                val req = criteria.accessRestriction.toString()
                val currentAccess = station.metadata.accessType.name
                if (!currentAccess.equals(req, ignoreCase = true) && !station.metadata.accessType.toString().equals(req, ignoreCase = true)) {
                    return@filter false
                }
            }
            
            // Apply province filtering
            if (criteria.province != null && station.location.province != criteria.province) {
                return@filter false
            }
            
            // Apply city filtering
            if (criteria.city != null && station.location.city != criteria.city) {
                return@filter false
            }
            
            // Apply verified-only filtering
            if (criteria.verifiedOnly == true) {
                val isVerified = station.provenance.validationStatus == ValidationStatus.VERIFIED ||
                        station.validationStatus == ValidationStatus.VALIDATED ||
                        station.validationStatus == ValidationStatus.VERIFIED
                if (!isVerified) {
                    return@filter false
                }
            }
            
            // All filters passed
            true
        }
    }
    
    /**
     * Filters stations by geographic radius around a coordinate.
     *
     * @param stations The list of stations to filter
     * @param latitude The center latitude
     * @param longitude The center longitude
     * @param radiusKm The radius in kilometers
     * @return A filtered list of stations within the specified radius
     */
    fun filterStationsByRadius(
        stations: List<ChargingStation>,
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 10.0
    ): List<ChargingStation> {
        val latDelta = (radiusKm * 1.25) / 111.0  // Bounding box buffer for candidate search
        val lngDelta = (radiusKm * 1.25) / (111.0 * Math.cos(Math.toRadians(latitude)))
        
        val minLat = latitude - latDelta
        val maxLat = latitude + latDelta
        val minLng = longitude - lngDelta
        val maxLng = longitude + lngDelta
        
        return stations.filter { station ->
            val coord = station.location.coordinate
            coord.latitude in minLat..maxLat && coord.longitude in minLng..maxLng
        }
    }
    
    /**
     * Combines multiple filtering criteria into a single operation.
     *
     * @param stations The list of stations to filter
     * @param criteria The filtering criteria to apply
     * @param latitude Optional center latitude for geographic filtering
     * @param longitude Optional center longitude for geographic filtering
     * @return A filtered list of stations matching all criteria
     */
    fun filterStationsCombined(
        stations: List<ChargingStation>,
        criteria: StationFilterCriteria,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<ChargingStation> {
        var filteredStations = filterStations(stations, criteria)
        
        if (latitude != null && longitude != null && criteria.radiusKm != null) {
            filteredStations = filterStationsByRadius(
                filteredStations, 
                latitude, 
                longitude, 
                criteria.radiusKm
            )
        }
        
        return filteredStations
    }

    fun filterStationsWithGeographic(
        stations: List<ChargingStation>,
        criteria: StationFilterCriteria,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Double? = 10.0
    ): List<ChargingStation> {
        val combinedCriteria = if (criteria.radiusKm == null && radiusKm != null) criteria.copy(radiusKm = radiusKm) else criteria
        return filterStationsCombined(stations, combinedCriteria, latitude, longitude)
    }

}
