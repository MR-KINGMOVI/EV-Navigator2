package com.evnavigator.iran.ui.screen.map

import com.evnavigator.iran.domain.intelligence.energy.TripEnergyPrediction
import com.evnavigator.iran.domain.intelligence.trip.CorridorStationProjector
import com.evnavigator.iran.domain.journey.multistop.WaypointNode
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import com.evnavigator.iran.domain.model.catalog.ConnectorNormalizer
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.policy.DomainClock

/**
 * Pure helper for destination coordinate resolution and corridor charging station projection
 * on the Map Navigation screen (V13.3 Sections 1.2, 6, 7, 8 & 9).
 *
 * V13.3 Invariants:
 * 1. Uses [CanonicalConnectorType.fromDomainType] (never passing `DomainConnectorType` enum to `fromRaw(String?)`).
 * 2. Evaluates ALL eligible stations along the route corridor without artificial `size >= 2` early termination,
 *    ranking candidates by route progress, effective AC/DC power, freshness/verification, and cross-track detour.
 * 3. Never uses the synthetic `(100.0 - remainingKm * 0.22)` arrival SOC formula; computes arrival SOC from
 *    actual physical energy prediction (`routeEnergyPrediction`) and usable battery capacity.
 */
internal object MapDestinationAndCorridorHelper {

    data class DestinationPreset(
        val id: String,
        val displayNameFa: String,
        val coordinate: GeoCoordinate,
        val keywords: List<String>
    )

    val INTERCITY_PRESETS: List<DestinationPreset> = listOf(
        DestinationPreset("dest_qom", "قم (حرم مطهر / عوارضی)", GeoCoordinate(34.6416, 50.8746), listOf("قم", "qom")),
        DestinationPreset("dest_isfahan", "اصفهان (دروازه شیراز / میدان آزادی)", GeoCoordinate(32.6539, 51.6660), listOf("اصفهان", "isfahan", "esfahan")),
        DestinationPreset("dest_karaj", "کرج (میدان استاندارد / مهرشهر)", GeoCoordinate(35.8400, 50.9391), listOf("کرج", "karaj", "البرز")),
        DestinationPreset("dest_qazvin", "قزوین (دروازه رشت / عوارضی)", GeoCoordinate(36.2688, 50.0041), listOf("قزوین", "qazvin")),
        DestinationPreset("dest_rasht", "رشت (میدان گیل / بلوار امام)", GeoCoordinate(37.2808, 49.5832), listOf("رشت", "rasht", "گیلان")),
        DestinationPreset("dest_kashan", "کاشان (میدان پانزده خرداد)", GeoCoordinate(33.9850, 51.4100), listOf("کاشان", "kashan")),
        DestinationPreset("dest_shiraz", "شیراز (دروازه قرآن)", GeoCoordinate(29.6355, 52.5619), listOf("شیراز", "shiraz")),
        DestinationPreset("dest_mashhad", "مشهد (میدان جانباز / پایانه امام رضا)", GeoCoordinate(36.2972, 59.6067), listOf("مشهد", "mashhad")),
        DestinationPreset("dest_tabriz", "تبریز (میدان ائل‌گلی)", GeoCoordinate(38.0225, 46.3622), listOf("تبریز", "tabriz")),
        DestinationPreset("dest_pardis", "پردیس (پارک فناوری)", GeoCoordinate(35.7419, 51.8152), listOf("پردیس", "pardis", "دماوند")),
        DestinationPreset("dest_milad", "تهران (برج میلاد)", GeoCoordinate(35.7448, 51.3753), listOf("میلاد", "milad")),
        DestinationPreset("dest_azadi", "تهران (میدان آزادی)", GeoCoordinate(35.6997, 51.3380), listOf("آزادی", "azadi")),
        DestinationPreset("dest_tajrish", "تهران (میدان تجریش)", GeoCoordinate(35.8068, 51.4288), listOf("تجریش", "tajrish"))
    )

    private val EXPLICIT_COORD_REGEX = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,،\s]\s*(-?\d+(?:\.\d+)?)\s*$""")

    fun resolveDestinationQuery(query: String): Pair<GeoCoordinate, String>? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val coordMatch = EXPLICIT_COORD_REGEX.matchEntire(trimmed)
        if (coordMatch != null) {
            val lat = coordMatch.groupValues[1].toDoubleOrNull()
            val lon = coordMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && lat in 24.0..40.5 && lon in 43.5..64.5) {
                val rev = com.evnavigator.iran.domain.geocoding.GeocodingAndReverseGeocodingService.reverseGeocode(GeoCoordinate(lat, lon))
                return GeoCoordinate(lat, lon) to rev.title
            }
        }

        val normalized = trimmed.lowercase()
        val matchedPreset = INTERCITY_PRESETS.firstOrNull { preset ->
            preset.displayNameFa == trimmed || preset.keywords.any { normalized == it.lowercase() }
        }
        if (matchedPreset != null) {
            return matchedPreset.coordinate to matchedPreset.displayNameFa
        }

        val geocoded = com.evnavigator.iran.domain.geocoding.GeocodingAndReverseGeocodingService.geocodeQuery(trimmed)
        return geocoded?.let { it.coordinate to it.title }
    }

    /**
     * Evaluates and ranks all compatible charging stations along the route polyline without
     * artificial early `break` limits, checking port-level connector and AC/DC vehicle power compatibility.
     */
    fun buildCorridorWaypointNodes(
        allStations: List<DomainChargingStationWithPorts>,
        routePolyline: List<GeoCoordinate>,
        vehicle: DomainVehicle?,
        maxCrossTrackMeters: Double = 15_000.0,
        maxCandidates: Int = 8,
        clock: DomainClock = DomainClock.SYSTEM
    ): List<WaypointNode> {
        if (routePolyline.size < 2 || allStations.isEmpty()) return emptyList()

        val vehicleConnectors: Set<CanonicalConnectorType> = if (vehicle != null) {
            ConnectorNormalizer.toCanonicalSet(vehicle.connectorTypes)
        } else {
            emptySet()
        }
        if (vehicle != null && vehicleConnectors.isEmpty()) {
            return emptyList()
        }

        data class RankedCandidate(
            val node: WaypointNode,
            val distanceAlongMeters: Double,
            val score: Double
        )

        val ranked = mutableListOf<RankedCandidate>()

        for (stationWithPorts in allStations) {
            val st = stationWithPorts.station
            if (st.operationalStatus.name == "OUT_OF_SERVICE") continue
            if (st.validationStatus.uppercase() in setOf("OUTDATED", "UNAVAILABLE", "DECOMMISSIONED")) continue

            val compatiblePorts = stationWithPorts.ports.filter { port ->
                val portStatus = port.status.trim().uppercase()
                val statusOk = portStatus !in setOf("OUT_OF_SERVICE", "UNAVAILABLE", "FAULTED", "OFFLINE")
                val portCanon = CanonicalConnectorType.fromDomainType(port.connectorType)
                    .takeIf { it != CanonicalConnectorType.UNKNOWN }
                    ?: port.canonicalConnector
                val connectorOk = if (vehicle != null) {
                    portCanon != CanonicalConnectorType.UNKNOWN && portCanon in vehicleConnectors
                } else {
                    portCanon != CanonicalConnectorType.UNKNOWN
                }
                val powerOk = if (vehicle != null) {
                    if (portCanon.isDcFast) (vehicle.dcMaxChargePowerKw ?: 0.0) > 0.0
                    else (vehicle.acMaxChargePowerKw ?: 0.0) > 0.0
                } else {
                    (port.powerKw ?: 0.0) > 0.0
                }
                statusOk && connectorOk && powerOk && (port.powerKw ?: 0.0) > 0.0
            }
            if (compatiblePorts.isEmpty()) continue

            val stationCoord = GeoCoordinate(st.latitude, st.longitude)
            val projection = CorridorStationProjector.projectOntoPolyline(stationCoord, routePolyline)
            if (projection.crossTrackMeters > maxCrossTrackMeters) continue

            val bestPort = compatiblePorts.maxByOrNull { port ->
                val portCanon = CanonicalConnectorType.fromDomainType(port.connectorType)
                    .takeIf { it != CanonicalConnectorType.UNKNOWN }
                    ?: port.canonicalConnector
                val portKw = port.powerKw ?: 0.0
                val vehLimitKw = if (vehicle != null) {
                    if (portCanon.isDcFast) vehicle.dcMaxChargePowerKw ?: portKw
                    else vehicle.acMaxChargePowerKw ?: portKw
                } else portKw
                minOf(portKw, vehLimitKw)
            } ?: continue

            val bestPortKw = bestPort.powerKw ?: continue
            val stationFreshness = st.evaluateFreshness(clock)
            val portConns = compatiblePorts.mapNotNull { p ->
                val canon = CanonicalConnectorType.fromDomainType(p.connectorType)
                    .takeIf { it != CanonicalConnectorType.UNKNOWN }
                    ?: p.canonicalConnector
                canon.takeIf { it != CanonicalConnectorType.UNKNOWN }?.name
            }.distinct()

            val trustScore = when {
                st.isVerified && st.hasTraceableExternalEvidence && stationFreshness.isFresh -> 0.30
                st.isVerified && stationFreshness.isFresh -> 0.18
                else -> 0.05
            }
            val powerScore = (bestPortKw / 150.0).coerceIn(0.05, 1.0) * 0.45
            val proximityScore = (1.0 - (projection.crossTrackMeters / maxCrossTrackMeters).coerceIn(0.0, 1.0)) * 0.25

            ranked.add(
                RankedCandidate(
                    node = WaypointNode(
                        id = st.id,
                        name = st.canonicalName,
                        coordinate = stationCoord,
                        isChargingStop = true,
                        maxChargingPowerKw = bestPortKw,
                        supportedConnectors = portConns
                    ),
                    distanceAlongMeters = projection.distanceAlongRouteMeters,
                    score = powerScore + trustScore + proximityScore
                )
            )
        }

        return ranked
            .sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.distanceAlongMeters })
            .take(maxCandidates.coerceAtLeast(1))
            .sortedBy { it.distanceAlongMeters }
            .map { it.node }
    }

    /**
     * Builds UI charging stop summary cards strictly from the selected charging stops in the
     * planned route (`selectedChargingLegs`) and physical energy predictions — never from a
     * linear `100 - remainingKm * 0.22` formula!
     */
    fun buildPlannedChargingStopSummaries(
        selectedChargingStops: List<PlannedChargingStopSummaryInput>,
        routeEnergyPrediction: TripEnergyPrediction?,
        departureSocPercent: Double,
        usableBatteryKwh: Double?
    ): List<MapNavigationViewModel.ChargingStopSummary> {
        if (selectedChargingStops.isEmpty()) return emptyList()
        val fallbackArrivalSoc = routeEnergyPrediction?.arrivalSocNominalPercent
            ?: if (routeEnergyPrediction != null && usableBatteryKwh != null && usableBatteryKwh > 0.0) {
                (departureSocPercent - (routeEnergyPrediction.expectedWh / (usableBatteryKwh * 1000.0)) * 100.0).coerceIn(0.0, 100.0)
            } else {
                departureSocPercent
            }

        return selectedChargingStops.map { stop ->
            MapNavigationViewModel.ChargingStopSummary(
                id = stop.id,
                name = stop.name,
                operator = stop.operator,
                powerKw = stop.powerKw,
                distanceFromOriginKm = stop.distanceFromOriginKm,
                estimatedArrivalSoc = stop.arrivalSocPercent.takeIf { it.isFinite() } ?: fallbackArrivalSoc.toInt(),
                recommendedChargeMinutes = stop.chargingDurationMinutes.toInt().coerceAtLeast(1)
            )
        }
    }

    data class PlannedChargingStopSummaryInput(
        val id: String,
        val name: String,
        val operator: String,
        val powerKw: Int,
        val distanceFromOriginKm: Int,
        val arrivalSocPercent: Int,
        val chargingDurationMinutes: Double
    )
}
