package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StationMetadataTest {
    
    @Test
    fun `test minimal station metadata creation`() {
        val metadata = StationMetadata.createMinimal(
            canonicalName = "Test Charging Station",
            accessType = AccessType.PUBLIC
        )
        
        assertEquals("Test Charging Station", metadata.canonicalName)
        assertEquals(AccessType.PUBLIC, metadata.accessType)
        assertNull(metadata.operatorId)
        assertNull(metadata.operatorName)
        assertNull(metadata.networkId)
        assertNull(metadata.networkName)
        assertNull(metadata.accessRestrictions)
        assertNull(metadata.openingHours)
        assertFalse(metadata.is247)
        assertNull(metadata.paymentMethods)
        assertNull(metadata.amenities)
        assertNull(metadata.contactPhone)
        assertNull(metadata.contactEmail)
        assertNull(metadata.website)
        assertNull(metadata.notes)
        assertNull(metadata.source)
        assertNull(metadata.sourceReference)
        assertNull(metadata.retrievedDate)
        assertEquals(0.5, metadata.confidence)
        assertEquals(ValidationStatus.UNKNOWN, metadata.validationStatus)
    }
    
    @Test
    fun `test full station metadata`() {
        val metadata = StationMetadata(
            canonicalName = "Test Charging Station",
            operatorId = "operator_123",
            operatorName = "Test Operator",
            networkId = "network_456", 
            networkName = "Test Network",
            accessType = AccessType.PRIVATE,
            accessRestrictions = setOf(AccessRestriction.CUSTOMER),
            openingHours = OpeningHours(
                regularHours = mapOf(
                    DayOfWeek.MONDAY to HourRange(8, 17)
                ),
                exceptionalHours = listOf(
                    ExceptionalHour(java.util.Date(), HourRange(0, 23), "Christmas")
                )
            ),
            is247 = false,
            paymentMethods = setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP),
            amenities = setOf(Amenity.PARKING, Amenity.WIFI),
            contactPhone = "+98 123 456 7890",
            contactEmail = "info@test.com",
            website = "https://test.com",
            notes = "Test station for development",
            source = "Test Provider",
            sourceReference = "ref_789",
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.9,
            validationStatus = ValidationStatus.VALIDATED
        )
        
        assertEquals("Test Charging Station", metadata.canonicalName)
        assertEquals("operator_123", metadata.operatorId)
        assertEquals("Test Operator", metadata.operatorName)
        assertEquals("network_456", metadata.networkId)
        assertEquals("Test Network", metadata.networkName)
        assertEquals(AccessType.PRIVATE, metadata.accessType)
        assertEquals(setOf(AccessRestriction.CUSTOMER), metadata.accessRestrictions)
        assertNotNull(metadata.openingHours)
        assertFalse(metadata.is247)
        assertEquals(setOf(PaymentMethod.CARD, PaymentMethod.MOBILE_APP), metadata.paymentMethods)
        assertEquals(setOf(Amenity.PARKING, Amenity.WIFI), metadata.amenities)
        assertEquals("+98 123 456 7890", metadata.contactPhone)
        assertEquals("info@test.com", metadata.contactEmail)
        assertEquals("https://test.com", metadata.website)
        assertEquals("Test station for development", metadata.notes)
        assertEquals("Test Provider", metadata.source)
        assertEquals("ref_789", metadata.sourceReference)
        assertNotNull(metadata.retrievedDate)
        assertEquals(0.9, metadata.confidence)
        assertEquals(ValidationStatus.VALIDATED, metadata.validationStatus)
    }
    
    @Test
    fun `test has payment methods`() {
        // With payment methods
        val metadataWithPayment = StationMetadata.createMinimal(
            canonicalName = "Test Station",
            accessType = AccessType.PUBLIC
        ).copy(paymentMethods = setOf(PaymentMethod.CARD))
        
        assertTrue(metadataWithPayment.hasPaymentMethods)
        
        // Without payment methods
        val metadataWithoutPayment = StationMetadata.createMinimal(
            canonicalName = "Test Station", 
            accessType = AccessType.PUBLIC
        ).copy(paymentMethods = null)
        
        assertFalse(metadataWithoutPayment.hasPaymentMethods)
    }
    
    @Test
    fun `test has amenities`() {
        // With amenities
        val metadataWithAmenities = StationMetadata.createMinimal(
            canonicalName = "Test Station",
            accessType = AccessType.PUBLIC
        ).copy(amenities = setOf(Amenity.PARKING))
        
        assertTrue(metadataWithAmenities.hasAmenities)
        
        // Without amenities
        val metadataWithoutAmenities = StationMetadata.createMinimal(
            canonicalName = "Test Station",
            accessType = AccessType.PUBLIC
        ).copy(amenities = null)
        
        assertFalse(metadataWithoutAmenities.hasAmenities)
    }
    
    @Test
    fun `test has contact info`() {
        // With contact info
        val metadataWithContact = StationMetadata.createMinimal(
            canonicalName = "Test Station",
            accessType = AccessType.PUBLIC
        ).copy(contactPhone = "+98 123 456 7890")
        
        assertTrue(metadataWithContact.hasContactInfo)
        
        // Without contact info
        val metadataWithoutContact = StationMetadata.createMinimal(
            canonicalName = "Test Station",
            accessType = AccessType.PUBLIC
        ).copy(contactPhone = null)
        
        assertFalse(metadataWithoutContact.hasContactInfo)
    }
    
    @Test
    fun `test invalid canonical name`() {
        try {
            StationMetadata.createMinimal(
                canonicalName = "",
                accessType = AccessType.PUBLIC
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Canonical name cannot be blank", e.message)
        }
    }
    
    @Test
    fun `test invalid confidence level`() {
        try {
            StationMetadata.createMinimal(
                canonicalName = "Test Station",
                accessType = AccessType.PUBLIC
            ).copy(confidence = 1.5)
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Confidence must be between 0.0 and 1.0", e.message)
        }
    }
    
    @Test
    fun `test invalid access restrictions`() {
        try {
            StationMetadata.createMinimal(
                canonicalName = "Test Station",
                accessType = AccessType.PUBLIC
            ).copy(accessRestrictions = emptySet())
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Access restrictions cannot be empty if provided", e.message)
        }
    }
    
    @Test
    fun `test opening hours with invalid data`() {
        try {
            OpeningHours(
                regularHours = mapOf(DayOfWeek.MONDAY to HourRange(25, 10)),
                exceptionalHours = null
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Start hour must be between 0 and 23", e.message)
        }
    }
    
    @Test
    fun `test hour range with invalid start`() {
        try {
            HourRange(25, 10)
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Start hour must be between 0 and 23", e.message)
        }
    }
    
    @Test
    fun `test hour range with invalid end`() {
        try {
            HourRange(10, 25)
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("End hour must be between 0 and 23", e.message)
        }
    }
    
    @Test
    fun `test hour range with start after end`() {
        try {
            HourRange(15, 10)
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Start hour cannot be after end hour", e.message)
        }
    }
}