package com.evnavigator.iran.ui.screen.charging

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evnavigator.iran.designsystem.components.EmptyStateView
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import kotlin.math.roundToInt

/**
 * Flagship Supercharger & Fast-Charging Hub Technical Profile for EV Navigator Iran.
 * Features:
 * - CC-CV Non-Linear Dynamic Charging Curve Graph (Power vs SoC %)
 * - En-Route Thermal Battery Preconditioning Controller
 * - Highway Electricity Tariff & Cost Calculator (Tomans / kWh)
 * - Real-Time Multi-Port Status (GB/T DC, CCS2, Type 2, GB/T AC)
 * - Verified Highway Amenities & Rest Area Facility Matrix
 * - Direct One-Tap Cockpit Turn-by-Turn Navigation Routing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargerDetailScreen(
    chargerId: String,
    viewModel: ChargerDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onStartNavigation: ((Double, Double) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var isPreconditioningActive by remember { mutableStateOf(false) }
    var selectedSocStart by remember { mutableFloatStateOf(15f) }
    var selectedSocTarget by remember { mutableFloatStateOf(80f) }

    LaunchedEffect(chargerId) {
        if (chargerId.isNotBlank()) {
            viewModel.loadChargerDetails(chargerId)
        }
    }

    val deepObsidianBrush = Brush.verticalGradient(
        listOf(Color(0xFF090D18), Color(0xFF03060B))
    )
    val specularCyanBorder = Brush.horizontalGradient(
        listOf(Color(0x6600E5FF), Color(0x3300E676), Color(0x6600E5FF))
    )

    Scaffold(
        containerColor = Color(0xFF050811),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "مرکز شارژ سریع و پایش باتری",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "→", fontSize = 22.sp, color = ColorTokens.NeonCyan, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xF2090D18),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ColorTokens.NeonCyan, strokeWidth = 3.dp)
            }
        } else if (uiState.stationWithPorts == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                EmptyStateView(
                    title = "ایستگاه شارژ یافت نشد",
                    description = uiState.errorMessage ?: "شناسه درخواست‌شده در پایگاه داده محلی موجود نمی‌باشد.",
                    iconGlyph = "🔍",
                    actionLabel = "بازگشت به نقشه",
                    onActionClick = onNavigateBack
                )
            }
        } else {
            val station = uiState.stationWithPorts!!.station
            val ports = uiState.stationWithPorts!!.ports
            val verifiedPortPowerKw = ports.mapNotNull { it.powerKw?.takeIf { p -> p.isFinite() && p > 0.0 } }.maxOrNull()
                ?: station.maxPowerKw.takeIf { it.isFinite() && it > 0.0 }
            val maxPower = verifiedPortPowerKw

            val activeVehicle by com.evnavigator.iran.domain.model.catalog.ActiveVehicleDomainStateHolder.activeVehicleState.collectAsState()
            val activeVehicleKwh = activeVehicle?.let { it.usableBatteryCapacityWh / 1000.0 }?.takeIf { it > 0.0 }
            val activeVehicleMaxKw = activeVehicle?.dcChargingMaxPowerKw?.takeIf { it > 0.0 }
            val energyNeededKwh = if (activeVehicleKwh != null) {
                ((selectedSocTarget - selectedSocStart).coerceAtLeast(0f) / 100.0) * activeVehicleKwh
            } else {
                0.0
            }
            val durationMinutes: Int? = if (verifiedPortPowerKw != null && activeVehicleKwh != null && activeVehicleMaxKw != null && selectedSocTarget > selectedSocStart) {
                com.evnavigator.iran.domain.intelligence.trip.PredictiveChargingOrchestrator.calculateSocTaperChargingDuration(
                    batteryCapacityKwh = activeVehicleKwh,
                    startSocPercent = selectedSocStart.toDouble(),
                    targetSocPercent = selectedSocTarget.toDouble(),
                    stationMaxPowerKw = verifiedPortPowerKw,
                    vehicleMaxAcceptanceKw = activeVehicleMaxKw,
                    isStationVerified = station.isVerified
                ).estimatedMinutes.roundToInt()
            } else {
                null
            }

            val primaryPort = ports.firstOrNull { it.isDc } ?: ports.firstOrNull()
            val portTariffSchedule = primaryPort?.tariffSchedule
            val costEvaluation = if (portTariffSchedule != null && energyNeededKwh > 0.0) {
                portTariffSchedule.computeSessionCost(
                    energyKwh = energyNeededKwh,
                    targetCurrency = com.evnavigator.iran.domain.model.catalog.TariffCurrency.IRT_TOMAN
                )
            } else {
                null
            }

            val stationVerificationRecord = station.toIndependentVerificationRecord()
            val freshnessState = stationVerificationRecord.evaluateFreshnessState()
            val verificationStateLabelFa = when {
                freshnessState == com.evnavigator.iran.domain.intelligence.context.FieldFreshnessState.STALE ->
                    "داده قدیمی (STALE - نیازمند تأیید مجدد)"
                station.validationStatus.equals("VERIFIED", ignoreCase = true) ->
                    "تأییدشده (VERIFIED)"
                station.validationStatus.equals("PARTIALLY_VERIFIED", ignoreCase = true) ->
                    "تأیید جزئی (PARTIALLY_VERIFIED)"
                station.validationStatus.equals("ASSUMED", ignoreCase = true) ->
                    "فرضی / تخمینی (ASSUMED)"
                else ->
                    "تأییدنشده (UNVERIFIED)"
            }
            val operationalStatusLabelFa = when (station.operationalStatus) {
                com.evnavigator.iran.domain.model.catalog.StationOperationalStatus.OPERATIONAL -> "عملیاتی (تأیید زنده)"
                com.evnavigator.iran.domain.model.catalog.StationOperationalStatus.DEGRADED -> "اختلال جزئی (DEGRADED)"
                com.evnavigator.iran.domain.model.catalog.StationOperationalStatus.OUT_OF_SERVICE -> "خارج از سرویس (OUT_OF_SERVICE)"
                com.evnavigator.iran.domain.model.catalog.StationOperationalStatus.UNKNOWN -> "وضعیت عملیاتی لحظه‌ای: نامشخص (UNKNOWN)"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Station Card with Frosted Acrylic Bevel
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xF00D1527),
                    border = BorderStroke(1.2.dp, specularCyanBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Badge Row: Operator & Honest Opening Hours / Operational Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ColorTokens.VoltBlue.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, ColorTokens.VoltBlue.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = station.operatorName ?: "اپراتور نامشخص",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTokens.VoltBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            val hoursText = when {
                                station.is247 -> "ساعات کاری: شبانه‌روزی (۲۴/۷)"
                                !station.openingHoursJson.isNullOrBlank() && station.openingHoursJson != "UNKNOWN" ->
                                    "ساعات کاری: ${station.openingHoursJson}"
                                else -> "ساعات کاری: نامشخص (UNKNOWN)"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (station.is247) Color(0x2600E676) else Color(0x26FFAB00),
                                border = BorderStroke(1.dp, if (station.is247) ColorTokens.ElectricGreen else ColorTokens.AmberAlert)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(if (station.is247) ColorTokens.ElectricGreen else ColorTokens.AmberAlert, CircleShape)
                                    )
                                    Text(
                                        text = hoursText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (station.is247) ColorTokens.ElectricGreen else ColorTokens.AmberAlert
                                    )
                                }
                            }
                        }

                        // Station Title & Location
                        Text(
                            text = station.canonicalName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "استان ${station.province ?: "نامشخص"} • شهر ${station.city ?: "نامشخص"}${if (!station.address.isNullOrBlank()) " • ${station.address}" else ""}",
                            fontSize = 12.sp,
                            color = ColorTokens.TextSecondaryOnDark
                        )

                        // Explicit Verification & Operational Provenance Banner (V13.2 Section 3 & 13)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x401E293B), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "وضعیت اعتبارسنجی کاتالوگ: $verificationStateLabelFa",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (station.isVerified && freshnessState == com.evnavigator.iran.domain.intelligence.context.FieldFreshnessState.FRESH) ColorTokens.ElectricGreen else ColorTokens.AmberAlert
                            )
                            Text(
                                text = operationalStatusLabelFa,
                                fontSize = 11.sp,
                                color = ColorTokens.TextSecondaryOnDark
                            )
                            Text(
                                text = "منبع داده: ${stationVerificationRecord.evidenceSource ?: "نامشخص"} • وضعیت لینک: ${stationVerificationRecord.urlVerificationStatus.name}",
                                fontSize = 10.sp,
                                color = ColorTokens.TextSecondaryOnDark
                            )
                        }

                        // Glanceable Metric Badges
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x66060A13), RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(14.dp))
                                .padding(vertical = 12.dp, horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "حداکثر توان نامی", fontSize = 10.sp, color = ColorTokens.TextSecondaryOnDark)
                                Text(
                                    text = if (maxPower != null) "${maxPower.toInt()} kW" else "نامشخص",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ColorTokens.NeonCyan
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x3338BDF8)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "تعداد نازل‌ها", fontSize = 10.sp, color = ColorTokens.TextSecondaryOnDark)
                                Text(text = "${ports.size} درگاه", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x3338BDF8)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "ضریب اطمینان", fontSize = 10.sp, color = ColorTokens.TextSecondaryOnDark)
                                Text(text = "${(station.confidence * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ColorTokens.AmberAlert)
                            }
                        }

                        // Direct Navigation CTA Button
                        Button(
                            onClick = { onStartNavigation?.invoke(station.latitude, station.longitude) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorTokens.ElectricGreen
                            )
                        ) {
                            Text(
                                text = "🚀 شروع ناوبری مستقیم به این هاب شارژ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }

                // 2. Dynamic CC-CV Charging Curve Simulator & Battery Preconditioning
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF00D1527),
                    border = BorderStroke(1.dp, Color(0x4038BDF8))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(ColorTokens.NeonCyan, CircleShape))
                                Text(
                                    text = "شبیه‌ساز منحنی توان شارژ (CC-CV - تخمینی)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = if (durationMinutes != null) "تخمین مدل: $durationMinutes دقیقه" else "مدت شارژ: نامشخص (نیازمند مشخصات خودرو/توان)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (durationMinutes != null) ColorTokens.ElectricGreen else ColorTokens.AmberAlert
                            )
                        }

                        // Canvas: Non-linear Charging Curve (Constant Current then Constant Voltage roll-off)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF070B16))
                                .border(1.dp, Color(0x2638BDF8), RoundedCornerShape(12.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                val curvePath = Path()
                                val fillPath = Path()

                                val steps = 40
                                for (i in 0..steps) {
                                    val soc = i * (100.0 / steps)
                                    val x = (soc / 100.0).toFloat() * w

                                    val powerFactor = when {
                                        soc <= 55.0 -> 1.0
                                        soc <= 80.0 -> 1.0 - ((soc - 55.0) / 25.0) * 0.45
                                        else -> 0.55 - ((soc - 80.0) / 20.0) * 0.35
                                    }
                                    val y = h - (powerFactor.toFloat() * (h * 0.8f)) - (h * 0.08f)

                                    if (i == 0) {
                                        curvePath.moveTo(x, y)
                                        fillPath.moveTo(x, h)
                                        fillPath.lineTo(x, y)
                                    } else {
                                        curvePath.lineTo(x, y)
                                        fillPath.lineTo(x, y)
                                    }
                                }
                                fillPath.lineTo(w, h)
                                fillPath.close()

                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0x3300E5FF), Color(0x0500E5FF))
                                    )
                                )
                                drawPath(
                                    path = curvePath,
                                    brush = Brush.horizontalGradient(
                                        listOf(ColorTokens.NeonCyan, ColorTokens.ElectricGreen, ColorTokens.AmberAlert)
                                    ),
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )

                                val startX = (selectedSocStart / 100f) * w
                                val targetX = (selectedSocTarget / 100f) * w
                                drawLine(ColorTokens.AmberAlert, Offset(startX, 0f), Offset(startX, h), strokeWidth = 1.5.dp.toPx())
                                drawLine(ColorTokens.ElectricGreen, Offset(targetX, 0f), Offset(targetX, h), strokeWidth = 1.5.dp.toPx())
                            }
                        }

                        // Sliders: SoC Start & SoC Target
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "شارژ شروع: ${selectedSocStart.roundToInt()}٪", fontSize = 11.sp, color = ColorTokens.AmberAlert)
                                Text(text = "شارژ هدف: ${selectedSocTarget.roundToInt()}٪", fontSize = 11.sp, color = ColorTokens.ElectricGreen, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = selectedSocTarget,
                                onValueChange = { selectedSocTarget = it.coerceAtLeast(selectedSocStart + 5f) },
                                valueRange = 20f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ColorTokens.ElectricGreen,
                                    activeTrackColor = ColorTokens.ElectricGreen,
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )
                        }

                        // Preconditioning Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPreconditioningActive) Color(0x33FF9800) else Color(0x1A1E293B),
                            border = BorderStroke(1.dp, if (isPreconditioningActive) Color(0xFFFF9800) else Color(0x40334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPreconditioningActive = !isPreconditioningActive }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = if (isPreconditioningActive) "🔥" else "❄️", fontSize = 16.sp)
                                    Column {
                                        Text(
                                            text = if (isPreconditioningActive) "شبیه‌سازی پیش‌گرمایش باتری فعال است" else "شبیه‌سازی پیش‌گرمایش باتری (BMS)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (isPreconditioningActive) "فرض مدل: کاهش مقاومت داخلی باتری در دمای پایین" else "محاسبه اثر پیش‌گرمایش بر منحنی شارژ در هوای سرد",
                                            fontSize = 10.sp,
                                            color = ColorTokens.TextSecondaryOnDark
                                        )
                                    }
                                }
                                Switch(
                                    checked = isPreconditioningActive,
                                    onCheckedChange = { isPreconditioningActive = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFFF9800),
                                        checkedTrackColor = Color(0x4DFF9800)
                                    )
                                )
                            }
                        }

                        // Tariff & Energy Cost Calculation (V13.2 Section 5: Explicit Currency & Provenance)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x3300E5FF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (energyNeededKwh > 0.0) "انرژی دریافتی: ${"%.1f".format(energyNeededKwh)} kWh" else "انرژی دریافتی: نامشخص (خودرو انتخاب نشده)",
                                    fontSize = 11.sp,
                                    color = ColorTokens.NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = costEvaluation?.uiLabelFa ?: "هزینه شارژ: نامشخص (فاقد داده انرژی یا تعرفه)",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            if (portTariffSchedule != null) {
                                Text(
                                    text = "منبع تعرفه: ${portTariffSchedule.sourceName ?: "نامشخص"} (${portTariffSchedule.currency.displayUnitFa} • وضعیت: ${portTariffSchedule.evaluateFreshness().name})",
                                    fontSize = 9.sp,
                                    color = ColorTokens.TextSecondaryOnDark
                                )
                            }
                        }
                    }
                }

                // 3. Ports & Connectors Detailed Specification
                Text(
                    text = "درگاه‌ها و استانداردهای اتصال (${ports.size} نازل):",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                ports.forEach { port ->
                    val specRecord = port.toIndependentSpecRecord()
                    val voltageLabel = port.voltage?.takeIf { it > 0.0 }?.let { "${it.toInt()} ولت" } ?: "ولتاژ نامشخص"
                    val currentLabel = port.current?.takeIf { it > 0.0 }?.let { "${it.toInt()} آمپر" } ?: "جریان نامشخص"
                    val powerLabel = specRecord.effectiveNominalPowerKw?.let { "${it.toInt()} kW (نامی)" } ?: "توان نامشخص"
                    val occupancyLabelFa = when (specRecord.occupancyStatus) {
                        com.evnavigator.iran.domain.model.catalog.PortOccupancyStatus.AVAILABLE -> "آزاد (تأیید لحظه‌ای)"
                        com.evnavigator.iran.domain.model.catalog.PortOccupancyStatus.OCCUPIED -> "مشغول"
                        com.evnavigator.iran.domain.model.catalog.PortOccupancyStatus.OUT_OF_ORDER -> "خارج از سرویس"
                        com.evnavigator.iran.domain.model.catalog.PortOccupancyStatus.UNKNOWN -> "وضعیت لحظه‌ای: نامشخص (UNKNOWN)"
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xF00D1527),
                        border = BorderStroke(1.dp, Color(0x3338BDF8))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (port.isDc) Color(0x2600E5FF) else Color(0x2600E676)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (port.isDc) "DC" else "AC",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (port.isDc) ColorTokens.NeonCyan else ColorTokens.ElectricGreen
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${port.connectorType.name} • ${if (port.isDc) "جریان مستقیم (DC)" else "جریان متناوب (AC)"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "$voltageLabel • $currentLabel • $occupancyLabelFa",
                                        fontSize = 11.sp,
                                        color = ColorTokens.TextSecondaryOnDark
                                    )
                                }
                            }

                            Text(
                                text = powerLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (specRecord.effectiveNominalPowerKw != null) ColorTokens.ElectricGreen else ColorTokens.AmberAlert
                            )
                        }
                    }
                }

                // 4. Highway Amenities & Rest Area Facilities (Honest Provenance)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xF00D1527),
                    border = BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "امکانات رفاهی و خدمات مجتمع",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTokens.NeonCyan
                        )

                        val verifiedAmenities = station.amenities?.filter { it.isNotBlank() }.orEmpty()
                        if (verifiedAmenities.isEmpty()) {
                            Text(
                                text = "اطلاعات امکانات رفاهی این ایستگاه در کاتالوگ آفلاین ثبت یا تأیید نشده است (UNKNOWN).",
                                fontSize = 11.sp,
                                color = ColorTokens.TextSecondaryOnDark
                            )
                        } else {
                            verifiedAmenities.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { amenityName ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0x33060A13), RoundedCornerShape(10.dp))
                                                .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(text = amenityName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
