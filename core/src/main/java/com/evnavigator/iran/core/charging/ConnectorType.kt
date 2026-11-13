package com.evnavigator.iran.core.charging

/**
 * Canonical connector taxonomy for electric vehicle charging.
 *
 * This defines the standard connector types that can be used across the charging infrastructure.
 * It avoids duplicating connector definitions in different modules and distinguishes:
 * - Connector type
 * - Connector count  
 * - Connector physical status
 * - Charging power
 * - Charging protocol
 */
enum class ConnectorType {
    /**
     * Type 2 (IEC 62196) - Common in Europe, Asia, and Americas for AC charging
     */
    TYPE_2,

    /**
     * CCS Combo 1 (Combined Charging System) - Common in Europe and US for DC charging
     */
    CCS_COMBO_1,

    /**
     * CCS Combo 2 (Combined Charging System) - Common in Europe and US for DC charging  
     */
    CCS_COMBO_2,

    /**
     * CHAdeMO - Japanese standard for DC fast charging
     */
    CHAdeMO,

    /**
     * GB/T AC - Chinese standard for AC charging
     */
    GB_T_AC,

    /**
     * GB/T DC - Chinese standard for DC charging
     */
    GB_T_DC,

    /**
     * Tesla proprietary connector (NACS)
     */
    TESLA_NACS,

    /**
     * Tesla Supercharger connector (proprietary)
     */
    TESLA_SUPERCHARGER,

    /**
     * Other/unknown connector type
     */
    OTHER,

    /**
     * Unknown connector type - used when actual connector type is not known or cannot be determined
     */
    UNKNOWN
}
