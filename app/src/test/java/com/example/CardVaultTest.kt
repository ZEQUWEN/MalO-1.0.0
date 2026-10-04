package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.payments.CardBrand
import com.example.payments.CardVault
import com.example.payments.CryptoCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardVaultTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CardVault.clear(context)
    }

    @Test
    fun `stores only non sensitive descriptors`() {
        val card = CardVault.rememberFromInput(
            context = context,
            cardNumber = "2202 2012 3456 4477",
            expiryDigits = "1230",
            holderName = "IVAN IVANOV"
        )

        assertEquals(CardBrand.MIR, card.brand)
        assertEquals("4477", card.last4)
        assertEquals("220220", card.first6)
        assertEquals("12/30", card.expiryFormatted)
        assertTrue(card.isDefault)
        // The PAN itself must not be recoverable from the vault.
        val serialized = CardVault.load(context).toString()
        assertFalse(serialized.contains("2202201234564477"))
    }

    @Test
    fun `only one card can be default`() {
        CardVault.rememberFromInput(context, "4242424242424242", "1230", "A A")
        val second = CardVault.rememberFromInput(context, "5536913757200011", "1130", "B B")

        val cards = CardVault.cards(context)
        assertEquals(2, cards.size)
        assertEquals(1, cards.count { it.isDefault })
        assertEquals(second.cardId, CardVault.defaultCard(context)?.cardId)
    }

    @Test
    fun `make default moves the flag`() {
        val first = CardVault.rememberFromInput(context, "4242424242424242", "1230", "A A")
        CardVault.rememberFromInput(context, "5536913757200011", "1130", "B B")

        CardVault.makeDefault(context, first.cardId)
        assertEquals(first.cardId, CardVault.defaultCard(context)?.cardId)
    }

    @Test
    fun `removing the last card disables auto pay`() {
        val card = CardVault.rememberFromInput(context, "4242424242424242", "1230", "A A")
        CardVault.setAutoPayEnabled(context, true)
        assertTrue(CardVault.isAutoPayEnabled(context))

        CardVault.remove(context, card.cardId)
        assertTrue(CardVault.cards(context).isEmpty())
        assertFalse(CardVault.isAutoPayEnabled(context))
        assertNull(CardVault.defaultCard(context))
    }

    @Test
    fun `removing the default promotes another card`() {
        val first = CardVault.rememberFromInput(context, "4242424242424242", "1230", "A A")
        val second = CardVault.rememberFromInput(context, "5536913757200011", "1130", "B B")

        CardVault.remove(context, second.cardId)
        assertEquals(first.cardId, CardVault.defaultCard(context)?.cardId)
    }

    @Test
    fun `expired card is flagged`() {
        val card = CardVault.rememberFromInput(context, "4242424242424242", "0120", "A A")
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
