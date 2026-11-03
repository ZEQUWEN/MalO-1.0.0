package com.example.payments

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import com.example.api.DeepSeekMessage
import com.example.api.MaloBurnResponse
import com.example.api.MaloChatResponse
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@JsonClass(generateAdapter = true) data class EmailRequest(val email: String)
@JsonClass(generateAdapter = true) data class CodeRequest(val email: String, val code: String)
@JsonClass(generateAdapter = true) data class EmptyRequest(val unused: String? = null)
@JsonClass(generateAdapter = true) data class V2ChatRequest(val messages: List<DeepSeekMessage>)
@JsonClass(generateAdapter = true) data class IntentStatusRequest(val paymentToken: String)
@JsonClass(generateAdapter = true) data class IntentStatusResponse(val ok: Boolean = false, val status: String)
@JsonClass(generateAdapter = true) data class OkResponse(val ok: Boolean = false)
@JsonClass(generateAdapter = true) data class SessionResponse(val ok: Boolean = false, val sessionToken: String)
@JsonClass(generateAdapter = true) data class KoFiIntentResponse(
    val ok: Boolean = false, val paymentToken: String, val paymentUrl: String,
    val amount: String, val currency: String, val expiresAt: String
)

private interface AccountApi {
    @POST("api/v2/auth/request-code") suspend fun requestCode(@Body request: EmailRequest): Response<OkResponse>
    @POST("api/v2/auth/verify-code") suspend fun verifyCode(@Body request: CodeRequest): Response<SessionResponse>
    @POST("api/v2/auth/logout") suspend fun logout(@Header("Authorization") auth: String, @Body body: EmptyRequest): Response<OkResponse>
    @GET("api/v2/subscription") suspend fun subscription(@Header("Authorization") auth: String): Response<SubscriptionResponse>
    @POST("api/v2/kofi/intents") suspend fun intent(@Header("Authorization") auth: String, @Body body: EmptyRequest): Response<KoFiIntentResponse>
    @POST("api/v2/kofi/intent-status") suspend fun intentStatus(@Header("Authorization") auth: String, @Body body: IntentStatusRequest): Response<IntentStatusResponse>
    @POST("api/v2/malo/chat") suspend fun chat(@Header("Authorization") auth: String, @Body body: V2ChatRequest): Response<MaloChatResponse>
    @POST("api/v2/malo/burn") suspend fun burn(@Header("Authorization") auth: String, @Body body: EmptyRequest): Response<MaloBurnResponse>
}

/** Keystore key never leaves this installation; encrypted preferences are excluded from backup. */
private object SessionStore {
    private const val PREFS = "malo_account_session"
    private const val ALIAS = "malo_account_session_key_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    @Synchronized fun save(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("token", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) {
            throw IllegalStateException("Could not save session")
        }
    }
    @Synchronized fun load(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = prefs.getString("token", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            prefs.edit().remove("token").apply()
            null
        }
    }
    @Synchronized fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("token").apply() }
}

object AccountGateway {
    private val baseUrl by lazy {
        val value = runCatching { BuildConfig.MALO_GATEWAY_URL }.getOrDefault("").trim().trimEnd('/')
        if (value.startsWith("https://")) "$value/" else ""
    }
    val isConfigured get() = baseUrl.isNotEmpty()
    private val moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }
    private val api: AccountApi? by lazy {
        if (!isConfigured) null else Retrofit.Builder().baseUrl(baseUrl)
            .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build())
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(AccountApi::class.java)
    }
    fun hasSession(context: Context): Boolean = SessionStore.load(context) != null
    fun sessionFingerprint(context: Context): String? = SessionStore.load(context)?.let { token ->
        java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    fun clearSession(context: Context) = SessionStore.clear(context)
    private fun auth(context: Context): String? = SessionStore.load(context)?.let { "Bearer $it" }
    private suspend fun <T : Any> call(block: suspend (AccountApi) -> Response<T>): GatewayResult<T> {
        val service = api ?: return GatewayResult.NotConfigured
        return withContext(Dispatchers.IO) {
            try {
                val result = block(service)
                val body = result.body()
                if (result.isSuccessful && body != null) GatewayResult.Success(body)
                else {
                    val raw = result.errorBody()?.string()
                    val error = raw?.let { runCatching { moshi.adapter(GatewayErrorBody::class.java).fromJson(it)?.error }.getOrNull() }
                    GatewayResult.Error(error?.code ?: "HTTP_${result.code()}", error?.message ?: "Ошибка сервера ${result.code()}")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { PaymentGateway.classifyNetworkError(e) }
        }
    }
    suspend fun requestCode(email: String): GatewayResult<OkResponse> = call { it.requestCode(EmailRequest(email)) }
    suspend fun login(context: Context, email: String, code: String): GatewayResult<SessionResponse> {
        val result = call { it.verifyCode(CodeRequest(email, code)) }
        if (result is GatewayResult.Success) {
            try {
                SessionStore.save(context, result.data.sessionToken)
                com.example.util.SubscriptionValidator.revokeSubscription(context)
            }
            catch (_: Exception) { return GatewayResult.Error("SESSION_STORAGE_ERROR", "Не удалось безопасно сохранить вход на устройстве") }
        }
        return result
    }
    suspend fun logout(context: Context): GatewayResult<OkResponse> {
        val bearer = auth(context) ?: return GatewayResult.Error("UNAUTHORIZED", "Войдите в аккаунт")
        val result = call { it.logout(bearer, EmptyRequest()) }
        clearSession(context)
        return result
    }
    suspend fun subscription(context: Context): GatewayResult<SubscriptionResponse> {
        val bearer = auth(context) ?: return GatewayResult.Error("UNAUTHORIZED", "Войдите в аккаунт")
        return call { it.subscription(bearer) }
    }
    suspend fun intent(context: Context): GatewayResult<KoFiIntentResponse> {
        val bearer = auth(context) ?: return GatewayResult.Error("UNAUTHORIZED", "Войдите в аккаунт")
        return call { it.intent(bearer, EmptyRequest()) }
    }
    suspend fun intentStatus(context: Context, paymentToken: String): GatewayResult<IntentStatusResponse> {
        val bearer = auth(context) ?: return GatewayResult.Error("UNAUTHORIZED", "Войдите в аккаунт")
        return call { it.intentStatus(bearer, IntentStatusRequest(paymentToken)) }
    }
    suspend fun chat(context: Context, messages: List<DeepSeekMessage>): GatewayResult<MaloChatResponse> {
        val bearer = auth(context) ?: return GatewayResult.Error("UNAUTHORIZED", "Войдите в аккаунт")
        return call { it.chat(bearer, V2ChatRequest(messages)) }
    }
    suspend fun burn(context: Context): GatewayResult<MaloBurnResponse> {
        val bearer = auth(context) ?: return GatewayResult.Error("UNAUTHORIZED", "Войдите в аккаунт")
        return call { it.burn(bearer, EmptyRequest()) }
    }
}
