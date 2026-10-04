package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.payments.CardBrand
import com.example.payments.CardVault
import com.example.payments.CryptoCatalog
import com.example.payments.SavedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The local vault is a descriptor cache; YooKassa remains the card source of truth. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardVaultTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CardVault.clear(context)
    }

    private fun savedCard(
        id: String,
        brand: CardBrand = CardBrand.MIR,
        last4: String = "4477",
        default: Boolean = true,
        expiry: String = "30"
    ) = SavedCard(
        cardId = id,
        brandId = brand.id,
        first6 = "220513",
        last4 = last4,
        expiryMonth = "12",
        expiryYear = expiry,
        isDefault = default
    )

    @Test
    fun `stores only YooKassa card descriptors`() {
        val card = CardVault.upsert(context, savedCard("gateway-card-1"))

        assertEquals(CardBrand.MIR, card.brand)
        assertEquals("4477", card.last4)
        assertEquals("220513", card.first6)
        assertEquals("12/30", card.expiryFormatted)
        assertTrue(card.isDefault)
        // A descriptor contains no PAN, CVC, or acquirer token.
        val serialized = CardVault.load(context).toString()
        assertFalse(serialized.contains("220513874477"))
    }

    @Test
    fun `only one card can be default`() {
        CardVault.upsert(context, savedCard("card-1", CardBrand.VISA, "4242"))
        val second = CardVault.upsert(context, savedCard("card-2", CardBrand.MASTERCARD, "0011"))

        val cards = CardVault.cards(context)
        assertEquals(2, cards.size)
        assertEquals(1, cards.count { it.isDefault })
        assertEquals(second.cardId, CardVault.defaultCard(context)?.cardId)
    }

    @Test
    fun `make default moves the flag`() {
        val first = CardVault.upsert(context, savedCard("card-1", CardBrand.VISA, "4242"))
        CardVault.upsert(context, savedCard("card-2", CardBrand.MASTERCARD, "0011"))

        CardVault.makeDefault(context, first.cardId)
        assertEquals(first.cardId, CardVault.defaultCard(context)?.cardId)
    }

    @Test
    fun `removing the last card disables auto pay`() {
        val card = CardVault.upsert(context, savedCard("card-1", CardBrand.VISA, "4242"))
        CardVault.setAutoPayEnabled(context, true)
        assertTrue(CardVault.isAutoPayEnabled(context))

        CardVault.remove(context, card.cardId)
        assertTrue(CardVault.cards(context).isEmpty())
        assertFalse(CardVault.isAutoPayEnabled(context))
        assertNull(CardVault.defaultCard(context))
    }

    @Test
    fun `removing the default promotes another card`() {
        val first = CardVault.upsert(context, savedCard("card-1", CardBrand.VISA, "4242"))
        val second = CardVault.upsert(context, savedCard("card-2", CardBrand.MASTERCARD, "0011"))

        CardVault.remove(context, second.cardId)
        assertEquals(first.cardId, CardVault.defaultCard(context)?.cardId)
    }

    @Test
    fun `expired card is flagged`() {
        val card = CardVault.upsert(context, savedCard("card-1", expiry = "20"))
        assertTrue(card.isExpired)
    }

    @Test
    fun `crypto catalog exposes the expected networks`() {
        val usdt = CryptoCatalog.asset("USDT")!!
        val ids = usdt.networks.map { it.id }
        assertTrue(ids.containsAll(listOf("TRON", "TON", "ETH", "SOLANA")))
        assertTrue(CryptoCatalog.isAllowed("USDT", "TRON"))
        assertFalse(CryptoCatalog.isAllowed("BTC", "TON"))
        assertEquals("TRON", usdt.defaultNetwork().id)
        assertEquals(
            "https://tronscan.org/#/transaction/abc",
            CryptoCatalog.explorerUrl("TRON", "abc")
        )
    }
}
