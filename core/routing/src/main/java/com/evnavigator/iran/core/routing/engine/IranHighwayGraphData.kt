package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.EdgeDirection
import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.RoadClass
import com.evnavigator.iran.core.routing.model.RoadEdge
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.RoadNode
import com.evnavigator.iran.core.routing.model.TurnRestriction
import com.evnavigator.iran.domain.model.GeoCoordinate

data class CorridorNode(
    val id: String,
    val name: String,
    val coordinate: GeoCoordinate,
    val elevationMeters: Double? = null
)

data class CorridorEdge(
    val from: String,
    val to: String,
    val roadName: String,
    val distanceMeters: Double,
    val speedLimitKmh: Int,
    val detailedPath: List<GeoCoordinate>,
    val direction: EdgeDirection,
    val access: RoadAccess = RoadAccess.PUBLIC,
    val roadClass: RoadClass = RoadClass.MOTORWAY,
    val surface: String = "asphalt",
    val hasToll: Boolean = false,
    val isTunnel: Boolean = false,
    val isBridge: Boolean = false,
    val prohibitedTurns: List<String> = emptyList(),
    val mandatoryTurns: List<String> = emptyList(),
    val osmWayId: Long? = null
)

/**
 * National Iranian highway corridor baseline skeleton graph.
 * Used as the offline fallback skeleton when no regional OSM PBF/XML/EVRN dataset is loaded
 * in [com.evnavigator.iran.core.routing.importer.OsmDatasetManager].
 *
 * Every edge declares an explicit [EdgeDirection] (no implicit default direction).
 */
object IranOfflineRoadCorridors {

    val nodes = listOf(
        // Tehran Metropolitan Key Hubs
        CorridorNode("tehran_azadi", "میدان آزادی تهران", GeoCoordinate(35.6997, 51.3380), 1180.0),
        CorridorNode("tehran_hemmat_west", "بزرگراه همت / باکری", GeoCoordinate(35.7440, 51.2950), 1340.0),
        CorridorNode("tehran_hemmat_chamran", "بزرگراه همت / چمران", GeoCoordinate(35.7490, 51.3850), 1390.0),
        CorridorNode("tehran_hemmat_modarres", "بزرگراه همت / مدرس", GeoCoordinate(35.7520, 51.4250), 1410.0),
        CorridorNode("tehran_hemmat_east", "بزرگراه همت / پاسداران", GeoCoordinate(35.7550, 51.4600), 1440.0),
        CorridorNode("tehran_hakim_chamran", "بزرگراه حکیم / برج میلاد", GeoCoordinate(35.7420, 51.3780), 1365.0),
        CorridorNode("tehran_niayesh_chamran", "بزرگراه هاشمی رفسنجانی (نیایش) / چمران", GeoCoordinate(35.7780, 51.3950), 1520.0),
        CorridorNode("tehran_niayesh_valiasr", "بزرگراه نیایش / ولیعصر", GeoCoordinate(35.7800, 51.4100), 1545.0),
        CorridorNode("tehran_sadr_modarres", "بزرگراه صدر / مدرس", GeoCoordinate(35.7830, 51.4200), 1560.0),
        CorridorNode("tehran_towhid_navab", "تونل توحید / نواب", GeoCoordinate(35.7000, 51.3780), 1195.0),
        CorridorNode("tehran_azadegan_khalij", "بزرگراه آزادگان / آزادراه خلیج فارس", GeoCoordinate(35.5800, 51.3350), 1065.0),
        CorridorNode("tehran_toll_freeway5", "عوارضی آزادراه تهران - قم", GeoCoordinate(35.5350, 51.3400), 1040.0),
        CorridorNode("tehran_pars_east", "تهرانپارس / خروجی هراز و فیروزکوه", GeoCoordinate(35.7300, 51.5400), 1460.0),

        // Freeway 5 (Tehran - Qom - Kashan - Isfahan)
        CorridorNode("ikia_airport", "فرودگاه بین‌المللی امام خمینی", GeoCoordinate(35.4200, 51.1800), 1005.0),
        CorridorNode("hasanabad", "حسن‌آباد فشافویه", GeoCoordinate(35.3700, 51.2400), 980.0),
        CorridorNode("howz_soltan", "دریاچه حوض سلطان", GeoCoordinate(35.0300, 51.0100), 820.0),
        CorridorNode("qom_toll", "عوارضی شمالی قم", GeoCoordinate(34.7200, 50.9300), 890.0),
        CorridorNode("qom_mehromah", "مجتمع بین‌راهی مهر و ماه قم (سوپرشارژر)", GeoCoordinate(34.6850, 50.9200), 910.0),
        CorridorNode("qom_center", "میدان ۷۲ تن قم (ورودی شهر)", GeoCoordinate(34.6401, 50.8764), 930.0),
        CorridorNode("qom_south", "تقاطع خروجی کاشان / سلفچگان", GeoCoordinate(34.5800, 50.9300), 960.0),
        CorridorNode("maral_setareh", "مجتمع مارال ستاره (شارژر)", GeoCoordinate(34.4200, 51.1400), 940.0),
        CorridorNode("kashan_toll", "عوارضی کاشان", GeoCoordinate(33.9850, 51.4400), 980.0),
        CorridorNode("natanz_junction", "تقاطع نطنز / بادرود", GeoCoordinate(33.5200, 51.8900), 1610.0),
        CorridorNode("morcheh_khort", "مورچه‌خورت", GeoCoordinate(33.0900, 51.4800), 1680.0),
        CorridorNode("shahin_shahr", "شاهین‌شهر", GeoCoordinate(32.8600, 51.5600), 1595.0),
        CorridorNode("isfahan_kaveh", "ترمینال کاوه اصفهان", GeoCoordinate(32.7200, 51.6700), 1580.0),
        CorridorNode("isfahan_center", "سی‌وسه‌پل / میدان بزرگمهر اصفهان", GeoCoordinate(32.6500, 51.6800), 1575.0),
        CorridorNode("isfahan_soffeh", "بزرگراه شهدای صفه / هزارجریب اصفهان", GeoCoordinate(32.6370, 51.6830), 1610.0),

        // Freeway 2 (Tehran - Karaj - Qazvin - Zanjan - Tabriz)
        CorridorNode("karaj_bridge", "پل فردیس کرج", GeoCoordinate(35.7800, 51.0100), 1310.0),
        CorridorNode("hashtgerd", "هشتگرد", GeoCoordinate(35.9600, 50.6800), 1270.0),
        CorridorNode("abyek", "آبیک", GeoCoordinate(36.0500, 50.5300), 1250.0),
        CorridorNode("qazvin_east", "عوارضی قزوین", GeoCoordinate(36.2700, 50.0400), 1290.0),
        CorridorNode("qazvin_north", "تقاطع رشت / قزوین", GeoCoordinate(36.3100, 49.9800), 1330.0),
        CorridorNode("takestan", "تاکستان", GeoCoordinate(36.0700, 49.7000), 1265.0),
        CorridorNode("abhar", "ابهر / خرمدره", GeoCoordinate(36.2400, 49.1900), 1540.0),
        CorridorNode("zanjan_east", "عوارضی زنجان", GeoCoordinate(36.6700, 48.5100), 1660.0),
        CorridorNode("bostanabad", "بستان‌آباد", GeoCoordinate(37.8400, 46.8300), 1740.0),
        CorridorNode("tabriz_clock", "میدان ساعت / ایل‌گلی تبریز", GeoCoordinate(38.0700, 46.3000), 1360.0),

        // Freeway 1 & Road 49 (Qazvin - Rasht)
        CorridorNode("lowshan", "لوشان", GeoCoordinate(36.6300, 49.5200), 550.0),
        CorridorNode("manjil", "توربین‌های بادی منجیل", GeoCoordinate(36.7400, 49.4100), 390.0),
        CorridorNode("roodbar", "رودبار", GeoCoordinate(36.8100, 49.4200), 250.0),
        CorridorNode("imamzadeh_hashem", "امامزاده هاشم گیلان", GeoCoordinate(37.0300, 49.6300), 110.0),
        CorridorNode("rasht_center", "میدان شهرداری رشت", GeoCoordinate(37.2800, 49.5900), -5.0),

        // Road 59 (Chalus Road)
        CorridorNode("asara", "آسارا", GeoCoordinate(36.0300, 51.2400), 1860.0),
        CorridorNode("kandovan_tunnel", "تونل کندوان", GeoCoordinate(36.1500, 51.3100), 2680.0),
        CorridorNode("marzanabad", "مرزن‌آباد", GeoCoordinate(36.4500, 51.2900), 520.0),
        CorridorNode("chalus_coast", "ساحل چالوس / نوشهر", GeoCoordinate(36.6500, 51.4200), -20.0),

        // Road 77 (Haraz Road)
        CorridorNode("rudehen", "رودهن / بومهن", GeoCoordinate(35.7300, 51.9100), 1850.0),
        CorridorNode("polur", "پلور (دماوند)", GeoCoordinate(35.8500, 52.0600), 2270.0),
        CorridorNode("amol", "آمل", GeoCoordinate(36.4700, 52.3500), 76.0),
        CorridorNode("mahmudabad", "محمودآباد خزر", GeoCoordinate(36.6300, 52.2600), -18.0),

        // Road 44 (Tehran - Mashhad)
        CorridorNode("garmsar", "گرمسار", GeoCoordinate(35.2200, 52.3400), 856.0),
        CorridorNode("semnan_toll", "عوارضی سمنان", GeoCoordinate(35.5800, 53.3900), 1130.0),
        CorridorNode("damghan", "دامغان", GeoCoordinate(36.1700, 54.3400), 1170.0),
        CorridorNode("shahroud", "شاهرود", GeoCoordinate(36.4200, 54.9700), 1345.0),
        CorridorNode("sabzevar", "سبزوار", GeoCoordinate(36.2100, 57.6800), 977.0),
        CorridorNode("nishapur", "نیشابور", GeoCoordinate(36.2100, 58.7900), 1250.0),
        CorridorNode("mashhad_shrine", "مشهد مقدس (حرم مطهر)", GeoCoordinate(36.2970, 59.6060), 985.0),

        // Road 65 (Isfahan - Shiraz)
        CorridorNode("shahreza", "شهرضا", GeoCoordinate(32.0100, 51.8700), 1820.0),
        CorridorNode("abadeh", "آباده", GeoCoordinate(31.1800, 52.6500), 2030.0),
        CorridorNode("safashahr", "صفاشهر", GeoCoordinate(30.6100, 53.2000), 2250.0),
        CorridorNode("marvdasht", "مرودشت / تخت جمشید", GeoCoordinate(29.8700, 52.8100), 1620.0),
        CorridorNode("shiraz_quran", "دروازه قرآن / حافظیه شیراز", GeoCoordinate(29.6350, 52.5500), 1540.0)
    )

    val edges = listOf(
        // Tehran Urban Highway Grid Edges
        makeEdge("tehran_azadi", "tehran_hemmat_west", "بزرگراه شهید باکری", 6500.0, 90, listOf(
            GeoCoordinate(35.6997, 51.3380),
            GeoCoordinate(35.7150, 51.3150),
            GeoCoordinate(35.7300, 51.3050),
            GeoCoordinate(35.7440, 51.2950)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_hemmat_west", "tehran_hemmat_chamran", "بزرگراه شهید همت", 8800.0, 100, listOf(
            GeoCoordinate(35.7440, 51.2950),
            GeoCoordinate(35.7450, 51.3150),
            GeoCoordinate(35.7470, 51.3450),
            GeoCoordinate(35.7480, 51.3650),
            GeoCoordinate(35.7490, 51.3850)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_hemmat_chamran", "tehran_hemmat_modarres", "بزرگراه شهید همت", 4200.0, 100, listOf(
            GeoCoordinate(35.7490, 51.3850),
            GeoCoordinate(35.7500, 51.3950),
            GeoCoordinate(35.7510, 51.4150),
            GeoCoordinate(35.7520, 51.4250)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_hemmat_modarres", "tehran_hemmat_east", "بزرگراه شهید همت", 3800.0, 100, listOf(
            GeoCoordinate(35.7520, 51.4250),
            GeoCoordinate(35.7530, 51.4500),
            GeoCoordinate(35.7550, 51.4600)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_hemmat_east", "tehran_pars_east", "بزرگراه شهید زین‌الدین / باقری", 7600.0, 90, listOf(
            GeoCoordinate(35.7550, 51.4600),
            GeoCoordinate(35.7450, 51.5000),
            GeoCoordinate(35.7300, 51.5400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_hemmat_chamran", "tehran_hakim_chamran", "بزرگراه شهید چمران", 1200.0, 90, listOf(
            GeoCoordinate(35.7490, 51.3850),
            GeoCoordinate(35.7450, 51.3800),
            GeoCoordinate(35.7420, 51.3780)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_hakim_chamran", "tehran_towhid_navab", "بزرگراه شهید چمران و تونل توحید", 5100.0, 80, listOf(
            GeoCoordinate(35.7420, 51.3780),
            GeoCoordinate(35.7250, 51.3800),
            GeoCoordinate(35.7100, 51.3790),
            GeoCoordinate(35.7000, 51.3780)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK, isTunnel = true),
        makeEdge("tehran_niayesh_chamran", "tehran_hemmat_chamran", "بزرگراه شهید چمران", 3400.0, 90, listOf(
            GeoCoordinate(35.7780, 51.3950),
            GeoCoordinate(35.7650, 51.3900),
            GeoCoordinate(35.7490, 51.3850)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_niayesh_chamran", "tehran_niayesh_valiasr", "بزرگراه هاشمی رفسنجانی (نیایش)", 1800.0, 90, listOf(
            GeoCoordinate(35.7780, 51.3950),
            GeoCoordinate(35.7790, 51.4050),
            GeoCoordinate(35.7800, 51.4100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_niayesh_valiasr", "tehran_sadr_modarres", "تونل نیایش و بزرگراه صدر", 2100.0, 90, listOf(
            GeoCoordinate(35.7800, 51.4100),
            GeoCoordinate(35.7820, 51.4150),
            GeoCoordinate(35.7830, 51.4200)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK, isTunnel = true, isBridge = true),
        makeEdge("tehran_sadr_modarres", "tehran_hemmat_modarres", "بزرگراه آیت‌الله مدرس", 4100.0, 90, listOf(
            GeoCoordinate(35.7830, 51.4200),
            GeoCoordinate(35.7680, 51.4230),
            GeoCoordinate(35.7520, 51.4250)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_azadi", "tehran_towhid_navab", "خیابان آزادی", 3900.0, 60, listOf(
            GeoCoordinate(35.6997, 51.3380),
            GeoCoordinate(35.7000, 51.3580),
            GeoCoordinate(35.7000, 51.3780)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY),
        makeEdge("tehran_towhid_navab", "tehran_azadegan_khalij", "بزرگراه شهید نواب صفوی و شهید تندگویان", 14500.0, 80, listOf(
            GeoCoordinate(35.7000, 51.3780),
            GeoCoordinate(35.6700, 51.3790),
            GeoCoordinate(35.6400, 51.3750),
            GeoCoordinate(35.6100, 51.3550),
            GeoCoordinate(35.5800, 51.3350)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("tehran_azadegan_khalij", "tehran_toll_freeway5", "آزادراه خلیج فارس (آزادراه ۵)", 5200.0, 110, listOf(
            GeoCoordinate(35.5800, 51.3350),
            GeoCoordinate(35.5550, 51.3380),
            GeoCoordinate(35.5350, 51.3400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),

        // Freeway 5 (Tehran - Qom - Kashan - Isfahan)
        makeEdge("tehran_toll_freeway5", "ikia_airport", "آزادراه تهران - قم (آزادراه ۵)", 21000.0, 120, listOf(
            GeoCoordinate(35.5350, 51.3400),
            GeoCoordinate(35.4800, 51.2800),
            GeoCoordinate(35.4200, 51.1800)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("ikia_airport", "hasanabad", "آزادراه تهران - قم", 8500.0, 120, listOf(
            GeoCoordinate(35.4200, 51.1800),
            GeoCoordinate(35.3900, 51.2100),
            GeoCoordinate(35.3700, 51.2400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("hasanabad", "howz_soltan", "آزادراه خلیج فارس", 46000.0, 120, listOf(
            GeoCoordinate(35.3700, 51.2400),
            GeoCoordinate(35.2500, 51.1600),
            GeoCoordinate(35.1200, 51.0800),
            GeoCoordinate(35.0300, 51.0100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("howz_soltan", "qom_toll", "آزادراه خلیج فارس", 36000.0, 120, listOf(
            GeoCoordinate(35.0300, 51.0100),
            GeoCoordinate(34.8900, 50.9700),
            GeoCoordinate(34.7200, 50.9300)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("qom_toll", "qom_center", "بزرگراه امام علی قم", 9800.0, 90, listOf(
            GeoCoordinate(34.7200, 50.9300),
            GeoCoordinate(34.6800, 50.9000),
            GeoCoordinate(34.6401, 50.8764)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("qom_center", "qom_south", "بلوار الغدیر / کمربندی جنوبی قم", 8500.0, 80, listOf(
            GeoCoordinate(34.6401, 50.8764),
            GeoCoordinate(34.6000, 50.9000),
            GeoCoordinate(34.5800, 50.9300)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY),
        makeEdge("qom_center", "qom_mehromah", "کنارگذر آزادراه ۵ به سمت مهر و ماه", 6400.0, 90, listOf(
            GeoCoordinate(34.6401, 50.8764),
            GeoCoordinate(34.6600, 50.9000),
            GeoCoordinate(34.6850, 50.9200)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("isfahan_center", "isfahan_soffeh", "خیابان چهارباغ بالا / هزارجریب", 2400.0, 60, listOf(
            GeoCoordinate(32.6500, 51.6800),
            GeoCoordinate(32.6430, 51.6815),
            GeoCoordinate(32.6370, 51.6830)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY),
        makeEdge("qom_toll", "qom_mehromah", "کنارگذر آزادراه ۵ (مجتمع مهر و ماه)", 4500.0, 100, listOf(
            GeoCoordinate(34.7200, 50.9300),
            GeoCoordinate(34.7000, 50.9250),
            GeoCoordinate(34.6850, 50.9200)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("qom_mehromah", "qom_south", "کمربندی شرقی قم", 12500.0, 100, listOf(
            GeoCoordinate(34.6850, 50.9200),
            GeoCoordinate(34.6300, 50.9250),
            GeoCoordinate(34.5800, 50.9300)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("qom_south", "maral_setareh", "آزادراه قم - کاشان (آزادراه ۵)", 26000.0, 120, listOf(
            GeoCoordinate(34.5800, 50.9300),
            GeoCoordinate(34.5000, 51.0300),
            GeoCoordinate(34.4200, 51.1400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("maral_setareh", "kashan_toll", "آزادراه قم - کاشان", 58000.0, 120, listOf(
            GeoCoordinate(34.4200, 51.1400),
            GeoCoordinate(34.2500, 51.3100),
            GeoCoordinate(34.0300, 51.3600),
            GeoCoordinate(33.9850, 51.4400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("kashan_toll", "natanz_junction", "آزادراه کاشان - اصفهان", 69000.0, 120, listOf(
            GeoCoordinate(33.9850, 51.4400),
            GeoCoordinate(33.7800, 51.6800),
            GeoCoordinate(33.5200, 51.8900)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("natanz_junction", "morcheh_khort", "آزادراه نطنز - اصفهان", 65000.0, 120, listOf(
            GeoCoordinate(33.5200, 51.8900),
            GeoCoordinate(33.3100, 51.6500),
            GeoCoordinate(33.0900, 51.4800)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("morcheh_khort", "shahin_shahr", "آزادراه معلم (ورودی شمالی اصفهان)", 27000.0, 110, listOf(
            GeoCoordinate(33.0900, 51.4800),
            GeoCoordinate(32.9700, 51.5200),
            GeoCoordinate(32.8600, 51.5600)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("shahin_shahr", "isfahan_kaveh", "بزرگراه معلم و بلوار کاوه", 17500.0, 90, listOf(
            GeoCoordinate(32.8600, 51.5600),
            GeoCoordinate(32.7900, 51.6200),
            GeoCoordinate(32.7200, 51.6700)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("isfahan_kaveh", "isfahan_center", "خیابان کاوه و چهارباغ اصفهان", 8500.0, 60, listOf(
            GeoCoordinate(32.7200, 51.6700),
            GeoCoordinate(32.6850, 51.6750),
            GeoCoordinate(32.6500, 51.6800)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY),

        // Freeway 2 (Tehran - Karaj - Qazvin - Zanjan - Tabriz)
        makeEdge("tehran_azadi", "karaj_bridge", "آزادراه تهران - کرج (آزادراه ۲)", 35000.0, 110, listOf(
            GeoCoordinate(35.6997, 51.3380),
            GeoCoordinate(35.7300, 51.2200),
            GeoCoordinate(35.7400, 51.0900),
            GeoCoordinate(35.7800, 51.0100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, isBridge = true),
        makeEdge("karaj_bridge", "hashtgerd", "آزادراه کرج - قزوین", 36000.0, 120, listOf(
            GeoCoordinate(35.7800, 51.0100),
            GeoCoordinate(35.8400, 50.9200),
            GeoCoordinate(35.9600, 50.6800)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("hashtgerd", "abyek", "آزادراه کرج - قزوین", 19000.0, 120, listOf(
            GeoCoordinate(35.9600, 50.6800),
            GeoCoordinate(36.0500, 50.5300)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("abyek", "qazvin_east", "آزادراه کرج - قزوین", 49000.0, 120, listOf(
            GeoCoordinate(36.0500, 50.5300),
            GeoCoordinate(36.1700, 50.2800),
            GeoCoordinate(36.2700, 50.0400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("qazvin_east", "qazvin_north", "کمربندی شمالی قزوین", 7500.0, 100, listOf(
            GeoCoordinate(36.2700, 50.0400),
            GeoCoordinate(36.3100, 49.9800)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("qazvin_north", "takestan", "آزادراه قزوین - زنجان (آزادراه ۲)", 35000.0, 120, listOf(
            GeoCoordinate(36.3100, 49.9800),
            GeoCoordinate(36.1900, 49.8400),
            GeoCoordinate(36.0700, 49.7000)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("takestan", "abhar", "آزادراه قزوین - زنجان", 52000.0, 120, listOf(
            GeoCoordinate(36.0700, 49.7000),
            GeoCoordinate(36.1600, 49.4400),
            GeoCoordinate(36.2400, 49.1900)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("abhar", "zanjan_east", "آزادراه ابهر - زنجان", 72000.0, 120, listOf(
            GeoCoordinate(36.2400, 49.1900),
            GeoCoordinate(36.4300, 48.7900),
            GeoCoordinate(36.6700, 48.5100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("zanjan_east", "bostanabad", "آزادراه پیامبر اعظم (زنجان - تبریز)", 195000.0, 120, listOf(
            GeoCoordinate(36.6700, 48.5100),
            GeoCoordinate(37.1500, 47.7800),
            GeoCoordinate(37.5500, 47.2500),
            GeoCoordinate(37.8400, 46.8300)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("bostanabad", "tabriz_clock", "آزادراه تبریز و بلوار شهید باکری", 52000.0, 110, listOf(
            GeoCoordinate(37.8400, 46.8300),
            GeoCoordinate(38.0100, 46.5200),
            GeoCoordinate(38.0400, 46.4000),
            GeoCoordinate(38.0700, 46.3000)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),

        // Freeway 1 & Road 49 (Qazvin - Rasht)
        makeEdge("qazvin_north", "lowshan", "آزادراه قزوین - رشت (آزادراه ۱)", 58000.0, 110, listOf(
            GeoCoordinate(36.3100, 49.9800),
            GeoCoordinate(36.4600, 49.6800),
            GeoCoordinate(36.6300, 49.5200)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("lowshan", "manjil", "آزادراه قزوین - رشت (منطقه بادگیر منجیل)", 17000.0, 100, listOf(
            GeoCoordinate(36.6300, 49.5200),
            GeoCoordinate(36.7400, 49.4100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("manjil", "roodbar", "آزادراه قزوین - رشت (رودبار)", 11000.0, 90, listOf(
            GeoCoordinate(36.7400, 49.4100),
            GeoCoordinate(36.8100, 49.4200)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, isTunnel = true),
        makeEdge("roodbar", "imamzadeh_hashem", "آزادراه رودبار - رشت", 34000.0, 100, listOf(
            GeoCoordinate(36.8100, 49.4200),
            GeoCoordinate(36.9000, 49.4900),
            GeoCoordinate(37.0300, 49.6300)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY),
        makeEdge("imamzadeh_hashem", "rasht_center", "بزرگراه خلیج فارس ورودی رشت", 32000.0, 90, listOf(
            GeoCoordinate(37.0300, 49.6300),
            GeoCoordinate(37.1500, 49.6100),
            GeoCoordinate(37.2400, 49.5800),
            GeoCoordinate(37.2800, 49.5900)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),

        // Road 59 (Chalus Road)
        makeEdge("tehran_hemmat_west", "asara", "آزادراه تهران - شمال و جاده چالوس", 41000.0, 80, listOf(
            GeoCoordinate(35.7440, 51.2950),
            GeoCoordinate(35.7900, 51.2700),
            GeoCoordinate(35.8800, 51.2900),
            GeoCoordinate(35.9800, 51.3500),
            GeoCoordinate(36.0300, 51.2400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY, hasToll = true, isTunnel = true),
        makeEdge("asara", "kandovan_tunnel", "جاده کرج - چالوس (گردنه کندوان)", 18000.0, 70, listOf(
            GeoCoordinate(36.0300, 51.2400),
            GeoCoordinate(36.1200, 51.3200),
            GeoCoordinate(36.1500, 51.3100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY, isTunnel = true),
        makeEdge("kandovan_tunnel", "marzanabad", "جاده چالوس (پایین‌دست البرز)", 36000.0, 75, listOf(
            GeoCoordinate(36.1500, 51.3100),
            GeoCoordinate(36.2100, 51.3400),
            GeoCoordinate(36.2900, 51.3200),
            GeoCoordinate(36.4500, 51.2900)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY),
        makeEdge("marzanabad", "chalus_coast", "قطعه چهارم آزادراه تهران - شمال", 24000.0, 90, listOf(
            GeoCoordinate(36.4500, 51.2900),
            GeoCoordinate(36.5600, 51.3600),
            GeoCoordinate(36.6500, 51.4200)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),

        // Road 77 (Haraz Road)
        makeEdge("tehran_pars_east", "rudehen", "آزادراه تهران - پردیس", 38000.0, 100, listOf(
            GeoCoordinate(35.7300, 51.5400),
            GeoCoordinate(35.7400, 51.6900),
            GeoCoordinate(35.7400, 51.7800),
            GeoCoordinate(35.7300, 51.9100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),
        makeEdge("rudehen", "polur", "جاده هراز (جاده ۷۷)", 22000.0, 70, listOf(
            GeoCoordinate(35.7300, 51.9100),
            GeoCoordinate(35.7500, 51.9700),
            GeoCoordinate(35.7800, 52.0300),
            GeoCoordinate(35.8500, 52.0600)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY, isTunnel = true),
        makeEdge("polur", "amol", "جاده هراز (کوهستان البرز)", 72000.0, 70, listOf(
            GeoCoordinate(35.8500, 52.0600),
            GeoCoordinate(35.8800, 52.1700),
            GeoCoordinate(35.9100, 52.2300),
            GeoCoordinate(36.0300, 52.2600),
            GeoCoordinate(36.4700, 52.3500)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.PRIMARY, isTunnel = true),
        makeEdge("amol", "mahmudabad", "بزرگراه آمل - محمودآباد", 21000.0, 90, listOf(
            GeoCoordinate(36.4700, 52.3500),
            GeoCoordinate(36.6300, 52.2600)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),

        // Road 44 (Tehran - Mashhad)
        makeEdge("tehran_pars_east", "garmsar", "بزرگراه امام رضا (جاده ۴۴)", 88000.0, 110, listOf(
            GeoCoordinate(35.7300, 51.5400),
            GeoCoordinate(35.6100, 51.5200),
            GeoCoordinate(35.4800, 51.6800),
            GeoCoordinate(35.3400, 52.0700),
            GeoCoordinate(35.2200, 52.3400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("garmsar", "semnan_toll", "بزرگراه گرمسار - سمنان", 112000.0, 110, listOf(
            GeoCoordinate(35.2200, 52.3400),
            GeoCoordinate(35.2500, 52.4900),
            GeoCoordinate(35.4600, 53.2100),
            GeoCoordinate(35.5800, 53.3900)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("semnan_toll", "damghan", "بزرگراه سمنان - دامغان", 118000.0, 110, listOf(
            GeoCoordinate(35.5800, 53.3900),
            GeoCoordinate(36.1700, 54.3400)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("damghan", "shahroud", "بزرگراه دامغان - شاهرود", 68000.0, 110, listOf(
            GeoCoordinate(36.1700, 54.3400),
            GeoCoordinate(36.4200, 54.9700)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("shahroud", "sabzevar", "بزرگراه شاهرود - سبزوار (جاده کویری)", 255000.0, 110, listOf(
            GeoCoordinate(36.4200, 54.9700),
            GeoCoordinate(36.4100, 55.6500),
            GeoCoordinate(36.3600, 56.3700),
            GeoCoordinate(36.2100, 56.8800),
            GeoCoordinate(36.2100, 57.6800)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("sabzevar", "nishapur", "بزرگراه سبزوار - نیشابور", 114000.0, 110, listOf(
            GeoCoordinate(36.2100, 57.6800),
            GeoCoordinate(36.2100, 58.7900)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("nishapur", "mashhad_shrine", "بزرگراه نیشابور - مشهد", 126000.0, 110, listOf(
            GeoCoordinate(36.2100, 58.7900),
            GeoCoordinate(36.1000, 59.0600),
            GeoCoordinate(36.3200, 59.4800),
            GeoCoordinate(36.2970, 59.6060)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.MOTORWAY, hasToll = true),

        // Road 65 (Isfahan - Shiraz)
        makeEdge("isfahan_center", "shahreza", "بزرگراه خلیج فارس اصفهان (جاده ۶۵)", 79000.0, 110, listOf(
            GeoCoordinate(32.6500, 51.6800),
            GeoCoordinate(32.6100, 51.6600),
            GeoCoordinate(32.4900, 51.7500),
            GeoCoordinate(32.0100, 51.8700)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("shahreza", "abadeh", "بزرگراه شهرضا - آباده", 132000.0, 110, listOf(
            GeoCoordinate(32.0100, 51.8700),
            GeoCoordinate(31.6700, 52.0500),
            GeoCoordinate(31.1800, 52.6500)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("abadeh", "safashahr", "بزرگراه آباده - صفاشهر", 88000.0, 110, listOf(
            GeoCoordinate(31.1800, 52.6500),
            GeoCoordinate(31.0300, 52.8300),
            GeoCoordinate(30.6100, 53.2000)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("safashahr", "marvdasht", "بزرگراه صفاشهر - سعادت‌شهر - مرودشت", 116000.0, 110, listOf(
            GeoCoordinate(30.6100, 53.2000),
            GeoCoordinate(30.2000, 53.1800),
            GeoCoordinate(30.0800, 53.1400),
            GeoCoordinate(29.9500, 52.9200),
            GeoCoordinate(29.8700, 52.8100)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK),
        makeEdge("marvdasht", "shiraz_quran", "بزرگراه مرودشت - شیراز (ورودی دروازه قرآن)", 48000.0, 100, listOf(
            GeoCoordinate(29.8700, 52.8100),
            GeoCoordinate(29.7400, 52.6800),
            GeoCoordinate(29.6350, 52.5500)
        ), direction = EdgeDirection.BIDIRECTIONAL, roadClass = RoadClass.TRUNK)
    )

    private fun makeEdge(
        from: String,
        to: String,
        roadName: String,
        dist: Double,
        speedLimit: Int,
        path: List<GeoCoordinate>,
        direction: EdgeDirection,
        access: RoadAccess = RoadAccess.PUBLIC,
        roadClass: RoadClass = RoadClass.MOTORWAY,
        hasToll: Boolean = false,
        isTunnel: Boolean = false,
        isBridge: Boolean = false,
        prohibitedTurns: List<String> = emptyList(),
        mandatoryTurns: List<String> = emptyList(),
        osmWayId: Long? = null
    ): CorridorEdge {
        return CorridorEdge(
            from = from,
            to = to,
            roadName = roadName,
            distanceMeters = dist,
            speedLimitKmh = speedLimit,
            detailedPath = path,
            direction = direction,
            access = access,
            roadClass = roadClass,
            surface = "asphalt",
            hasToll = hasToll,
            isTunnel = isTunnel,
            isBridge = isBridge,
            prohibitedTurns = prohibitedTurns,
            mandatoryTurns = mandatoryTurns,
            osmWayId = osmWayId
        )
    }

    /**
     * Converts the national Iranian road corridor network into a topological [RoadGraph]
     * with String node/edge IDs, adjacency lists, node elevations, and turn restrictions.
     */
    fun toRoadGraph(): RoadGraph {
        val roadNodes = mutableMapOf<String, RoadNode>()
        nodes.forEach { cNode ->
            roadNodes[cNode.id] = RoadNode(
                id = cNode.id,
                coordinate = cNode.coordinate,
                elevationMeters = cNode.elevationMeters
            )
        }

        val roadEdges = mutableMapOf<String, RoadEdge>()
        val adjacency = mutableMapOf<String, MutableList<String>>()
        val restrictions = mutableListOf<TurnRestriction>()
        val edgeRestrictionsById = mutableMapOf<String, MutableList<TurnRestriction>>()

        edges.forEachIndexed { idx, cEdge ->
            val fromEdgeId = "edge_${idx + 1}"
            val viaNodeStr = cEdge.to

            for (prohibitedTargetStr in cEdge.prohibitedTurns) {
                val targetEdgeIdx = edges.indexOfFirst {
                    (it.from == viaNodeStr && it.to == prohibitedTargetStr) ||
                        (it.to == viaNodeStr && it.from == prohibitedTargetStr && it.direction != EdgeDirection.FORWARD)
                }
                if (targetEdgeIdx >= 0) {
                    val toEdgeId = "edge_${targetEdgeIdx + 1}"
                    val pRestr = TurnRestriction.Prohibited(fromEdgeId, toEdgeId)
                    val vRestr = TurnRestriction.ViaNode(fromEdgeId, viaNodeStr, toEdgeId)
                    restrictions.add(pRestr)
                    restrictions.add(vRestr)
                    edgeRestrictionsById.getOrPut(fromEdgeId) { mutableListOf() }.add(pRestr)
                }
            }

            for (mandatoryTargetStr in cEdge.mandatoryTurns) {
                val targetEdgeIdx = edges.indexOfFirst {
                    (it.from == viaNodeStr && it.to == mandatoryTargetStr) ||
                        (it.to == viaNodeStr && it.from == mandatoryTargetStr && it.direction != EdgeDirection.FORWARD)
                }
                if (targetEdgeIdx >= 0) {
                    val toEdgeId = "edge_${targetEdgeIdx + 1}"
                    val mRestr = TurnRestriction.Mandatory(fromEdgeId, toEdgeId)
                    restrictions.add(mRestr)
                    edgeRestrictionsById.getOrPut(fromEdgeId) { mutableListOf() }.add(mRestr)
                }
            }
        }

        edges.forEachIndexed { idx, cEdge ->
            val edgeId = "edge_${idx + 1}"
            if (!roadNodes.containsKey(cEdge.from) || !roadNodes.containsKey(cEdge.to)) return@forEachIndexed
            val dist = com.evnavigator.iran.domain.model.Distance.fromMeters(cEdge.distanceMeters)
            val spd = com.evnavigator.iran.domain.model.Speed.ofKilometersPerHour(cEdge.speedLimitKmh.toDouble())
            val meta = mapOf<String, Any>(
                "name" to cEdge.roadName,
                "surface" to cEdge.surface,
                "hasToll" to cEdge.hasToll,
                "isTunnel" to cEdge.isTunnel,
                "isBridge" to cEdge.isBridge,
                "direction" to cEdge.direction.name
            )

            if (cEdge.direction == EdgeDirection.FORWARD || cEdge.direction == EdgeDirection.BIDIRECTIONAL) {
                roadEdges[edgeId] = RoadEdge(
                    id = edgeId,
                    startNode = cEdge.from,
                    endNode = cEdge.to,
                    distance = dist,
                    speed = spd,
                    roadClass = cEdge.roadClass,
                    access = cEdge.access,
                    restrictions = edgeRestrictionsById[edgeId] ?: emptyList(),
                    metadata = meta
                )
                adjacency.getOrPut(cEdge.from) { mutableListOf() }.add(edgeId)
            }
            if (cEdge.direction == EdgeDirection.REVERSE || cEdge.direction == EdgeDirection.BIDIRECTIONAL) {
                val revEdgeId = if (cEdge.direction == EdgeDirection.REVERSE) edgeId else "${edgeId}_rev"
                roadEdges[revEdgeId] = RoadEdge(
                    id = revEdgeId,
                    startNode = cEdge.to,
                    endNode = cEdge.from,
                    distance = dist,
                    speed = spd,
                    roadClass = cEdge.roadClass,
                    access = cEdge.access,
                    restrictions = emptyList(),
                    metadata = meta
                )
                adjacency.getOrPut(cEdge.to) { mutableListOf() }.add(revEdgeId)
            }
        }

        return RoadGraph(
            nodes = roadNodes,
            edges = roadEdges,
            nodeToEdges = adjacency,
            turnRestrictions = restrictions
        )
    }
}
