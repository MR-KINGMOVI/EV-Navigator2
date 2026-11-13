package com.evnavigator.iran.ui.screen

import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.model.catalog.DomainVehicle

/** Occupants + luggage added to curb mass. This is an assumption, not catalog data. */
const val ASSUMED_PAYLOAD_KG = 100.0

/**
 * Builds physics parameters from the Domain vehicle catalog entry.
 * Returns null when any required field is missing so the caller falls back to the
 * explicitly-flagged "assumed" parameters (never silently invented values).
 *
 * V12.6 P1-6: Preserves `dcMaxChargePowerKw` and `acMaxChargePowerKw` independently (`null` if unknown).
 */
fun DomainVehicle.toPhysicsParamsOrNull(): VehiclePhysicsParams? =
    this.toPhysicsParamsOrNull(assumedPayloadKg = ASSUMED_PAYLOAD_KG)
