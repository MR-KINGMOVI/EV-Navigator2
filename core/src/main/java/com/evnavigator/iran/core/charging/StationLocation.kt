package com.evnavigator.iran.core.charging

/**
 * Complete station location model including geographic coordinates and address information.
 *
 * This model supports:
 * - Geographic coordinates (latitude/longitude)
 * - Address information where available
 * - Geographic precision
 * - Location provenance metadata
 * - Regional context (province, city, locality)
 * - Road/highway context
 */
data class StationLocation(
    val coordinate: GeoCoordinate,
    val address: String? = null, // Full address when available
    val province: String? = null, // Province/State
    val city: String? = null, // City/town
    val locality: String? = null, // Local area or neighborhood
    val road: String? = null, // Road/highway name
    val roadNumber: String? = null, // Road number or identifier
    val postalCode: String? = null, // Postal code
    val locationContext: LocationContext? = null, // Context like urban/rural
    val source: String? = null, // Source of the location data
    val retrievedDate: Long? = null, // When this location information was retrieved
    val enforceValidation: Boolean = true
) {
    
    init {
        if (enforceValidation) {
            // Ensure coordinate is valid
            require(coordinate.isValid) { "Coordinate must be within geographic bounds" }
            
            // Validate that address fields are reasonable if provided
            address?.let { 
                require(it.isNotBlank()) { "Address cannot be blank if provided" } 
            }
            province?.let { 
                require(it.isNotBlank()) { "Province cannot be blank if provided" } 
            }
            city?.let { 
                require(it.isNotBlank()) { "City cannot be blank if provided" } 
            }
            locality?.let { 
                require(it.isNotBlank()) { "Locality cannot be blank if provided" } 
            }
            road?.let { 
                require(it.isNotBlank()) { "Road cannot be blank if provided" } 
            }
        }
    }
    
    /**
     * Gets a human-readable location description
     */
    fun getHumanReadableDescription(): String {
        val parts = mutableListOf<String>()
        
        address?.let { parts.add(it) }
        city?.let { parts.add(it) }
        province?.let { parts.add(it) }
        
        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "Location at ${coordinate.toCoordinateString()}"
        }
    }
    
    /**
     * Checks if this location has address information
     */
    val hasAddress: Boolean
        get() = !address.isNullOrBlank()
    
    /**
     * Checks if this location has geographic context
     */
    val hasGeographicContext: Boolean
        get() = province != null || city != null || locality != null || road != null
    
    
    val latitude: Double get() = coordinate.latitude
    val longitude: Double get() = coordinate.longitude

    constructor(
        latitude: Double,
        longitude: Double,
        address: String? = null,
        province: String? = null,
        city: String? = null,
        locality: String? = null,
        road: String? = null,
        roadNumber: String? = null,
        postalCode: String? = null,
        locationContext: LocationContext? = null,
        source: String? = null,
        retrievedDate: Long? = null
    ) : this(
        coordinate = GeoCoordinate(latitude, longitude, enforceBounds = false),
        address = address,
        province = province,
        city = city,
        locality = locality,
        road = road,
        roadNumber = roadNumber,
        postalCode = postalCode,
        locationContext = locationContext,
        source = source,
        retrievedDate = retrievedDate,
        enforceValidation = false
    )

    companion object {
        /**
         * Creates a minimal station location with just coordinates
         */
        fun createMinimal(coordinate: GeoCoordinate): StationLocation {
            return StationLocation(coordinate = coordinate)
        }
        
        /**
         * Creates a station location with full address information
         */
        fun createWithAddress(
            coordinate: GeoCoordinate,
            address: String,
            province: String? = null,
            city: String? = null,
            locality: String? = null,
            road: String? = null,
            postalCode: String? = null
        ): StationLocation {
            return StationLocation(
                coordinate = coordinate,
                address = address,
                province = province,
                city = city,
                locality = locality,
                road = road,
                postalCode = postalCode
            )
        }
    }
}

/**
 * Represents the geographic context of a location
 */
enum class LocationContext {
    URBAN,      // Urban area
    SUBURBAN,   // Suburban area  
    RURAL,      // Rural area
    HIGHWAY,    // Highway/roadside
    INDUSTRIAL, // Industrial area
    OTHER       // Other context
}