package com.evnavigator.iran.core.weather.validation

/**
 * Result of weather or traffic validation.
 */
data class ValidationResult(
    val hasErrors: Boolean,
    val hasWarnings: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
