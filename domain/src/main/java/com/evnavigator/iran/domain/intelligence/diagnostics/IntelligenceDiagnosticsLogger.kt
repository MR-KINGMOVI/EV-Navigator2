package com.evnavigator.iran.domain.intelligence.diagnostics

/**
 * Stages in the EV intelligence processing pipeline.
 */
enum class CalculationStage {
    INPUT_VALIDATION,
    PHYSICS_ORCHESTRATION,
    UNCERTAINTY_EVALUATION,
    ROUTE_SCORING,
    CHARGING_OPTIMIZATION,
    DYNAMIC_REPLANNING
}

/**
 * Classification of failures during intelligence processing.
 */
enum class FailureCategory {
    MISSING_MANDATORY_INPUT,
    PHYSICALLY_INVALID_RANGE,
    DATASET_UNAVAILABLE,
    TIMEOUT_OR_CANCELLED,
    UNREACHABLE_DESTINATION,
    INTERNAL_COMPUTATION_ERROR
}

/**
 * Privacy-preserving diagnostic event record.
 * Sanitized to strictly exclude GPS coordinates, VINs, API keys, and personal credentials.
 */
data class IntelligenceDiagnosticEvent(
    val stage: CalculationStage,
    val isSuccess: Boolean,
    val durationMicros: Long,
    val failureCategory: FailureCategory? = null,
    val sanitizedSummary: String,
    val engineVersion: String = "14.0.0",
    val timestampEpochMs: Long = System.currentTimeMillis()
)

/**
 * Diagnostic logger providing privacy-safe tracing and crash-resilient execution wrappers.
 */
class IntelligenceDiagnosticsLogger(
    var isEnabled: Boolean = true,
    private val maxBufferCapacity: Int = 50
) {
    private val buffer = mutableListOf<IntelligenceDiagnosticEvent>()
    private val lock = Any()

    /**
     * Record a sanitized diagnostic event.
     */
    fun logEvent(event: IntelligenceDiagnosticEvent) {
        if (!isEnabled) return
        val sanitized = sanitize(event)
        synchronized(lock) {
            if (buffer.size >= maxBufferCapacity) {
                buffer.removeAt(0)
            }
            buffer.add(sanitized)
        }
    }

    /**
     * Execute a block safely; catches exceptions, logs them with appropriate failure category,
     * and guarantees that diagnostic failures never crash navigation.
     */
    inline fun <T> runSafely(
        stage: CalculationStage,
        block: () -> T
    ): Result<T> {
        val start = System.nanoTime()
        return try {
            val result = block()
            val durationMicros = (System.nanoTime() - start) / 1000
            logEvent(
                IntelligenceDiagnosticEvent(
                    stage = stage,
                    isSuccess = true,
                    durationMicros = durationMicros,
                    sanitizedSummary = "Completed successfully."
                )
            )
            Result.success(result)
        } catch (e: IllegalArgumentException) {
            val durationMicros = (System.nanoTime() - start) / 1000
            logEvent(
                IntelligenceDiagnosticEvent(
                    stage = stage,
                    isSuccess = false,
                    durationMicros = durationMicros,
                    failureCategory = FailureCategory.PHYSICALLY_INVALID_RANGE,
                    sanitizedSummary = "Invalid parameter range: ${e.message}"
                )
            )
            Result.failure(e)
        } catch (e: NoSuchElementException) {
            val durationMicros = (System.nanoTime() - start) / 1000
            logEvent(
                IntelligenceDiagnosticEvent(
                    stage = stage,
                    isSuccess = false,
                    durationMicros = durationMicros,
                    failureCategory = FailureCategory.MISSING_MANDATORY_INPUT,
                    sanitizedSummary = "Required input missing: ${e.message}"
                )
            )
            Result.failure(e)
        } catch (e: Exception) {
            val durationMicros = (System.nanoTime() - start) / 1000
            logEvent(
                IntelligenceDiagnosticEvent(
                    stage = stage,
                    isSuccess = false,
                    durationMicros = durationMicros,
                    failureCategory = FailureCategory.INTERNAL_COMPUTATION_ERROR,
                    sanitizedSummary = "Internal error: ${e.javaClass.simpleName}"
                )
            )
            Result.failure(e)
        }
    }

    fun getRecentEvents(): List<IntelligenceDiagnosticEvent> = synchronized(lock) {
        buffer.toList()
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
    }

    private fun sanitize(event: IntelligenceDiagnosticEvent): IntelligenceDiagnosticEvent {
        // Redact any patterns resembling lat/lng numbers or tokens
        var text = event.sanitizedSummary
        text = text.replace(Regex("""\b\d{1,2}\.\d{4,8}\b"""), "[COORDINATE]")
        text = text.replace(Regex("""(?i)(key|token|bearer|secret)\s*[:=]\s*\S+"""), "$1=[REDACTED]")
        return event.copy(sanitizedSummary = text)
    }
}
