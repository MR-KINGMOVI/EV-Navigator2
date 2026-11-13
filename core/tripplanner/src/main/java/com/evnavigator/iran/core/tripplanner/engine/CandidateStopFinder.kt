package com.evnavigator.iran.core.tripplanner.engine

import com.evnavigator.iran.core.charging.AccessType
import com.evnavigator.iran.core.charging.ChargingPort
import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.charging.ChargingStationId
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.PortStatus
import com.evnavigator.iran.core.charging.PowerCapability
import com.evnavigator.iran.core.charging.Provenance
import com.evnavigator.iran.core.charging.StationFilterCriteria
import com.evnavigator.iran.core.charging.StationFilteringEngine
import com.evnavigator.iran.core.charging.StationLocation
import com.evnavigator.iran.core.charging.StationMetadata
import com.evnavigator.iran.core.charging.StationStatus
import com.evnavigator.iran.core.charging.ValidationStatus
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.tripplanner.model.TripConstraints
import com.evnavigator.iran.core.vehicle.ChargingSpecification
import com.evnavigator.iran.domain.model.catalog.DomainAccessType
import com.evnavigator.iran.domain.model.catalog.DomainChargingStation
import com.evnavigator.iran.domain.model.catalog.DomainConnectorType
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Finds usable charging stations near a point on the route using the Domain [ChargingStationCatalogRepository].
 *
 * Enforces Section 3C:
 * - Zero dependency on `:data` Room entities or DAOs.
 * - Never fabricates a synthetic 22 kW TYPE_2 port when a station has no valid ports (`ports.isEmpty()`).
 * - Preserves actual station validationStatus (`VERIFIED`, `VALIDATED`, `UNVERIFIED`) instead of hardcoding `VALIDATED`.
 */
class CandidateStopFinder(
    private val chargingStationRepository: ChargingStationCatalogRepository
) {
    suspend fun findCandidates(
        nearPoint: GeoCoordinate,
        vehicleChargingSpec: ChargingSpecification,
        constraints: TripConstraints
    ): List<ChargingStation> {
        val domainStations: List<DomainChargingStation> = chargingStationRepository.observeStationsNearby(
            nearPoint.latitude,
            nearPoint.longitude,
            constraints.maxDetourKm
        ).firstOrNull() ?: emptyList()

        val candidates: List<ChargingStation> = domainStations.mapNotNull { stationRecord ->
            val stationWithPorts = chargingStationRepository.getStationWithPorts(stationRecord.id)
            val domainPorts: List<ChargingPort> = stationWithPorts?.ports?.mapNotNull { portRecord ->
                val validPowerKw = portRecord.powerKw?.takeIf { it.isFinite() && it > 0.0 }
                    ?: return@mapNotNull null
                val mappedConnector = mapDomainConnector(portRecord.connectorType)
                if (mappedConnector == ConnectorType.UNKNOWN) return@mapNotNull null

                val portStatus = try {
                    PortStatus.valueOf(portRecord.status)
                } catch (_: Exception) {
                    PortStatus.UNKNOWN
                }

                ChargingPort(
                    id = portRecord.id,
                    connectorType = mappedConnector,
                    connectorCount = portRecord.connectorCount,
                    voltage = portRecord.voltage,
                    current = portRecord.current,
                    power = validPowerKw,
                    isAc = portRecord.isAc,
                    isDc = portRecord.isDc,
                    phaseCount = portRecord.phaseCount,
                    status = portStatus,
                    pricePerKwh = portRecord.pricePerKwh
                )
            }.orEmpty()

            // Section 3C: Do NOT fabricate a default port when station has no valid ports
            if (domainPorts.isEmpty()) return@mapNotNull null

            val maxPort = domainPorts.maxByOrNull { it.power ?: 0.0 } ?: return@mapNotNull null
            val maxPortPowerKw = maxPort.power?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null

            val anyAvailable = domainPorts.any { it.status == PortStatus.AVAILABLE }
            val mappedValidationStatus = when (stationRecord.validationStatus.uppercase()) {
                "VERIFIED" -> ValidationStatus.VERIFIED
                "VALID", "VALIDATED" -> ValidationStatus.VALIDATED
                else -> ValidationStatus.UNVERIFIED
            }

            ChargingStation(
                id = ChargingStationId.fromString(stationRecord.id),
                location = StationLocation(
                    coordinate = com.evnavigator.iran.core.charging.GeoCoordinate(
                        stationRecord.latitude,
                        stationRecord.longitude
                    ),
                    address = stationRecord.address,
                    province = stationRecord.province,
                    city = stationRecord.city
                ),
                metadata = StationMetadata(
                    canonicalName = stationRecord.canonicalName,
                    operatorId = stationRecord.operatorId,
                    operatorName = stationRecord.operatorName,
                    networkId = stationRecord.networkId,
                    networkName = stationRecord.networkName,
                    accessType = mapAccessType(stationRecord.accessType),
                    accessRestrictions = null,
                    openingHours = null,
                    is247 = stationRecord.is247,
                    paymentMethods = null,
                    amenities = null,
                    contactPhone = stationRecord.contactPhone,
                    contactEmail = stationRecord.contactEmail,
                    website = stationRecord.website,
                    notes = stationRecord.notes,
                    source = stationRecord.source,
                    sourceReference = stationRecord.sourceReference,
                    retrievedDate = stationRecord.retrievedDate
                ),
                ports = domainPorts,
                powerCapabilities = listOf(
                    PowerCapability(
                        powerKw = maxPortPowerKw,
                        voltage = maxPort.voltage,
                        current = maxPort.current,
                        isAc = domainPorts.any { it.isAc },
                        isDc = domainPorts.any { it.isDc }
                    )
                ),
                status = if (anyAvailable) StationStatus.OPERATIONAL else StationStatus.UNKNOWN,
                isOperational = anyAvailable,
                provenance = Provenance(
                    source = stationRecord.source,
                    retrievedDate = stationRecord.retrievedDate,
                    confidence = stationRecord.confidence
                ),
                validationStatus = mappedValidationStatus
            )
        }

        val filterCriteria = StationFilterCriteria(
            connectorTypes = constraints.preferredConnectorTypes,
            minPowerKw = constraints.minStationPowerKw,
            verifiedOnly = if (constraints.verifiedStationsOnly) true else null
        )

        val filteredStations = StationFilteringEngine.filterStations(candidates, filterCriteria)
        val compatibleCandidates = mutableListOf<ChargingStation>()

        for (station in filteredStations) {
            val matchingResult =
                com.evnavigator.iran.core.charging.VehicleStationMatchingService.matchVehicleToStationWithPorts(
                    vehicleChargingSpec,
                    station.ports
                )
            if (matchingResult.isCompatible) {
                compatibleCandidates.add(station)
            }
        }

        return compatibleCandidates.sortedBy { station ->
            val domainCoord = GeoCoordinate(
                station.location.coordinate.latitude,
                station.location.coordinate.longitude
            )
            domainCoord.distanceTo(nearPoint)
        }
    }

    private fun mapDomainConnector(type: DomainConnectorType): ConnectorType = when (type) {
        DomainConnectorType.TYPE_2 -> ConnectorType.TYPE_2
        DomainConnectorType.CCS_COMBO_1 -> ConnectorType.CCS_COMBO_1
        DomainConnectorType.CCS_COMBO_2 -> ConnectorType.CCS_COMBO_2
        DomainConnectorType.CHAdeMO -> ConnectorType.CHAdeMO
        DomainConnectorType.GB_T_AC -> ConnectorType.GB_T_AC
        DomainConnectorType.GB_T_DC -> ConnectorType.GB_T_DC
        DomainConnectorType.TESLA_NACS -> ConnectorType.TESLA_NACS
        DomainConnectorType.TESLA_SUPERCHARGER -> ConnectorType.TESLA_SUPERCHARGER
        DomainConnectorType.OTHER -> ConnectorType.OTHER
        DomainConnectorType.UNKNOWN -> ConnectorType.UNKNOWN
    }

    private fun mapAccessType(accessType: DomainAccessType): AccessType = when (accessType) {
        DomainAccessType.PUBLIC -> AccessType.PUBLIC
        DomainAccessType.PRIVATE -> AccessType.PRIVATE
        DomainAccessType.RESTRICTED -> AccessType.RESTRICTED
        DomainAccessType.SEMI_PUBLIC -> AccessType.SEMI_PUBLIC
        DomainAccessType.UNKNOWN -> AccessType.PUBLIC
    }
}
