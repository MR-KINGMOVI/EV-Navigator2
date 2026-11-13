package com.evnavigator.iran.designsystem.locale

/**
 * Supported application languages.
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRtl: Boolean
) {
    PERSIAN("fa", "Persian", "فارسی", true),
    ENGLISH("en", "English", "English", false);

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: PERSIAN
        }
    }
}
