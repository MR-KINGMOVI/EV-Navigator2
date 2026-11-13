package com.evnavigator.iran.automotive.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.evnavigator.iran.automotive.adapter.AutomotiveManeuver
import com.evnavigator.iran.automotive.adapter.AutomotiveManeuverType
import com.evnavigator.iran.domain.model.Distance
import java.util.Locale

/**
 * Production-ready Automotive Voice Guidance engine with Persian announcements and distance deduplication.
 * Ensures the vehicle cockpit receives clear, context-aware, non-repetitive audio instructions.
 */
class AutomotiveVoiceGuidance(
    private val context: Context? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AutomotiveVoiceGuidance"
        val DISTANCE_THRESHOLDS_METERS = listOf(2000, 1000, 500, 200, 50)
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isVoiceEnabled = true

    // Track announced threshold per maneuver key to prevent duplicate announcements
    private var lastAnnouncedManeuverKey: String? = null
    private var lastAnnouncedThreshold: Int? = null

    init {
        context?.let {
            try {
                tts = TextToSpeech(it.applicationContext, this)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize TextToSpeech: ${e.message}")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val farsiLocale = Locale("fa", "IR")
            val result = tts?.setLanguage(farsiLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Persian TTS language not supported, falling back to default locale")
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsInitialized = true
            Log.d(TAG, "TextToSpeech successfully initialized")
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status $status")
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        isVoiceEnabled = enabled
        if (!enabled) {
            stop()
        }
    }

    fun isVoiceEnabled(): Boolean = isVoiceEnabled

    /**
     * Checks if a voice prompt should be triggered for the current maneuver and distance.
     * Deduplicates announcements so each distance threshold is spoken exactly once.
     */
    fun onLocationOrManeuverUpdated(maneuver: AutomotiveManeuver?, distanceToManeuver: Distance?): String? {
        if (!isVoiceEnabled || maneuver == null || distanceToManeuver == null) return null

        val distMeters = distanceToManeuver.meters.toInt()
        val maneuverKey = "${maneuver.type.name}_${maneuver.roadName}_${maneuver.distanceToManeuver?.meters?.toInt() ?: 0}"

        if (maneuverKey != lastAnnouncedManeuverKey) {
            // New maneuver encountered: reset threshold tracker
            lastAnnouncedManeuverKey = maneuverKey
            lastAnnouncedThreshold = null
        }

        // Find applicable threshold (closest bucket that current distance is <= to)
        val matchedThreshold = DISTANCE_THRESHOLDS_METERS.lastOrNull { distMeters <= it } ?: return null

        if (lastAnnouncedThreshold != null && matchedThreshold >= lastAnnouncedThreshold!!) {
            // Already announced this or a closer threshold for this maneuver
            return null
        }

        lastAnnouncedThreshold = matchedThreshold
        val announcement = buildPersianAnnouncement(maneuver, matchedThreshold)
        speak(announcement)
        return announcement
    }

    internal fun buildPersianAnnouncement(maneuver: AutomotiveManeuver, thresholdMeters: Int): String {
        val roadPart = if (!maneuver.roadName.isNullOrBlank()) " به سمت ${maneuver.roadName}" else ""
        val maneuverInstruction = when (maneuver.type) {
            AutomotiveManeuverType.TURN_LEFT -> "گردش به چپ"
            AutomotiveManeuverType.TURN_RIGHT -> "گردش به راست"
            AutomotiveManeuverType.KEEP_LEFT, AutomotiveManeuverType.RAMP_LEFT, AutomotiveManeuverType.EXIT_LEFT -> "متمایل به چپ"
            AutomotiveManeuverType.KEEP_RIGHT, AutomotiveManeuverType.RAMP_RIGHT, AutomotiveManeuverType.EXIT_RIGHT -> "متمایل به راست"
            AutomotiveManeuverType.U_TURN -> "دور برگردان"
            AutomotiveManeuverType.MERGE_LEFT, AutomotiveManeuverType.MERGE_RIGHT -> "ادغام در مسیر"
            AutomotiveManeuverType.ROUNDABOUT -> "ورود به میدان"
            AutomotiveManeuverType.DESTINATION -> "رسیدن به مقصد"
            else -> "ادامه مسیر"
        }

        return when {
            thresholdMeters <= 50 -> "اکنون $maneuverInstruction$roadPart"
            thresholdMeters <= 200 -> "۲۰۰ متر جلوتر، $maneuverInstruction$roadPart"
            thresholdMeters <= 500 -> "۵۰۰ متر جلوتر، $maneuverInstruction$roadPart"
            thresholdMeters <= 1000 -> "۱ کیلومتر جلوتر، $maneuverInstruction$roadPart"
            else -> "۲ کیلومتر جلوتر، $maneuverInstruction$roadPart"
        }
    }

    private fun speak(text: String) {
        if (!isVoiceEnabled || !isTtsInitialized) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "NavGuidance_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to speak instruction: ${e.message}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop TTS: ${e.message}")
        }
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to shutdown TTS: ${e.message}")
        }
    }
}
