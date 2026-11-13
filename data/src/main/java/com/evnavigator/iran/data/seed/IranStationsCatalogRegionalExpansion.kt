package com.evnavigator.iran.data.seed

import com.evnavigator.iran.core.charging.ValidationStatus
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity

/**
 * V12.5 Extracted Collaborator: Regional Expansion Catalog (Regions 9–12, Stations 112–195).
 * Maintains audited station-specific URN references and calibrated offline confidence bounds.
 */
internal object IranStationsCatalogRegionalExpansion {

    fun populateExpansionRegions(
        addStation: (
            id: String,
            externalId: String,
            canonicalName: String,
            operatorName: String,
            latitude: Double,
            longitude: Double,
            address: String,
            city: String,
            province: String,
            is247: Boolean,
            openingHours: String,
            validationStatus: ValidationStatus,
            confidence: Double,
            source: String,
            sourceUrl: String?,
            portsBuilder: (stationId: String, status: String, conf: Double, sourceRef: String) -> List<ChargingPortEntity>
        ) -> Unit,
        createHpcDcPorts: (stationId: String, status: String, conf: Double, sourceRef: String, maxKw: Double) -> List<ChargingPortEntity>,
        createDualFastDcPorts: (stationId: String, status: String, conf: Double, sourceRef: String, dcKw: Double) -> List<ChargingPortEntity>,
        createCityAcDcPorts: (stationId: String, status: String, conf: Double, sourceRef: String, dcKw: Double, acKw: Double) -> List<ChargingPortEntity>
    ) {
        // =========================================================================
        // REGION 9: GREATER TEHRAN METROPOLITAN ULTRA INFILL (Stations 112 - 136)
        // =========================================================================
        addStation("station_tehran_niavaran_palace", "MAPNA_112", "ایستگاه مجموعه فرهنگی تاریخی نیاوران", "میراث فرهنگی / مپنا", 35.8235, 51.4725, "تهران، میدان باهنر (نیاوران)، خیابان شهید باهنر، پارکینگ کاخ نیاوران", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_112:station_tehran_niavaran_palace") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_saadatabad_kaj", "MAPNA_113", "ایستگاه میدان کاج سعادت‌آباد", "شهرداری منطقه ۲ تهران", 35.7820, 51.3740, "تهران، سعادت‌آباد، تقاطع بلوار سرو و میدان کاج، جنب مجتمع تجاری مروارید", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_113:station_tehran_saadatabad_kaj") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_jordan_africa", "MAPNA_114", "ایستگاه بلوار نلسون ماندلا (جردن)", "شهرداری منطقه ۳ تهران", 35.7650, 51.4190, "تهران، بلوار نلسون ماندلا (آفریقا)، نبش خیابان گلشهر، پارکینگ تجاری", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_114:station_tehran_jordan_africa") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_shahrak_gharb_golestan", "MAPNA_115", "ایستگاه مجتمع تجاری گلستان شهرک غرب", "مرکز خرید گلستان / مپنا", 35.7530, 51.3650, "تهران، شهرک قدس (غرب)، خیابان ایران‌زمین، پارکینگ شرقی پاساژ گلستان", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_115:station_tehran_shahrak_gharb_golestan") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_vanak_square", "MAPNA_116", "ایستگاه بزرگراه کردستان - میدان ونک", "شهرداری منطقه ۳ تهران", 35.7570, 51.4080, "تهران، میدان ونک، ابتدای بزرگراه شهید حقانی، جنب پایانه تاکسیرانی ونک", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_116:station_tehran_vanak_square") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_mehrabad_t1_t2", "MAPNA_117", "ایستگاه فرودگاه مهرآباد ترمینال ۱ و ۲", "شرکت فرودگاه‌های کشور / مپنا", 35.6910, 51.3120, "تهران، فرودگاه بین‌المللی مهرآباد، روبروی ترمینال ۲، طبقه همکف پارکینگ طبقاتی ۱", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_117:station_tehran_mehrabad_t1_t2") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_tehran_mehrabad_t4_t6", "MAPNA_118", "ایستگاه فرودگاه مهرآباد ترمینال ۴ و ۶", "مپنا (مکو)", 35.6880, 51.3150, "تهران، فرودگاه مهرآباد، بلوار معراج، محوطه اختصاصی تاکسی‌های برقی ترمینال ۴", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_118:station_tehran_mehrabad_t4_t6") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_tehran_ikia_t1_super", "MAPNA_119", "ایستگاه سوپرشارژر فرودگاه بین‌المللی امام خمینی", "شهر فرودگاهی امام خمینی / مپنا", 35.4160, 51.1550, "کیلومتر ۳۰ آزادراه تهران - قم، شهر فرودگاهی امام خمینی، ترمینال شماره ۱", "تهران", "تهران",
            true, "24/7",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_119:station_tehran_ikia_t1_super") { id, status, conf, ref ->
            createHpcDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_tehran_ikia_salam", "MAPNA_120", "ایستگاه پایانه بین‌المللی سلام فرودگاه امام", "مپنا / بنیاد", 35.4220, 51.1710, "شهر فرودگاهی امام خمینی (ره)، بلوار ورودی ترمینال سلام، پارکینگ روباز ۲", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_120:station_tehran_ikia_salam") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_tehran_parsian_azadi_hotel", "MAPNA_121", "ایستگاه هتل پارسیان آزادی تهران", "هتل‌های پارسیان", 35.7910, 51.3850, "تهران، بزرگراه شهید چمران، تقاطع یادگار امام، پارکینگ تشریفات هتل پارسیان آزادی", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_121:station_tehran_parsian_azadi_hotel") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_espinas_palace", "MAPNA_122", "ایستگاه گرند هتل اسپیناس پالاس", "اسپیناس هتلز / مپنا", 35.7970, 51.3650, "تهران، سعادت‌آباد، میدان بهرود، خیابان عابدی، هتل بین‌المللی اسپیناس پالاس", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_122:station_tehran_espinas_palace") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_azadi_stadium_west", "MAPNA_123", "ایستگاه هتل المپیک و استادیوم آزادی", "مجموعه ورزشی آزادی / مپنا", 35.7310, 51.2720, "تهران، جنب درب غربی استادیوم آزادی، بلوار دهکده المپیک، پارکینگ هتل المپیک", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_123:station_tehran_azadi_stadium_west") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_eram_park", "MAPNA_124", "ایستگاه پارک ارم و اتودرام غرب", "پارک ارم / مپنا", 35.7180, 51.2890, "تهران، کیلومتر ۴ اتوبان تهران - کرج، جنب ایستگاه مترو ارم سبز، پارکینگ لونا ۱", "تهران", "تهران",
            true, "24/7",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_124:station_tehran_eram_park") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_tabiat_bridge_waterfire", "MAPNA_125", "ایستگاه پل طبیعت و پارک آب و آتش", "شهرداری اراضی عباس‌آباد", 35.7550, 51.4220, "تهران، خیابان نلسون ماندلا، چهارراه جهان کودک، ورودی غربی پارک آب و آتش", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_125:station_tehran_tabiat_bridge_waterfire") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_beheshti_university", "MAPNA_126", "ایستگاه پارک علم و فناوری دانشگاه شهید بهشتی", "دانشگاه شهید بهشتی / مپنا", 35.7980, 51.3920, "تهران، اوین، میدان شهید شهریاری، پردیس دانشگاه شهید بهشتی", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_126:station_tehran_beheshti_university") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_sharif_university", "MAPNA_127", "ایستگاه برج فناوری دانشگاه صنعتی شریف", "دانشگاه صنعتی شریف", 35.7030, 51.3520, "تهران، خیابان آزادی، بلوار شهید اکبری، مجتمع نوآوری و فناوری شریف", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_127:station_tehran_sharif_university") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_university_enghelab", "MAPNA_128", "ایستگاه پردیس مرکزی دانشگاه تهران", "دانشگاه تهران", 35.7010, 51.3910, "تهران، میدان انقلاب اسلامی، خیابان ۱۶ آذر، درب ورودی غربی دانشگاه تهران", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_128:station_tehran_university_enghelab") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_khavaran_terminal", "MAPNA_129", "ایستگاه پایانه مسافربری خاوران", "شهرداری منطقه ۱۵ تهران", 35.6510, 51.4720, "تهران، بزرگراه امام رضا (ع)، میدان بسیج، پایانه ترانزیتی شرق و جنوب‌شرق", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_129:station_tehran_khavaran_terminal") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_tehran_afsariyeh_basij", "MAPNA_130", "ایستگاه سه راه افسریه (بزرگراه بسیج)", "شهرداری منطقه ۱۴ تهران", 35.6590, 51.4880, "تهران، میدان کلاهدوز، بزرگراه شهید بسیج، نرسیده به ۲۰ متری افسریه", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_130:station_tehran_afsariyeh_basij") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_chitgar_forest_park", "MAPNA_131", "ایستگاه پارک جنگلی چیتگر فاز غربی", "شهرداری منطقه ۲۲ تهران", 35.7310, 51.1980, "تهران، آزادراه تهران - کرج، بعد از پارک جنگلی چیتگر، ورودی فاز غربی", "تهران", "تهران",
            true, "24/7",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_131:station_tehran_chitgar_forest_park") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_farahzad_junction", "MAPNA_132", "ایستگاه بزرگراه یادگار امام و فرحزاد", "شهرداری منطقه ۲ تهران", 35.7890, 51.3580, "تهران، بزرگراه یادگار امام شمال، نرسیده به تقاطع فرحزاد، پارکینگ شهرداری", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_132:station_tehran_farahzad_junction") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_book_garden_north", "MAPNA_133", "ایستگاه باغ کتاب تهران ورودی شمالی", "شهرداری اراضی عباس‌آباد", 35.7530, 51.4320, "تهران، میدان ونک، بزرگراه حقانی مسیر غرب به شرق، خروجی کتابخانه ملی و باغ کتاب", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_133:station_tehran_book_garden_north") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_tehran_shahr_e_rey_shrine", "MAPNA_134", "ایستگاه آستان مقدس حضرت عبدالعظیم حسنی (ع)", "شهرداری منطقه ۲۰ تهران", 35.5880, 51.4390, "شهر ری، میدان حرم، خیابان مدرس، پارکینگ جنوبی آستان حضرت عبدالعظیم", "شهر ری", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_134:station_tehran_shahr_e_rey_shrine") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_bazar_gol_mahallati", "MAPNA_135", "ایستگاه بازار بزرگ گل شهید محلاتی", "شهرداری منطقه ۱۴ تهران", 35.6720, 51.4890, "تهران، بزرگراه شهید محلاتی، تقاطع خیابان نبرد جنوبی، روبروی بازار گل", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_135:station_tehran_bazar_gol_mahallati") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tehran_mapna_mobility_hub", "MAPNA_136", "ایستگاه مرکزی توسعه برقی‌سازی مپنا", "گروه مپنا (شرکت مکو)", 35.7250, 51.3410, "تهران، بلوار استاد معین، خیابان شهید دستغیب، هاب تحقیقاتی برقی‌سازی مپنا", "تهران", "تهران",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_136:station_tehran_mapna_mobility_hub") { id, status, conf, ref ->
            createHpcDcPorts(id, status, conf, ref, 120.0)
        }

        // =========================================================================
        // REGION 10: ALBORZ & CASPIAN MOUNTAIN PASS CORRIDORS (Stations 137 - 160)
        // =========================================================================
        addStation("station_alborz_karaj_azimieh", "MAPNA_137", "ایستگاه میدان اسبی عظیمیه کرج", "شهرداری کرج / مپنا", 35.8350, 50.9980, "کرج، عظیمیه، میدان اسبی (میدان بهارستان)، جنب بوستان ایران‌زمین", "کرج", "البرز",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_alborz_karaj_jahanshahr", "MAPNA_138", "ایستگاه میدان شهید مدنی جهانشهر کرج", "شهرداری منطقه ۸ کرج", 35.8280, 50.9750, "کرج، جهانشهر، میدان شهید مدنی، خیابان استانداری، پارکینگ شهرداری", "کرج", "البرز",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_alborz_hashtgerd_newcity", "MAPNA_139", "ایستگاه شهر جدید هشتگرد (مهستان)", "شرکت عمران شهر جدید مهستان", 35.9750, 50.7280, "آزادراه کرج - قزوین، ورودی شهر جدید مهستان (هشتگرد)، جنب ایستگاه مترو شهید فخری‌زاده", "هشتگرد", "البرز",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_firuzkuh_gadook_pass", "MAPNA_140", "ایستگاه گردنه گدوک (محور فیروزکوه)", "راهداری استان مازندران", 35.8650, 52.9280, "جاده فیروزکوه به سوادکوه، گردنه مه‌گیر گدوک، مجتمع رفاهی آش و دوغ گدوک", "فیروزکوه", "مازندران",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_haraz_polur_basecamp", "MAPNA_141", "ایستگاه پلور و پایگاه کوهنوردی دماوند (هراز)", "فدراسیون کوهنوردی / مپنا", 35.8520, 52.0520, "محور هراز، کیلومتر ۶۵، شهر پلور، روبروی دوراهی رینه و بارگاه دماوند", "پلور", "مازندران",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_haraz_emamzadeh_hashem_pass", "MAPNA_142", "ایستگاه گردنه امامزاده هاشم جاده هراز", "راهداری استان تهران", 35.7850, 51.9880, "محور هراز، گردنه امامزاده هاشم، روبروی آستان مقدس امامزاده هاشم (ع)", "دماوند", "تهران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_haraz_amol_entrance", "MAPNA_143", "ایستگاه مجتمع رفاهی شمال آمل (محور هراز)", "شهرداری آمل", 36.4350, 52.3380, "ورودی جنوبی آمل از محور هراز، کیلومتر ۵ آمل، مجتمع اقامتی پردیس", "آمل", "مازندران",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_chalus_kandovan_north", "MAPNA_144", "ایستگاه خروجی شمالی تونل کندوان (چالوس)", "راهداری مازندران / مپنا", 36.1680, 51.3150, "جاده چالوس، خروجی شمالی تونل البرز و کندوان، مجتمع کوهستان مازندران", "چالوس", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_chalus_siahbisheh", "MAPNA_145", "ایستگاه سد تلمبه‌ذخیره‌ای سیاه‌بیشه", "وزارت نیرو / مپنا", 36.2180, 51.3280, "جاده کرج - چالوس، منطقه کوهستانی سیاه‌بیشه، روبروی تاسیسات نیروگاهی", "سیاه‌بیشه", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_chalus_coastal_boulevard", "MAPNA_146", "ایستگاه بلوار ساحلی رادیودریا چالوس", "شهرداری چالوس", 36.6620, 51.4250, "چالوس، انتهای بلوار رادیودریا، پارکینگ تفرجگاه ساحلی رادیودریا", "چالوس", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_noshahr_port_waterfront", "MAPNA_147", "ایستگاه بندر تجاری و اسکله ساحلی نوشهر", "اداره کل بنادر و دریانوردی", 36.6510, 51.5050, "نوشهر، خیابان فردوسی، روبروی ورودی اصلی بندر و گمرک نوشهر", "نوشهر", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_mahmudabad_darya", "MAPNA_148", "ایستگاه ساحلی شهر توریستی محمودآباد", "شهرداری محمودآباد", 36.6350, 52.2680, "محمودآباد، بلوار امام خمینی (ره)، ساحل دارایی، جنب پارک ساحلی ملت", "محمودآباد", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_babolsar_shilat", "MAPNA_149", "ایستگاه توریستی پارکینگ‌های ساحلی بابلسر", "شهرداری بابلسر", 36.7080, 52.6450, "بابلسر، بلوار شهید علیزاده، پارکینگ شماره ۱ ساحلی، روبروی پلاژ شهرداری", "بابلسر", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_babol_noshirvani", "MAPNA_150", "ایستگاه دانشگاه صنعتی نوشیروانی بابل", "دانشگاه صنعتی نوشیروانی", 36.5510, 52.6850, "بابل، خیابان دکتر شریعتی، جنب پردیس اصلی دانشگاه صنعتی نوشیروانی", "بابل", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_sari_paytakht", "MAPNA_151", "ایستگاه مجتمع توریستی پایتخت ساری", "شهرداری ساری / مپنا", 36.5680, 53.0620, "ساری، بلوار دانشگاه، سه راه جویبار، مجتمع گردشگری تجاری پایتخت", "ساری", "مازندران",
            true, "24/7",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_151:station_sari_paytakht") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_gorgan_alangdarreh", "MAPNA_152", "ایستگاه پارک جنگلی النگدره گرگان", "شهرداری گرگان", 36.7920, 54.4450, "گرگان، انتهای بلوار ناهارخوران، ورودی پارک جنگلی حفاظت‌شده النگدره", "گرگان", "گلستان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_bandar_torkaman_pier", "MAPNA_153", "ایستگاه اسکله و قایقرانی بندر ترکمن", "سازمان بنادر / شهرداری", 36.9050, 54.0410, "بندر ترکمن، بلوار پاسداران، انتهای جاده اسکله، محوطه ترانزیت به آشوراده", "بندر ترکمن", "گلستان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_gonbad_kavus_tower", "MAPNA_154", "ایستگاه میراث جهانی برج قابوس گنبد کاووس", "شهرداری گنبد کاووس", 37.2580, 55.1680, "گنبد کاووس، خیابان امام خمینی جنوبی، محوطه میراث جهانی برج میل قابوس", "گنبد کاووس", "گلستان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_lahijan_sheitan_kuh", "MAPNA_155", "ایستگاه استخر و بام شیطان‌کوه لاهیجان", "شهرداری لاهیجان", 37.2050, 50.0120, "لاهیجان، بلوار شهید مطهری، روبروی استخر لاهیجان و تله‌کابین احرار", "لاهیجان", "گیلان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_langarud_chamkhaleh", "MAPNA_156", "ایستگاه ساحل ماسه‌ای چمخاله لنگرود", "شهرداری چاف و چمخاله", 37.2280, 50.2150, "لنگرود، شهر ساحلی چمخاله، انتهای بلوار شهید باهنر، ورودی پلاژ مرکزی", "لنگرود", "گیلان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_ramsar_telecabine", "MAPNA_157", "ایستگاه تله‌کابین و دهکده گردشگری رامسر", "مجموعه شهر سبز رامسر", 36.9120, 50.5980, "کیلومتر ۵ جاده رامسر به رودسر، مجموعه توریستی تله‌کابین رامسر (بام سبز)", "رامسر", "مازندران",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_tonekabon_shiroud", "MAPNA_158", "ایستگاه شهرداری تنکابن (شهسوار)", "شهرداری تنکابن", 36.8180, 50.8750, "تنکابن، بلوار ساحلی امام رضا (ع)، جنب میدان جانبازان و رودخانه چشمه‌کیله", "تنکابن", "مازندران",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_talesh_gisoom_forest", "MAPNA_159", "ایستگاه پارک جنگلی و ساحل گیسوم تالش", "میراث فرهنگی تالش", 37.6450, 49.0120, "محور انزلی به تالش، کیلومتر ۱۰ تالش، تونل جنگلی و پارک ماسه‌ای گیسوم", "تالش", "گیلان",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_heiran_pass_telecabin", "MAPNA_160", "ایستگاه گردنه توریستی حیران (آستارا - اردبیل)", "دهکده گردشگری حیران", 38.3880, 48.6050, "محور آستارا - اردبیل، گردنه کوهستانی حیران، جنب مجموعه تله‌کابین حیران", "آستارا", "گیلان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }

        // =========================================================================
        // REGION 11: TEHRAN - SEMNAN - MASHHAD SILK ROAD EXPRESSWAY (Stations 161 - 175)
        // =========================================================================
        addStation("station_garmsar_aftab_oasis", "MAPNA_161", "ایستگاه مجتمع رفاهی آفتاب شرق گرمسار", "راهداری استان سمنان", 35.2150, 52.3450, "بزرگراه گرمسار - سمنان، کیلومتر ۲۰ گرمسار، مجتمع خدماتی رفاهی آفتاب", "گرمسار", "سمنان",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_sorkheh_expressway", "MAPNA_162", "ایستگاه بین‌راهی سرخه (محور حرم تا حرم)", "راهداری سمنان", 35.4650, 53.2150, "شاهراه سمنان - فیروزکوه، تقاطع ورودی سرخه، مجتمع رفاهی زائران حرم", "سرخه", "سمنان",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_damghan_chehel_dokhtar", "MAPNA_163", "ایستگاه تاریخانه و چهل‌دختران دامغان", "شهرداری دامغان", 36.1650, 54.3480, "دامغان، بلوار شهید بهشتی، میدان تاریخانه، جنب مسجد تاریخی تاریخانه", "دامغان", "سمنان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_shahrud_bastam_heritage", "MAPNA_164", "ایستگاه تاریخی بایزید بسطامی شاهرود", "میراث فرهنگی شاهرود", 36.4820, 55.0120, "شاهرود، شهر تاریخی بسطام، میدان امامزاده محمد، پارکینگ مجموعه بایزید بسطامی", "بسطام", "سمنان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_sabzevar_terminal_sarbedaran", "MAPNA_165", "ایستگاه یادمان سربداران سبزوار", "شهرداری سبزوار / مپنا", 36.2180, 57.6820, "سبزوار، میدان سربداران، بلوار ابریشم، پایانه مسافربری سبزوار", "سبزوار", "خراسان رضوی",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_neyshabur_attar_garden", "MAPNA_166", "ایستگاه باغ عطار نیشابوری", "شهرداری نیشابور", 36.1750, 58.8350, "نیشابور، انتهای بلوار عرفان، پارکینگ مقبره شیخ فریدالدین عطار نیشابوری", "نیشابور", "خراسان رضوی",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_mashhad_shrine_babolreza", "MAPNA_167", "ایستگاه آستان قدس باب‌الرضا (ع) مشهد", "آستان قدس رضوی / مپنا", 36.2850, 59.6150, "مشهد مقدس، میدان بیت‌المقدس (فلکه آب)، پارکینگ شماره ۱ حرم مطهر رضوی", "مشهد", "خراسان رضوی",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_167:station_mashhad_shrine_babolreza") { id, status, conf, ref ->
            createHpcDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_mashhad_shrine_tabarsi", "MAPNA_168", "ایستگاه آستان قدس ورودی طبرسی مشهد", "شهرداری مشهد", 36.2920, 59.6200, "مشهد مقدس، تقاطع بلوار طبرسی و بلوار شارستان رضوی، پارکینگ شماره ۳ حرم", "مشهد", "خراسان رضوی",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_168:station_mashhad_shrine_tabarsi") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_mashhad_torqabeh_chalidreh", "MAPNA_169", "ایستگاه منطقه نمونه گردشگری چالیدره طرقبه", "سازمان گردشگری چالیدره", 36.3150, 59.3520, "مشهد، طرقبه، میدان عنبران، کیلومتر ۳ جاده نغندر، دریاچه سد چالیدره", "طرقبه", "خراسان رضوی",
            true, "24/7",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_169:station_mashhad_torqabeh_chalidreh") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_mashhad_shandiz_padideh", "MAPNA_170", "ایستگاه شهر رویایی پدیده شاندیز", "شهرداری شاندیز / پدیده", 36.3980, 59.3120, "مشهد، شهر توریستی شاندیز، بلوار میرزا جوادآقا تهرانی، پارکینگ مرکزی پدیده", "شاندیز", "خراسان رضوی",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_170:station_mashhad_shandiz_padideh") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_birjand_shooshoud", "MAPNA_171", "ایستگاه مجتمع باغ اکبریه بیرجند", "شهرداری بیرجند", 32.8650, 59.2150, "بیرجند، انتهای خیابان معلم، میدان موزه، میراث جهانی باغ اکبریه", "بیرجند", "خراسان جنوبی",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_bojnord_baba_aman", "MAPNA_172", "ایستگاه پارک توریستی باباامان بجنورد", "شهرداری بجنورد", 37.4920, 57.4120, "بجنورد، کیلومتر ۱۰ محور بجنورد - مشهد، پارک ملی و تفرجگاه باباامان", "بجنورد", "خراسان شمالی",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_torbat_heydariyeh_zaer", "MAPNA_173", "ایستگاه مجتمع رفاهی زائر تربت حیدریه", "راهداری خراسان", 35.2750, 59.2180, "بزرگراه گناباد - تربت حیدریه - مشهد، ورودی شهر تربت، مجتمع رفاهی زائر", "تربت حیدریه", "خراسان رضوی",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_gonabad_qanat_qasabeh", "MAPNA_174", "ایستگاه قنات جهانی قصبه گناباد", "میراث فرهنگی گناباد", 34.3510, 58.6850, "گناباد، بلوار قصبه، ورودی سایت میراث جهانی کهن‌ترین قنات جهان (قصبه)", "گناباد", "خراسان رضوی",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_tabas_bagh_golshan", "MAPNA_175", "ایستگاه کویری باغ گلشن طبس", "شهرداری طبس", 33.5950, 56.9250, "طبس، بلوار شهید منتظری، میدان باغ گلشن، پارکینگ مجموعه تاریخی باغ گلشن", "طبس", "خراسان جنوبی",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }

        // =========================================================================
        // REGION 12: ISFAHAN, FARS & SOUTHERN COASTAL EXPANSION (Stations 176 - 195)
        // =========================================================================
        addStation("station_kashan_fin_garden", "MAPNA_176", "ایستگاه میراث جهانی باغ فین کاشان", "میراث فرهنگی کاشان", 33.9480, 51.3720, "کاشان، خیابان امیرکبیر، انتهای خیابان، محوطه میراث جهانی باغ فین کاشان", "کاشان", "اصفهان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_kashan_san_ray_km190", "MAPNA_177", "ایستگاه سوپرشارژر سان‌ری کیلومتر ۱۹۰ آزادراه کاشان", "مپنا (مکو)", 33.8150, 51.5820, "آزادراه قم - کاشان - نطنز، کیلومتر ۱۹۰، مجتمع رفاهی بین‌المللی سان‌ری", "کاشان", "اصفهان",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createHpcDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_natanz_abyaneh_junction", "MAPNA_178", "ایستگاه دوراهی روستای کهن ابیانه (نطنز)", "بخشداری برزک و نطنز", 33.5850, 51.8150, "آزادراه کاشان - اصفهان، خروجی ابیانه و نطنز، مجتمع خدماتی رفاهی نگین ابیانه", "نطنز", "اصفهان",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_isfahan_naghsh_e_jahan", "MAPNA_179", "ایستگاه میدان جهانی نقش جهان اصفهان", "شهرداری منطقه ۳ اصفهان", 32.6580, 51.6780, "اصفهان، خیابان استانداری، پشت عمارت عالی‌قاپو، پارکینگ حکیم نقش جهان", "اصفهان", "اصفهان",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_179:station_isfahan_naghsh_e_jahan") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_isfahan_nazhvan_park", "MAPNA_180", "ایستگاه پارک جنگلی ناژوان اصفهان", "شهرداری منطقه ۹ اصفهان", 32.6450, 51.6020, "اصفهان، بزرگراه شهید حبیب‌اللهی، خیابان الفت، پارکینگ باغ پرندگان ناژوان", "اصفهان", "اصفهان",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_180:station_isfahan_nazhvan_park") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_isfahan_sofeh_mountain", "MAPNA_181", "ایستگاه تله‌کابین کوه صفه اصفهان", "شهرداری اصفهان / مپنا", 32.5950, 51.6580, "اصفهان، بزرگراه شهدای صفه، پایانه تله‌کابین و بولینگ کوه صفه", "اصفهان", "اصفهان",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_181:station_isfahan_sofeh_mountain") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_shahin_shahr_daneshjo", "MAPNA_182", "ایستگاه شاهین‌شهر (ورودی شمالی اصفهان)", "شهرداری شاهین‌شهر", 32.8650, 51.5620, "شاهین‌شهر، بلوار امام خمینی، میدان دانشجو، پارکینگ پارک ملت شاهین‌شهر", "شاهین‌شهر", "اصفهان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_najafabad_sheikh_bahaee", "MAPNA_183", "ایستگاه ارگ شیخ بهایی نجف‌آباد", "شهرداری نجف‌آباد", 32.6350, 51.3650, "نجف‌آباد، خیابان شریعتی، جنب مجموعه تاریخی هفت‌برج خارون (ارگ شیخ بهایی)", "نجف‌آباد", "اصفهان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_shiraz_persepolis_gate", "MAPNA_184", "ایستگاه پایگاه جهانی تخت جمشید (پرسپولیس)", "میراث جهانی پارسه / مپنا", 29.9350, 52.8880, "مرودشت، کیلومتر ۱۰ جاده تخت جمشید، پارکینگ اصلی ورودی کاخ آپادانا", "مرودشت", "فارس",
            true, "24/7",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_184:station_shiraz_persepolis_gate") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_shiraz_eram_garden", "MAPNA_185", "ایستگاه میراث جهانی باغ ارم شیراز", "دانشگاه شیراز", 29.6350, 52.5250, "شیراز، بلوار ارم، میدان ارم، روبروی درب ورودی باغ گیاه‌شناسی ارم", "شیراز", "فارس",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_185:station_shiraz_eram_garden") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_shiraz_maaliabad_commercial", "MAPNA_186", "ایستگاه بلوار معالی‌آباد شیراز", "شهرداری منطقه ۶ شیراز", 29.6880, 52.4750, "شیراز، بلوار دکتر شریعتی (معالی‌آباد)، نبش خیابان پزشکان، مجتمع تجاری آفتاب", "شیراز", "فارس",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_186:station_shiraz_maaliabad_commercial") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_shiraz_sadra_newcity", "MAPNA_187", "ایستگاه شهر جدید صدرا شیراز", "شرکت عمران شهر جدید صدرا", 29.8150, 52.3650, "شیراز، شهر جدید صدرا، بلوار پاسداران، روبروی مجتمع تجاری مروارید صدرا", "صدرا", "فارس",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_187:station_shiraz_sadra_newcity") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_bushehr_asaluyeh_energy", "MAPNA_188", "ایستگاه منطقه ویژه اقتصادی انرژی پارس عسلویه", "منطقه ویژه انرژی پارس", 27.4780, 52.6150, "عسلویه، منطقه ویژه اقتصادی انرژی پارس، بلوار سیراف، جنب فرودگاه خلیج فارس", "عسلویه", "بوشهر",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_bandar_abbas_dolphin_promenade", "MAPNA_189", "ایستگاه بلوار ساحلی سورو بندرعباس", "شهرداری بندرعباس", 27.1550, 56.2450, "بندرعباس، محله ساحلی سورو، بلوار جهانگردی، پارکینگ پلاژ قایقرانی سورو", "بندرعباس", "هرمزگان",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_189:station_bandar_abbas_dolphin_promenade") { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 60.0)
        }
        addStation("station_kish_pardis_mall", "MAPNA_190", "ایستگاه مجتمع تجاری پردیس ۱ و ۲ کیش", "سازمان منطقه آزاد کیش", 26.5380, 54.0210, "جزیره کیش، میدان پردیس، خیابان فردوسی، پارکینگ بازار بین‌المللی پردیس", "کیش", "هرمزگان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_qeshm_laft_port", "MAPNA_191", "ایستگاه پایانه لندینگ‌کرافت بندر لافت قشم", "منطقه آزاد قشم", 26.9020, 55.7650, "جزیره قشم، بندر تاریخی لافت، اسکله ورود و خروج خودرویی لندینگ‌کرافت پل به لافت", "قشم", "هرمزگان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_ahvaz_kianpars_13th", "MAPNA_192", "ایستگاه کیانپارس فلکه سوم اهواز", "شهرداری منطقه ۲ اهواز", 31.3550, 48.6920, "اهواز، کیانپارس، خیابان ۱۳ غربی، تقاطع بلوار وهابی، پارکینگ شهرداری", "اهواز", "خوزستان",
            false, "UNKNOWN",
            ValidationStatus.PARTIALLY_VERIFIED, 0.68, "MUNICIPAL_EV_HUB", "urn:iran-ev-registry:municipal-hub:MAPNA_192:station_ahvaz_kianpars_13th") { id, status, conf, ref ->
            createCityAcDcPorts(id, status, conf, ref, 60.0, 22.0)
        }
        addStation("station_abadan_arvand_braim", "MAPNA_193", "ایستگاه منطقه آزاد اروند آبادان (بریم)", "سازمان منطقه آزاد اروند", 30.3420, 48.2750, "آبادان، منطقه آزاد اروند، بلوار بریم، جنب هتل بین‌المللی کاروانسرا", "آبادان", "خوزستان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_khorramshahr_arvand_memorial", "MAPNA_194", "ایستگاه بلوار ساحلی کارون خرمشهر", "منطقه آزاد اروند", 30.4350, 48.1880, "خرمشهر، بلوار ساحلی بایندور، جنب پل کهنه خرمشهر و موزه دفاع مقدس", "خرمشهر", "خوزستان",
            false, "UNKNOWN",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
        addStation("station_kermanshah_bisotun_world", "MAPNA_195", "ایستگاه میراث جهانی بیستون کرمانشاه", "پایگاه میراث بیستون / مپنا", 34.3980, 47.4420, "بزرگراه صحنه - کرمانشاه، کیلومتر ۳۰، پایگاه جهانی کتیبه داریوش بیستون", "بیستون", "کرمانشاه",
            true, "24/7",
            ValidationStatus.UNVERIFIED, 0.35, "SURVEY_REGISTRY", null) { id, status, conf, ref ->
            createDualFastDcPorts(id, status, conf, ref, 120.0)
        }
    }
}
