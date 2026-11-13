package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertFailsWith as assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessPaymentModelTest {

    @Test
    fun `test access type enum`() {
        assertEquals(AccessType.PUBLIC, AccessType.PUBLIC)
        assertEquals(AccessType.RESTRICTED, AccessType.RESTRICTED)
        assertEquals(AccessType.MEMBERSHIP, AccessType.MEMBERSHIP)
        assertEquals(AccessType.PRIVATE, AccessType.PRIVATE)
        assertEquals(AccessType.UNKNOWN, AccessType.UNKNOWN)
    }

    @Test
    fun `test access restriction enum`() {
        assertEquals(AccessRestriction.ORGANIZATION, AccessRestriction.ORGANIZATION)
        assertEquals(AccessRestriction.EMPLOYEE, AccessRestriction.EMPLOYEE)
        assertEquals(AccessRestriction.CUSTOMER, AccessRestriction.CUSTOMER)
        assertEquals(AccessRestriction.CREDENTIALS, AccessRestriction.CREDENTIALS)
        assertEquals(AccessRestriction.TIME_RESTRICTION, AccessRestriction.TIME_RESTRICTION)
        assertEquals(AccessRestriction.VEHICLE_RESTRICTION, AccessRestriction.VEHICLE_RESTRICTION)
        assertEquals(AccessRestriction.UNKNOWN, AccessRestriction.UNKNOWN)
    }

    @Test
    fun `test payment method enum`() {
        assertEquals(PaymentMethod.MOBILE_APP, PaymentMethod.MOBILE_APP)
        assertEquals(PaymentMethod.CARD, PaymentMethod.CARD)
        assertEquals(PaymentMethod.RFID, PaymentMethod.RFID)
        assertEquals(PaymentMethod.MEMBERSHIP, PaymentMethod.MEMBERSHIP)
        assertEquals(PaymentMethod.PREPAID, PaymentMethod.PREPAID)
        assertEquals(PaymentMethod.CASH, PaymentMethod.CASH)
        assertEquals(PaymentMethod.UNKNOWN, PaymentMethod.UNKNOWN)
    }

    @Test
    fun `test access payment info creation`() {
        val accessInfo = AccessPaymentInfo(
            accessType = AccessType.PUBLIC,
            accessRestrictions = setOf(AccessRestriction.CREDENTIALS),
            paymentMethods = setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP),
            requiresMembership = false,
            source = "ChargePoint",
            sourceReference = "12345",
            retrievedDate = 1634567890000L,
            confidence = 0.9
        )

        assertEquals(AccessType.PUBLIC, accessInfo.accessType)
        assertEquals(setOf(AccessRestriction.CREDENTIALS), accessInfo.accessRestrictions)
        assertEquals(setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP), accessInfo.paymentMethods)
        assertFalse(accessInfo.requiresMembership)
        assertEquals("ChargePoint", accessInfo.source)
        assertEquals("12345", accessInfo.sourceReference)
        assertEquals(1634567890000L, accessInfo.retrievedDate)
        assertEquals(0.9, accessInfo.confidence)
    }

    @Test
    fun `test access payment info with minimal data`() {
        val accessInfo = AccessPaymentInfo.createMinimal(AccessType.PRIVATE)

        assertEquals(AccessType.PRIVATE, accessInfo.accessType)
        assertEquals(null, accessInfo.accessRestrictions)
        assertEquals(null, accessInfo.paymentMethods)
        assertFalse(accessInfo.requiresMembership)
        assertEquals(null, accessInfo.source)
        assertEquals(null, accessInfo.sourceReference)
        assertEquals(null, accessInfo.retrievedDate)
        assertEquals(0.5, accessInfo.confidence)
    }

    @Test
    fun `test access payment info with public station`() {
        val accessInfo = AccessPaymentInfo.createPublicStation(
            setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP, PaymentMethod.RFID)
        )

        assertEquals(AccessType.PUBLIC, accessInfo.accessType)
        assertEquals(setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP, PaymentMethod.RFID), accessInfo.paymentMethods)
        assertFalse(accessInfo.requiresMembership)
    }

    @Test
    fun `test payment method acceptance`() {
        val accessInfo = AccessPaymentInfo(
            accessType = AccessType.PUBLIC,
            paymentMethods = setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP)
        )

        assertTrue(accessInfo.acceptsPaymentMethod(PaymentMethod.CARD))
        assertTrue(accessInfo.acceptsPaymentMethod(PaymentMethod.MOBILE_APP))
        assertFalse(accessInfo.acceptsPaymentMethod(PaymentMethod.RFID))
    }

    @Test
    fun `test membership required property`() {
        // Test with explicit requiresMembership = true
        val accessInfo1 = AccessPaymentInfo(
            accessType = AccessType.PUBLIC,
            requiresMembership = true
        )
        assertTrue(accessInfo1.isMembershipRequired)

        // Test with access type MEMBERSHIP 
        val accessInfo2 = AccessPaymentInfo(
            accessType = AccessType.MEMBERSHIP,
            requiresMembership = false
        )
        assertTrue(accessInfo2.isMembershipRequired)
    }

    @Test
    fun `test publicly accessible property`() {
        val publicAccess = AccessPaymentInfo(
            accessType = AccessType.PUBLIC
        )
        assertTrue(publicAccess.isPubliclyAccessible)

        val privateAccess = AccessPaymentInfo(
            accessType = AccessType.PRIVATE
        )
        assertFalse(privateAccess.isPubliclyAccessible)
    }

    @Test
    fun `test invalid confidence level`() {
        assertThrows<IllegalArgumentException> {
            AccessPaymentInfo(
                accessType = AccessType.PUBLIC,
                confidence = 1.5
            )
        }
    }

    @Test
    fun `test invalid empty access restrictions`() {
        assertThrows<IllegalArgumentException> {
            AccessPaymentInfo(
                accessType = AccessType.PUBLIC,
                accessRestrictions = setOf()
            )
        }
    }

    @Test
    fun `test invalid empty payment methods`() {
        assertThrows<IllegalArgumentException> {
            AccessPaymentInfo(
                accessType = AccessType.PUBLIC,
                paymentMethods = setOf()
            )
        }
    }

    @Test
    fun `test null access restrictions`() {
        val accessInfo = AccessPaymentInfo(
            accessType = AccessType.PUBLIC,
            accessRestrictions = null
        )

        assertEquals(null, accessInfo.accessRestrictions)
    }

    @Test
    fun `test null payment methods`() {
        val accessInfo = AccessPaymentInfo(
            accessType = AccessType.PUBLIC,
            paymentMethods = null
        )

        assertEquals(null, accessInfo.paymentMethods)
    }
}