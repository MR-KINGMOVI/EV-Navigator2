package com.evnavigator.iran.domain.routing.importer

import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.*

/**
 * Route formats supported by the EV Navigator Route Importer.
 */
enum class RouteFileFormat(val displayName: String) {
    GPX("فایل GPX (GPS Exchange Format)"),
    GEOJSON("فایل GeoJSON (LineString / Features)"),
    COORDINATE_STREAM("مختصات متنی (Lat, Lon)"),
    PRESET("مسیرهای آماده بین‌شهری ایران")
}

/**
 * Detailed representation of an imported route, including elevation profile,
 * waypoints, and suggested EV charging stop candidates along the corridor.
 */
data class ImportedRouteData(
    val routeName: String,
    val format: RouteFileFormat,
    val coordinates: List<GeoCoordinate>,
    val elevationsMeters: List<Double>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Double,
    val minElevationMeters: Double,
    val maxElevationMeters: Double,
    val totalClimbMeters: Double,
    val totalDescentMeters: Double,
    val origin: JourneyWaypoint,
    val destination: JourneyWaypoint,
    val suggestedChargingStops: List<JourneyWaypoint> = emptyList(),
    val notes: String = ""
) {
    val totalDistanceKm: Double
        get() = (totalDistanceMeters / 1000.0 * 10.0).roundToInt() / 10.0

    val estimatedHours: Double
        get() = (estimatedDurationSeconds / 3600.0 * 10.0).roundToInt() / 10.0
}

/**
 * Iconic pre-configured Iranian EV road trip route presets with topographic curvature
 * and strategic charging infrastructure along major corridors.
 */
data class PresetIranRoute(
    val id: String,
    val title: String,
    val subtitle: String,
    val distanceKm: Double,
    val estimatedDurationMinutes: Int,
    val chargingStopsSummary: String,
    val originCity: String,
    val destinationCity: String,
    val originCoordinate: GeoCoordinate,
    val destinationCoordinate: GeoCoordinate,
    val corridorCoordinates: List<GeoCoordinate>,
    val corridorElevations: List<Double>,
    val recommendedChargingStops: List<JourneyWaypoint>
)

/**
 * High-performance parser and synthesizer for external routes from GPX, GeoJSON,
 * Google Maps / Neshan / Balad coordinate streams, and iconic Iranian presets.
 */
object RouteFileImporter {

    private const val MAX_IMPORT_PAYLOAD_CHARS = 5 * 1024 * 1024 // 5 MB text limit
    private const val MAX_WAYPOINT_COUNT = 50_000

    /**
     * Automatically inspects text payload, detects format, and extracts structured route.
     */
    fun autoDetectAndParse(
        rawContent: String,
        customName: String? = null
    ): Result<ImportedRouteData> {
        if (rawContent.length > MAX_IMPORT_PAYLOAD_CHARS) {
            return Result.failure(IllegalArgumentException("حجم فایل مسیر بیش از حد مجاز (۵ مگابایت) است."))
        }
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("محتوای فایل یا مسیر خالی است."))
        }

        return when {
            trimmed.startsWith("<?xml") || trimmed.contains("<gpx", ignoreCase = true) -> {
                parseGpx(trimmed, customName)
            }
            trimmed.startsWith("{") && (trimmed.contains("Feature", ignoreCase = true) || trimmed.contains("LineString", ignoreCase = true)) -> {
                parseGeoJson(trimmed, customName)
            }
            else -> {
                parseCoordinateStream(trimmed, customName)
            }
        }
    }

    /**
     * Parses GPX XML containing `<trkpt lat="..." lon="...">` and optional `<ele>...</ele>`.
     */
    fun parseGpx(gpxXml: String, customName: String? = null): Result<ImportedRouteData> {
        return try {
            if (gpxXml.length > MAX_IMPORT_PAYLOAD_CHARS) {
                return Result.failure(IllegalArgumentException("حجم فایل GPX بیش از حد مجاز است."))
            }
            if (gpxXml.contains("<!DOCTYPE", ignoreCase = true) || gpxXml.contains("<!ENTITY", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("فایل GPX حاوی تعاریف غیرمجاز DOCTYPE/ENTITY است."))
            }
            val nameRegex = Regex("<name>(.*?)</name>", RegexOption.IGNORE_CASE)
            val extractedName = customName
                ?: nameRegex.find(gpxXml)?.groupValues?.get(1)?.trim()
                ?: "مسیر وارد شده GPX"

            val trkptRegex = Regex("""<trkpt\s+lat=["']([0-9.-]+)["']\s+lon=["']([0-9.-]+)["']\s*>(?:.*?<ele>([0-9.-]+)</ele>)?.*?</trkpt>""", RegexOption.DOT_MATCHES_ALL)
            val matches = trkptRegex.findAll(gpxXml).take(MAX_WAYPOINT_COUNT).toList()

            val coordinates = mutableListOf<GeoCoordinate>()
            val elevations = mutableListOf<Double>()

            for (match in matches) {
                val lat = match.groupValues[1].toDoubleOrNull()
                val lon = match.groupValues[2].toDoubleOrNull()
                val ele = match.groupValues.getOrNull(3)?.toDoubleOrNull() ?: Double.NaN

                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    coordinates.add(GeoCoordinate(lat, lon))
                    elevations.add(ele)
                }
            }

            if (coordinates.size < 2) {
                // Fallback attempt: match wpt or simple lat lon attributes
                val fallbackRegex = Regex("""lat=["']([0-9.-]+)["']\s+lon=["']([0-9.-]+)["']""")
                val fallbackMatches = fallbackRegex.findAll(gpxXml).take(MAX_WAYPOINT_COUNT).toList()
                for (m in fallbackMatches) {
                    val lat = m.groupValues[1].toDoubleOrNull()
                    val lon = m.groupValues[2].toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        coordinates.add(GeoCoordinate(lat, lon))
                        elevations.add(Double.NaN)
                    }
                }
            }

            if (coordinates.size < 2) {
                return Result.failure(IllegalArgumentException("هیچ نقطه مکانی معتبری در فایل GPX یافت نشد."))
            }

            val routeData = buildImportedRouteData(extractedName, RouteFileFormat.GPX, coordinates, elevations)
            Result.success(routeData)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("خطا در پردازش فایل GPX: ${e.message}", e))
        }
    }

    /**
     * Parses GeoJSON FeatureCollection, Feature, or Geometry containing LineString coordinates.
     */
    fun parseGeoJson(geoJson: String, customName: String? = null): Result<ImportedRouteData> {
        return try {
            if (geoJson.length > MAX_IMPORT_PAYLOAD_CHARS) {
                return Result.failure(IllegalArgumentException("حجم فایل GeoJSON بیش از حد مجاز است."))
            }
            val coordinates = mutableListOf<GeoCoordinate>()
            val elevations = mutableListOf<Double>()

            // Robust extraction of coordinates: [lon, lat, ele?]
            val coordPairRegex = Regex("""\[\s*([0-9.-]+)\s*,\s*([0-9.-]+)(?:\s*,\s*([0-9.-]+))?\s*\]""")
            val matches = coordPairRegex.findAll(geoJson).take(MAX_WAYPOINT_COUNT).toList()

            for (match in matches) {
                val lon = match.groupValues[1].toDoubleOrNull()
                val lat = match.groupValues[2].toDoubleOrNull()
                val ele = match.groupValues.getOrNull(3)?.toDoubleOrNull() ?: Double.NaN

                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    coordinates.add(GeoCoordinate(lat, lon))
                    elevations.add(ele)
                }
            }

            if (coordinates.size < 2) {
                return Result.failure(IllegalArgumentException("مختصات LineString معتبری در فایل GeoJSON یافت نشد."))
            }

            val extractedName = customName ?: "مسیر وارد شده GeoJSON"
            val routeData = buildImportedRouteData(extractedName, RouteFileFormat.GEOJSON, coordinates, elevations)
            Result.success(routeData)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("خطا در پردازش فایل GeoJSON: ${e.message}", e))
        }
    }

    /**
     * Parses plain text lines with latitude, longitude pairs (CSV, spaces, or tabs).
     */
    fun parseCoordinateStream(text: String, customName: String? = null): Result<ImportedRouteData> {
        return try {
            if (text.length > MAX_IMPORT_PAYLOAD_CHARS) {
                return Result.failure(IllegalArgumentException("حجم متن مختصات بیش از حد مجاز است."))
            }
            val coordinates = mutableListOf<GeoCoordinate>()
            val elevations = mutableListOf<Double>()

            val lines = text.lineSequence().take(MAX_WAYPOINT_COUNT)
            for (line in lines) {
                val clean = line.trim()
                if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("//")) continue

                val parts = clean.split(Regex("[,\\s\\t]+")).filter { it.isNotBlank() }
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lon = parts[1].toDoubleOrNull()
                    val ele = parts.getOrNull(2)?.toDoubleOrNull() ?: Double.NaN

                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        coordinates.add(GeoCoordinate(lat, lon))
                        elevations.add(ele)
                    }
                }
            }

            if (coordinates.size < 2) {
                return Result.failure(IllegalArgumentException("فرمت متنی مختصات نامعتبر است (حداقل دو خط lat, lon لازم است)."))
            }

            val extractedName = customName ?: "مسیر دستی وارد شده"
            val routeData = buildImportedRouteData(extractedName, RouteFileFormat.COORDINATE_STREAM, coordinates, elevations)
            Result.success(routeData)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("خطا در خواندن خطوط مختصات: ${e.message}", e))
        }
    }

    /**
     * Synthesizes topographic metrics, step distances, and origin/destination waypoints.
     */
    private fun buildImportedRouteData(
        name: String,
        format: RouteFileFormat,
        coordinates: List<GeoCoordinate>,
        elevations: List<Double>
    ): ImportedRouteData {
        var totalDistMeters = 0.0
        var totalClimb = 0.0
        var totalDescent = 0.0
        val validElevations = elevations.filter { !it.isNaN() }
        val hasDem = validElevations.isNotEmpty()
        var minEle = if (hasDem) validElevations.minOrNull() ?: Double.NaN else Double.NaN
        var maxEle = if (hasDem) validElevations.maxOrNull() ?: Double.NaN else Double.NaN

        for (i in 0 until coordinates.size - 1) {
            val c1 = coordinates[i]
            val c2 = coordinates[i + 1]
            val stepDist = calculateHaversineDistanceMeters(c1, c2)
            totalDistMeters += stepDist

            val e1 = elevations.getOrNull(i) ?: Double.NaN
            val e2 = elevations.getOrNull(i + 1) ?: Double.NaN

            if (!e1.isNaN() && !e2.isNaN()) {
                if (e2 > e1) {
                    totalClimb += (e2 - e1)
                } else {
                    totalDescent += (e1 - e2)
                }
            }
        }

        if (!hasDem) {
            totalClimb = Double.NaN
            totalDescent = Double.NaN
        }

        // Average speed estimation: ~85 km/h for highways = ~23.6 m/s
        val durationSec = totalDistMeters / 23.6

        val originWp = JourneyWaypoint(
            id = "import_wp_origin",
            name = "مبدأ (${name})",
            coordinate = coordinates.first(),
            type = WaypointType.ORIGIN
        )

        val destinationWp = JourneyWaypoint(
            id = "import_wp_destination",
            name = "مقصد (${name})",
            coordinate = coordinates.last(),
            type = WaypointType.DESTINATION
        )

        val importNotes = if (hasDem) "" else "اطلاعات ارتفاعی (DEM) در فایل موجود نیست؛ وضعیت ارتفاع نامشخص (UNKNOWN) ثبت شد."
        return ImportedRouteData(
            routeName = name,
            format = format,
            coordinates = coordinates,
            elevationsMeters = elevations,
            totalDistanceMeters = totalDistMeters,
            estimatedDurationSeconds = durationSec,
            minElevationMeters = minEle,
            maxElevationMeters = maxEle,
            totalClimbMeters = totalClimb,
            totalDescentMeters = totalDescent,
            origin = originWp,
            destination = destinationWp,
            notes = importNotes
        )
    }

    /**
     * Curated, verified Iranian Iconic EV Road Trip Presets with high-resolution coordinates,
     * realistic mountain topography, and optimal DC fast/ultra-fast charging stops.
     */
    fun getPresetRoutes(): List<PresetIranRoute> {
        return listOf(
            // 1. Tehran to Isfahan
            PresetIranRoute(
                id = "preset_tehran_isfahan",
                title = "تهران به اصفهان",
                subtitle = "آزادراه خلیج فارس و امیرکبیر • ۴۴۰ کیلومتر",
                distanceKm = 440.0,
                estimatedDurationMinutes = 270,
                chargingStopsSummary = "۱ توقف شارژ فوق‌سریع: مجتمع مهروماه قم (۱۲۰ kW)",
                originCity = "تهران",
                destinationCity = "اصفهان",
                originCoordinate = GeoCoordinate(35.6997, 51.3380),
                destinationCoordinate = GeoCoordinate(32.6370, 51.6830),
                corridorCoordinates = listOf(
                    GeoCoordinate(35.6997, 51.3380), // Azadi Tehran
                    GeoCoordinate(35.5350, 51.3500), // Shahr Aftab
                    GeoCoordinate(35.2500, 51.1500), // Robat Karim
                    GeoCoordinate(34.8500, 50.9500), // Aliabad Freeway
                    GeoCoordinate(34.6401, 50.8764), // Mehr-o-Mah Qom (HPC Stop)
                    GeoCoordinate(34.3500, 51.1500), // Qom-Kashan freeway
                    GeoCoordinate(33.9850, 51.4100), // Kashan Amir Kabir
                    GeoCoordinate(33.5120, 51.8900), // Natanz Ferdos Kavir
                    GeoCoordinate(33.0500, 51.8500), // Mourcheh Khort
                    GeoCoordinate(32.8620, 51.5620), // Shahin Shahr
                    GeoCoordinate(32.6370, 51.6830)  // Isfahan Khaju
                ),
                corridorElevations = listOf(1190.0, 1050.0, 1000.0, 950.0, 930.0, 970.0, 1020.0, 1380.0, 1620.0, 1580.0, 1570.0),
                recommendedChargingStops = listOf(
                    JourneyWaypoint(
                        id = "stop_mehromah_qom",
                        name = "ایستگاه فوق‌سریع مجتمع مهر و ماه قم (مپنا)",
                        coordinate = GeoCoordinate(34.6401, 50.8764),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 85.0,
                        stationDetails = CandidateStation(
                            stationId = "station_qom_mehromah",
                            stationName = "ایستگاه فوق‌سریع مجتمع مهر و ماه قم",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 120.0,
                            detourDistanceMeters = 300.0,
                            detourDurationSeconds = 45.0,
                            distanceAlongRouteMeters = 135_000.0
                        )
                    )
                )
            ),

            // 2. Tehran to Rasht & Caspian Coast
            PresetIranRoute(
                id = "preset_tehran_rasht",
                title = "تهران به رشت و انزلی",
                subtitle = "آزادراه قزوین-رشت و کریدور کاسپین • ۳۳۵ کیلومتر",
                distanceKm = 335.0,
                estimatedDurationMinutes = 220,
                chargingStopsSummary = "۱ توقف شارژ سریع: مجتمع زیتون قزوین (۶۰ kW)",
                originCity = "تهران",
                destinationCity = "رشت",
                originCoordinate = GeoCoordinate(35.6997, 51.3380),
                destinationCoordinate = GeoCoordinate(37.2800, 49.5850),
                corridorCoordinates = listOf(
                    GeoCoordinate(35.6997, 51.3380), // Tehran
                    GeoCoordinate(35.8412, 51.0185), // Karaj
                    GeoCoordinate(35.9550, 50.6850), // Hashtgerd
                    GeoCoordinate(36.0800, 50.4000), // Abyek
                    GeoCoordinate(36.1950, 50.1500), // Zeytoon Qazvin (Charger Stop)
                    GeoCoordinate(36.4250, 49.6850), // Koohin Pass
                    GeoCoordinate(36.6500, 49.5200), // Lowshan
                    GeoCoordinate(36.7420, 49.4120), // Manjil Windfarm
                    GeoCoordinate(36.8900, 49.5100), // Roodbar Olive Hub
                    GeoCoordinate(37.0500, 49.6000), // Emamzadeh Hashem
                    GeoCoordinate(37.2800, 49.5850)  // Rasht Sabzeh Meydan
                ),
                corridorElevations = listOf(1190.0, 1310.0, 1280.0, 1260.0, 1290.0, 1980.0, 450.0, 360.0, 220.0, 75.0, 12.0),
                recommendedChargingStops = listOf(
                    JourneyWaypoint(
                        id = "stop_zeytoon_qazvin",
                        name = "ایستگاه شارژ بین‌راهی مجتمع زیتون قزوین",
                        coordinate = GeoCoordinate(36.1950, 50.1500),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 85.0,
                        stationDetails = CandidateStation(
                            stationId = "station_qazvin_zeytoon",
                            stationName = "ایستگاه بین‌راهی مجتمع زیتون قزوین",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 60.0,
                            detourDistanceMeters = 200.0,
                            detourDurationSeconds = 30.0,
                            distanceAlongRouteMeters = 120_000.0
                        )
                    )
                )
            ),

            // 3. Tehran to Mashhad (Holy Shrine Corridor)
            PresetIranRoute(
                id = "preset_tehran_mashhad",
                title = "تهران به مشهد مقدس",
                subtitle = "بزرگراه امام رضا (ع) • ۸۹۸ کیلومتر",
                distanceKm = 898.0,
                estimatedDurationMinutes = 610,
                chargingStopsSummary = "۳ توقف شارژ فوق‌سریع: سمنان، شاهرود و سبزوار (۱۲۰ kW)",
                originCity = "تهران",
                destinationCity = "مشهد",
                originCoordinate = GeoCoordinate(35.6997, 51.3380),
                destinationCoordinate = GeoCoordinate(36.2870, 59.6150),
                corridorCoordinates = listOf(
                    GeoCoordinate(35.6997, 51.3380), // Tehran
                    GeoCoordinate(35.4500, 51.6500), // Pakdasht
                    GeoCoordinate(35.3200, 51.9800), // Eyvanki
                    GeoCoordinate(35.2250, 52.3420), // Garmsar Aftab
                    GeoCoordinate(35.5820, 53.4020), // Semnan Ghazali (Stop 1)
                    GeoCoordinate(36.1680, 54.3450), // Damghan
                    GeoCoordinate(36.4180, 54.9750), // Shahrud Haft Khan (Stop 2)
                    GeoCoordinate(36.3500, 56.1200), // Meyami
                    GeoCoordinate(36.2180, 57.6750), // Sabzevar Sarbedaran (Stop 3)
                    GeoCoordinate(36.1850, 58.8250), // Neyshabur Khayyam
                    GeoCoordinate(36.2870, 59.6150)  // Mashhad Shrine
                ),
                corridorElevations = listOf(1190.0, 1020.0, 980.0, 890.0, 1140.0, 1170.0, 1340.0, 1210.0, 970.0, 1250.0, 990.0),
                recommendedChargingStops = listOf(
                    JourneyWaypoint(
                        id = "stop_semnan_ghazali",
                        name = "ایستگاه ترانزیت مجتمع غزالی سمنان (۱۲۰ kW)",
                        coordinate = GeoCoordinate(35.5820, 53.4020),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 85.0,
                        stationDetails = CandidateStation(
                            stationId = "station_semnan_ghazali",
                            stationName = "ایستگاه ترانزیت مجتمع غزالی سمنان",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 120.0,
                            detourDistanceMeters = 250.0,
                            detourDurationSeconds = 40.0,
                            distanceAlongRouteMeters = 215_000.0
                        )
                    ),
                    JourneyWaypoint(
                        id = "stop_shahrud_haft_khan",
                        name = "ایستگاه ترانزیتی هفت‌خوان شاهرود (۱۲۰ kW)",
                        coordinate = GeoCoordinate(36.4180, 54.9750),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 85.0,
                        stationDetails = CandidateStation(
                            stationId = "station_shahrud_haft_khan",
                            stationName = "ایستگاه ترانزیتی هفت‌خوان شاهرود",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 120.0,
                            detourDistanceMeters = 300.0,
                            detourDurationSeconds = 45.0,
                            distanceAlongRouteMeters = 410_000.0
                        )
                    ),
                    JourneyWaypoint(
                        id = "stop_sabzevar_sarbedaran",
                        name = "ایستگاه فوق‌سریع سربداران سبزوار (۱۲۰ kW)",
                        coordinate = GeoCoordinate(36.2180, 57.6750),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 85.0,
                        stationDetails = CandidateStation(
                            stationId = "station_sabzevar_sarbedaran",
                            stationName = "ایستگاه سربداران سبزوار",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 120.0,
                            detourDistanceMeters = 200.0,
                            detourDurationSeconds = 35.0,
                            distanceAlongRouteMeters = 660_000.0
                        )
                    )
                )
            ),

            // 4. Tehran to Tabriz (Northwest Expressway)
            PresetIranRoute(
                id = "preset_tehran_tabriz",
                title = "تهران به تبریز",
                subtitle = "آزادراه پیامبر اعظم (ص) • ۶۳۰ کیلومتر",
                distanceKm = 630.0,
                estimatedDurationMinutes = 400,
                chargingStopsSummary = "۲ توقف شارژ: مجتمع زیتون قزوین و غزال زنجان (۱۲۰ kW)",
                originCity = "تهران",
                destinationCity = "تبریز",
                originCoordinate = GeoCoordinate(35.6997, 51.3380),
                destinationCoordinate = GeoCoordinate(38.0255, 46.3685),
                corridorCoordinates = listOf(
                    GeoCoordinate(35.6997, 51.3380), // Tehran
                    GeoCoordinate(35.8412, 51.0185), // Karaj
                    GeoCoordinate(36.1950, 50.1500), // Zeytoon Qazvin (Stop 1)
                    GeoCoordinate(36.3120, 49.8520), // Takestan Aftab
                    GeoCoordinate(36.4500, 49.3500), // Abhar
                    GeoCoordinate(36.6210, 48.1500), // Ghazal Zanjan (Stop 2)
                    GeoCoordinate(37.1000, 47.9500), // Sarcham
                    GeoCoordinate(37.4200, 47.7150), // Mianeh Sahar
                    GeoCoordinate(37.7500, 46.9500), // Bostanabad
                    GeoCoordinate(38.0255, 46.3685)  // Tabriz El-Goli
                ),
                corridorElevations = listOf(1190.0, 1310.0, 1290.0, 1260.0, 1540.0, 1680.0, 1420.0, 1380.0, 1740.0, 1360.0),
                recommendedChargingStops = listOf(
                    JourneyWaypoint(
                        id = "stop_qazvin_zeytoon",
                        name = "ایستگاه مجتمع زیتون قزوین (۶۰ kW)",
                        coordinate = GeoCoordinate(36.1950, 50.1500),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 80.0,
                        stationDetails = CandidateStation(
                            stationId = "station_qazvin_zeytoon",
                            stationName = "ایستگاه بین‌راهی مجتمع زیتون قزوین",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 60.0,
                            detourDistanceMeters = 200.0,
                            detourDurationSeconds = 30.0,
                            distanceAlongRouteMeters = 120_000.0
                        )
                    ),
                    JourneyWaypoint(
                        id = "stop_zanjan_ghazal",
                        name = "ایستگاه فوق‌سریع مجتمع غزال زنجان (۱۲۰ kW)",
                        coordinate = GeoCoordinate(36.6210, 48.1500),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 85.0,
                        stationDetails = CandidateStation(
                            stationId = "station_zanjan_ghazal",
                            stationName = "ایستگاه فوق‌سریع مجتمع غزال زنجان",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 120.0,
                            detourDistanceMeters = 280.0,
                            detourDurationSeconds = 40.0,
                            distanceAlongRouteMeters = 330_000.0
                        )
                    )
                )
            ),

            // 5. Isfahan to Shiraz (Persian Heritage Corridor)
            PresetIranRoute(
                id = "preset_isfahan_shiraz",
                title = "اصفهان به شیراز",
                subtitle = "محور تاریخی پاسارگاد و پارسه • ۴۸۵ کیلومتر",
                distanceKm = 485.0,
                estimatedDurationMinutes = 320,
                chargingStopsSummary = "۱ توقف شارژ فوق‌سریع: شاهین شهرضا و آباده (۱۲۰ kW)",
                originCity = "اصفهان",
                destinationCity = "شیراز",
                originCoordinate = GeoCoordinate(32.6370, 51.6830),
                destinationCoordinate = GeoCoordinate(29.6450, 52.4920),
                corridorCoordinates = listOf(
                    GeoCoordinate(32.6370, 51.6830), // Isfahan
                    GeoCoordinate(32.5510, 51.6840), // Isfahan City Center
                    GeoCoordinate(32.0120, 51.8750), // Shahreza Shahin (Stop)
                    GeoCoordinate(31.5500, 52.3500), // Izadkhast Historic Fort
                    GeoCoordinate(31.1820, 52.6520), // Abadeh Omid
                    GeoCoordinate(30.6500, 53.0500), // Surmaq
                    GeoCoordinate(30.2000, 53.1800), // Pasargadae
                    GeoCoordinate(29.9350, 52.8850), // Persepolis
                    GeoCoordinate(29.6450, 52.4920)  // Shiraz Chamran
                ),
                corridorElevations = listOf(1570.0, 1550.0, 1820.0, 2150.0, 2010.0, 1920.0, 1850.0, 1620.0, 1540.0),
                recommendedChargingStops = listOf(
                    JourneyWaypoint(
                        id = "stop_shahreza_shahin",
                        name = "ایستگاه ترانزیت مجتمع شاهین شهرضا (۱۲۰ kW)",
                        coordinate = GeoCoordinate(32.0120, 51.8750),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 85.0,
                        stationDetails = CandidateStation(
                            stationId = "station_shahreza_shahin",
                            stationName = "ایستگاه ترانزیت مجتمع شاهین شهرضا",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 120.0,
                            detourDistanceMeters = 200.0,
                            detourDurationSeconds = 30.0,
                            distanceAlongRouteMeters = 85_000.0
                        )
                    )
                )
            ),

            // 6. Kish Island Coastal Circuit
            PresetIranRoute(
                id = "preset_kish_circuit",
                title = "رینگ ساحلی جزیره کیش",
                subtitle = "مسیر ساحلی هتل مارینا، غروب کیش و دامون • ۵۵ کیلومتر",
                distanceKm = 55.0,
                estimatedDurationMinutes = 60,
                chargingStopsSummary = "شارژ فوق‌سریع هتل مارینا پارک کیش (۱۲۰ kW)",
                originCity = "کیش (فرودگاه)",
                destinationCity = "کیش (مارینا پارک)",
                originCoordinate = GeoCoordinate(26.5260, 53.9800),
                destinationCoordinate = GeoCoordinate(26.5180, 54.0320),
                corridorCoordinates = listOf(
                    GeoCoordinate(26.5260, 53.9800), // Kish Airport
                    GeoCoordinate(26.5420, 53.9750), // Damoon Mall
                    GeoCoordinate(26.5580, 53.9850), // Mirmohana Coastal Park
                    GeoCoordinate(26.5400, 53.9200), // Greek Ship Sunset
                    GeoCoordinate(26.5050, 53.9500), // Southern Coast
                    GeoCoordinate(26.4950, 54.0100), // Coral Beach
                    GeoCoordinate(26.5180, 54.0320)  // Marina Park Hub
                ),
                corridorElevations = listOf(15.0, 5.0, 2.0, 1.0, 3.0, 2.0, 4.0),
                recommendedChargingStops = listOf(
                    JourneyWaypoint(
                        id = "stop_kish_marina",
                        name = "ایستگاه فوق‌سریع هتل مارینا پارک کیش (۱۲۰ kW)",
                        coordinate = GeoCoordinate(26.5180, 54.0320),
                        type = WaypointType.CHARGING_STOP,
                        targetDepartureSocPercent = 95.0,
                        stationDetails = CandidateStation(
                            stationId = "station_kish_marina",
                            stationName = "ایستگاه فوق‌سریع هتل مارینا پارک کیش",
                            compatibleConnectors = listOf("CCS2", "GB/T"),
                            maxPowerKw = 120.0,
                            detourDistanceMeters = 100.0,
                            detourDurationSeconds = 15.0,
                            distanceAlongRouteMeters = 54_000.0
                        )
                    )
                )
            )
        )
    }

    private fun calculateHaversineDistanceMeters(c1: GeoCoordinate, c2: GeoCoordinate): Double {
        val r = 6371000.0
        val lat1Rad = Math.toRadians(c1.latitude)
        val lat2Rad = Math.toRadians(c2.latitude)
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
