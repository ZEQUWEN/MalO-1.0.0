package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeepSeekMessage(
    @Json(name = "role") val role: String, // "system", "user", "assistant"
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class MaloChatRequest(
    @Json(name = "userId") val userId: String,
    @Json(name = "messages") val messages: List<DeepSeekMessage>
)

@JsonClass(generateAdapter = true)
data class MaloChatResponse(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "reply") val reply: String
)

@JsonClass(generateAdapter = true)
data class MaloBurnRequest(
    @Json(name = "userId") val userId: String
)

@JsonClass(generateAdapter = true)
data class MaloBurnResponse(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "serverHistoryStored") val serverHistoryStored: Boolean = false
)
