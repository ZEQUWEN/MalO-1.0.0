package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class DeepSeekChatRequest(
    @Json(name = "model") val model: String = "deepseek-chat",
    @Json(name = "messages") val messages: List<DeepSeekMessage>,
    @Json(name = "temperature") val temperature: Float = 0.85f,
    @Json(name = "max_tokens") val maxTokens: Int = 2048,
    @Json(name = "stream") val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DeepSeekMessage(
    @Json(name = "role") val role: String, // "system", "user", "assistant"
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class DeepSeekChatResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "choices") val choices: List<DeepSeekChoice>? = null,
    @Json(name = "error") val error: DeepSeekError? = null
)

@JsonClass(generateAdapter = true)
data class DeepSeekChoice(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "message") val message: DeepSeekMessage? = null,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class DeepSeekError(
    @Json(name = "message") val message: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "code") val code: String? = null
)

interface DeepSeekApiService {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: DeepSeekChatRequest
    ): DeepSeekChatResponse
}

object DeepSeekClient {
    private const val BASE_URL = "https://api.deepseek.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: DeepSeekApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(DeepSeekApiService::class.java)
    }
}
