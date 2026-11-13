package com.evnavigator.iran.ui.screen.map.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Automotive Persian & English Voice Guidance Synthesizer + Cockpit Radar Audio Chime Engine.
 * Announces turn-by-turn maneuvers, speed camera warnings, and battery thermal pre-conditioning states.
 */
class PersianVoiceNavigator(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false
    var isMuted: Boolean = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val faLocale = Locale("fa", "IR")
                    val res = tts?.setLanguage(faLocale)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.US
                    }
                    tts?.setSpeechRate(1.02f)
                    isReady = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PersianVoiceNavigator", "TTS init failed", e)
            isReady = false
        }
    }

    fun playCockpitChime(isAlert: Boolean = false) {
        if (isMuted) return
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
            toneGen.startTone(
                if (isAlert) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                160
            )
        } catch (e: Exception) { android.util.Log.w("PersianVoiceNavigator", "Tone generator failed", e) }
    }

    fun speakGuidance(persianText: String, isAlert: Boolean = false) {
        if (isMuted) return
        playCockpitChime(isAlert)
        if (isReady) {
            try {
                tts?.speak(persianText, TextToSpeech.QUEUE_FLUSH, null, "ev_nav_utterance_${System.currentTimeMillis()}")
            } catch (e: Exception) { android.util.Log.w("PersianVoiceNavigator", "TTS speak failed", e) }
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) { android.util.Log.w("PersianVoiceNavigator", "TTS shutdown failed", e) }
    }
}
