package com.evnavigator.iran.core.charging

/**
 * Station metadata model that captures all non-geographic information about a charging station.
 *
 * This model separates:
 * - Station metadata (name, operator, network, access)
 * - Connector capability (which connectors are available)
 * - Live operational state (availability, status)
 *
 * Station metadata includes:
 * - Canonical name
 * - Operator information  
 * - Network membership
 * - Address details (where available separately)
 * - Access restrictions
 * - Opening hours
 * - Payment methods
 * - Amenities
 * - Contact information
 * - Notes
 */
data class StationMetadata(
    // Core identification
    val canonicalName: String, // Official/standard name of the station
    
    // Operator and network information  
    val operatorId: String?, // Operator identifier (from external system)
    val operatorName: String?, // Human-readable operator name
    val networkId: String?, // Network identifier if part of a network
    val networkName: String?, // Network name if applicable
    
    // Access and restrictions
    val accessType: AccessType, // Public, private, restricted, membership
    val accessRestrictions: Set<AccessRestriction>?, // Specific restrictions if any
    
    // Opening hours and availability
    val openingHours: OpeningHours?, // Regular opening hours
    val is247: Boolean, // Whether station is open 24/7
    
    // Payment information
    val paymentMethods: Set<PaymentMethod>?, // Available payment methods
    
    // Amenities and services  
    val amenities: Set<Amenity>?, // Available amenities (parking, restrooms, etc.)
    
    // Contact information (where legitimately available)
    val contactPhone: String?, // Contact phone number
    val contactEmail: String?, // Contact email address
    val website: String?, // Station website
    
    // Notes and additional information
    val notes: String?, // Additional notes about the station
    
    // Source and provenance
    val source: String?, // Source of this metadata (e.g., "ChargePoint", "Shell")
    val sourceReference: String? = null, // Reference ID from source system
    val retrievedDate: Long? = null, // When this information was retrieved
    val confidence: Double = 0.5, // Confidence level 0.0 to 1.0
    
    // Validation status
    val validationStatus: ValidationStatus = ValidationStatus.UNKNOWN
) {
    
    init {
        require(canonicalName.isNotBlank()) { "Canonical name cannot be blank" }
        
        // Validate confidence level
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        
        // If access restrictions are provided, they should not be empty
        accessRestrictions?.let {
            require(it.isNotEmpty()) { "Access restrictions cannot be empty if provided" }
        }
    }
    
    /**
     * Checks if this station has any payment methods defined
     */
    val hasPaymentMethods: Boolean
        get() = paymentMethods != null && paymentMethods.isNotEmpty()
        
    /**
     * Checks if this station has any amenities defined
     */
    val hasAmenities: Boolean
        get() = amenities != null && amenities.isNotEmpty()
        
    /**
     * Checks if this station has contact information
     */
    val hasContactInfo: Boolean
        get() = contactPhone != null || contactEmail != null || website != null
        
    
    val name: String get() = canonicalName
    val operator: String? get() = operatorName
    val network: String? get() = networkName
    val accessPayment: AccessPaymentInfo get() = AccessPaymentInfo(accessType = accessType)

    constructor(
        name: String,
        operator: String? = null,
        network: String? = null,
        accessType: AccessType = AccessType.PUBLIC,
        accessPayment: AccessPaymentInfo? = null,
        is247: Boolean = false,
        website: String? = null,
        notes: String? = null,
        source: String? = null,
        sourceReference: String? = null,
        retrievedDate: Long? = null,
        confidence: Double = 0.5,
        validationStatus: ValidationStatus = ValidationStatus.UNKNOWN
    ) : this(
        canonicalName = name,
        operatorId = null,
        operatorName = operator,
        networkId = null,
        networkName = network,
        accessType = accessPayment?.accessType ?: accessType,
        accessRestrictions = null,
        openingHours = null,
        is247 = is247,
        paymentMethods = null,
        amenities = null,
        contactPhone = null,
        contactEmail = null,
        website = website,
        notes = notes,
        source = source,
        sourceReference = sourceReference,
        retrievedDate = retrievedDate,
        confidence = confidence,
        validationStatus = validationStatus
    )

    companion object {
        /**
         * Creates a minimal station metadata with required fields only
         */
        fun createMinimal(
            canonicalName: String,
            accessType: AccessType
        ): StationMetadata {
            return StationMetadata(
                canonicalName = canonicalName,
                operatorId = null,
                operatorName = null,
                networkId = null,
                networkName = null,
                accessType = accessType,
                accessRestrictions = null,
                openingHours = null,
                is247 = false,
                paymentMethods = null,
                amenities = null,
                contactPhone = null,
                contactEmail = null,
                website = null,
                notes = null,
                source = null,
                sourceReference = null,
                retrievedDate = null,
                confidence = 0.5,
                validationStatus = ValidationStatus.UNKNOWN
            )
        }
    }
}

/**
 * Represents amenities available at a charging station
 */
enum class Amenity {
    PARKING,          // Parking space available  
    RESTROOMS,        // Restrooms available
    CAFETERIA,        // Cafeteria or food service
    WIFI,             // Wi-Fi access
    SHOPPING,         // Shopping facilities nearby
    RESTAURANT,       // Restaurant on-site
    ATM,              // ATM machine
    PARKING_COVERED,  // Covered parking
    PARKING_SECURED,  // Secured parking
    OTHER
}

