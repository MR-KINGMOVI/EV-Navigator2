package com.evnavigator.iran.core.charging

/**
 * Enum representing different access types for charging stations.
 *
 * This defines who can use the station and under what conditions.
 */
enum class AccessType {
    /**
     * Station is open to the public without restrictions
     */
    PUBLIC,

    /**
     * Station is restricted to specific users or groups
     */
    RESTRICTED,

    /**
     * Station requires membership or special access
     */
    MEMBERSHIP,

    /**
     * Station is private property and not generally accessible
     */
    PRIVATE,

    /**
     * Access type unknown or not specified
     */
    UNKNOWN
}

/**
 * Enum representing specific access restrictions that may apply to a charging station.
 *
 * These are more granular than AccessType and can be combined.
 */
enum class AccessRestriction {
    /**
     * Only members of a specific organization or group can use the station
     */
    ORGANIZATION,

    /**
     * Only employees of certain companies can use the station
     */
    EMPLOYEE,

    /**
     * Only customers of a specific business can use the station
     */
    CUSTOMER,

    /**
     * Only users with specific credentials (e.g., RFID, app) can use the station
     */
    CREDENTIALS,

    /**
     * Station has time-based access restrictions
     */
    TIME_RESTRICTION,

    /**
     * Station is restricted to specific vehicle types or owners
     */
    VEHICLE_RESTRICTION,

    /**
     * Access restriction unknown or not specified
     */
    UNKNOWN
}

/**
 * Enum representing payment methods accepted at a charging station.
 *
 * This enables users to understand what payment options are available before arriving.
 */
enum class PaymentMethod {
    /**
     * Payment via mobile app (e.g., through the charging network's app)
     */
    MOBILE_APP,

    /**
     * Payment via credit/debit card
     */
    CARD,

    /**
     * Payment via RFID card or tag
     */
    RFID,

    /**
     * Payment via membership program
     */
    MEMBERSHIP,

    /**
     * Payment via account balance or prepayment
     */
    PREPAID,

    /**
     * Payment via cash (rare for modern charging stations)
     */
    CASH,

    /**
     * Payment method unknown or not specified
     */
    UNKNOWN
}

/**
 * Model representing access and payment information for a charging station.
 *
 * This model separates:
 * - Who can access the station (AccessType, AccessRestrictions)
 * - How to pay for charging (PaymentMethods)
 *
 * All information should be verified from reliable sources and never assume
 * payment support without explicit evidence.
 */
data class AccessPaymentInfo(
    /**
     * The general access type for the station
     */
    val accessType: AccessType = AccessType.UNKNOWN,

    /**
     * Specific access restrictions that may apply (if any)
     */
    val accessRestrictions: Set<AccessRestriction>? = null,

    /**
     * Payment methods accepted at this station
     */
    val paymentMethods: Set<PaymentMethod>? = null,

    /**
     * Whether the station requires membership for access
     */
    val requiresMembership: Boolean = false,

    /**
     * Source of this access/payment information (e.g., "ChargePoint", "Shell")
     */
    val source: String? = null,

    /**
     * Reference ID from source system
     */
    val sourceReference: String? = null,

    /**
     * When this information was retrieved
     */
    val retrievedDate: Long? = null,

    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double = 0.5,

    /**
     * Validation status of this information
     */
    val validationStatus: ValidationStatus = ValidationStatus.UNKNOWN
) {
    init {
        // Validate confidence level
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        
        // If access restrictions are provided, they should not be empty
        accessRestrictions?.let {
            require(it.isNotEmpty()) { "Access restrictions cannot be empty if provided" }
        }
        
        // If payment methods are provided, they should not be empty
        paymentMethods?.let {
            require(it.isNotEmpty()) { "Payment methods cannot be empty if provided" }
        }
    }

    /**
     * Checks if this station accepts a specific payment method
     */
    fun acceptsPaymentMethod(paymentMethod: PaymentMethod): Boolean {
        return paymentMethods?.contains(paymentMethod) ?: false
    }

    /**
     * Checks if this station requires membership for access
     */
    val isMembershipRequired: Boolean
        get() = requiresMembership || accessType == AccessType.MEMBERSHIP

    /**
     * Checks if this station is publicly accessible
     */
    val isPubliclyAccessible: Boolean
        get() = accessType == AccessType.PUBLIC

    companion object {
        /**
         * Creates a minimal access/payment info with only required fields
         */
        fun createMinimal(
            accessType: AccessType = AccessType.UNKNOWN
        ): AccessPaymentInfo {
            return AccessPaymentInfo(accessType = accessType)
        }

        /**
         * Creates a publicly accessible station with common payment methods
         */
        fun createPublicStation(
            paymentMethods: Set<PaymentMethod> = setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP)
        ): AccessPaymentInfo {
            return AccessPaymentInfo(
                accessType = AccessType.PUBLIC,
                paymentMethods = paymentMethods,
                requiresMembership = false
            )
        }
    }
}


object AccessPaymentModel {
    enum class AccessType {
        PUBLIC, RESTRICTED, MEMBERSHIP, PRIVATE, UNKNOWN;
        fun toDomain(): com.evnavigator.iran.core.charging.AccessType = when(this) {
            PUBLIC -> com.evnavigator.iran.core.charging.AccessType.PUBLIC
            RESTRICTED -> com.evnavigator.iran.core.charging.AccessType.RESTRICTED
            MEMBERSHIP -> com.evnavigator.iran.core.charging.AccessType.MEMBERSHIP
            PRIVATE -> com.evnavigator.iran.core.charging.AccessType.PRIVATE
            UNKNOWN -> com.evnavigator.iran.core.charging.AccessType.UNKNOWN
        }
    }
    operator fun invoke(accessType: Any): AccessPaymentInfo {
        val domainType = when (accessType) {
            is com.evnavigator.iran.core.charging.AccessType -> accessType
            is AccessType -> accessType.toDomain()
            else -> com.evnavigator.iran.core.charging.AccessType.UNKNOWN
        }
        return AccessPaymentInfo(accessType = domainType)
    }
}
