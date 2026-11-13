package com.evnavigator.iran.core.routing.engine

import kotlin.math.*

/**
 * Intelligent Traffic Safety & Speed Radar Alert Engine for Iranian Highways and Freeways.
 * Monitors real-time GPS coordinates against:
 * 1. Fixed gantries and automated average-speed enforcement radars.
 * 2. Highway police mobile radar traps and speed monitoring zones.
 * 3. Dangerous mountain hairpin passes (گردنه‌ها) requiring EV regenerative brake management.
 * 4. Runaway truck ramps and emergency heavy-vehicle check stations.
 * 5. High-incident sharp curves and tunnel approaches.
 */
object TrafficRadarEngine {

    enum class HazardType {
        SPEED_CAMERA_FIXED,
        AVERAGE_SPEED_ZONE_START,
        AVERAGE_SPEED_ZONE_END,
        POLICE_SPEED_TRAP,
        DANGEROUS_MOUNTAIN_PASS,
        HEAVY_VEHICLE_BRAKE_CHECK,
        TUNNEL_APPROACH,
        HIGH_ACCIDENT_CURVE
    }

    data class TrafficHazard(
        val id: String,
        val title: String,
        val type: HazardType,
        val latitude: Double,
        val longitude: Double,
        val highwayName: String,
        val speedLimitKmh: Int,
        val warningDistanceMeters: Double = 600.0,
        val persianAnnouncement: String
    )

    data class ActiveRadarAlert(
        val hazard: TrafficHazard,
        val distanceToHazardMeters: Double,
        val isOverspeeding: Boolean,
        val currentSpeedKmh: Double,
        val alertSeverity: AlertSeverity
    )

    enum class AlertSeverity {
        INFO,      // Approaching within 1000m, normal speed
        WARNING,   // Approaching within 600m, close to limit
        CRITICAL   // Approaching within 400m AND overspeeding! (Red alert)
    }

    private val verifiedIranianHazards: List<TrafficHazard> by lazy {
        listOf(
            // =========================================================================
            // FREEWAY 7: TEHRAN - QOM - KASHAN - ISFAHAN (Persian Gulf Freeway)
            // =========================================================================
            TrafficHazard(
                id = "cam_f7_km15",
                title = "دوربین ثبت تخلف سرعت کهریزک (کیلومتر ۱۵)",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 35.5120,
                longitude = 51.3650,
                highwayName = "آزادراه تهران - قم (خلیج فارس)",
                speedLimitKmh = 120,
                warningDistanceMeters = 700.0,
                persianAnnouncement = "دوربین ثبت تخلف سرعت ۱۲۰ کیلومتر در ۷۰۰ متری"
            ),
            TrafficHazard(
                id = "cam_f7_km30_toll",
                title = "گانتری کنترل هوشمند عوارضی و سرعت کیلومتر ۳۰",
                type = HazardType.AVERAGE_SPEED_ZONE_START,
                latitude = 35.4150,
                longitude = 51.1850,
                highwayName = "آزادراه تهران - قم",
                speedLimitKmh = 120,
                warningDistanceMeters = 800.0,
                persianAnnouncement = "شروع محدوده کنترل میانگین سرعت ۱۲۰ کیلومتر"
            ),
            TrafficHazard(
                id = "cam_f7_km45_mahtab",
                title = "دوربین عکاسی مجتمع مهتاب (کیلومتر ۴۵)",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 35.2580,
                longitude = 51.0250,
                highwayName = "آزادراه تهران - قم",
                speedLimitKmh = 120,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین کنترل سرعت در ۶۰۰ متری مجتمع مهتاب"
            ),
            TrafficHazard(
                id = "cam_f7_km70_maral",
                title = "سامانه نظارت تصویری مارال ستاره (کیلومتر ۷۰)",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 35.0850,
                longitude = 50.9420,
                highwayName = "آزادراه تهران - قم",
                speedLimitKmh = 120,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین ثبت تخلف ۱۲۰ کیلومتر در محدوده مارال ستاره"
            ),
            TrafficHazard(
                id = "cam_f7_km110_qom_bypass",
                title = "دوربین کمربندی جنوبی قم به سمت کاشان",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 34.6150,
                longitude = 50.8850,
                highwayName = "کمربندی بزرگراه امام علی قم",
                speedLimitKmh = 100,
                warningDistanceMeters = 500.0,
                persianAnnouncement = "دوربین ثبت تخلف ۱۰۰ کیلومتر در ۵۰۰ متری"
            ),
            TrafficHazard(
                id = "cam_f7_km160_mehromah",
                title = "گانتری کنترل سرعت مجتمع بین‌المللی مهر و ماه",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 34.4520,
                longitude = 51.1150,
                highwayName = "آزادراه قم - کاشان",
                speedLimitKmh = 120,
                warningDistanceMeters = 700.0,
                persianAnnouncement = "دوربین کنترل سرعت ۱۲۰ کیلومتر روبروی مجتمع مهر و ماه"
            ),
            TrafficHazard(
                id = "cam_f7_km210_kashan",
                title = "دوربین پایش پلاک و سرعت ورودی کاشان",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 34.0250,
                longitude = 51.4850,
                highwayName = "آزادراه کاشان - نطنز",
                speedLimitKmh = 120,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین کنترل سرعت ورودی کاشان در ۶۰۰ متری"
            ),
            TrafficHazard(
                id = "cam_f7_km320_morvarid",
                title = "دوربین ثبت تخلف پلیس راه میمه - اصفهان",
                type = HazardType.POLICE_SPEED_TRAP,
                latitude = 33.2150,
                longitude = 51.4250,
                highwayName = "محور میمه به اصفهان",
                speedLimitKmh = 110,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "محدوده کنترل سرعت پلیس راه میمه، سرعت مجاز ۱۱۰"
            ),
            TrafficHazard(
                id = "cam_f7_km390_shahinshahr",
                title = "گانتری پایش هوشمند عوارضی شاهین‌شهر به اصفهان",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 32.8850,
                longitude = 51.5850,
                highwayName = "آزادراه معلم (شمال اصفهان)",
                speedLimitKmh = 110,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین ثبت تخلف ۱۱۰ کیلومتر ورودی اصفهان"
            ),

            // =========================================================================
            // FREEWAY 2: TEHRAN - KARAJ - QAZVIN - ZANJAN - TABRIZ
            // =========================================================================
            TrafficHazard(
                id = "cam_f2_km10_garmdarreh",
                title = "دوربین ثبت تخلف سرعت گرمدره",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 35.7550,
                longitude = 51.0850,
                highwayName = "آزادراه تهران - کرج",
                speedLimitKmh = 100,
                warningDistanceMeters = 500.0,
                persianAnnouncement = "دوربین کنترل سرعت ۱۰۰ کیلومتر گرمدره"
            ),
            TrafficHazard(
                id = "cam_f2_km35_hesarak",
                title = "دوربین خروجی حصارک و کمالشهر",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 35.8450,
                longitude = 50.8850,
                highwayName = "آزادراه کرج - قزوین",
                speedLimitKmh = 110,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین کنترل سرعت ۱۱۰ کیلومتر در ۶۰۰ متری"
            ),
            TrafficHazard(
                id = "cam_f2_km65_abyek",
                title = "سامانه میانگین سرعت آبیک قزوین",
                type = HazardType.AVERAGE_SPEED_ZONE_START,
                latitude = 36.0250,
                longitude = 50.4850,
                highwayName = "آزادراه کرج - قزوین",
                speedLimitKmh = 120,
                warningDistanceMeters = 700.0,
                persianAnnouncement = "شروع محدوده کنترل میانگین سرعت ۱۲۰ کیلومتر آبیک"
            ),
            TrafficHazard(
                id = "cam_f2_km130_qazvin_bypass",
                title = "دوربین عوارضی کمربندی قزوین به زنجان",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 36.2850,
                longitude = 49.9850,
                highwayName = "آزادراه قزوین - زنجان",
                speedLimitKmh = 120,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین کنترل سرعت ۱۲۰ کیلومتر قزوین"
            ),
            TrafficHazard(
                id = "cam_f2_km175_takestan",
                title = "دوربین مجتمع رفاهی نگین تاکستان",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 36.0850,
                longitude = 49.6850,
                highwayName = "آزادراه قزوین - زنجان",
                speedLimitKmh = 120,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین ثبت تخلف سرعت ۱۲۰ کیلومتر تاکستان"
            ),
            TrafficHazard(
                id = "cam_f2_km290_zanjan",
                title = "سامانه هوشمند عوارضی شرق زنجان",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 36.6550,
                longitude = 48.5520,
                highwayName = "آزادراه زنجان - تبریز",
                speedLimitKmh = 120,
                warningDistanceMeters = 700.0,
                persianAnnouncement = "دوربین کنترل سرعت ۱۲۰ کیلومتر ورودی زنجان"
            ),
            TrafficHazard(
                id = "cam_f2_km420_bostanabad",
                title = "دوربین آزادراه پیامبر اعظم بستان‌آباد",
                type = HazardType.SPEED_CAMERA_FIXED,
                latitude = 37.8350,
                longitude = 46.8520,
                highwayName = "آزادراه زنجان - تبریز (پیامبر اعظم)",
                speedLimitKmh = 120,
                warningDistanceMeters = 600.0,
                persianAnnouncement = "دوربین ثبت تخلف ۱۲۰ کیلومتر بستان‌آباد"
            ),

            // =========================================================================
            // CHALUS & HARAZ MOUNTAIN PASSES & TUNNELS
            // =========================================================================
            TrafficHazard(
                id = "pass_chalus_kandovan",
                title = "گردنه کوهستانی و تونل البرز کندوان",
                type = HazardType.DANGEROUS_MOUNTAIN_PASS,
                latitude = 36.1550,
                longitude = 51.3120,
                highwayName = "جاده ۵۹ (کرج - چالوس)",
                speedLimitKmh = 60,
                warningDistanceMeters = 800.0,
                persianAnnouncement = "هشدار: گردنه مه‌گیر و سراشیبی شدید کندوان، سرعت مجاز ۶۰ کیلومتر و استفاده از ترمز کمکی ژنراتور"
            ),
            TrafficHazard(
                id = "tunnel_chalus_alborz_south",
                title = "ورودی جنوبی تونل طولانی البرز (آزادراه تهران - شمال)",
                type = HazardType.TUNNEL_APPROACH,
                latitude = 36.0850,
                longitude = 51.3250,
                highwayName = "آزادراه تهران - شمال",
                speedLimitKmh = 80,
                warningDistanceMeters = 700.0,
                persianAnnouncement = "ورود به تونل ۶ کیلومتری البرز، سرعت مجاز ۸۰ کیلومتر، چراغ‌ها روشن"
            ),
            TrafficHazard(
                id = "pass_haraz_emamzadeh_hashem",
                title = "گردنه و تونل امامزاده هاشم هراز",
                type = HazardType.DANGEROUS_MOUNTAIN_PASS,
                latitude = 35.7920,
                longitude = 51.9920,
                highwayName = "جاده ۷۷ (هراز)",
                speedLimitKmh = 60,
                warningDistanceMeters = 800.0,
                persianAnnouncement = "هشدار: گردنه مرتفع امامزاده هاشم، لغزندگی جاده و پیچ‌های متوالی"
            ),
            TrafficHazard(
                id = "pass_firuzkuh_gadook",
                title = "گردنه کوهستانی مه‌گیر گدوک سوادکوه",
                type = HazardType.DANGEROUS_MOUNTAIN_PASS,
                latitude = 35.8750,
                longitude = 52.9350,
                highwayName = "جاده ۷۹ (فیروزکوه)",
                speedLimitKmh = 70,
                warningDistanceMeters = 900.0,
                persianAnnouncement = "هشدار: گردنه مه‌گیر گدوک، کاهش دید افقی و لغزندگی مسیر"
            ),
            TrafficHazard(
                id = "pass_heiran_astara",
                title = "گردنه کوهستانی و مه‌آلود حیران (آستارا - اردبیل)",
                type = HazardType.DANGEROUS_MOUNTAIN_PASS,
                latitude = 38.3950,
                longitude = 48.6120,
                highwayName = "محور آستارا - اردبیل",
                speedLimitKmh = 50,
                warningDistanceMeters = 700.0,
                persianAnnouncement = "هشدار: گردنه پرپیچ و خم و مه‌گیر حیران، سرعت مجاز ۵۰ کیلومتر"
            ),
            TrafficHazard(
                id = "pass_asadabad_hamedan",
                title = "گردنه اسدآباد همدان (شاهراه غرب کشور)",
                type = HazardType.DANGEROUS_MOUNTAIN_PASS,
                latitude = 34.8250,
                longitude = 48.2150,
                highwayName = "محور همدان - کرمانشاه",
                speedLimitKmh = 60,
                warningDistanceMeters = 800.0,
                persianAnnouncement = "هشدار: شیب تند و پیچ‌های گردنه اسدآباد، تعویض دنده به حالت سنگین"
            )
        )
    }

    /**
     * Calculate Great-Circle distance using Haversine formula.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Evaluate live GPS fix and speed against all verified Iranian highway hazards.
     * Returns the highest-priority active alert (if within warning distance).
     */
    fun evaluateHazards(
        currentLatitude: Double,
        currentLongitude: Double,
        currentSpeedKmh: Double
    ): ActiveRadarAlert? {
        val candidates = verifiedIranianHazards.mapNotNull { hazard ->
            val dist = calculateDistanceMeters(currentLatitude, currentLongitude, hazard.latitude, hazard.longitude)
            if (dist <= hazard.warningDistanceMeters) {
                val isOverspeed = currentSpeedKmh > (hazard.speedLimitKmh + 3) // 3 km/h enforcement tolerance
                val severity = when {
                    dist <= 400.0 && isOverspeed -> AlertSeverity.CRITICAL
                    dist <= 500.0 && currentSpeedKmh >= hazard.speedLimitKmh -> AlertSeverity.WARNING
                    else -> AlertSeverity.INFO
                }
                ActiveRadarAlert(
                    hazard = hazard,
                    distanceToHazardMeters = dist,
                    isOverspeeding = isOverspeed,
                    currentSpeedKmh = currentSpeedKmh,
                    alertSeverity = severity
                )
            } else {
                null
            }
        }

        // Return closest critical alert first, otherwise closest warning/info
        return candidates.minByOrNull { alert ->
            val priorityMultiplier = when (alert.alertSeverity) {
                AlertSeverity.CRITICAL -> 0.3
                AlertSeverity.WARNING -> 0.7
                AlertSeverity.INFO -> 1.0
            }
            alert.distanceToHazardMeters * priorityMultiplier
        }
    }
}
