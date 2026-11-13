package com.evnavigator.iran.domain.model.charging

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.ChargingConnector
import com.evnavigator.iran.domain.model.ConnectorType

enum class ChargingStationStatus {
    AVAILABLE,
    OCCUPIED,
    OUT_OF_SERVICE,
    MAINTENANCE,
    UNKNOWN
}

enum class OfflineChargingStationStatus {
    AVAILABLE,
    OCCUPIED,
    OFFLINE,
    UNKNOWN
}

enum class AccessRestriction {
    PUBLIC,
    PRIVATE,
    RESTRICTED,
    CUSTOMERS_ONLY
}

enum class PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    CASH,
    APP,
    RFID,
    FREE
}

data class OpeningHours(
    val isOpen24_7: Boolean = true,
    val description: String? = null
)

data class OperatorInfo(
    val name: String,
    val phone: String? = null,
    val website: String? = null
)

data class ChargingSpeedInfo(
    val maxPowerKw: Double,
    val averagePowerKw: Double = maxPowerKw
)

/**
 * Represents a charging station with its key characteristics and capabilities.
 *
 * This data class captures essential information about electric vehicle charging stations,
 * including their location, operational details, connector types, power specifications,
 * availability status, and integration points for offline functionality.
 */
data class ChargingStation(
    val id: String,
    val name: String?,
    val coordinate: GeoCoordinate,
    val address: String?,
    val city: String?,
    val country: String?,
    val postalCode: String?,
    val phone: String?,
    val website: String?,
    
    // Operational details
    val isOperational: Boolean,
    val status: ChargingStationStatus,
    val access: AccessRestriction?,
    val paymentMethods: List<PaymentMethod>,
    val openingHours: OpeningHours?,
    
    // Connector and power information  
    val connectors: List<ChargingConnector>,
    val maxPowerKw: Double,
    val minPowerKw: Double,
    
    // Station metadata
    val totalSlots: Int,
    val availableSlots: Int,
    val occupiedSlots: Int,
    
    // Data source and timestamps
    val dataSource: String,
    val lastUpdated: Long,
    val verifiedAt: Long?,
    
    // Additional properties for offline functionality
    val isFastChargeCapable: Boolean = false,
    val hasReserve: Boolean = false,
    val supportsV2G: Boolean = false,
    val hasIndoorParking: Boolean = false,
    val hasLiftAccess: Boolean = false,
    val hasSecurityCamera: Boolean = false,
    
    // Offline-specific fields
    val offlineStatus: OfflineChargingStationStatus = OfflineChargingStationStatus.UNKNOWN,
    val lastKnownAvailability: Long? = null,
    val confidenceScore: Double? = null,
    val coverageArea: String? = null,
    val operatorInfo: OperatorInfo? = null,
    
    // Additional metadata for enhanced offline experience
    val amenities: List<String> = emptyList(),
    val chargingSpeeds: Map<ConnectorType, ChargingSpeedInfo> = emptyMap()
) {
    /**
     * Gets the operational status of the station.
     *
     * @return True if the station is operational and available, false otherwise
     */
    fun isOperationalAndAvailable(): Boolean {
        return isOperational && 
               offlineStatus != OfflineChargingStationStatus.OFFLINE &&
               (status == ChargingStationStatus.AVAILABLE || status == ChargingStationStatus.UNKNOWN)
    }

    /**
     * Checks if the station has available charging slots.
     *
     * @return True if there are available slots, false otherwise
     */
    fun hasAvailableSlots(): Boolean {
        return availableSlots > 0 && 
               (offlineStatus != OfflineChargingStationStatus.OFFLINE) &&
               isOperational
    }

    /**
     * Gets the number of currently occupied slots.
     *
     * @return Number of occupied slots or -1 if not available
     */
    fun getOccupiedSlotCount(): Int {
        return if (occupiedSlots >= 0) occupiedSlots else (totalSlots - availableSlots)
    }
    
    companion object {
        /**
         * Creates a new ChargingStation with updated availability.
         *
         * @param availableSlots Number of currently available slots
         * @return New instance with updated slot information
         */
        fun updateAvailability(station: ChargingStation, availableSlots: Int): ChargingStation {
            return station.copy(availableSlots = availableSlots)
        }
    }
}