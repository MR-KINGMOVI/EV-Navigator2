package com.evnavigator.iran.ui.screen.journey

import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.intelligence.charging.StationFreshnessState
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.ChargingStationWithPortsDomainModel
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V11 Extracted Collaborator: Strategic Iranian transit hubs, bilingual search filtering,
 * and route-corridor charging station projection.
 */
object DestinationSearchCoordinator {

    val strategicIranHubs: List<DestinationSearchResult> = listOf(
        DestinationSearchResult("hub_tehran_azadi", "تهران (میدان آزادی)", "تقاطع بزرگراه جناح و لشگری • مرکز ترانزیت غرب", GeoCoordinate(35.6997, 51.3380), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_tehran_milad", "تهران (برج میلاد)", "مرکز همایش‌ها و ایستگاه شارژ سریع مپنا", GeoCoordinate(35.7448, 51.3753), DestinationType.CHARGING_STATION),
        DestinationSearchResult("hub_qom_mehromah", "قم (مجتمع مهروماه)", "کیلومتر ۶ آزادراه تهران-قم • ایستگاه شارژ فوق‌سریع DC", GeoCoordinate(34.6401, 50.8764), DestinationType.CHARGING_STATION),
        DestinationSearchResult("hub_kashan_fin", "کاشان (میدان جهاد / فین)", "ورودی شهر تاریخی کاشان و تقاطع نطنز", GeoCoordinate(33.9850, 51.4100), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_isfahan_khaju", "اصفهان (پل خواجو)", "مرکز تاریخی اصفهان و کریدور مرکزی ایران", GeoCoordinate(32.6370, 51.6830), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_isfahan_citycenter", "اصفهان (سیتی سنتر)", "بزرگراه شهید دستجردی • هاب تجاری و شارژ مکو", GeoCoordinate(32.5510, 51.6860), DestinationType.CHARGING_STATION),
        DestinationSearchResult("hub_shiraz_quran", "شیراز (دروازه قرآن)", "ورودی شمالی شیراز • محور ترانزیت استان فارس", GeoCoordinate(29.6360, 52.5590), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_tabriz_elgoli", "تبریز (ائل‌گلی)", "شمال غرب کشور • بزرگراه شهید کسایی", GeoCoordinate(38.0260, 46.3680), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_mashhad_haram", "مشهد (میدان بیت‌المقدس)", "شمال شرق کشور • پایانه مرکزی و حرم مطهر", GeoCoordinate(36.2870, 59.6150), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_karaj_hesarak", "کرج (میدان حصارک)", "تقاطع اتوبان کرج-قزوین و ورودی کمالشهر", GeoCoordinate(35.8450, 50.9320), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_rasht_shahrdari", "رشت (میدان شهرداری)", "استان گیلان • کریدور ساحلی شمال", GeoCoordinate(37.2800, 49.5830), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_yazd_amirchakhmaq", "یزد (میدان امیرچخmaq)", "مرکز کویری ایران و محور ترانزیت جنوب شرق", GeoCoordinate(31.8940, 54.3710), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_qazvin_minoodar", "قزوین (میدان مینودر)", "تقاطع کریدور شمال به غرب و آزادراه قزوین-زنجان", GeoCoordinate(36.2790, 50.0430), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_zanjan_freeway", "زنجان (مجتمع آفتاب درخشان)", "کیلومتر ۴۰ آزادراه زنجان-تبریز • شارژ سریع", GeoCoordinate(36.6760, 48.4960), DestinationType.CHARGING_STATION),
        DestinationSearchResult("hub_ahvaz_whitesbridge", "اهواز (پل سفید)", "مرکز استان خوزستان • جنوب غرب کشور", GeoCoordinate(31.3270, 48.6940), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_kerman_azadi", "کرمان (میدان آزادی)", "جنوب شرق • محور ترانزیت بندرعباس و چابهار", GeoCoordinate(30.2910, 57.0670), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_bandarabbas_port", "بندرعباس (بلوار ساحلی)", "بزرگترین بندر تجاری خلیج فارس", GeoCoordinate(27.1830, 56.2660), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_kish_marjan", "جزیره کیش (ساحل مرجان)", "منطقه آزاد کیش • هاب خودروهای تمام‌برقی", GeoCoordinate(26.5160, 54.0280), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_qeshm_dargahan", "جزیره قشم (درگهان)", "مرکز تجاری و ساحلی تنگه هرمز", GeoCoordinate(26.9640, 55.9750), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_sari_imam", "ساری (میدان امام)", "مرکز استان مازندران • محور فیروزکوه به دریا", GeoCoordinate(36.5650, 53.0590), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_gorgan_naharkhoran", "گرگان (بلوار ناهارخوران)", "استان گلستان • کریدور جنگل‌های هیرکانی", GeoCoordinate(36.7900, 54.4420), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_urmia_band", "ارومیه (بلوار امامت)", "مرکز آذربایجان غربی • پایانه مرزی سرو", GeoCoordinate(37.5250, 45.0470), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_hamedan_buali", "همدان (میدان بوعلی‌سینا)", "پایتخت تاریخ و تمدن ایران • دامنه‌های الوند", GeoCoordinate(34.7980, 48.5140), DestinationType.CITY_HUB),
        DestinationSearchResult("hub_bushehr_lian", "بوشهر (بلوار ساحلی لیان)", "پایگاه انرژی و خلیج همیشه فارس", GeoCoordinate(28.9230, 50.8360), DestinationType.CITY_HUB)
    )

    fun filterResults(
        query: String,
        chargingStations: List<ChargingStationWithPortsDomainModel>
    ): List<DestinationSearchResult> {
        val trimmed = query.trim().lowercase()
        val hubMatches = if (trimmed.isEmpty()) {
            strategicIranHubs
        } else {
            strategicIranHubs.filter {
                it.title.lowercase().contains(trimmed) ||
                    it.subtitle.lowercase().contains(trimmed) ||
                    matchesPersianCityAlias(it.id, trimmed)
            }
        }

        val stationMatches = (if (trimmed.isEmpty()) {
            chargingStations.take(15)
        } else {
            chargingStations.filter { s ->
                s.station.canonicalName.lowercase().contains(trimmed) ||
                    (s.station.city?.lowercase()?.contains(trimmed) == true) ||
                    (s.station.province?.lowercase()?.contains(trimmed) == true) ||
                    (s.station.operatorName?.lowercase()?.contains(trimmed) == true)
            }.take(20)
        }).mapNotNull { s ->
            val bestPort = s.ports
                .filter { (it.powerKw ?: 0.0) > 0.0 }
                .maxByOrNull { it.powerKw ?: 0.0 }
            val maxKw = bestPort?.powerKw ?: s.station.maxPowerKw.takeIf { it > 0.0 } ?: return@mapNotNull null
            val operator = s.station.operatorName ?: "اپراتور ثبت‌نشده"
            val city = s.station.city ?: "ایران"
            DestinationSearchResult(
                id = "station_${s.station.id}",
                title = s.station.canonicalName,
                subtitle = "$city • ${maxKw.toInt()} kW • $operator",
                coordinate = GeoCoordinate(s.station.latitude, s.station.longitude),
                type = DestinationType.CHARGING_STATION,
                stationDetails = CandidateStation(
                    stationId = s.station.id,
                    stationName = s.station.canonicalName,
                    compatibleConnectors = listOfNotNull(bestPort?.connectorType?.name).ifEmpty { s.ports.map { it.connectorType.name }.distinct() },
                    maxPowerKw = maxKw,
                    detourDistanceMeters = 0.0,
                    detourDurationSeconds = 0.0,
                    distanceAlongRouteMeters = 0.0
                )
            )
        }

        val geocodedMatches = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            com.evnavigator.iran.domain.geocoding.GeocodingAndReverseGeocodingService
                .searchPlaces(query, emptyList(), limit = 10)
                .map { place ->
                    val destType = when (place.placeType) {
                        com.evnavigator.iran.domain.geocoding.GeocodingPlaceType.CHARGING_STATION -> DestinationType.CHARGING_STATION
                        com.evnavigator.iran.domain.geocoding.GeocodingPlaceType.CITY_CENTROID -> DestinationType.CITY_HUB
                        else -> DestinationType.CUSTOM_MAP_PIN
                    }
                    DestinationSearchResult(
                        id = "geo_${place.placeId}",
                        title = place.title,
                        subtitle = place.subtitle,
                        coordinate = place.coordinate,
                        type = destType
                    )
                }
        }

        return (stationMatches + hubMatches + geocodedMatches).distinctBy { "${it.title}_${"%.4f".format(it.coordinate.latitude)}_${"%.4f".format(it.coordinate.longitude)}" }
    }

    fun matchesPersianCityAlias(hubId: String, query: String): Boolean {
        val q = query.lowercase()
        return when (hubId) {
            "hub_tehran_azadi", "hub_tehran_milad" -> q.contains("تهران") || q.contains("tehran") || q.contains("آزادی") || q.contains("azadi") || q.contains("milad")
            "hub_isfahan_khaju", "hub_isfahan_citycenter" -> q.contains("اصفهان") || q.contains("isfahan") || q.contains("esfahan") || q.contains("خواجو") || q.contains("khaju")
            "hub_qom_mehromah" -> q.contains("قم") || q.contains("qom") || q.contains("مهروماه") || q.contains("mehromah")
            "hub_kashan_fin" -> q.contains("کاشان") || q.contains("kashan")
            "hub_shiraz_quran" -> q.contains("شیراز") || q.contains("shiraz")
            "hub_tabriz_elgoli" -> q.contains("تبریز") || q.contains("tabriz") || q.contains("elgoli")
            "hub_mashhad_haram" -> q.contains("مشهد") || q.contains("mashhad") || q.contains("haram")
            "hub_karaj_hesarak" -> q.contains("کرج") || q.contains("karaj") || q.contains("hesarak")
            "hub_rasht_shahrdari" -> q.contains("رشت") || q.contains("rasht") || q.contains("gilan")
            "hub_yazd_amirchakhmaq" -> q.contains("یزد") || q.contains("yazd") || q.contains("amirchakhmaq")
            "hub_qazvin_minoodar" -> q.contains("قزوین") || q.contains("qazvin") || q.contains("minoodar")
            "hub_zanjan_freeway" -> q.contains("زنجان") || q.contains("zanjan") || q.contains("soltaniyeh")
            "hub_ahvaz_whitesbridge" -> q.contains("اهواز") || q.contains("ahvaz") || q.contains("ahwaz") || q.contains("karun")
            "hub_kerman_azadi" -> q.contains("کرمان") || q.contains("kerman") || q.contains("mahan")
            "hub_bandarabbas_port" -> q.contains("بندرعباس") || q.contains("bandar") || q.contains("abbas")
            else -> false
        }
    }

    fun projectBestChargingStopAlongPolyline(
        roadPolyline: List<GeoCoordinate>,
        roadDistMeters: Double,
        chargingStations: List<ChargingStationWithPortsDomainModel>,
        physicsParams: VehiclePhysicsParams,
        totalEnergyUsedKwh: Double
    ): JourneyWaypoint? {
        if (roadPolyline.size < 2 || physicsParams.usableBatteryKwh <= 0.0) return null
        val normVehConnectors = physicsParams.supportedConnectors.map {
            it.uppercase().replace("-", "").replace("_", "").replace("/", "")
        }.toSet()

        val cumulativeMeters = DoubleArray(roadPolyline.size)
        var runningMeters = 0.0
        for (i in 1 until roadPolyline.size) {
            runningMeters += calculateHaversineDistanceMeters(roadPolyline[i - 1], roadPolyline[i])
            cumulativeMeters[i] = runningMeters
        }
        val totalPolyMeters = runningMeters.coerceAtLeast(roadDistMeters)

        data class ProjectedCandidate(
            val entity: ChargingStationWithPortsDomainModel,
            val selectedPortConnector: String,
            val selectedPortPowerKw: Double,
            val lateralDistanceMeters: Double,
            val distanceAlongRouteMeters: Double
        )

        val projectedCandidates = chargingStations.mapNotNull { s ->
            if (s.ports.isEmpty() || s.station.validationStatus == "UNVERIFIED") return@mapNotNull null
            val matchingPort = s.ports
                .filter { port ->
                    val pKw = port.powerKw ?: 0.0
                    if (pKw <= 0.0 || port.status == "OUT_OF_SERVICE" || port.status == "UNAVAILABLE") return@filter false
                    if (normVehConnectors.isEmpty()) true
                    else {
                        val norm = port.connectorType.name.uppercase().replace("-", "").replace("_", "").replace("/", "")
                        norm in normVehConnectors ||
                            (norm.contains("CCS2") && normVehConnectors.any { it.contains("CCS2") }) ||
                            (norm.contains("GBT") && normVehConnectors.any { it.contains("GBT") })
                    }
                }
                .maxByOrNull { it.powerKw ?: 0.0 } ?: return@mapNotNull null

            val sCoord = GeoCoordinate(s.station.latitude, s.station.longitude)
            var bestIdx = 0
            var minLatMeters = Double.MAX_VALUE
            for (idx in roadPolyline.indices) {
                val d = calculateHaversineDistanceMeters(roadPolyline[idx], sCoord)
                if (d < minLatMeters) {
                    minLatMeters = d
                    bestIdx = idx
                }
            }
            val alongMeters = cumulativeMeters[bestIdx]
            if (minLatMeters <= 12_500.0 && alongMeters in (totalPolyMeters * 0.05)..(totalPolyMeters * 0.95)) {
                ProjectedCandidate(
                    entity = s,
                    selectedPortConnector = matchingPort.connectorType.name,
                    selectedPortPowerKw = matchingPort.powerKw ?: 0.0,
                    lateralDistanceMeters = minLatMeters,
                    distanceAlongRouteMeters = alongMeters
                )
            } else {
                null
            }
        }

        val bestCandidate = projectedCandidates.minByOrNull {
            it.lateralDistanceMeters + abs(it.distanceAlongRouteMeters - (totalPolyMeters * 0.5)) * 0.15
        } ?: return null

        val candidateStation = bestCandidate.entity
        val sCoord = GeoCoordinate(candidateStation.station.latitude, candidateStation.station.longitude)
        val detourMeters = bestCandidate.lateralDistanceMeters
        val maxKw = bestCandidate.selectedPortPowerKw
        val freshness = when (candidateStation.station.validationStatus) {
            "VERIFIED" -> StationFreshnessState.VERIFIED_RECENT
            "PARTIALLY_VERIFIED" -> StationFreshnessState.STALE
            else -> StationFreshnessState.UNKNOWN
        }
        val remainingFraction = ((totalPolyMeters - bestCandidate.distanceAlongRouteMeters) / totalPolyMeters.coerceAtLeast(1.0)).coerceIn(0.1, 1.0)
        val requiredSocForRemainder = (totalEnergyUsedKwh * remainingFraction / physicsParams.usableBatteryKwh) * 100.0
        val dynamicTargetSoc = (requiredSocForRemainder + 20.0).coerceIn(60.0, 95.0)

        return JourneyWaypoint(
            id = "wp_charge_${candidateStation.station.id}",
            name = candidateStation.station.canonicalName,
            coordinate = sCoord,
            type = WaypointType.CHARGING_STOP,
            targetDepartureSocPercent = dynamicTargetSoc,
            stationDetails = CandidateStation(
                stationId = candidateStation.station.id,
                stationName = candidateStation.station.canonicalName,
                compatibleConnectors = listOf(bestCandidate.selectedPortConnector),
                maxPowerKw = maxKw,
                detourDistanceMeters = detourMeters * 2.0,
                detourDurationSeconds = (detourMeters * 2.0) / 11.1,
                distanceAlongRouteMeters = bestCandidate.distanceAlongRouteMeters,
                freshnessState = freshness,
                liveStatusLastUpdatedEpochMs = candidateStation.station.updatedAt
            )
        )
    }

    fun calculateHaversineDistanceMeters(c1: GeoCoordinate, c2: GeoCoordinate): Double {
        val r = 6371000.0
        val lat1Rad = Math.toRadians(c1.latitude)
        val lat2Rad = Math.toRadians(c2.latitude)
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)

        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun calculateBearingDegrees(c1: GeoCoordinate, c2: GeoCoordinate): Float {
        val lat1 = Math.toRadians(c1.latitude)
        val lon1 = Math.toRadians(c1.longitude)
        val lat2 = Math.toRadians(c2.latitude)
        val lon2 = Math.toRadians(c2.longitude)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val radians = atan2(y, x)
        return ((Math.toDegrees(radians) + 360) % 360).toFloat()
    }
}
