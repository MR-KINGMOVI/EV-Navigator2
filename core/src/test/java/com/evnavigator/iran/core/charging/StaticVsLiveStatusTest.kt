package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertFailsWith as assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StaticVsLiveStatusTest {

    @Test
    fun `test static status enum`() {
        assertEquals(StaticVsLiveStatus.STATIC, StaticVsLiveStatus.STATIC)
        assertEquals(StaticVsLiveStatus.LIVE, StaticVsLiveStatus.LIVE)
    }

    @Test
    fun `test freshness creation`() {
        val freshness = Freshness(
            timestamp = 1634567890000L,
            source = "ChargePoint",
            ageMillis = 3600000L,
            confidence = 0.9,
            version = "v1.2.3",
            isFresh = true
        )

        assertEquals(1634567890000L, freshness.timestamp)
        assertEquals("ChargePoint", freshness.source)
        assertEquals(3600000L, freshness.ageMillis)
        assertEquals(0.9, freshness.confidence)
        assertEquals("v1.2.3", freshness.version)
        assertEquals(true, freshness.isFresh)
    }

    @Test
    fun `test freshness invalid confidence`() {
        assertThrows<IllegalArgumentException> {
            Freshness(
                timestamp = 1634567890000L,
                source = "ChargePoint",
                ageMillis = 3600000L,
                confidence = 1.5
            )
        }
    }

    @Test
    fun `test freshness invalid timestamp`() {
        assertThrows<IllegalArgumentException> {
            Freshness(
                timestamp = -1000L,
                source = "ChargePoint",
                ageMillis = 3600000L,
                confidence = 0.9
            )
        }
    }

    @Test
    fun `test freshness invalid age`() {
        assertThrows<IllegalArgumentException> {
            Freshness(
                timestamp = 1634567890000L,
                source = "ChargePoint",
                ageMillis = -3600000L,
                confidence = 0.9
            )
        }
    }

    @Test
    fun `test operational status enum`() {
        assertEquals(OperationalStatus.AVAILABLE, OperationalStatus.AVAILABLE)
        assertEquals(OperationalStatus.IN_USE, OperationalStatus.IN_USE)
        assertEquals(OperationalStatus.OUT_OF_SERVICE, OperationalStatus.OUT_OF_SERVICE)
        assertEquals(OperationalStatus.FAULTED, OperationalStatus.FAULTED)
        assertEquals(OperationalStatus.UNKNOWN, OperationalStatus.UNKNOWN)
    }

    @Test
    fun `test static live separation with static data`() {
        val separation = StaticLiveSeparation(
            statusType = StaticVsLiveStatus.STATIC,
            freshness = null,
            operationalStatus = null,
            metadata = mapOf("key" to "value")
        )

        assertEquals(StaticVsLiveStatus.STATIC, separation.statusType)
        assertNull(separation.freshness)
        assertNull(separation.operationalStatus)
        assertEquals(mapOf("key" to "value"), separation.metadata)
    }

    @Test
    fun `test static live separation with live data`() {
        val freshness = Freshness(
            timestamp = 1634567890000L,
            source = "ChargePoint",
            ageMillis = 3600000L,
            confidence = 0.9
        )

        val separation = StaticLiveSeparation(
            statusType = StaticVsLiveStatus.LIVE,
            freshness = freshness,
            operationalStatus = OperationalStatus.AVAILABLE,
            metadata = mapOf("key" to "value")
        )

        assertEquals(StaticVsLiveStatus.LIVE, separation.statusType)
        assertEquals(freshness, separation.freshness)
        assertEquals(OperationalStatus.AVAILABLE, separation.operationalStatus)
        assertEquals(mapOf("key" to "value"), separation.metadata)
    }

    @Test
    fun `test static live separation invalid operational status for static data`() {
        assertThrows<IllegalArgumentException> {
            StaticLiveSeparation(
                statusType = StaticVsLiveStatus.STATIC,
                freshness = null,
                operationalStatus = OperationalStatus.AVAILABLE
            )
        }
    }

    @Test
    fun `test static live separation missing freshness for live data`() {
        assertThrows<IllegalArgumentException> {
            StaticLiveSeparation(
                statusType = StaticVsLiveStatus.LIVE,
                freshness = null,
                operationalStatus = null
            )
        }
    }

    @Test
    fun `test static live separation valid live data with no operational status`() {
        val freshness = Freshness(
            timestamp = 1634567890000L,
            source = "ChargePoint",
            ageMillis = 3600000L,
            confidence = 0.9
        )

        val separation = StaticLiveSeparation(
            statusType = StaticVsLiveStatus.LIVE,
            freshness = freshness,
            operationalStatus = null
        )

        assertEquals(StaticVsLiveStatus.LIVE, separation.statusType)
        assertEquals(freshness, separation.freshness)
        assertNull(separation.operationalStatus)
    }
}