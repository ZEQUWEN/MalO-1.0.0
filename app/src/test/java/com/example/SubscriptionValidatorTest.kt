package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.SubscriptionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Local receipts are created only from a server-confirmed gateway entitlement. */
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
    fun `gateway entitlement is signed and becomes active`() {
        val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        val receipt = SubscriptionValidator.activateFromGateway(
            context = context,
            transactionId = "yk-payment-123",
            paymentMethod = "CARD",
            amountPaid = "499.00 RUB",
            expiresAt = expiry
        )

        assertNotNull(receipt.signature)
        assertTrue(SubscriptionValidator.isSubscriptionValid(context))
        val active = SubscriptionValidator.getActiveReceipt(context)
        assertNotNull(active)
        assertEquals("CARD", active?.paymentMethod)
        assertEquals("yk-payment-123", active?.transactionId)
        assertEquals(expiry, active?.expiresAt)
    }

    @Test
    fun `expired gateway entitlement is rejected`() {
        SubscriptionValidator.activateFromGateway(
            context = context,
            transactionId = "yk-payment-expired",
            paymentMethod = "SBP",
            amountPaid = "499.00 RUB",
            expiresAt = System.currentTimeMillis() - 1
        )

        assertFalse(SubscriptionValidator.isSubscriptionValid(context))
        assertNull(SubscriptionValidator.getActiveReceipt(context))
    }

    @Test
    fun `revoke removes the cached entitlement`() {
        SubscriptionValidator.activateFromGateway(
            context = context,
            transactionId = "yk-payment-456",
            paymentMethod = "SBP",
            amountPaid = "499.00 RUB",
            expiresAt = System.currentTimeMillis() + 60_000
        )
        assertTrue(SubscriptionValidator.isSubscriptionValid(context))

        SubscriptionValidator.revokeSubscription(context)
        assertFalse(SubscriptionValidator.isSubscriptionValid(context))
        assertNull(SubscriptionValidator.getActiveReceipt(context))
    }
}
