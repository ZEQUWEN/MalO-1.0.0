package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.PaymentValidationResult
import com.example.util.SubscriptionValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionValidatorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SubscriptionValidator.revokeSubscription(context)
    }

    @Test
    fun testInvalidCardFailsLuhn() {
        // Random number that fails Luhn checksum
        val result = SubscriptionValidator.validateCardDetails("4242 4242 4242 4241", "12/28", "123")
        assertNotNull(result)
        assertTrue(result is PaymentValidationResult.Failure)
        assertEquals("ERR_LUHN_FAILED", (result as PaymentValidationResult.Failure).errorCode)
        assertFalse(SubscriptionValidator.isSubscriptionValid(context))
    }

    @Test
    fun testExpiredCardFails() {
        // Standard Luhn card with past expiration
        val result = SubscriptionValidator.validateCardDetails("4242 4242 4242 4242", "01/20", "123")
        assertNotNull(result)
        assertTrue(result is PaymentValidationResult.Failure)
        assertEquals("ERR_CARD_EXPIRED", (result as PaymentValidationResult.Failure).errorCode)
    }

    @Test
    fun testValidCardSucceedsAndSignsReceipt() {
        // Valid Luhn test card (Visa test 4242 4242 4242 4242)
        val result = SubscriptionValidator.processCardPayment(context, "4242 4242 4242 4242", "12/29", "777")
        assertTrue(result is PaymentValidationResult.Success)
        val success = result as PaymentValidationResult.Success
        assertNotNull(success.receipt.signature)
        assertTrue(SubscriptionValidator.isSubscriptionValid(context))

        val active = SubscriptionValidator.getActiveReceipt(context)
        assertNotNull(active)
        assertEquals("CARD", active?.paymentMethod)
        assertTrue(active!!.expiresAt > System.currentTimeMillis())
    }

    @Test
    fun testCryptoTxValidationAndDuplicatePrevention() {
        val validTx = "a8f4c2e6b9d10457382910fae5cb3498172049eaf5bc218390d4e5fa68c719e0"

        // First use: Valid
        val firstResult = SubscriptionValidator.processCryptoPayment(context, "USDT", "TRC20", validTx, "4.99 USDT")
        assertTrue(firstResult is PaymentValidationResult.Success)
        assertTrue(SubscriptionValidator.isSubscriptionValid(context))

        // Replay attack: Using the same TXID again must fail
        val replayResult = SubscriptionValidator.processCryptoPayment(context, "USDT", "TRC20", validTx, "4.99 USDT")
        assertTrue(replayResult is PaymentValidationResult.Failure)
        assertEquals("ERR_TX_ALREADY_USED", (replayResult as PaymentValidationResult.Failure).errorCode)
    }

    @Test
    fun testRevokeSubscription() {
        SubscriptionValidator.processCardPayment(context, "4242 4242 4242 4242", "12/29", "777")
        assertTrue(SubscriptionValidator.isSubscriptionValid(context))

        SubscriptionValidator.revokeSubscription(context)
        assertFalse(SubscriptionValidator.isSubscriptionValid(context))
        assertNull(SubscriptionValidator.getActiveReceipt(context))
    }
}
