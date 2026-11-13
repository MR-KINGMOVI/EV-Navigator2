package com.evnavigator.iran.domain.journey

import com.evnavigator.iran.domain.journey.lifecycle.JourneyEvent
import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleManager
import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleState
import com.evnavigator.iran.domain.journey.lifecycle.JourneyTransitionResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JourneyLifecycleManagerTest {

    private lateinit var manager: JourneyLifecycleManager

    @Before
    fun setUp() {
        manager = JourneyLifecycleManager()
    }

    @Test
    fun testInitialStateIsDraft() {
        assertEquals(JourneyLifecycleState.DRAFT, manager.currentState.value)
    }

    @Test
    fun testHappyPathLifecycle() {
        // DRAFT -> PLANNING
        val p1 = manager.processEvent(JourneyEvent.StartPlanning("Tehran", "Isfahan"))
        assertTrue(p1 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.PLANNING, manager.currentState.value)

        // PLANNING -> READY_TO_START
        val p2 = manager.processEvent(JourneyEvent.PlanReady("plan_teh_isf_01"))
        assertTrue(p2 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.READY_TO_START, manager.currentState.value)

        // READY_TO_START -> ACTIVE
        val p3 = manager.processEvent(JourneyEvent.StartJourney)
        assertTrue(p3 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.ACTIVE, manager.currentState.value)

        // ACTIVE -> APPROACHING_CHARGING_STOP
        val p4 = manager.processEvent(JourneyEvent.ApproachChargingStop("station_qom", 1800.0))
        assertTrue(p4 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.APPROACHING_CHARGING_STOP, manager.currentState.value)

        // APPROACHING -> REACHED
        val p5 = manager.processEvent(JourneyEvent.ReachChargingStop("station_qom"))
        assertTrue(p5 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.CHARGING_STOP_REACHED, manager.currentState.value)

        // REACHED -> CHARGING_IN_PROGRESS
        val p6 = manager.processEvent(JourneyEvent.StartCharging("station_qom", 18.0, 120.0))
        assertTrue(p6 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.CHARGING_IN_PROGRESS, manager.currentState.value)

        // CHARGING -> READY_TO_DEPART
        val p7 = manager.processEvent(JourneyEvent.FinishCharging("station_qom", 80.0))
        assertTrue(p7 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.READY_TO_DEPART, manager.currentState.value)

        // READY_TO_DEPART -> ACTIVE
        val p8 = manager.processEvent(JourneyEvent.DepartChargingStop("station_qom"))
        assertTrue(p8 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.ACTIVE, manager.currentState.value)

        // ACTIVE -> COMPLETED
        val p9 = manager.processEvent(JourneyEvent.ReachDestination)
        assertTrue(p9 is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.COMPLETED, manager.currentState.value)
    }

    @Test
    fun testCannotStartJourneyFromDraftDirectly() {
        val result = manager.processEvent(JourneyEvent.StartJourney)
        assertTrue(result is JourneyTransitionResult.InvalidTransition)
        assertEquals(JourneyLifecycleState.DRAFT, manager.currentState.value)
    }

    @Test
    fun testTerminalStateCannotTransition() {
        manager.processEvent(JourneyEvent.CancelJourney("Changed mind"))
        assertEquals(JourneyLifecycleState.CANCELLED, manager.currentState.value)
        assertTrue(manager.currentState.value.isTerminal)

        val next = manager.processEvent(JourneyEvent.StartJourney)
        assertTrue(next is JourneyTransitionResult.InvalidTransition)
        assertEquals(JourneyLifecycleState.CANCELLED, manager.currentState.value)
    }

    @Test
    fun testReplanningFailureRestoresPreReplanningState() {
        manager.processEvent(JourneyEvent.StartPlanning("Tehran", "Qom"))
        manager.processEvent(JourneyEvent.PlanReady("plan_1"))
        manager.processEvent(JourneyEvent.StartJourney)
        assertEquals(JourneyLifecycleState.ACTIVE, manager.currentState.value)

        manager.processEvent(JourneyEvent.TriggerReplanning("Off route by 200m"))
        assertEquals(JourneyLifecycleState.REPLANNING, manager.currentState.value)

        // Replan failed (e.g. offline, timeout) -> restores ACTIVE
        val failResult = manager.processEvent(JourneyEvent.ReplanFailed("Network timeout"))
        assertTrue(failResult is JourneyTransitionResult.Success)
        assertEquals(JourneyLifecycleState.ACTIVE, manager.currentState.value)
    }

    @Test
    fun testDuplicatePauseReturnsNoOp() {
        manager.processEvent(JourneyEvent.StartPlanning("Tehran", "Qom"))
        manager.processEvent(JourneyEvent.PlanReady("plan_1"))
        manager.processEvent(JourneyEvent.StartJourney)
        manager.processEvent(JourneyEvent.PauseJourney("Lunch"))
        assertEquals(JourneyLifecycleState.TEMPORARILY_PAUSED, manager.currentState.value)

        val dup = manager.processEvent(JourneyEvent.PauseJourney("Duplicate click"))
        assertTrue(dup is JourneyTransitionResult.NoOp)
    }
}
