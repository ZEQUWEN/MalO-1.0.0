package com.example.payments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/* ------------------------------------------------------------- catalog -- */

@JsonClass(generateAdapter = true)
data class PlanDto(
    val id: String,
    val name: String,
    val periodDays: Int,
    val priceRub: String,
    val priceUsd: String
)

@JsonClass(generateAdapter = true)
data class CardOptionsDto(
    val enabled: Boolean = false,
    val provider: String? = null,
    val currency: String = "RUB",
    val brands: List<String> = emptyList(),
    val paymentMethods: List<String> = emptyList(),
    val supportsSavedCards: Boolean = false,
    val supportsSbp: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NetworkDto(
    val id: String,
    val title: String,
    val short: String,
    val color: String? = null,
    val explorer: String? = null,
    val minConfirmations: Int = 1
)

@JsonClass(generateAdapter = true)
data class AssetDto(
    val asset: String,
    val name: String,
    val color: String? = null,
    val decimals: Int = 2,
    val defaultNetwork: String? = null,
    val networks: List<NetworkDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CryptoOptionsDto(
    val enabled: Boolean = false,
    val provider: String? = null,
    val assets: List<AssetDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CatalogResponse(
    val ok: Boolean = false,
    val plan: PlanDto? = null,
    val card: CardOptionsDto? = null,
    val crypto: CryptoOptionsDto? = null
)

@JsonClass(generateAdapter = true)
data class PaymentMethodAvailabilityDto(
    val id: String,
    val provider: String
)

@JsonClass(generateAdapter = true)
data class PaymentMethodsResponse(
    val ok: Boolean = false,
    val methods: List<PaymentMethodAvailabilityDto> = emptyList()
)

/* -------------------------------------------------------- subscription -- */

@JsonClass(generateAdapter = true)
data class SubscriptionDto(
    val subscriptionId: String? = null,
    val userId: String? = null,
    val planId: String? = null,
    val planName: String? = null,
    val status: String = "inactive",
    val paymentMethod: String? = null,
    val autoRenew: Boolean = false,
    val cardId: String? = null,
    val currentPeriodStart: Long? = null,
    val currentPeriodEnd: Long? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val canceledAt: Long? = null,
    val lastTransactionId: String? = null,
    val lastAmount: String? = null,
    val signature: String? = null
) {
    val isActive: Boolean
        get() = status == "active" && (currentPeriodEnd ?: 0L) > System.currentTimeMillis()
}

@JsonClass(generateAdapter = true)
data class CardDto(
    val cardId: String,
    val brand: String? = null,
    val last4: String = "****",
    val first6: String? = null,
    val expiryMonth: String? = null,
    val expiryYear: String? = null,
    val holderName: String? = null,
    val issuerCountry: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = 0L
) {
    fun toSavedCard(): SavedCard = SavedCard(
        cardId = cardId,
        brandId = brand ?: CardBrand.UNKNOWN.id,
        last4 = last4,
        first6 = first6,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        holderName = holderName,
        isDefault = isDefault,
        createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis()
    )
}

@JsonClass(generateAdapter = true)
data class SubscriptionResponse(
    val ok: Boolean = false,
    val subscription: SubscriptionDto? = null,
    val cards: List<CardDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CardsResponse(
    val ok: Boolean = false,
    val cards: List<CardDto> = emptyList(),
    val subscription: SubscriptionDto? = null
)

/* -------------------------------------------------------------- crypto -- */

@JsonClass(generateAdapter = true)
data class CreateInvoiceRequest(
    val userId: String,
    val asset: String,
    val network: String
)

@JsonClass(generateAdapter = true)
data class CryptoInvoiceDto(
    val invoiceId: Long,
    val payloadId: String? = null,
    val userId: String? = null,
    val asset: String = "USDT",
    val network: String = "TRON",
    val networkTitle: String? = null,
    val amount: String = "0",
    val status: String = "active",
    val payUrl: String? = null,
    val miniAppUrl: String? = null,
    val webAppUrl: String? = null,
    val hash: String? = null,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val paidAt: Long? = null,
    val txHash: String? = null
) {
    val isPaid: Boolean get() = status.equals("paid", ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class InvoiceResponse(
    val ok: Boolean = false,
    val invoice: CryptoInvoiceDto? = null,
    val subscription: SubscriptionDto? = null
)

/* ---------------------------------------------------------------- card -- */

@JsonClass(generateAdapter = true)
data class CardCheckoutRequest(
    val userId: String,
    val saveCard: Boolean = true,
    /** YooKassa method: `bank_card` or `sbp`. */
    val paymentMethod: String = "bank_card",
    val returnUrl: String? = null,
    val idempotenceKey: String? = null
)

@JsonClass(generateAdapter = true)
data class CardPaymentDto(
    val paymentId: String,
    val status: String = "pending",
    val paymentMethod: String? = null,
    val confirmationUrl: String? = null,
    val amount: String? = null,
    val saveCard: Boolean = false
) {
    val isSucceeded: Boolean get() = status.equals("succeeded", ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class CardCheckoutResponse(
    val ok: Boolean = false,
    val payment: CardPaymentDto? = null,
    val subscription: SubscriptionDto? = null
)

@JsonClass(generateAdapter = true)
data class PaymentStatusResponse(
    val ok: Boolean = false,
    val payment: CardPaymentDto? = null,
    val subscription: SubscriptionDto? = null
)

@JsonClass(generateAdapter = true)
data class SimpleUserRequest(
    val userId: String,
    val immediate: Boolean? = null,
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class GatewayErrorBody(
    val ok: Boolean = false,
    @Json(name = "error") val error: GatewayErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class GatewayErrorDto(
    val code: String = "UNKNOWN",
    val message: String = "Неизвестная ошибка шлюза"
)
