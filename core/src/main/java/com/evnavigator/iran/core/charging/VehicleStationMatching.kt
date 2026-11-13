package com.evnavigator.iran.core.charging

import com.evnavigator.iran.core.vehicle.ChargingSpecification

/**
 * Result of vehicle-station matching evaluation.
 *
 * This represents the complete compatibility assessment between a vehicle and charging station.
 */
data class VehicleStationMatchingResult(
    val isCompatible: Boolean,  // Overall compatibility status
    val connectorCompatibility: ConnectorCompatibility,
    val powerCompatibility: PowerCompatibility,
    val acDcCompatibility: ACDCCompatibility,
    
    // Detailed information about why the match was made or not
    val connectorCompatibilityDetails: String = "",
    val powerCompatibilityDetails: String = "",
    val acDcCompatibilityDetails: String = ""
)

/**
 * Service for matching vehicles with charging stations.
 *
 * This service evaluates whether a vehicle can use a charging station based on:
 * - Connector type compatibility
 * - Power capability compatibility  
 * - AC/DC mode compatibility
 */
object VehicleStationMatchingService {
    
    private fun mapVehicleConnectorType(vType: com.evnavigator.iran.core.vehicle.ConnectorType): ConnectorType {
        return when (vType) {
            com.evnavigator.iran.core.vehicle.ConnectorType.TYPE_2 -> ConnectorType.TYPE_2
            com.evnavigator.iran.core.vehicle.ConnectorType.CCS_COMBO_1 -> ConnectorType.CCS_COMBO_1
            com.evnavigator.iran.core.vehicle.ConnectorType.CCS_COMBO_2 -> ConnectorType.CCS_COMBO_2
            com.evnavigator.iran.core.vehicle.ConnectorType.CHAdeMO -> ConnectorType.CHAdeMO
            com.evnavigator.iran.core.vehicle.ConnectorType.GB_T -> ConnectorType.GB_T_DC
            com.evnavigator.iran.core.vehicle.ConnectorType.GB_T_DC -> ConnectorType.GB_T_DC
            com.evnavigator.iran.core.vehicle.ConnectorType.GB_T_AC -> ConnectorType.GB_T_AC
            com.evnavigator.iran.core.vehicle.ConnectorType.TESLA_ROADSTER -> ConnectorType.TESLA_NACS
            com.evnavigator.iran.core.vehicle.ConnectorType.TESLA_Supercharger -> ConnectorType.TESLA_SUPERCHARGER
            com.evnavigator.iran.core.vehicle.ConnectorType.OTHER -> ConnectorType.OTHER
        }
    }
    
    /**
     * Determines if a vehicle is compatible with a charging station.
     *
     * @param vehicleChargingSpec The charging specification of the vehicle
     * @param stationConnectorTypes The connector types available at the station
     * @param stationAcPowerKw The AC power capability of the station (nullable)
     * @param stationDcPowerKw The DC power capability of the station (nullable)
     * @param stationIsAc Whether the station provides AC charging (nullable)
     * @param stationIsDc Whether the station provides DC charging (nullable)
     * @return The matching result with detailed compatibility information
     */
    fun matchVehicleToStation(
        vehicleChargingSpec: ChargingSpecification,
        stationConnectorTypes: Set<ConnectorType>,
        stationAcPowerKw: Double?,
        stationDcPowerKw: Double?,
        stationIsAc: Boolean?,
        stationIsDc: Boolean?
    ): VehicleStationMatchingResult {
        
        if (!vehicleChargingSpec.hasAcCharging && !vehicleChargingSpec.hasDcCharging) {
            return VehicleStationMatchingResult(
                isCompatible = false,
                connectorCompatibility = if (vehicleChargingSpec.connectorTypes.any { it.name == "OTHER" || it.name == "UNKNOWN" }) ConnectorCompatibility.UNKNOWN else ConnectorCompatibility.INCOMPATIBLE,
                powerCompatibility = PowerCompatibility.INCOMPATIBLE,
                acDcCompatibility = ACDCCompatibility.COMPATIBLE,
                connectorCompatibilityDetails = "Vehicle has no charging capability",
                powerCompatibilityDetails = "Vehicle has no charging power capability",
                acDcCompatibilityDetails = "Vehicle has no charging modes"
            )
        }

        // Determine connector compatibility
        val vehicleConnectorTypes = vehicleChargingSpec.connectorTypes.map { mapVehicleConnectorType(it) }.toSet()
        val isConnectorCompatible = ConnectorCompatibilityEngine.isCompatible(
            vehicleConnectorTypes,
            stationConnectorTypes
        )
        val hasUnknownConnector = vehicleConnectorTypes.contains(ConnectorType.OTHER) ||
                                  vehicleConnectorTypes.contains(ConnectorType.UNKNOWN) ||
                                  stationConnectorTypes.contains(ConnectorType.OTHER) ||
                                  stationConnectorTypes.contains(ConnectorType.UNKNOWN)

        val connectorCompatibility = if (isConnectorCompatible) {
            ConnectorCompatibility.COMPATIBLE
        } else if (vehicleConnectorTypes.isEmpty() || stationConnectorTypes.isEmpty() || hasUnknownConnector) {
            ConnectorCompatibility.UNKNOWN
        } else {
            ConnectorCompatibility.INCOMPATIBLE
        }
        
        // Determine power compatibility (evaluate both AC and DC capabilities in kW)
        val vehicleAcPowerKw: Double? = vehicleChargingSpec.acChargingMaxPowerKw?.let { it.watts / 1000.0 }
        val vehicleDcPowerKw: Double? = vehicleChargingSpec.dcChargingMaxPowerKw?.let { it.watts / 1000.0 }
        
        val maxVehiclePowerKw: Double? = when {
            vehicleAcPowerKw != null && vehicleDcPowerKw != null -> maxOf(vehicleAcPowerKw, vehicleDcPowerKw)
            vehicleAcPowerKw != null -> vehicleAcPowerKw
            vehicleDcPowerKw != null -> vehicleDcPowerKw
            else -> null
        }
        
        // Evaluate power compatibility for both AC and DC capabilities
        val acPowerCompatibility = PowerCompatibilityEngine.determineEffectivePower(
            maxVehiclePowerKw,
            stationAcPowerKw
        )
        
        val dcPowerCompatibility = PowerCompatibilityEngine.determineEffectivePower(
            maxVehiclePowerKw,
            stationDcPowerKw
        )
        
        // Determine AC/DC compatibility
        val acDcCompatibility = ACDCCompatibilityEngine.determineCompatibility(
            vehicleChargingSpec.hasAcCharging,
            vehicleChargingSpec.hasDcCharging,
            stationIsAc,
            stationIsDc
        )
        
        val effectivePowerCompatibility = when {
            stationIsDc == true && stationIsAc == true -> {
                if (acPowerCompatibility.status == PowerCompatibility.COMPATIBLE || dcPowerCompatibility.status == PowerCompatibility.COMPATIBLE) {
                    PowerCompatibility.COMPATIBLE
                } else if (acPowerCompatibility.status == PowerCompatibility.UNKNOWN || dcPowerCompatibility.status == PowerCompatibility.UNKNOWN) {
                    PowerCompatibility.UNKNOWN
                } else {
                    PowerCompatibility.INCOMPATIBLE
                }
            }
            stationIsDc == true -> dcPowerCompatibility.status
            stationIsAc == true -> acPowerCompatibility.status
            else -> PowerCompatibility.UNKNOWN
        }

        // Overall compatibility
        val isCompatible = connectorCompatibility == ConnectorCompatibility.COMPATIBLE &&
                           effectivePowerCompatibility == PowerCompatibility.COMPATIBLE &&
                           acDcCompatibility.status == ACDCCompatibility.COMPATIBLE
        
        // Create detailed compatibility messages
        val connectorDetails = when (connectorCompatibility) {
            ConnectorCompatibility.COMPATIBLE -> "Compatible connectors found"
            ConnectorCompatibility.INCOMPATIBLE -> "No compatible connectors found"
            ConnectorCompatibility.UNKNOWN -> "Connector types unknown or insufficient information"
        }
        
        val powerDetails = when (effectivePowerCompatibility) {
            PowerCompatibility.COMPATIBLE -> "Compatible power capability found"
            PowerCompatibility.INCOMPATIBLE -> "Incompatible power capability"
            PowerCompatibility.UNKNOWN -> "Power capability unknown or insufficient information"
        }
        
        val acDcDetails = when (acDcCompatibility.status) {
            ACDCCompatibility.COMPATIBLE -> "Compatible AC/DC modes found"
            ACDCCompatibility.INCOMPATIBLE -> "Incompatible AC/DC modes"
            ACDCCompatibility.UNKNOWN -> "AC/DC mode information unknown or insufficient"
        }
        
        return VehicleStationMatchingResult(
            isCompatible = isCompatible,
            connectorCompatibility = connectorCompatibility,
            powerCompatibility = effectivePowerCompatibility,
            acDcCompatibility = acDcCompatibility.status,
            connectorCompatibilityDetails = connectorDetails,
            powerCompatibilityDetails = powerDetails,
            acDcCompatibilityDetails = acDcDetails
        )
    }
    
    /**
     * Determines if a vehicle is compatible with a charging station based on the station's 
     * complete information including ports and connectors.
     *
     * @param vehicleChargingSpec The charging specification of the vehicle
     * @param stationPorts The charging ports available at the station (with their capabilities)
     * @return The matching result with detailed compatibility information
     */
    fun matchVehicleToStationWithPorts(
        vehicleChargingSpec: ChargingSpecification,
        stationPorts: List<ChargingPort>
    ): VehicleStationMatchingResult {
        val stationConnectorTypes = stationPorts.map { it.connectorType }.toSet()
        
        var stationAcPowerKw: Double? = null
        var stationDcPowerKw: Double? = null
        
        for (port in stationPorts) {
            val p = port.power
            if (port.isAc && p != null) {
                if (stationAcPowerKw == null || p > stationAcPowerKw) {
                    stationAcPowerKw = p
                }
            }
            if (port.isDc && p != null) {
                if (stationDcPowerKw == null || p > stationDcPowerKw) {
                    stationDcPowerKw = p
                }
            }
        }
        
        val stationIsAc = stationPorts.any { it.isAc }
        val stationIsDc = stationPorts.any { it.isDc }
        
        return matchVehicleToStation(
            vehicleChargingSpec,
            stationConnectorTypes,
            stationAcPowerKw,
            stationDcPowerKw,
            stationIsAc,
            stationIsDc
        )
    }
}
