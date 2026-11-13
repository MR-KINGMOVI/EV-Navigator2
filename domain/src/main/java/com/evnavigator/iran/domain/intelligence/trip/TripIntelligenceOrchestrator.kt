package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.model.trip.intelligence.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Main orchestrator service for adaptive trip intelligence (V13.3 Section 14).
 *
 * V13.3 Invariants:
 * 1. Strictly propagates [CancellationException] across all inner and outer coroutine boundaries.
 * 2. Distinguishes [TripIntelligenceExecutionStatus.FULL_SUCCESS] from [TripIntelligenceExecutionStatus.PARTIAL_SUCCESS]
 *    when energy/feasibility analysis succeeds (`shouldRequireCharging == true`) but charging plan generation fails,
 *    preserving the exact [TripIntelligenceResult.chargingPlanError].
 */
class TripIntelligenceOrchestrator(
    private val energyAndFeasibilityOrchestrator: TripEnergyAndFeasibilityOrchestrator,
    private val predictiveChargingOrchestrator: PredictiveChargingOrchestrator
) {

    suspend fun processTripIntelligence(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext?
    ): Result<TripIntelligenceResult> {
        val requestStartMs = System.currentTimeMillis()
        return try {
            validateTripRequest(request)

            val energyAndFeasibilityResult = withContext(Dispatchers.Default) {
                coroutineContext.ensureActive()
                energyAndFeasibilityOrchestrator.calculateEnergyAndFeasibility(request, context)
            }

            if (energyAndFeasibilityResult.isFailure) {
                val err = energyAndFeasibilityResult.exceptionOrNull()
                if (err is CancellationException) throw err
                return Result.failure(
                    err ?: IllegalStateException("Failed to calculate energy and feasibility")
                )
            }

            val energyResult = energyAndFeasibilityResult.getOrNull()
            val needsCharging = shouldRequireCharging(energyResult)

            var chargingPlan: ChargingPlanCandidate? = null
            var chargingPlanError: TripIntelligenceError? = null

            if (needsCharging) {
                val planResult = try {
                    predictiveChargingOrchestrator.generateChargingPlan(request, context)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: TripIntelligenceError) {
                    Result.failure(e)
                } catch (e: Exception) {
                    Result.failure(TripIntelligenceError.SystemFailure(e.message ?: "Charging plan failed", e))
                }

                if (planResult.isSuccess) {
                    chargingPlan = planResult.getOrNull()
                } else {
                    val rawErr = planResult.exceptionOrNull()
                    if (rawErr is CancellationException) throw rawErr
                    chargingPlanError = when (rawErr) {
                        is TripIntelligenceError -> rawErr
                        else -> TripIntelligenceError.InfeasiblePlan(
                            rawErr?.message ?: "تولید برنامه شارژ برای این مسیر امکان‌پذیر نبود."
                        )
                    }
                }
            }

            val executionStatus = when {
                needsCharging && chargingPlan == null -> TripIntelligenceExecutionStatus.PARTIAL_SUCCESS
                else -> TripIntelligenceExecutionStatus.FULL_SUCCESS
            }

            val finalResult = TripIntelligenceResult(
                isSuccess = true,
                requestTimestamp = requestStartMs,
                completionTimestamp = System.currentTimeMillis(),
                vehicleAndBatteryInputs = energyResult?.vehicleAndBatteryInputs,
                routeAndSegmentInputs = energyResult?.routeAndSegmentInputs,
                energyPrediction = energyResult?.energyPrediction,
                arrivalSocPrediction = energyResult?.arrivalSocPrediction,
                reserveFeasibility = energyResult?.reserveFeasibility,
                chargingRequirement = energyResult?.chargingRequirement,
                chargingPlanCandidate = chargingPlan,
                dataQualityAndFreshness = energyResult?.dataQualityAndFreshness,
                predictionAssumptions = energyResult?.predictionAssumptions,
                decisionExplanation = energyResult?.decisionExplanation,
                error = chargingPlanError,
                chargingPlanError = chargingPlanError,
                executionStatus = executionStatus
            )

            Result.success(finalResult)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateTripRequest(request: TripIntelligenceRequest) {
        require(request.trip.id.isNotBlank()) { "Trip information is required" }
    }

    private fun shouldRequireCharging(result: TripIntelligenceResult?): Boolean {
        if (result == null) return false
        if (result.chargingRequirement?.requiresCharging == true) return true
        val feasibility = result.reserveFeasibility
        return feasibility != null && !feasibility.isFeasible
    }

    suspend fun processTripIntelligenceSuspend(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext?
    ): Result<TripIntelligenceResult> {
        return try {
            processTripIntelligence(request, context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancel() {
        // Cooperative cancellation is handled via coroutine job hierarchy and ensureActive()
    }
}
