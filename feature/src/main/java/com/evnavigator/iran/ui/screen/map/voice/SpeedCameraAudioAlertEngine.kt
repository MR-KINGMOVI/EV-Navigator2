package com.evnavigator.iran.ui.screen.map.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Types of automotive cockpit acoustic audio alerts.
 */
enum class CockpitAlertSound {
    SPEED_CAMERA_WARNING,      // دوربين ثبت سرعت پیش‌رو (هشدار ملایم)
    CRITICAL_OVERSPEED_ALARM,  // سرعت غیرمجاز بحرانی (بوق هشدار مقطوع)
    AVERAGE_SPEED_ZONE_START,  // ورود به محدوده کنترل میانگین سرعت آزادراه
    TURN_MANEUVER_APPROACH,    // اعلام نزدیک شدن به گردش یا خروجی
    DESTINATION_ARRIVED,       // رسیدن موفقیت‌آمیز به ایستگاه شارژ یا مقصد
    RECALCULATING_REROUTE      // تغییر مسیر خودکار به دلیل ترافیک یا راهبندان
}

/**
 * Autonomous Acoustic Audio Synthesizer for EV Navigator Iran.
 * Generates pure-tone high-frequency sinusoidal waveforms dynamically using Android AudioTrack
 * and ToneGenerator, requiring ZERO external MP3/WAV assets and guaranteeing zero latency.
 */
class SpeedCameraAudioAlertEngine(
    private val context: Context
) {
    companion object {
        private const val TAG = "SpeedAlertEngine"
        private const val SAMPLE_RATE = 44100
    }

    private val audioScope = CoroutineScope(Dispatchers.Default)
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed, will use AudioTrack synthesizer fallback", e)
        }
    }

    /**
     * Plays a high-fidelity synthesized acoustic chime according to the cockpit event.
     */
    fun playAlert(sound: CockpitAlertSound) {
        audioScope.launch {
            try {
                when (sound) {
                    CockpitAlertSound.SPEED_CAMERA_WARNING -> {
                        // Two mellow warning pulses: 587 Hz -> 880 Hz
                        synthesizeTones(listOf(Tone(587.33, 120), Tone(880.0, 160)))
                    }
                    CockpitAlertSound.CRITICAL_OVERSPEED_ALARM -> {
                        // Sharp urgent high-voltage alert: Triple 1760 Hz pulse
                        synthesizeUrgentAlarm()
                    }
                    CockpitAlertSound.AVERAGE_SPEED_ZONE_START -> {
                        // Steady descending advisory tone: 784 Hz -> 659 Hz -> 523 Hz
                        synthesizeTones(listOf(Tone(783.99, 100), Tone(659.25, 100), Tone(523.25, 180)))
                    }
                    CockpitAlertSound.TURN_MANEUVER_APPROACH -> {
                        // Gentle navigation guidance chime: 440 Hz -> 659 Hz
                        synthesizeTones(listOf(Tone(440.0, 90), Tone(659.25, 140)))
                    }
                    CockpitAlertSound.DESTINATION_ARRIVED -> {
                        // Major triad completion chord: 523 Hz -> 659 Hz -> 784 Hz -> 1046 Hz
                        synthesizeTones(listOf(
                            Tone(523.25, 100),
                            Tone(659.25, 100),
                            Tone(783.99, 100),
                            Tone(1046.50, 300)
                        ))
                    }
                    CockpitAlertSound.RECALCULATING_REROUTE -> {
                        // Subtle double beep: 600 Hz -> 600 Hz
                        synthesizeTones(listOf(Tone(600.0, 80), Tone(0.0, 50), Tone(600.0, 80)))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error synthesizing alert audio tone", e)
            }
        }
    }

    private data class Tone(val frequencyHz: Double, val durationMs: Int)

    private fun synthesizeUrgentAlarm() {
        toneGenerator?.let { tg ->
            tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 350)
            return
        }
        synthesizeTones(listOf(
            Tone(1760.0, 100),
            Tone(0.0, 40),
            Tone(1760.0, 100),
            Tone(0.0, 40),
            Tone(1760.0, 150)
        ))
    }

    private fun synthesizeTones(tones: List<Tone>) {
        val totalSamples = tones.sumOf { (it.durationMs * SAMPLE_RATE) / 1000 }
        val generatedSnd = ShortArray(totalSamples)
        var sampleOffset = 0

        for (t in tones) {
            val numSamples = (t.durationMs * SAMPLE_RATE) / 1000
            for (i in 0 until numSamples) {
                if (t.frequencyHz <= 0.0) {
                    generatedSnd[sampleOffset++] = 0
                } else {
                    // Sine wave generation with smooth attack/decay envelope to prevent clicking
                    val angle = 2.0 * Math.PI * i / (SAMPLE_RATE / t.frequencyHz)
                    val envelope = when {
                        i < 200 -> (i / 200.0)
                        i > numSamples - 200 -> ((numSamples - i) / 200.0)
                        else -> 1.0
                    }
                    val sample = (sin(angle) * 32767 * envelope * 0.75).toInt().toShort()
                    generatedSnd[sampleOffset++] = sample
                }
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(totalSamples * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(generatedSnd, 0, totalSamples)
        track.play()
        track.notificationMarkerPosition = totalSamples
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                track.release()
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
