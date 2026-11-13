package com.evnavigator.iran.domain.model.update

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for UpdatePriority enum.
 */
class UpdatePriorityTest {

    @Test
    fun `CRITICAL priority should be highest`() {
        val priorities = listOf(
            UpdatePriority.CRITICAL,
            UpdatePriority.HIGH,
            UpdatePriority.MEDIUM,
            UpdatePriority.LOW,
            UpdatePriority.BACKGROUND
        )

        for (i in priorities.indices) {
            for (j in priorities.indices) {
                if (i < j) {
                    assertTrue(
                        "Priority ${priorities[i]} should be higher than ${priorities[j]}",
                        priorities[i].ordinal < priorities[j].ordinal
                    )
                }
            }
        }
    }

    @Test
    fun `CRITICAL priority should be used for security patches`() {
        val priority = UpdatePriority.CRITICAL
        assertEquals(0, priority.ordinal)
    }

    @Test
    fun `HIGH priority should be used for major version updates`() {
        val priority = UpdatePriority.HIGH
        assertEquals(1, priority.ordinal)
    }

    @Test
    fun `MEDIUM priority should be used for minor version updates`() {
        val priority = UpdatePriority.MEDIUM
        assertEquals(2, priority.ordinal)
    }

    @Test
    fun `LOW priority should be used for optional enhancements`() {
        val priority = UpdatePriority.LOW
        assertEquals(3, priority.ordinal)
    }

    @Test
    fun `BACKGROUND priority should be lowest`() {
        val priority = UpdatePriority.BACKGROUND
        assertEquals(4, priority.ordinal)
    }

    @Test
    fun `all priority values should be distinct`() {
        val values = UpdatePriority.values()
        val distinctValues = values.toSet()
        assertEquals("All priority values should be distinct", values.size, distinctValues.size)
    }

    @Test
    fun `CRITICAL priority should be mandatory`() {
        val priority = UpdatePriority.CRITICAL
        assertTrue("CRITICAL priority should be mandatory", priority.isMandatory)
    }

    @Test
    fun `HIGH priority should be mandatory`() {
        val priority = UpdatePriority.HIGH
        assertTrue("HIGH priority should be mandatory", priority.isMandatory)
    }

    @Test
    fun `MEDIUM priority should not be mandatory`() {
        val priority = UpdatePriority.MEDIUM
        assertFalse("MEDIUM priority should not be mandatory", priority.isMandatory)
    }

    @Test
    fun `LOW priority should not be mandatory`() {
        val priority = UpdatePriority.LOW
        assertFalse("LOW priority should not be mandatory", priority.isMandatory)
    }

    @Test
    fun `BACKGROUND priority should not be mandatory`() {
        val priority = UpdatePriority.BACKGROUND
        assertFalse("BACKGROUND priority should not be mandatory", priority.isMandatory)
    }
}
