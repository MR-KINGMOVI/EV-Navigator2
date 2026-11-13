package com.evnavigator.iran.core.charging

import com.evnavigator.iran.core.energy.model.Power
import com.evnavigator.iran.core.vehicle.ChargingSpecification
import com.evnavigator.iran.core.vehicle.ConnectorType as VehicleConnectorType
import com.evnavigator.iran.core.charging.ConnectorType as StationConnectorType
import org.junit.Test
import kotlin.test.assertEquals

class VehicleStationMatchingTest {

    @Test
    fun `test compatible vehicle and station`() {
        // Create a vehicle with CCS2 connector and DC charging capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create matching station with CCS2 connector and DC power capability
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            null,  // AC power not relevant here
            100.0, // DC power at station
            false, // Station doesn't provide AC
            true   // Station provides DC
        )
        
        assertEquals(true, result.isCompatible)
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test incompatible connector types`() {
        // Create a vehicle with CCS2 connector
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create station with incompatible connector (Type 2)
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.TYPE_2),
            null,  // AC power not relevant here
            100.0, // DC power at station
            false, // Station doesn't provide AC
            true   // Station provides DC
        )
        
        assertEquals(false, result.isCompatible)
        assertEquals(ConnectorCompatibility.INCOMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test unknown connector compatibility`() {
        // Create a vehicle with unknown connector
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.OTHER),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create station with known connector
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            null,  // AC power not relevant here
            100.0, // DC power at station
            false, // Station doesn't provide AC
            true   // Station provides DC
        )
        
        assertEquals(false, result.isCompatible)
        assertEquals(ConnectorCompatibility.UNKNOWN, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test vehicle with AC capability matching DC station`() {
        // Create a vehicle with both AC and DC capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = Power(7.0),
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create station with DC power capability only
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            null,  // AC power not relevant here
            100.0, // DC power at station
            false, // Station doesn't provide AC
            true   // Station provides DC
        )
        
        assertEquals(true, result.isCompatible)
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test incompatible power capability`() {
        // Create a vehicle with low power capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = Power(10.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create station with high power capability, but vehicle can't use it all
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            null,  // AC power not relevant here
            100.0, // DC power at station - too high for vehicle
            false, // Station doesn't provide AC
            true   // Station provides DC
        )
        
        assertEquals(true, result.isCompatible)  // Should still be compatible since effective power > 0
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test unknown power compatibility`() {
        // Create a vehicle with known capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create station with unknown power capability
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            null,  // AC power unknown
            null,  // DC power unknown
            true,  // Station provides AC (unknown capability)
            true   // Station provides DC (unknown capability)
        )
        
        assertEquals(false, result.isCompatible)  // Should not be compatible when power is unknown
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.UNKNOWN, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test vehicle with only AC capability matching AC station`() {
        // Create a vehicle with only AC capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = Power(7.0),
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.TYPE_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = false,
            isV2GCompatible = false
        )
        
        // Create station with AC power capability
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.TYPE_2),
            7.0,   // AC power at station
            null,  // DC power not relevant here
            true,  // Station provides AC
            false  // Station doesn't provide DC
        )
        
        assertEquals(true, result.isCompatible)
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test vehicle with only DC capability matching DC station`() {
        // Create a vehicle with only DC capability  
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create station with DC power capability
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            null,  // AC power not relevant here
            100.0, // DC power at station
            false, // Station doesn't provide AC
            true   // Station provides DC
        )
        
        assertEquals(true, result.isCompatible)
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test vehicle with both AC and DC matching station that provides only AC`() {
        // Create a vehicle with both AC and DC capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = Power(7.0),
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.TYPE_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = false,
            isV2GCompatible = false
        )
        
        // Create station with AC power capability only
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.TYPE_2),
            7.0,   // AC power at station
            null,  // DC power not relevant here
            true,  // Station provides AC
            false  // Station doesn't provide DC
        )
        
        assertEquals(true, result.isCompatible)
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test vehicle with both AC and DC matching station that provides only DC`() {
        // Create a vehicle with both AC and DC capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = Power(7.0),
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create station with DC power capability only
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            null,  // AC power not relevant here
            100.0, // DC power at station
            false, // Station doesn't provide AC
            true   // Station provides DC
        )
        
        assertEquals(true, result.isCompatible)
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test vehicle with no charging capability`() {
        // Create a vehicle with no charging capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.OTHER),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = false,
            isV2GCompatible = false
        )
        
        // Create station with any capability
        val result = VehicleStationMatchingService.matchVehicleToStation(
            vehicleSpec,
            setOf(StationConnectorType.CCS_COMBO_2),
            100.0, // AC power at station - irrelevant but provided for completeness
            null,  // DC power not relevant here
            true,  // Station provides AC (but vehicle can't use it)
            false  // Station doesn't provide DC (but vehicle can't use it either)
        )
        
        assertEquals(false, result.isCompatible)
        assertEquals(ConnectorCompatibility.UNKNOWN, result.connectorCompatibility) 
        assertEquals(PowerCompatibility.INCOMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }

    @Test
    fun `test matching with ports`() {
        // Create a vehicle with CCS2 connector and DC charging capability
        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = Power(50.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(VehicleConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )
        
        // Create ports with different capabilities
        val ports = listOf(
            ChargingPort(
                id = "port1",
                connectorType = StationConnectorType.CCS_COMBO_2,
                power = 100.0,
                isAc = false,
                isDc = true
            )
        )
        
        val result = VehicleStationMatchingService.matchVehicleToStationWithPorts(
            vehicleSpec,
            ports
        )
        
        assertEquals(true, result.isCompatible)
        assertEquals(ConnectorCompatibility.COMPATIBLE, result.connectorCompatibility)
        assertEquals(PowerCompatibility.COMPATIBLE, result.powerCompatibility)
        assertEquals(ACDCCompatibility.COMPATIBLE, result.acDcCompatibility)
    }
}
