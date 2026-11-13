package com.evnavigator.iran.core.util

/**
 * High-performance Persian text normalizer for search, indexing, and comparison.
 * Standardizes Arabic characters (ي, ك, ة) to Persian equivalents (ی, ک, ه)
 * and normalizes Perso-Arabic numerals to standard ASCII digits.
 */
object PersianTextNormalizer {
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة' -> sb.append('ه')
                '۰', '٠' -> sb.append('0')
                '۱', '١' -> sb.append('1')
                '۲', '٢' -> sb.append('2')
                '۳', '٣' -> sb.append('3')
                '۴', '٤' -> sb.append('4')
                '۵', '٥' -> sb.append('5')
                '۶', '٦' -> sb.append('6')
                '۷', '٧' -> sb.append('7')
                '۸', '٨' -> sb.append('8')
                '۹', '٩' -> sb.append('9')
                '\u200C' -> sb.append(' ') // Zero-width non-joiner to space
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }
}
