package com.evnavigator.iran.domain.error

/**
 * Central typed domain error hierarchy separating recoverable operational failures from fatal errors.
 * Carries correlation context (journeyId / routeGenerationId) and user-facing recovery guidance in Persian.
 */
sealed class EvDomainError(
    open val code: String,
    open val messageEn: String,
    open val messageFa: String,
    open val recoveryActionFa: String,
    open val isRecoverable: Boolean,
    open val correlationId: String? = null,
    open val routeGenerationId: Long? = null,
    open val cause: Throwable? = null
) {
    data class RoutingFailure(
        val reasonCode: String,
        override val messageEn: String,
        override val messageFa: String = "محاسبه مسیر جاده‌ای امکان‌پذیر نیست",
        override val recoveryActionFa: String = "مختصات مبدأ/مقصد یا بسته نقشه آفلاین استان را بررسی کنید.",
        override val isRecoverable: Boolean = true,
        override val correlationId: String? = null,
        override val routeGenerationId: Long? = null,
        override val cause: Throwable? = null
    ) : EvDomainError(
        code = "ROUTING_$reasonCode",
        messageEn = messageEn,
        messageFa = messageFa,
        recoveryActionFa = recoveryActionFa,
        isRecoverable = isRecoverable,
        correlationId = correlationId,
        routeGenerationId = routeGenerationId,
        cause = cause
    )

    data class EnergyInputIncomplete(
        val missingFields: List<String>,
        override val messageEn: String,
        override val messageFa: String = "مشخصات فیزیکی خودرو یا وضعیت باتری ناقص است",
        override val recoveryActionFa: String = "خودروی معتبر انتخاب کنید و درصد شارژ باتری (SOC) را وارد نمایید.",
        override val isRecoverable: Boolean = true,
        override val correlationId: String? = null,
        override val routeGenerationId: Long? = null
    ) : EvDomainError(
        code = "ENERGY_INPUT_INCOMPLETE",
        messageEn = messageEn,
        messageFa = messageFa,
        recoveryActionFa = recoveryActionFa,
        isRecoverable = isRecoverable,
        correlationId = correlationId,
        routeGenerationId = routeGenerationId
    )

    data class SocUnverifiedOrStale(
        val rejectedValue: Double?,
        val rejectedSource: String,
        override val messageEn: String,
        override val messageFa: String = "درصد شارژ باتری (SOC) تأییدنشده یا منقضی است",
        override val recoveryActionFa: String = "درصد فعلی باتری را به صورت دستی تأیید یا از تلمتری زنده دریافت کنید.",
        override val isRecoverable: Boolean = true,
        override val correlationId: String? = null
    ) : EvDomainError(
        code = "SOC_UNVERIFIED_OR_STALE",
        messageEn = messageEn,
        messageFa = messageFa,
        recoveryActionFa = recoveryActionFa,
        isRecoverable = isRecoverable,
        correlationId = correlationId
    )

    data class StationRejected(
        val stationId: String,
        val rejectionReasons: List<String>,
        override val messageEn: String,
        override val messageFa: String = "ایستگاه شارژ فاقد شرایط اعتبار یا سازگاری کانکتور است",
        override val recoveryActionFa: String = "ایستگاه شارژ تأییدشده با پورت سازگار انتخاب کنید.",
        override val isRecoverable: Boolean = true,
        override val correlationId: String? = null,
        override val routeGenerationId: Long? = null
    ) : EvDomainError(
        code = "STATION_REJECTED",
        messageEn = messageEn,
        messageFa = messageFa,
        recoveryActionFa = recoveryActionFa,
        isRecoverable = isRecoverable,
        correlationId = correlationId,
        routeGenerationId = routeGenerationId
    )

    data class GpsFixRejected(
        val rejectionReason: String,
        override val messageEn: String,
        override val messageFa: String = "سیگنال موقعیت‌یاب نامعتبر، قدیمی یا خارج از دالان مسیر است",
        override val recoveryActionFa: String = "در فضای باز قرار بگیرید تا سیگنال GPS پایدار شود.",
        override val isRecoverable: Boolean = true,
        override val correlationId: String? = null,
        override val routeGenerationId: Long? = null
    ) : EvDomainError(
        code = "GPS_FIX_REJECTED",
        messageEn = messageEn,
        messageFa = messageFa,
        recoveryActionFa = recoveryActionFa,
        isRecoverable = isRecoverable,
        correlationId = correlationId,
        routeGenerationId = routeGenerationId
    )

    data class PackageIntegrityFailure(
        val packageId: String,
        val failureKind: String,
        override val messageEn: String,
        override val messageFa: String = "خطا در بررسی صحت فایل یا فضای ذخیره‌سازی بسته آفلاین",
        override val recoveryActionFa: String = "فضای خالی دستگاه را بررسی کرده و دانلود بسته را مجدداً تلاش کنید.",
        override val isRecoverable: Boolean = true,
        override val correlationId: String? = null,
        override val cause: Throwable? = null
    ) : EvDomainError(
        code = "PACKAGE_$failureKind",
        messageEn = messageEn,
        messageFa = messageFa,
        recoveryActionFa = recoveryActionFa,
        isRecoverable = isRecoverable,
        correlationId = correlationId,
        cause = cause
    )
}

/**
 * Privacy-preserving structured diagnostic record explaining *why* a domain decision was made.
 * Never logs raw user coordinates or personal identifiers.
 */
data class DecisionDiagnosticEntry(
    val timestampEpochMs: Long,
    val subsystem: String,
    val decisionOutcome: String,
    val reasonCode: String,
    val explanationFa: String,
    val explanationEn: String,
    val correlationId: String? = null,
    val routeGenerationId: Long? = null
)
