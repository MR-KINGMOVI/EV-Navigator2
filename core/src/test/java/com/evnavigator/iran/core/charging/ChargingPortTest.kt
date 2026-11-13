package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChargingPortTest {
    
    @Test
    fun `test create AC charging port`() {
        val port = ChargingPort.createAcPort(
            id = "ac_port_1",
            connectorType = ConnectorType.TYPE_2,
            voltage = 230.0,
            current = 32.0,
            phaseCount = 1
        )
        
        assertEquals("ac_port_1", port.id)
        assertEquals(ConnectorType.TYPE_2, port.connectorType)
        assertEquals(1, port.connectorCount)
        assertEquals(230.0, port.voltage)
        assertEquals(32.0, port.current)
        assertEquals(7.36, port.power) // 230 * 32 / 1000
        assertTrue(port.isAc)
        assertFalse(port.isDc)
        assertEquals(1, port.phaseCount)
        assertNull(port.protocol)
        assertEquals(PortStatus.UNKNOWN, port.status)
        assertNull(port.source)
        assertNull(port.sourceReference)
        assertNull(port.retrievedDate)
        assertEquals(0.5, port.confidence)
        assertEquals(ValidationStatus.UNKNOWN, port.validationStatus)
    }
    
    @Test
    fun `test create DC charging port`() {
        val port = ChargingPort.createDcPort(
            id = "dc_port_1",
            connectorType = ConnectorType.CCS_COMBO_2,
            voltage = 400.0,
            current = 125.0
        )
        
        assertEquals("dc_port_1", port.id)
        assertEquals(ConnectorType.CCS_COMBO_2, port.connectorType)
        assertEquals(1, port.connectorCount)
        assertEquals(400.0, port.voltage)
        assertEquals(125.0, port.current)
        assertEquals(50.0, port.power) // 400 * 125 / 1000
        assertFalse(port.isAc)
        assertTrue(port.isDc)
        assertNull(port.phaseCount)
        assertNull(port.protocol)
        assertEquals(PortStatus.UNKNOWN, port.status)
        assertNull(port.source)
        assertNull(port.sourceReference)
        assertNull(port.retrievedDate)
        assertEquals(0.5, port.confidence)
        assertEquals(ValidationStatus.UNKNOWN, port.validationStatus)
    }
    
    @Test
    fun `test charging port with all parameters`() {
        val port = ChargingPort(
            id = "port_1",
            connectorType = ConnectorType.TYPE_2,
            connectorCount = 2,
            voltage = 230.0,
            current = 32.0,
            power = 7.36,
            isAc = true,
            isDc = false,
            phaseCount = 1,
            protocol = ChargingProtocol.CCS,
            status = PortStatus.AVAILABLE,
            source = "Test Provider",
            sourceReference = "ref_123",
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.9,
            validationStatus = ValidationStatus.VALIDATED
        )
        
        assertEquals("port_1", port.id)
        assertEquals(ConnectorType.TYPE_2, port.connectorType)
        assertEquals(2, port.connectorCount)
        assertEquals(230.0, port.voltage)
        assertEquals(32.0, port.current)
        assertEquals(7.36, port.power)
        assertTrue(port.isAc)
        assertFalse(port.isDc)
        assertEquals(1, port.phaseCount)
        assertEquals(ChargingProtocol.CCS, port.protocol)
        assertEquals(PortStatus.AVAILABLE, port.status)
        assertEquals("Test Provider", port.source)
        assertEquals("ref_123", port.sourceReference)
        assertNotNull(port.retrievedDate)
        assertEquals(0.9, port.confidence)
        assertEquals(ValidationStatus.VALIDATED, port.validationStatus)
    }
    
    @Test
    fun `test effective power calculation`() {
        // Power explicitly provided
        val portWithPower = ChargingPort(
            id = "port_1",
            connectorType = ConnectorType.TYPE_2,
            voltage = 230.0,
            current = 32.0,
            power = 7.36, // Explicitly provided
            isAc = true,
            isDc = false
        )
        
        assertEquals(7.36, portWithPower.effectivePowerKw)
        
        // Power calculated from voltage/current
        val portCalculated = ChargingPort(
            id = "port_2",
            connectorType = ConnectorType.TYPE_2,
            voltage = 230.0,
            current = 32.0,
            isAc = true,
            isDc = false
        )
        
        assertEquals(7.36, portCalculated.effectivePowerKw)
        
        // Power not calculable (missing data)
        val portMissingData = ChargingPort(
            id = "port_3",
            connectorType = ConnectorType.TYPE_2,
            voltage = null,
            current = 32.0,
            isAc = true,
            isDc = false
        )
        
        assertNull(portMissingData.effectivePowerKw)
    }
    
    @Test
    fun `test compatibility check`() {
        val port = ChargingPort.createAcPort(
            id = "port_1",
            connectorType = ConnectorType.TYPE_2,
            voltage = 230.0,
            current = 32.0
        )
        
        assertTrue(port.isCompatibleWith(ConnectorType.TYPE_2))
        assertFalse(port.isCompatibleWith(ConnectorType.CCS_COMBO_2))
    }
    
    @Test
    fun `test human readable description`() {
        val port1 = ChargingPort.createAcPort(
            id = "port_1",
            connectorType = ConnectorType.TYPE_2,
            voltage = 230.0,
            current = 32.0
        )
        
        val desc1 = port1.getHumanReadableDescription()
        assertTrue(desc1.contains("TYPE_2"))
        assertTrue(desc1.contains("230V"))
        assertTrue(desc1.contains("32A"))
        assertTrue(desc1.contains("7.4kW"))
        
        val port2 = ChargingPort.createDcPort(
            id = "port_2",
            connectorType = ConnectorType.CCS_COMBO_2,
            voltage = 500.0,
            current = 125.0
        )
        
        val desc2 = port2.getHumanReadableDescription()
        assertTrue(desc2.contains("CCS_COMBO_2"))
        assertTrue(desc2.contains("500V"))
        assertTrue(desc2.contains("125A"))
        assertTrue(desc2.contains("62.5kW"))
    }
    
    @Test
    fun `test invalid port ID`() {
        try {
            ChargingPort(
                id = "",
                connectorType = ConnectorType.TYPE_2,
                isAc = true,
                isDc = false
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Port ID cannot be blank", e.message)
        }
    }
    
    @Test
    fun `test invalid connector count`() {
        try {
            ChargingPort(
                id = "port_1",
                connectorType = ConnectorType.TYPE_2,
                connectorCount = -1,
                isAc = true,
                isDc = false
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Connector count cannot be negative", e.message)
        }
    }
    
    @Test
    fun `test invalid voltage`() {
        try {
            ChargingPort(
                id = "port_1",
                connectorType = ConnectorType.TYPE_2,
                voltage = -10.0, // Invalid negative voltage
                isAc = true,
                isDc = false
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Voltage must be positive if provided", e.message)
        }
    }
    
    @Test
    fun `test invalid current`() {
        try {
            ChargingPort(
                id = "port_1",
                connectorType = ConnectorType.TYPE_2,
                current = -5.0, // Invalid negative current
                isAc = true,
                isDc = false
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Current must be positive if provided", e.message)
        }
    }
    
    @Test
    fun `test invalid power`() {
        try {
            ChargingPort(
                id = "port_1",
                connectorType = ConnectorType.TYPE_2,
                power = -10.0, // Invalid negative power
                isAc = true,
                isDc = false
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Power cannot be negative if provided", e.message)
        }
    }
    
    @Test
    fun `test invalid confidence`() {
        try {
            ChargingPort(
                id = "port_1",
                connectorType = ConnectorType.TYPE_2,
                isAc = true,
                isDc = false,
                confidence = 1.5 // Invalid confidence > 1.0
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Confidence must be between 0.0 and 1.0", e.message)
        }
    }
    
    @Test
    fun `test invalid AC DC flags`() {
        try {
            ChargingPort(
                id = "port_1",
                connectorType = ConnectorType.TYPE_2,
                isAc = false, // No AC
                isDc = false  // No DC - invalid!
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Port must support either AC or DC charging", e.message)
        }
    }
}