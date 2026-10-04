package com.example.util

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.security.MessageDigest
import java.util.UUID

@JsonClass(generateAdapter = true)
data class SubscriptionReceipt(
    val subscriptionId: String,
    val planName: String,
    val paymentMethod: String,
    val transactionId: String,
    val amountPaid: String,
    val activatedAt: Long,
    val expiresAt: Long,
    val signature: String
)

object SubscriptionValidator {

    private const val PREFS_NAME = "malo_subscription_secure_prefs"
    private const val KEY_RECEIPT_JSON = "active_receipt_json"
    private const val HMAC_SALT = "SCP-1471-SECURE-VALIDATION-SALT-DEEPSEEK-PRO-987123"

    private val moshi by lazy { Moshi.Builder().build() }
    private val receiptAdapter by lazy { moshi.adapter(SubscriptionReceipt::class.java) }

    /**
     * Mirrors an entitlement confirmed by the payment gateway into a locally
     * signed receipt, so the app keeps working offline until the paid period
     * runs out. This function is only called with a server-confirmed result;
     * the APK has no local card or transaction "success" path.
     */
    fun activateFromGateway(
        context: Context,
        transactionId: String,
        paymentMethod: String,
        amountPaid: String,
        expiresAt: Long
    ): SubscriptionReceipt {
        val now = System.currentTimeMillis()
        val subId = UUID.randomUUID().toString()
        val receipt = SubscriptionReceipt(
            subscriptionId = subId,
            planName = "Pro (DeepSeek AI)",
            paymentMethod = paymentMethod,
            transactionId = transactionId,
            amountPaid = amountPaid,
            activatedAt = now,
            expiresAt = expiresAt,
            signature = computeHmac(subId, paymentMethod, transactionId, now, expiresAt)
        )
        saveReceipt(context, receipt)
        return receipt
    }

    private fun computeHmac(
        subId: String,
        paymentMethod: String,
        txId: String,
        activatedAt: Long,
        expiresAt: Long
    ): String {
        val payload = "$subId|$paymentMethod|$txId|$activatedAt|$expiresAt|$HMAC_SALT"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that the subscription receipt is authentic and unexpired.
     */
    fun isSubscriptionValid(context: Context): Boolean {
        val receipt = getActiveReceipt(context) ?: return false
        val now = System.currentTimeMillis()

        if (now > receipt.expiresAt) {
            revokeSubscription(context)
            return false
        }

        val expectedSig = computeHmac(
            receipt.subscriptionId,
            receipt.paymentMethod,
            receipt.transactionId,
            receipt.activatedAt,
            receipt.expiresAt
        )

        val isValid = receipt.signature.equals(expectedSig, ignoreCase = true)
        if (!isValid) {
            revokeSubscription(context)
        }
        return isValid
    }

    fun getActiveReceipt(context: Context): SubscriptionReceipt? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECEIPT_JSON, null) ?: return null
        return try {
            receiptAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveReceipt(context: Context, receipt: SubscriptionReceipt) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = receiptAdapter.toJson(receipt)
        prefs.edit().putString(KEY_RECEIPT_JSON, json).apply()

        // Also sync with worker prefs
        val globalPrefs = context.getSharedPreferences("malo_notification_prefs", Context.MODE_PRIVATE)
        globalPrefs.edit().putBoolean("is_pro_user", true).apply()
    }

    fun revokeSubscription(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECEIPT_JSON).apply()

        val globalPrefs = context.getSharedPreferences("malo_notification_prefs", Context.MODE_PRIVATE)
        globalPrefs.edit().putBoolean("is_pro_user", false).apply()
    }
}
