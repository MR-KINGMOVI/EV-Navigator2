package com.evnavigator.iran.automotive.session

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.engine.OfflineHighwayGraphSolver
import com.evnavigator.iran.data.seed.IranStationsCatalog
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Android Auto Screen for strategic Iranian Highway Corridors (V12 Refactored).
 * Precomputes offline highway graph distances via [OfflineHighwayGraphSolver]
 * and refreshes asynchronously on [Dispatchers.Default] without blocking the main thread.
 */
class AutomotiveCorridorsScreen(carContext: CarContext) : Screen(carContext) {

    private data class StrategicCorridorSpec(
        val id: String,
        val title: String,
        val startCoord: GeoCoordinate,
        val endCoord: GeoCoordinate,
        val cities: Set<String>,
        val advisory: String
    )

    private val routingEngine = IranRealRoadRoutingEngine(enableOnlineRouting = false)
    private val computedDistancesKm = ConcurrentHashMap<String, Int>()

    private val corridorSpecs = listOf(
        StrategicCorridorSpec(
            id = "tehran_isfahan",
            title = "آزادراه تهران - قم - کاشان - اصفهان",
            startCoord = GeoCoordinate(35.6997, 51.3380),
            endCoord = GeoCoordinate(32.6500, 51.6800),
            cities = setOf("تهران", "قم", "کاشان", "اصفهان"),
            advisory = "مسیر آزادراهی با پوشش شارژرهای سریع DC در مهروماه قم و کاشان"
        ),
        StrategicCorridorSpec(
            id = "tehran_rasht",
            title = "آزادراه تهران - کرج - قزوین - رشت",
            startCoord = GeoCoordinate(35.6997, 51.3380),
            endCoord = GeoCoordinate(37.2800, 49.5900),
            cities = setOf("تهران", "کرج", "قزوین", "رشت"),
            advisory = "گردنه کوهین و لوشان: امکان بازیابی انرژی (Regen) در سرازیری"
        ),
        StrategicCorridorSpec(
            id = "tehran_mashhad",
            title = "محور ترانزیتی تهران - سمنان - شاهرود - مشهد",
            startCoord = GeoCoordinate(35.7300, 51.5400),
            endCoord = GeoCoordinate(36.2970, 59.6060),
            cities = setOf("تهران", "سمنان", "شاهرود", "سبزوار", "نیشابور", "مشهد"),
            advisory = "نظارت مستمر بر دمای باتری در اقلیم کویری و فواصل طولانی بین شهرها"
        ),
        StrategicCorridorSpec(
            id = "tehran_tabriz",
            title = "آزادراه تهران - قزوین - زنجان - تبریز",
            startCoord = GeoCoordinate(35.6997, 51.3380),
            endCoord = GeoCoordinate(38.0700, 46.3000),
            cities = setOf("تهران", "کرج", "قزوین", "زنجان", "تبریز"),
            advisory = "گردنه شیبلی: افت دمای باتری در فصول سرد و نیاز به پیش‌گرمایش"
        ),
        StrategicCorridorSpec(
            id = "isfahan_shiraz",
            title = "محور اصفهان - شهرضا - آباده - شیراز",
            startCoord = GeoCoordinate(32.6500, 51.6800),
            endCoord = GeoCoordinate(29.6350, 52.5500),
            cities = setOf("اصفهان", "آباده", "شیراز"),
            advisory = "پوشش شارژ سریع در محور مرکز به جنوب"
        )
    )

    init {
        lifecycleScope.launch(Dispatchers.Default) {
            var anyUpdated = false
            for (spec in corridorSpecs) {
                val distKm = try {
                    (routingEngine.calculateRealRoute(spec.startCoord, spec.endCoord).totalDistanceMeters / 1000.0).roundToInt()
                } catch (_: Exception) {
                    (OfflineHighwayGraphSolver.solveOfflineRoute(spec.startCoord, spec.endCoord).totalDistanceMeters / 1000.0).roundToInt()
                }
                if (computedDistancesKm.put(spec.id, distKm) != distKm) {
                    anyUpdated = true
                }
            }
            if (anyUpdated) {
                withContext(Dispatchers.Main) {
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()
        val (allStations, _) = IranStationsCatalog.getProductionStationsAndPorts()

        for (spec in corridorSpecs) {
            val computedDistKm = computedDistancesKm[spec.id] ?: try {
                (OfflineHighwayGraphSolver.solveOfflineRoute(spec.startCoord, spec.endCoord).totalDistanceMeters / 1000.0).roundToInt()
            } catch (_: Exception) {
                0
            }
            val verifiedCount = allStations.count { st ->
                st.city in spec.cities && st.validationStatus == "VERIFIED"
            }
            val partialCount = allStations.count { st ->
                st.city in spec.cities && st.validationStatus == "PARTIALLY_VERIFIED"
            }

            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(spec.title)
                    .addText("مسافت گراف: $computedDistKm km • ایستگاه تأییدشده: $verifiedCount • تأیید بخشی: $partialCount")
                    .addText(spec.advisory)
                    .setOnClickListener {
                        CarToast.makeText(
                            carContext,
                            "کریدور «${spec.title}» ($computedDistKm km) انتخاب شد",
                            CarToast.LENGTH_SHORT
                        ).show()
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("کریدورهای شارژ کشور (محاسبه از گراف)")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemListBuilder.build())
            .build()
    }
}

class AutomotiveRadarHudScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("نقاط ثبت سرعت و کنترل سرعت مجاز")
                .addText("پایگاه داده آفلاین محدودیت سرعت معابر • هشدار صوتی فارسی بر اساس موقعیت GPS")
                .build()
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("مشخصات توپوگرافی گردنه‌های کوهستانی")
                .addText("منجیل، کندوان، گدوک و کوهین • محاسبه اثر شیب بر مصرف انرژی")
                .build()
        )

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("⛰️ گردنه‌ها و شیب جاده‌ها")
                .setOnClickListener {
                    screenManager.push(AutomotiveMountainPassHazardsScreen(carContext))
                }
                .build()
        )

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🆘 امداد خودروی برقی")
                .setBackgroundColor(CarColor.RED)
                .setOnClickListener {
                    screenManager.push(AutomotiveEmergencySosScreen(carContext))
                }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("اطلاعات ایمنی و گردنه‌های ایران")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

class AutomotiveBatteryHealthScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()
        val bmsTemp = AutomotiveNavigationStateHolder.bmsHardwareTemperatureCelsius.value
        val currentSoc = AutomotiveNavigationStateHolder.currentVehicleSocPercent.value
        val socSource = AutomotiveNavigationStateHolder.socDataSource.value.name

        val bmsSensorStatus = if (bmsTemp != null) {
            "متصل • دمای پک باتری: ${String.format("%.1f°C", bmsTemp)}"
        } else {
            "عدم اتصال سنسور سخت‌افزاری OBD-II / CAN (داده مستقیم BMS در دسترس نیست)"
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("وضعیت ارتباط با سامانه مدیریت باتری (BMS)")
                .addText(bmsSensorStatus)
                .build()
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("شارژ فعلی ثبت‌شده: ${currentSoc?.let { "${it.toInt()}٪ ($socSource)" } ?: "نامشخص"}")
                .addText("محدوده دمایی توصیه‌شده شارژ سریع DC: ۲۲ تا ۲۸ درجه سانتی‌گراد")
                .build()
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("تخمین بازیابی ترمز احیایی (Regenerative Braking)")
                .addText("محاسبه ریاضی بر اساس شیب سرازیری مسیر و ظرفیت باتری")
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("وضعیت باتری و تله‌متری (BMS)")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

class AutomotiveMountainPassHazardsScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()
        val passes = listOf(
            Triple("۱. گردنه کندوان (محور کرج - چالوس)", "ارتفاع ۲۶۸۰ متر • شیب متوسط ۸.۲٪", "بازیابی انرژی در سرازیری شمال"),
            Triple("۲. گردنه امامزاده هاشم (محور هراز)", "ارتفاع ۲۲۷۰ متر • پیچ‌های پی‌درپی", "بازیابی انرژی بهینه رژنراتیو"),
            Triple("۳. گردنه گدوک (محور فیروزکوه)", "ارتفاع ۲۱۵۰ متر • افت دما در زمستان", "افزایش مصرف انرژی در صعود"),
            Triple("۴. گردنه کوهین و منجیل (آزادراه قزوین - رشت)", "ارتفاع ۱۳۳۰ به ۳۹۰ متر • منطقه بادخیز", "سرازیری لوشان: بازیابی شارژ باتری")
        )
        for ((name, specs, recovery) in passes) {
            itemListBuilder.addItem(
                Row.Builder().setTitle(name).addText(specs).addText(recovery).build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("گردنه‌های کوهستانی و بازیابی انرژی")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemListBuilder.build())
            .build()
    }
}

class AutomotiveEmergencySosScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()
        val emergencyServices = listOf(
            Triple("مرکز پشتیبانی شبانه‌روزی شارژرهای مپنا", "شماره تماس: ۱۵۴۴ و ۰۲۱-۲۳۱۵۱۵۴۴", "پشتیبانی فنی ایستگاه‌های شارژ"),
            Triple("امداد خودرو سراسری کانون جهانگردی", "شماره تماس: ۰۹۶۲۹", "حمل تخصصی خودروهای برقی با کفی استاندارد"),
            Triple("پلیس راهور و اطلاعات راهداری کشور", "شماره تماس: ۱۴۱", "وضعیت ترافیک و انسداد گردنه‌ها")
        )
        for ((serviceName, phone, desc) in emergencyServices) {
            itemListBuilder.addItem(
                Row.Builder().setTitle(serviceName).addText(phone).addText(desc).build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("امداد و فوریت‌های خودروهای برقی")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemListBuilder.build())
            .build()
    }
}
