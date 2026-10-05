package com.example.payments

import android.content.Context
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Retrofit description of `server/` — the MalO payment gateway. */
interface PaymentGatewayApi {

    @GET("api/catalog")
    suspend fun catalog(): Response<CatalogResponse>

    @GET("api/subscription")
    suspend fun subscription(@Query("userId") userId: String): Response<SubscriptionResponse>

    @POST("api/subscription/cancel")
    suspend fun cancelSubscription(@Body body: SimpleUserRequest): Response<SubscriptionResponse>

    @POST("api/subscription/resume")
    suspend fun resumeSubscription(@Body body: SimpleUserRequest): Response<SubscriptionResponse>

    @POST("api/subscription/charge")
    suspend fun chargeNow(@Body body: SimpleUserRequest): Response<SubscriptionResponse>

    @POST("api/crypto/invoices")
    suspend fun createInvoice(@Body body: CreateInvoiceRequest): Response<InvoiceResponse>

    @GET("api/crypto/invoices/{invoiceId}")
    suspend fun invoiceStatus(
        @Path("invoiceId") invoiceId: Long,
        @Query("userId") userId: String
    ): Response<InvoiceResponse>

    @POST("api/checkout")
    suspend fun cardCheckout(@Body body: CardCheckoutRequest): Response<CardCheckoutResponse>

    @GET("api/cards/payments/{paymentId}")
    suspend fun cardPaymentStatus(
        @Path("paymentId") paymentId: String,
        @Query("userId") userId: String
    ): Response<PaymentStatusResponse>

    @GET("api/cards")
    suspend fun cards(@Query("userId") userId: String): Response<CardsResponse>

    @POST("api/cards/{cardId}/default")
    suspend fun makeCardDefault(
        @Path("cardId") cardId: String,
        @Body body: SimpleUserRequest
    ): Response<CardsResponse>

    @DELETE("api/cards/{cardId}")
    suspend fun deleteCard(
        @Path("cardId") cardId: String,
        @Query("userId") userId: String
    ): Response<CardsResponse>
}

/** Result wrapper so the UI can distinguish "offline" from "declined". */
sealed class GatewayResult<out T> {
    data class Success<T>(val data: T) : GatewayResult<T>()
    data class Error(val code: String, val message: String) : GatewayResult<Nothing>()
    data object NotConfigured : GatewayResult<Nothing>()
}

/**
 * Thin client around [PaymentGatewayApi].
 *
 * `MALO_GATEWAY_URL` / `MALO_CLIENT_KEY` come from `.env` through the Secrets
 * Gradle plugin. When the URL is a placeholder checkout is disabled; the app
 * never substitutes an offline or test-card payment for a real transaction.
 */
object PaymentGateway {

    private const val PREFS = "malo_gateway_prefs"
    private const val KEY_USER_ID = "gateway_user_id"

    private val baseUrl: String by lazy {
        val raw = runCatching { BuildConfig.MALO_GATEWAY_URL }.getOrDefault("")
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank() || trimmed.startsWith("MY_") || !trimmed.startsWith("http")) "" else "$trimmed/"
    }

    private val clientKey: String by lazy {
        val raw = runCatching { BuildConfig.MALO_CLIENT_KEY }.getOrDefault("")
        if (raw.startsWith("MY_")) "" else raw
    }

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    private val moshi: Moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                Interceptor { chain ->
                    val builder = chain.request().newBuilder()
                        .addHeader("Accept", "application/json")
                    if (clientKey.isNotBlank()) builder.addHeader("X-MalO-Client-Key", clientKey)
                    chain.proceed(builder.build())
                }
            )
            .build()
    }

    private val api: PaymentGatewayApi? by lazy {
        if (!isConfigured) null
        else Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PaymentGatewayApi::class.java)
    }

    /** Stable anonymous installation id — no account system required. */
    fun userId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }
        val generated = "malo-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_USER_ID, generated).apply()
        return generated
    }

    private suspend fun <T : Any> call(block: suspend (PaymentGatewayApi) -> Response<T>): GatewayResult<T> {
        val service = api ?: return GatewayResult.NotConfigured
        return withContext(Dispatchers.IO) {
            try {
                val response = block(service)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    GatewayResult.Success(body)
                } else {
                    val raw = response.errorBody()?.string()
                    val parsed = raw?.let {
                        runCatching { moshi.adapter(GatewayErrorBody::class.java).fromJson(it) }.getOrNull()
                    }
                    GatewayResult.Error(
                        code = parsed?.error?.code ?: "HTTP_${response.code()}",
                        message = parsed?.error?.message ?: "Шлюз вернул ошибку ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                classifyNetworkError(e)
            }
        }
    }

    /**
     * «Failed to connect to malo.up.railway.app» is never a provider decline:
     * the request never reached the gateway, so CryptoBot was never called.
     * Distinguish the three причины so the user is not told to retry a payment
     * when the real problem is DNS, a dead deployment, or a blocked TLS path.
     */
    internal fun classifyNetworkError(e: Throwable): GatewayResult.Error = when (e) {
        is java.net.UnknownHostException -> GatewayResult.Error(
            "GATEWAY_DNS_FAILED",
            "Не удалось определить адрес платёжного шлюза. Проверь интернет/DNS: " +
                "домен шлюза не резолвится у этого провайдера."
        )
        is java.net.SocketTimeoutException -> GatewayResult.Error(
            "GATEWAY_TIMEOUT",
            "Платёжный шлюз не ответил вовремя. Попробуй ещё раз через минуту."
        )
        is javax.net.ssl.SSLException -> GatewayResult.Error(
            "GATEWAY_TLS_BLOCKED",
            "TLS-соединение с платёжным шлюзом было разорвано. Обычно так выглядит " +
                "фильтрация трафика на стороне сети или провайдера."
        )
        is java.net.ConnectException, is java.net.NoRouteToHostException, is java.net.PortUnreachableException ->
            GatewayResult.Error(
                "GATEWAY_UNREACHABLE",
                "Платёжный шлюз недоступен: соединение не устанавливается. Сервис может быть " +
                    "остановлен, либо доступ к нему ограничен в текущей сети."
            )
        is java.io.IOException -> GatewayResult.Error(
            "GATEWAY_UNREACHABLE",
            e.message ?: "Нет связи с платёжным шлюзом"
        )
        else -> GatewayResult.Error("NETWORK_ERROR", e.message ?: "Нет связи с платёжным шлюзом")
    }

    suspend fun catalog(): GatewayResult<CatalogResponse> = call { it.catalog() }

    suspend fun subscription(context: Context): GatewayResult<SubscriptionResponse> =
        call { it.subscription(userId(context)) }

    suspend fun cancelSubscription(context: Context, immediate: Boolean = false): GatewayResult<SubscriptionResponse> =
        call { it.cancelSubscription(SimpleUserRequest(userId(context), immediate = immediate, reason = "user_request")) }

    suspend fun resumeSubscription(context: Context): GatewayResult<SubscriptionResponse> =
        call { it.resumeSubscription(SimpleUserRequest(userId(context))) }

    suspend fun chargeNow(context: Context): GatewayResult<SubscriptionResponse> =
        call { it.chargeNow(SimpleUserRequest(userId(context))) }

    suspend fun createInvoice(context: Context, asset: String, network: String): GatewayResult<InvoiceResponse> =
        call { it.createInvoice(CreateInvoiceRequest(userId(context), asset.uppercase(), network.uppercase())) }

    suspend fun invoiceStatus(context: Context, invoiceId: Long): GatewayResult<InvoiceResponse> =
        call { it.invoiceStatus(invoiceId, userId(context)) }

    /**
     * Starts a hosted YooKassa payment. `bank_card` may be saved for renewal;
     * `sbp` is an explicit one-time СБП checkout. No PAN/CVC is sent here.
     */
    suspend fun cardCheckout(
        context: Context,
        saveCard: Boolean,
        paymentMethod: String = "bank_card"
    ): GatewayResult<CardCheckoutResponse> =
        call {
            it.cardCheckout(
                CardCheckoutRequest(
                    userId = userId(context),
                    saveCard = saveCard && paymentMethod == "bank_card",
                    paymentMethod = paymentMethod,
                    returnUrl = "malo://payment/return",
                    idempotenceKey = UUID.randomUUID().toString()
                )
            )
        }

    suspend fun cardPaymentStatus(context: Context, paymentId: String): GatewayResult<PaymentStatusResponse> =
        call { it.cardPaymentStatus(paymentId, userId(context)) }

    suspend fun cards(context: Context): GatewayResult<CardsResponse> = call { it.cards(userId(context)) }

    suspend fun makeCardDefault(context: Context, cardId: String): GatewayResult<CardsResponse> =
        call { it.makeCardDefault(cardId, SimpleUserRequest(userId(context))) }

    suspend fun deleteCard(context: Context, cardId: String): GatewayResult<CardsResponse> =
        call { it.deleteCard(cardId, userId(context)) }
}
