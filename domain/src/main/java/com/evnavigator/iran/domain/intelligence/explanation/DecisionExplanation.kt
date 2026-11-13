package com.evnavigator.iran.domain.intelligence.explanation

/**
 * Domain category for user-facing and diagnostic explanations.
 */
enum class ExplanationCategory {
    ROUTE_SELECTION,
    CHARGING_RECOMMENDATION,
    STATION_REJECTION,
    SOC_CHANGE,
    CONFIDENCE_LIMITATION,
    SAFETY_ALERT
}

/**
 * Epistemic standing of the evidence underlying the explanation.
 */
enum class EvidenceType {
    VERIFIED_FACT,
    PHYSICAL_ESTIMATE,
    ASSUMED_FALLBACK,
    UNKNOWN_MISSING
}

/**
 * Stable, machine-readable reason codes for UI adapters and analytics.
 */
object ReasonCodes {
    const val ROUTE_FASTER_TIME = "ROUTE_FASTER_TIME"
    const val ROUTE_LOWER_ENERGY = "ROUTE_LOWER_ENERGY"
    const val ROUTE_HIGHEST_SAFETY_RESERVE = "ROUTE_HIGHEST_SAFETY_RESERVE"
    const val ROUTE_RESTRICTION_AVOIDED = "ROUTE_RESTRICTION_AVOIDED"

    const val CHARGER_OPTIMAL_POWER_AND_DETOUR = "CHARGER_OPTIMAL_POWER_AND_DETOUR"
    const val CHARGER_CONNECTOR_MISMATCH = "CHARGER_CONNECTOR_MISMATCH"
    const val CHARGER_UNREACHABLE_BATTERY_DEPLETED = "CHARGER_UNREACHABLE_BATTERY_DEPLETED"
    const val CHARGER_INSUFFICIENT_SAFETY_RESERVE = "CHARGER_INSUFFICIENT_SAFETY_RESERVE"
    const val CHARGER_LIVE_STATUS_UNVERIFIED = "CHARGER_LIVE_STATUS_UNVERIFIED"
    const val CHARGER_NO_BACKUP_ALTERNATIVES = "CHARGER_NO_BACKUP_ALTERNATIVES"

    const val SOC_DEFICIT_EXTREME_TEMP_OR_SPEED = "SOC_DEFICIT_EXTREME_TEMP_OR_SPEED"
    const val UNCERTAINTY_MISSING_ELEVATION = "UNCERTAINTY_MISSING_ELEVATION"
    const val UNCERTAINTY_STALE_ENVIRONMENT = "UNCERTAINTY_STALE_ENVIRONMENT"
    const val UNCERTAINTY_UNVERIFIED_VEHICLE_SPECS = "UNCERTAINTY_UNVERIFIED_VEHICLE_SPECS"
}

/**
 * Structured, explainable decision object with stable code and localization.
 */
data class DecisionExplanation(
    val explanationId: String,
    val category: ExplanationCategory,
    val reasonCode: String,
    val evidenceType: EvidenceType,
    val summaryEn: String,
    val summaryFa: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Factory for creating verified, un-hallucinated explanations.
 */
object ExplanationFactory {

    fun routeSelected(
        routeId: String,
        reasonCode: String,
        timeSavedMin: Int?,
        energySavedKwh: Double?
    ): DecisionExplanation {
        val (en, fa) = when (reasonCode) {
            ReasonCodes.ROUTE_FASTER_TIME ->
                "Selected for shortest travel time (saved ~$timeSavedMin min)." to
                "به دلیل کوتاه‌ترین زمان سفر انتخاب شد (حدود $timeSavedMin دقیقه صرفه‌جویی)."
            ReasonCodes.ROUTE_LOWER_ENERGY ->
                "Selected for maximum energy efficiency (saved ~$energySavedKwh kWh)." to
                "به دلیل بالاترین بهره‌وری انرژی انتخاب شد (حدود $energySavedKwh کیلووات‌ساعت صرفه‌جویی)."
            ReasonCodes.ROUTE_HIGHEST_SAFETY_RESERVE ->
                "Selected for highest arrival battery safety reserve." to
                "به دلیل بیشترین حاشیه امن ذخیره باتری در مقصد انتخاب شد."
            else ->
                "Selected as optimal compromise between time and energy." to
                "به عنوان بهینه‌ترین تعادل بین زمان و مصرف انرژی انتخاب شد."
        }

        return DecisionExplanation(
            explanationId = "exp_route_${System.currentTimeMillis()}",
            category = ExplanationCategory.ROUTE_SELECTION,
            reasonCode = reasonCode,
            evidenceType = EvidenceType.PHYSICAL_ESTIMATE,
            summaryEn = en,
            summaryFa = fa,
            metadata = mapOf("routeId" to routeId)
        )
    }

    fun stationRejected(
        stationId: String,
        stationName: String,
        reasonCode: String,
        details: String
    ): DecisionExplanation {
        val (en, fa) = when (reasonCode) {
            ReasonCodes.CHARGER_CONNECTOR_MISMATCH ->
                "Station '$stationName' does not support your vehicle's connector ($details)." to
                "ایستگاه «$stationName» از کانکتور شارژ خودروی شما پشتیبانی نمی‌کند ($details)."
            ReasonCodes.CHARGER_UNREACHABLE_BATTERY_DEPLETED ->
                "Station '$stationName' is unreachable with current battery charge." to
                "ایستگاه «$stationName» با شارژ فعلی باتری در دسترس نیست."
            ReasonCodes.CHARGER_INSUFFICIENT_SAFETY_RESERVE ->
                "Station '$stationName' arrival SOC is below safety reserve ($details)." to
                "میزان شارژ در زمان رسیدن به ایستگاه «$stationName» کمتر از حاشیه امن تعیین شده است ($details)."
            else ->
                "Station '$stationName' rejected: $details" to
                "ایستگاه «$stationName» رد شد: $details"
        }

        val evidence = if (reasonCode == ReasonCodes.CHARGER_CONNECTOR_MISMATCH) {
            EvidenceType.VERIFIED_FACT
        } else {
            EvidenceType.PHYSICAL_ESTIMATE
        }

        return DecisionExplanation(
            explanationId = "exp_chg_rej_${System.currentTimeMillis()}",
            category = ExplanationCategory.STATION_REJECTION,
            reasonCode = reasonCode,
            evidenceType = evidence,
            summaryEn = en,
            summaryFa = fa,
            metadata = mapOf("stationId" to stationId, "stationName" to stationName)
        )
    }

    fun confidenceLimitation(
        reasonCode: String,
        field: String
    ): DecisionExplanation {
        val (en, fa) = when (reasonCode) {
            ReasonCodes.UNCERTAINTY_MISSING_ELEVATION ->
                "Elevation profile is missing for part of the route. Energy range confidence reduced." to
                "پروفایل شیب جاده برای بخشی از مسیر در دسترس نیست. دقت تخمین مصرف کاهش یافته است."
            ReasonCodes.UNCERTAINTY_STALE_ENVIRONMENT ->
                "Weather or traffic observations are outdated. Bounded safety margins expanded." to
                "داده‌های آب‌وهوا یا ترافیک قدیمی هستند. حاشیه اطمینان افزایش یافت."
            ReasonCodes.UNCERTAINTY_UNVERIFIED_VEHICLE_SPECS ->
                "Vehicle specifications contain estimated values ($field). Range prediction is conservative." to
                "مشخصات خودرو شامل مقادیر تخمینی است ($field). پیش‌بینی برد به صورت محافظه‌کارانه انجام شد."
            else ->
                "Input data quality limitation ($field)." to
                "محدودیت کیفیت داده‌های ورودی ($field)."
        }

        return DecisionExplanation(
            explanationId = "exp_conf_${System.currentTimeMillis()}",
            category = ExplanationCategory.CONFIDENCE_LIMITATION,
            reasonCode = reasonCode,
            evidenceType = EvidenceType.ASSUMED_FALLBACK,
            summaryEn = en,
            summaryFa = fa,
            metadata = mapOf("field" to field)
        )
    }
}
