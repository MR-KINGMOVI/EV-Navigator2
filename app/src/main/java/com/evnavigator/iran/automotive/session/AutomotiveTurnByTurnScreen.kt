package com.evnavigator.iran.automotive.session

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.evnavigator.iran.automotive.adapter.AutomotiveManeuverType
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationState
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationStateAdapter
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationStatus
import com.evnavigator.iran.automotive.voice.AutomotiveVoiceGuidance
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Genuine Turn-by-Turn Navigation Guidance Screen for Android Auto Head-Units (V11 Extracted).
 * Connected directly to real road maneuvers and Persian voice guidance.
 */
class AutomotiveTurnByTurnScreen(
    carContext: CarContext,
    private var navigationState: AutomotiveNavigationState
) : Screen(carContext) {

    private val voiceGuidance = AutomotiveVoiceGuidance(carContext)
    private val navAdapter = AutomotiveNavigationStateAdapter()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                voiceGuidance.destroy()
            }
        })
        lifecycleScope.launch {
            AutomotiveNavigationStateHolder.navigationState.collectLatest { domainState ->
                if (domainState != null) {
                    navigationState = navAdapter.convertToAutomotiveState(domainState)
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        val currentManeuver = navigationState.currentManeuver
        val nextManeuver = navigationState.nextManeuver
        val distToManeuver = navigationState.distanceToManeuver

        if (navigationState.navigationStatus == AutomotiveNavigationStatus.NAVIGATING) {
            voiceGuidance.onLocationOrManeuverUpdated(currentManeuver, distToManeuver)
        }

        val maneuverTitle = when {
            navigationState.navigationStatus == AutomotiveNavigationStatus.ERROR ->
                "خطا در ناوبری: موقعیت GPS خودرو در دسترس نیست یا مسیر خارج از پوشش است"
            currentManeuver != null -> {
                val distStr = distToManeuver?.let {
                    if (it.meters >= 1000.0) "${String.format("%.1f", it.toKilometers())} کیلومتر" else "${it.meters.toInt()} متر"
                } ?: "مسیر"
                "${getManeuverIcon(currentManeuver.type)} $distStr: ${currentManeuver.roadName ?: "ادامه مسیر"}"
            }
            else -> "در انتظار دریافت موقعیت GPS برای محاسبه مانور بعدی..."
        }

        val roadDetails = "معبر: ${navigationState.currentRoad ?: "نامشخص"} • کیفیت داده: ${navigationState.dataQuality.name}"
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(maneuverTitle)
                .addText(roadDetails)
                .build()
        )

        if (nextManeuver != null) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("مانور بعدی: ${getManeuverIcon(nextManeuver.type)} ${nextManeuver.roadName ?: ""}")
                    .addText("فاصله تقریبی: ${nextManeuver.distanceToManeuver?.let { String.format("%.1f km", it.toKilometers()) } ?: "نامشخص"}")
                    .build()
            )
        }

        val remDist = navigationState.remainingDistance?.let { "${String.format("%.1f", it.toKilometers())} km" } ?: "نامشخص"
        val soc = navigationState.SOC?.let { "${it.toInt()}٪" } ?: "نامشخص"
        val arrivalSoc = if (navigationState.predictedArrivalSOC != null) {
            val range = if (navigationState.predictedArrivalSocMin != null && navigationState.predictedArrivalSocMax != null) {
                " (${navigationState.predictedArrivalSocMin!!.toInt()}..${navigationState.predictedArrivalSocMax!!.toInt()}٪)"
            } else ""
            "${navigationState.predictedArrivalSOC!!.toInt()}٪$range"
        } else {
            "نامشخص"
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("باقی‌مانده: $remDist • شارژ فعلی: $soc")
                .addText("شارژ در مقصد: $arrivalSoc • وضعیت GPS: ${navigationState.GPSQuality.name} • ترافیک: ${navigationState.trafficStatus.name}")
                .build()
        )

        val voiceTitle = if (voiceGuidance.isVoiceEnabled()) "🔊 سخنگوی فارسی: روشن" else "🔇 سخنگوی فارسی: خاموش"
        paneBuilder.addAction(
            Action.Builder()
                .setTitle(voiceTitle)
                .setOnClickListener {
                    voiceGuidance.setVoiceEnabled(!voiceGuidance.isVoiceEnabled())
                    invalidate()
                }
                .build()
        )

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("شارژرهای مسیر")
                    .setOnClickListener {
                        screenManager.push(AutomotiveChargersScreen(carContext) { updated ->
                            navigationState = updated
                            invalidate()
                        })
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("خروج از هدایت")
                    .setOnClickListener {
                        voiceGuidance.destroy()
                        screenManager.pop()
                    }
                    .build()
            )
            .build()

        val screenTitle = navigationState.currentRoad?.let { "هدایت مسیر • $it" } ?: "هدایت مسیر"
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(screenTitle)
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun getManeuverIcon(type: AutomotiveManeuverType): String = when (type) {
        AutomotiveManeuverType.TURN_LEFT -> "⬅️"
        AutomotiveManeuverType.TURN_RIGHT -> "➡️"
        AutomotiveManeuverType.KEEP_LEFT, AutomotiveManeuverType.RAMP_LEFT, AutomotiveManeuverType.EXIT_LEFT -> "↖️"
        AutomotiveManeuverType.KEEP_RIGHT, AutomotiveManeuverType.RAMP_RIGHT, AutomotiveManeuverType.EXIT_RIGHT -> "↗️"
        AutomotiveManeuverType.U_TURN -> "🔄"
        AutomotiveManeuverType.MERGE_LEFT, AutomotiveManeuverType.MERGE_RIGHT -> "⤴️"
        AutomotiveManeuverType.ROUNDABOUT -> "⭕"
        AutomotiveManeuverType.DESTINATION -> "🏁"
        else -> "⬆️"
    }
}
