package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertFailsWith as assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpeningHoursModelTest {

    @Test
    fun `test hour range creation`() {
        val range = HourRange(9, 0, 17, 0)
        
        assertEquals(9, range.startHour)
        assertEquals(0, range.startMinute)
        assertEquals(17, range.endHour)
        assertEquals(0, range.endMinute)
    }

    @Test
    fun `test hour range with minutes`() {
        val range = HourRange(9, 30, 17, 45)
        
        assertEquals(9, range.startHour)
        assertEquals(30, range.startMinute)
        assertEquals(17, range.endHour)
        assertEquals(45, range.endMinute)
    }

    @Test
    fun `test hour range contains time`() {
        val range = HourRange(9, 0, 17, 0)
        
        // Within range
        assertTrue(range.containsTime(12, 30))
        
        // At start
        assertTrue(range.containsTime(9, 0))
        
        // At end
        assertTrue(range.containsTime(17, 0))
        
        // Before range
        assertFalse(range.containsTime(8, 0))
        
        // After range
        assertFalse(range.containsTime(18, 0))
    }

    @Test
    fun `test hour range same hour`() {
        val range = HourRange(14, 30, 14, 45)
        
        // Within range
        assertTrue(range.containsTime(14, 40))
        
        // At start
        assertTrue(range.containsTime(14, 30))
        
        // At end
        assertTrue(range.containsTime(14, 45))
        
        // Before range
        assertFalse(range.containsTime(14, 29))
        
        // After range
        assertFalse(range.containsTime(14, 46))
    }

    @Test
    fun `test hour range cross midnight`() {
        val range = HourRange(22, 0, 6, 0)
        
        // Within range (midnight to 6 AM)
        assertTrue(range.containsTime(3, 0))
        
        // At start
        assertTrue(range.containsTime(22, 0))
        
        // At end
        assertTrue(range.containsTime(6, 0))
        
        // Before range
        assertFalse(range.containsTime(21, 0))
        
        // After range
        assertFalse(range.containsTime(7, 0))
    }

    @Test
    fun `test hour range duration`() {
        val range1 = HourRange(9, 0, 17, 0) // 8 hours
        assertEquals(480, range1.durationMinutes)
        
        val range2 = HourRange(9, 30, 17, 45) // 8h 15m
        assertEquals(495, range2.durationMinutes)
        
        val range3 = HourRange(22, 0, 6, 0) // 8 hours (crossing midnight)
        assertEquals(480, range3.durationMinutes)
    }

    @Test
    fun `test invalid hour range start_hour`() {
        assertThrows<IllegalArgumentException> {
            HourRange(-1, 0, 17, 0)
        }
    }

    @Test
    fun `test invalid hour range end_hour`() {
        assertThrows<IllegalArgumentException> {
            HourRange(9, 0, 25, 0)
        }
    }

    @Test
    fun `test invalid hour range start_minute`() {
        assertThrows<IllegalArgumentException> {
            HourRange(9, -1, 17, 0)
        }
    }

    @Test
    fun `test invalid hour range end_minute`() {
        assertThrows<IllegalArgumentException> {
            HourRange(9, 0, 17, 60)
        }
    }

    @Test
    fun `test invalid hour range start_after_end`() {
        assertThrows<IllegalArgumentException> {
            HourRange(17, 0, 9, 0)
        }
    }

    @Test
    fun `test exceptional hour creation`() {
        val date = Date()
        val range = HourRange(9, 0, 17, 0)
        val exception = ExceptionalHour(date, range, "Holiday")
        
        assertEquals(date, exception.date)
        assertEquals(range, exception.hourRange)
        assertEquals("Holiday", exception.reason)
    }

    @Test
    fun `test opening hours creation`() {
        val dailyHours = mapOf(
            DayOfWeek.MONDAY to HourRange(9, 0, 17, 0),
            DayOfWeek.TUESDAY to HourRange(9, 0, 17, 0)
        )
        
        val openingHours = OpeningHours(
            dailyHours = dailyHours,
            is247 = false,
            exceptionalHours = null,
            source = "ChargePoint",
            sourceReference = "12345",
            retrievedDate = 1634567890000L,
            confidence = 0.9
        )
        
        assertEquals(dailyHours, openingHours.dailyHours)
        assertFalse(openingHours.is247)
        assertNull(openingHours.exceptionalHours)
        assertEquals("ChargePoint", openingHours.source)
        assertEquals("12345", openingHours.sourceReference)
        assertEquals(1634567890000L, openingHours.retrievedDate)
        assertEquals(0.9, openingHours.confidence)
    }

    @Test
    fun `test 247 opening hours`() {
        val openingHours = OpeningHours.create247()
        
        assertTrue(openingHours.is247)
        assertNull(openingHours.dailyHours)
    }

    @Test
    fun `test business hours`() {
        val openingHours = OpeningHours.createBusinessHours()
        
        assertEquals(7, openingHours.dailyHours?.size)
        assertNotNull(openingHours.dailyHours?.get(DayOfWeek.MONDAY))
        assertNotNull(openingHours.dailyHours?.get(DayOfWeek.TUESDAY))
        assertNotNull(openingHours.dailyHours?.get(DayOfWeek.WEDNESDAY))
        assertNotNull(openingHours.dailyHours?.get(DayOfWeek.THURSDAY))
        assertNotNull(openingHours.dailyHours?.get(DayOfWeek.FRIDAY))
        assertNull(openingHours.dailyHours?.get(DayOfWeek.SATURDAY))
        assertNull(openingHours.dailyHours?.get(DayOfWeek.SUNDAY))
    }

    @Test
    fun `test opening hours is open at`() {
        val businessHours = OpeningHours.createBusinessHours()
        
        // Should be open during business hours
        assertTrue(businessHours.isOpenAt(Date(), 12, 0))
        
        // Should be closed outside business hours  
        assertFalse(businessHours.isOpenAt(Date(), 20, 0))
    }

    @Test
    fun `test opening hours with 247`() {
        val openingHours = OpeningHours.create247()
        
        assertTrue(openingHours.isOpenAt(Date(), 12, 0))
        assertTrue(openingHours.isOpenAt(Date(), 23, 59))
        assertTrue(openingHours.isOpenAt(Date(), 0, 0))
    }

    @Test
    fun `test opening hours get hours for day`() {
        val businessHours = OpeningHours.createBusinessHours()
        
        val mondayHours = businessHours.getHoursForDay(DayOfWeek.MONDAY)
        assertNotNull(mondayHours)
        assertEquals(9, mondayHours!!.startHour)
        assertEquals(17, mondayHours!!.endHour)
        
        val weekendHours = businessHours.getHoursForDay(DayOfWeek.SATURDAY)
        assertNull(weekendHours)
    }

    @Test
    fun `test invalid confidence level`() {
        assertThrows<IllegalArgumentException> {
            OpeningHours(
                dailyHours = null,
                is247 = false,
                confidence = 1.5
            )
        }
    }

    @Test
    fun `test invalid empty daily hours`() {
        assertThrows<IllegalArgumentException> {
            OpeningHours(
                dailyHours = mapOf(),
                is247 = false
            )
        }
    }

    @Test
    fun `test invalid empty exceptional hours`() {
        assertThrows<IllegalArgumentException> {
            OpeningHours(
                dailyHours = null,
                is247 = false,
                exceptionalHours = setOf()
            )
        }
    }

    @Test
    fun `test 247 with non-null daily hours`() {
        val dailyHours = mapOf(DayOfWeek.MONDAY to HourRange(9, 0, 17, 0))
        
        assertThrows<IllegalArgumentException> {
            OpeningHours(
                dailyHours = dailyHours,
                is247 = true
            )
        }
    }

    @Test
    fun `test 247 with null daily hours`() {
        val openingHours = OpeningHours(
            dailyHours = null,
            is247 = true
        )
        
        assertTrue(openingHours.is247)
    }

    @Test
    fun `test 247 with all-null daily hours`() {
        val dailyHours = mapOf(
            DayOfWeek.MONDAY to null,
            DayOfWeek.TUESDAY to null
        )
        
        val openingHours = OpeningHours(
            dailyHours = dailyHours,
            is247 = true
        )
        
        assertTrue(openingHours.is247)
    }
}