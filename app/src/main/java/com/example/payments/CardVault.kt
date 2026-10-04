package com.example.payments

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * A card remembered for automatic renewals.
 *
 * Only non-sensitive descriptors live on the device: scheme, BIN head, last 4
 * digits and expiry. The reusable ЮKassa payment-method token stays on the
 * gateway and is never returned to or persisted by the APK. The PAN and CVC
 * are entered only on YooKassa's protected confirmation page.
 */
@JsonClass(generateAdapter = true)
data class SavedCard(
    val cardId: String,
    val brandId: String,
    val last4: String,
    val first6: String? = null,
    val expiryMonth: String? = null,
    val expiryYear: String? = null,
    val holderName: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional user-visible nickname, e.g. «Основная». */
    val label: String? = null
) {
    val brand: CardBrand get() = CardBrand.fromId(brandId)

    val maskedNumber: String get() = CardBrand.maskedNumber(last4, brand)

    val expiryFormatted: String
        get() {
            val month = expiryMonth?.padStart(2, '0') ?: "••"
            val year = expiryYear?.takeLast(2) ?: "••"
            return "$month/$year"
        }

    val isExpired: Boolean
        get() {
            val month = expiryMonth?.toIntOrNull() ?: return false
            val year = expiryYear?.takeLast(2)?.toIntOrNull() ?: return false
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR) % 100
            val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
            return year < currentYear || (year == currentYear && month < currentMonth)
        }
}

@JsonClass(generateAdapter = true)
data class CardVaultState(
    val cards: List<SavedCard> = emptyList(),
    val autoPayEnabled: Boolean = false
)

/**
 * Local app-private cache for the card mini app.
 *
 * The YooKassa gateway is the source of truth. This cache holds only the card
 * descriptors returned by it, so the app never implements its own card-entry
 * form and never stores a PAN, CVC, or a provider token.
 */
object CardVault {

    private const val PREFS = "malo_card_vault"
    private const val KEY_STATE = "vault_state_json"

    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val adapter by lazy { moshi.adapter(CardVaultState::class.java) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): CardVaultState {
        val json = prefs(context).getString(KEY_STATE, null) ?: return CardVaultState()
        return try {
            adapter.fromJson(json) ?: CardVaultState()
        } catch (_: Exception) {
            CardVaultState()
        }
    }

    private fun persist(context: Context, state: CardVaultState): CardVaultState {
        prefs(context).edit().putString(KEY_STATE, adapter.toJson(state)).apply()
        return state
    }

    fun cards(context: Context): List<SavedCard> =
        load(context).cards.sortedWith(compareByDescending<SavedCard> { it.isDefault }.thenByDescending { it.createdAt })

    fun defaultCard(context: Context): SavedCard? = cards(context).firstOrNull { it.isDefault }

    fun isAutoPayEnabled(context: Context): Boolean {
        val state = load(context)
        return state.autoPayEnabled && state.cards.any { it.isDefault }
    }

    fun setAutoPayEnabled(context: Context, enabled: Boolean): CardVaultState =
        persist(context, load(context).copy(autoPayEnabled = enabled))

    fun upsert(context: Context, card: SavedCard, makeDefault: Boolean = card.isDefault): SavedCard {
        val state = load(context)
        val withoutDuplicate = state.cards.filterNot { it.cardId == card.cardId }
        val normalized = if (makeDefault) withoutDuplicate.map { it.copy(isDefault = false) } else withoutDuplicate
        val stored = card.copy(isDefault = makeDefault || normalized.isEmpty())
        persist(
            context,
            state.copy(
                cards = normalized + stored,
                autoPayEnabled = state.autoPayEnabled || stored.isDefault
            )
        )
        return stored
    }

    fun makeDefault(context: Context, cardId: String): List<SavedCard> {
        val state = load(context)
        val updated = state.cards.map { it.copy(isDefault = it.cardId == cardId) }
        persist(context, state.copy(cards = updated, autoPayEnabled = true))
        return cards(context)
    }

    fun remove(context: Context, cardId: String): List<SavedCard> {
        val state = load(context)
        val removed = state.cards.firstOrNull { it.cardId == cardId }
        var remaining = state.cards.filterNot { it.cardId == cardId }
        if (removed?.isDefault == true && remaining.isNotEmpty()) {
            remaining = remaining.mapIndexed { index, card -> card.copy(isDefault = index == 0) }
        }
        persist(
            context,
            state.copy(cards = remaining, autoPayEnabled = state.autoPayEnabled && remaining.isNotEmpty())
        )
        return cards(context)
    }

    fun clear(context: Context) {
        persist(context, CardVaultState())
    }

    /** Replaces the local mirror with the authoritative list from the gateway. */
    fun syncFromGateway(context: Context, remote: List<SavedCard>): List<SavedCard> {
        val state = load(context)
        persist(
            context,
            state.copy(cards = remote, autoPayEnabled = state.autoPayEnabled && remote.any { it.isDefault })
        )
        return cards(context)
    }

}
