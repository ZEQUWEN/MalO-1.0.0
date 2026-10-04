package com.example

import com.example.payments.CardBrand
import com.example.payments.CardInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payment-system detection, PAN formatting and field validation.
 * These run on the JVM — no Android framework involved.
 */
class CardBrandTest {

    @Test
    fun `detects visa by leading four`() {
        assertEquals(CardBrand.VISA, CardBrand.detect("4"))
        assertEquals(CardBrand.VISA, CardBrand.detect("4242 4242 4242 4242"))
    }

    @Test
    fun `detects mastercard in both ranges`() {
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("5536"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("2221000000000009"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("2720 9900 0000 0000"))
    }

    @Test
    fun `detects mir before mastercard two series`() {
        assertEquals(CardBrand.MIR, CardBrand.detect("2200"))
        assertEquals(CardBrand.MIR, CardBrand.detect("2202 2012 3456 4477"))
        assertEquals(CardBrand.MIR, CardBrand.detect("2204"))
        assertEquals(CardBrand.MIR, CardBrand.detect("2205"))
        assertEquals(CardBrand.MIR, CardBrand.detect("22051387"))
        // Mastercard's 2-series starts at 2221, never at a generic 2-prefix.
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect("2206 0000"))
    }

    @Test
    fun `detects amex unionpay and jcb`() {
        assertEquals(CardBrand.AMEX, CardBrand.detect("3782 822463 10005"))
        assertEquals(CardBrand.UNIONPAY, CardBrand.detect("6250947000000014"))
        assertEquals(CardBrand.JCB, CardBrand.detect("3566002020360505"))
    }

    @Test
    fun `two series stays unknown until disambiguated`() {
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect("2"))
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect("22"))
    }

    @Test
    fun `empty input is unknown`() {
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect(""))
        assertFalse(CardBrand.UNKNOWN.isKnown)
    }

    @Test
    fun `luhn accepts valid and rejects mistyped numbers`() {
        assertTrue(CardBrand.luhnValid("4242424242424242"))
        assertTrue(CardBrand.luhnValid("5555555555554444"))
        assertFalse(CardBrand.luhnValid("4242424242424243"))
    }

    @Test
    fun `completeness requires both length and checksum`() {
        assertTrue(CardBrand.isComplete("4242 4242 4242 4242"))
        assertFalse(CardBrand.isComplete("4242 4242 4242"))
        assertFalse(CardBrand.isComplete("4242 4242 4242 4243"))
        assertTrue(CardBrand.isComplete("378282246310005")) // AmEx, 15 digits
    }

    @Test
    fun `formatting groups digits per scheme`() {
        assertEquals("4242 4242 4242 4242", CardBrand.format("4242424242424242"))
        assertEquals("3782 822463 10005", CardBrand.format("378282246310005"))
        assertEquals("4242 42", CardBrand.format("424242"))
    }

    @Test
    fun `masked number keeps only the last four digits`() {
        assertEquals("•••• •••• •••• 4477", CardBrand.maskedNumber("4477", CardBrand.MIR))
    }

    @Test
    fun `sanitize number drops separators and clamps length`() {
        assertEquals("4242424242424242", CardInput.sanitizeNumber("4242-4242 4242/4242"))
        assertEquals(15, CardInput.sanitizeNumber("3782 8224 6310 0051 234").length)
    }

    @Test
    fun `sanitize expiry auto prefixes impossible months`() {
        assertEquals("09", CardInput.sanitizeExpiry("9"))
        assertEquals("12", CardInput.sanitizeExpiry("12"))
        assertEquals("1228", CardInput.sanitizeExpiry("12/28"))
        assertEquals("1230", CardInput.sanitizeExpiry("1330"))
    }

    @Test
    fun `expiry validation rejects past dates`() {
        assertFalse(CardInput.expiryValid("0120"))
        assertFalse(CardInput.expiryValid("13"))
        assertTrue(CardInput.expiryValid("1299"))
    }

    @Test
    fun `cvc length follows the scheme`() {
        assertTrue(CardInput.cvcValid("123", CardBrand.VISA))
        assertFalse(CardInput.cvcValid("1234", CardBrand.VISA))
        assertTrue(CardInput.cvcValid("1234", CardBrand.AMEX))
        assertEquals("1234", CardInput.sanitizeCvc("12345", CardBrand.AMEX))
        assertEquals("123", CardInput.sanitizeCvc("12345", CardBrand.MIR))
    }

    @Test
    fun `holder name is normalised to upper case letters`() {
        assertEquals("IVAN IVANOV", CardInput.sanitizeHolder("Ivan Ivanov 123"))
    }

    @Test
    fun `formatted expiry inserts the slash`() {
        assertEquals("12/28", CardInput.formattedExpiry("1228"))
        assertEquals("1", CardInput.formattedExpiry("1"))
    }
}
