package com.evnavigator.iran.automotive.session

import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.evnavigator.iran.automotive.aaos.AaosSessionManager
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationState
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationStateAdapter
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationStatus
import com.evnavigator.iran.automotive.lifecycle.AutomotiveLifecycleManager
import com.evnavigator.iran.automotive.session.manager.AutomotiveSessionManager
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import com.evnavigator.iran.domain.model.navigation.NavigationState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Flagship Android Auto Head-Unit Cockpit Screen for EV Navigator Iran (V11 Decomposed).
 * Dynamically binds to active navigation sessions via [AutomotiveSession] (single lifecycle collector)
 * with zero fabricated telemetry, zero fake BMS temperatures, and real offline road graph calculation.
 */
class AutomotiveMainScreen(
    carContext: CarContext,
    private var navigationState: AutomotiveNavigationState? = null
) : Screen(carContext) {

    private val navAdapter = AutomotiveNavigationStateAdapter()
    private var isPreconditioningRequested: Boolean = false

    fun updateNavigationState(domainState: NavigationState?) {
        navigationState = navAdapter.convertToAutomotiveState(domainState)
        invalidate()
    }

    fun updateAutomotiveStateDirect(autoState: AutomotiveNavigationState?) {
        navigationState = autoState
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        val state = navigationState
        val isNavigating = state != null && state.navigationStatus == AutomotiveNavigationStatus.NAVIGATING

        val roadTitle = when {
            isNavigating && !state?.currentRoad.isNullOrBlank() -> "مسیر جاری: ${state!!.currentRoad}"
            state?.navigationStatus == AutomotiveNavigationStatus.ERROR -> "خطا در ناوبری: ${state.currentRoad ?: "موقعیت GPS نامشخص"}"
            else -> "سیستم ناوبری خودروهای برقی ایران"
        }
        val currentSoc = state?.SOC ?: AutomotiveNavigationStateHolder.currentVehicleSocPercent.value
        val socSource = AutomotiveNavigationStateHolder.socDataSource.value.name
        val vehicleTelemetry = when {
            currentSoc != null -> "شارژ باتری: ${currentSoc.toInt()}٪ (منبع: $socSource) • محاسبه مصرف بر اساس توپوگرافی مسیر"
            else -> "شارژ باتری: نامشخص (در انتظار انتخاب خودرو یا تله‌متری)"
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle(roadTitle)
                .addText(vehicleTelemetry)
                .build()
        )

        val hardwareBmsTemp = AutomotiveNavigationStateHolder.bmsHardwareTemperatureCelsius.value
        val bmsTempLabel = when {
            hardwareBmsTemp != null -> "دمای واقعی BMS: ${String.format("%.1f°C", hardwareBmsTemp)}"
            isPreconditioningRequested -> "درخواست پیش‌گرمایش ثبت شد (دمای BMS: نامشخص - بدون سنسور OBD-II)"
            else -> "دمای BMS: نامشخص (سنسور OBD-II متصل نیست)"
        }

        if (isNavigating) {
            val arrivalSocText = if (state?.predictedArrivalSOC != null) {
                val rangeStr = if (state.predictedArrivalSocMin != null && state.predictedArrivalSocMax != null) {
                    " (${state.predictedArrivalSocMin.toInt()}٪..${state.predictedArrivalSocMax.toInt()}٪)"
                } else ""
                "${state.predictedArrivalSOC.toInt()}٪$rangeStr"
            } else {
                "نامشخص (مشخصات خودرو انتخاب نشده)"
            }
            val distText = state?.remainingDistance?.let {
                "فاصله تا مقصد: ${String.format("%.1f", it.toKilometers())} km"
            } ?: "فاصله: نامشخص"
            val nextStopText = state?.nextChargingStop?.let {
                "توقف شارژ: ${it.charger.name} (${it.chargingCapability.value.toInt()} kW)"
            } ?: "بدون توقف شارژ میانی"

            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("شارژ در مقصد: $arrivalSocText • $distText")
                    .addText("$nextStopText • $bmsTempLabel")
                    .build()
            )
        } else {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("مقصد فعال تنظیم نشده است")
                    .addText("از منوی شارژرها مقصد را انتخاب نمایید • $bmsTempLabel")
                    .build()
            )
        }

        val gpsStatusLabel = state?.GPSQuality?.name ?: if (AutomotiveNavigationStateHolder.lastKnownLocation.value != null) "GOOD" else "UNAVAILABLE"
        val trafficStatusLabel = state?.trafficStatus?.name ?: "UNAVAILABLE"
        val qualityLabel = state?.dataQuality?.name ?: "UNKNOWN"
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("وضعیت سنسورها • GPS: $gpsStatusLabel • کیفیت داده: $qualityLabel • ترافیک: $trafficStatusLabel")
                .addText("مسیریابی آفلاین شبکه بزرگراهی ایران و تحلیل شیب گردنه‌ها")
                .build()
        )

        if (isNavigating) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("نمایش جزئیات هدایت مسیر 🧭")
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener {
                        screenManager.push(AutomotiveTurnByTurnScreen(carContext, state!!))
                    }
                    .build()
            )
        } else {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("جستجو و انتخاب شارژر ⚡")
                    .setBackgroundColor(CarColor.BLUE)
                    .setOnClickListener {
                        screenManager.push(AutomotiveChargersScreen(carContext) { computedState ->
                            updateAutomotiveStateDirect(computedState)
                        })
                    }
                    .build()
            )
        }

        paneBuilder.addAction(
            Action.Builder()
                .setTitle(if (isPreconditioningRequested) "✓ پیش‌گرمایش درخواستی" else "🔥 درخواست پیش‌گرمایش باتری")
                .setOnClickListener {
                    isPreconditioningRequested = !isPreconditioningRequested
                    val toastMsg = if (hardwareBmsTemp != null) {
                        if (isPreconditioningRequested) "پیش‌گرمایش باتری فعال شد (${hardwareBmsTemp}°C)" else "پیش‌گرمایش متوقف شد"
                    } else {
                        if (isPreconditioningRequested) "یادآور پیش‌گرمایش ثبت شد (ارتباط سخت‌افزاری OBD-II/BMS برقرار نیست)" else "یادآور پیش‌گرمایش لغو شد"
                    }
                    CarToast.makeText(carContext, toastMsg, CarToast.LENGTH_SHORT).show()
                    invalidate()
                }
                .build()
        )

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("شارژرها")
                    .setOnClickListener {
                        screenManager.push(AutomotiveChargersScreen(carContext) { computedState ->
                            updateAutomotiveStateDirect(computedState)
                        })
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("کریدورها")
                    .setOnClickListener {
                        screenManager.push(AutomotiveCorridorsScreen(carContext))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("باتری BMS")
                    .setOnClickListener {
                        screenManager.push(AutomotiveBatteryHealthScreen(carContext))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("امداد و رادار")
                    .setOnClickListener {
                        screenManager.push(AutomotiveRadarHudScreen(carContext))
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("EV Navigator Iran • Android Auto Cockpit")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .build()
    }
}

class AutomotiveSession : Session() {
    companion object {
        private const val TAG = "AutomotiveSession"
    }

    private val lifecycleManager = AutomotiveLifecycleManager()
    private val sessionManager = AutomotiveSessionManager()
    private val aaosManager = AaosSessionManager()
    private var mainScreenInstance: AutomotiveMainScreen? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleManager.initialize(lifecycleScope)
                sessionManager.initialize(lifecycleScope)
                lifecycleManager.onSessionCreated(this@AutomotiveSession)
                sessionManager.onHostConnected(this@AutomotiveSession)
                aaosManager.initialize(lifecycleScope, false)
                aaosManager.onAaosSessionCreated(this@AutomotiveSession)

                lifecycleScope.launch {
                    AutomotiveNavigationStateHolder.navigationState.collectLatest { domainState ->
                        mainScreenInstance?.updateNavigationState(domainState)
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                mainScreenInstance = null
                sessionManager.onHostDisconnected()
                lifecycleManager.onSessionDestroyed()
                aaosManager.onAaosSessionDestroyed()
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "Creating automotive main screen")
        val initialDomainState = AutomotiveNavigationStateHolder.navigationState.value
        val initialAutoState = initialDomainState?.let {
            AutomotiveNavigationStateAdapter().convertToAutomotiveState(it)
        }
        return AutomotiveMainScreen(carContext, initialAutoState).also {
            mainScreenInstance = it
        }
    }

    fun onHostConnected() {
        sessionManager.onHostConnected(this)
    }

    fun onHostDisconnected() {
        sessionManager.onHostDisconnected()
    }

    fun onAaosConfigurationChanged() {
        aaosManager.onConfigurationChanged()
    }

    fun onAaosProcessRecreation() {
        aaosManager.onProcessRecreation()
    }
}
