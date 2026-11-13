package com.evnavigator.iran.automotive.voice

import com.evnavigator.iran.automotive.adapter.AutomotiveManeuver
import com.evnavigator.iran.automotive.adapter.AutomotiveManeuverType
import com.evnavigator.iran.domain.model.Distance
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AutomotiveVoiceGuidanceTest {

    private lateinit var voiceGuidance: AutomotiveVoiceGuidance

    @Before
    fun setup() {
        voiceGuidance = AutomotiveVoiceGuidance(context = null)
    }

    @Test
    fun testBuildPersianAnnouncementTurnLeft() {
        val maneuver = AutomotiveManeuver(
            type = AutomotiveManeuverType.TURN_LEFT,
            distanceToManeuver = Distance.fromMeters(500.0),
            roadName = "بزرگراه همت",
            exitNumber = null
        )

        val text500 = voiceGuidance.buildPersianAnnouncement(maneuver, 500)
        assertEquals("۵۰۰ متر جلوتر، گردش به چپ به سمت بزرگراه همت", text500)

        val text50 = voiceGuidance.buildPersianAnnouncement(maneuver, 50)
        assertEquals("اکنون گردش به چپ به سمت بزرگراه همت", text50)
    }

    @Test
    fun testBuildPersianAnnouncementArrive() {
        val maneuver = AutomotiveManeuver(
            type = AutomotiveManeuverType.DESTINATION,
            distanceToManeuver = Distance.fromMeters(50.0),
            roadName = "ایستگاه شارژ مهروماه",
            exitNumber = null
        )

        val text = voiceGuidance.buildPersianAnnouncement(maneuver, 50)
        assertEquals("اکنون رسیدن به مقصد به سمت ایستگاه شارژ مهروماه", text)
    }

    @Test
    fun testDistanceDeduplicationPreventsRepetitionOnSameThreshold() {
        val maneuver = AutomotiveManeuver(
            type = AutomotiveManeuverType.TURN_RIGHT,
            distanceToManeuver = Distance.fromMeters(1000.0),
            roadName = "آزادراه تهران-قم",
            exitNumber = null
        )

        // First event at 950m (falls into <= 1000m bucket)
        val prompt1 = voiceGuidance.onLocationOrManeuverUpdated(maneuver, Distance.fromMeters(950.0))
        assertNotNull(prompt1)
        assertTrue(prompt1!!.contains("۱ کیلومتر جلوتر"))

        // Second event at 910m (still in <= 1000m bucket): MUST be deduplicated and return null
        val prompt2 = voiceGuidance.onLocationOrManeuverUpdated(maneuver, Distance.fromMeters(910.0))
        assertNull("Duplicate announcement at same threshold must be suppressed", prompt2)

        // Closer threshold at 480m (falls into <= 500m bucket): MUST trigger new announcement
        val prompt3 = voiceGuidance.onLocationOrManeuverUpdated(maneuver, Distance.fromMeters(480.0))
        assertNotNull(prompt3)
        assertTrue(prompt3!!.contains("۵۰۰ متر جلوتر"))
    }

    @Test
    fun testMuteSuppressesAllAnnouncements() {
        val maneuver = AutomotiveManeuver(
            type = AutomotiveManeuverType.U_TURN,
            distanceToManeuver = Distance.fromMeters(200.0),
            roadName = "بلوار آزادی",
            exitNumber = null
        )

        voiceGuidance.setVoiceEnabled(false)
        assertFalse(voiceGuidance.isVoiceEnabled())

        val prompt = voiceGuidance.onLocationOrManeuverUpdated(maneuver, Distance.fromMeters(190.0))
        assertNull("Muted voice guidance must return null", prompt)
    }
}
