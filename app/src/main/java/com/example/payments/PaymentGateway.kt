package com.example.payments

/** A transport result shared by account and chat API calls. */
sealed class GatewayResult<out T> {
    data class Success<T>(val data: T) : GatewayResult<T>()
    data class Error(val code: String, val message: String) : GatewayResult<Nothing>()
    data object NotConfigured : GatewayResult<Nothing>()
}

internal object PaymentGateway {
    fun classifyNetworkError(e: Throwable): GatewayResult.Error = when (e) {
        is java.net.UnknownHostException -> GatewayResult.Error("GATEWAY_DNS_FAILED", "Не удалось определить адрес шлюза. Проверьте интернет и DNS.")
        is java.net.SocketTimeoutException -> GatewayResult.Error("GATEWAY_TIMEOUT", "Шлюз не ответил вовремя. Попробуйте позже.")
        is javax.net.ssl.SSLException -> GatewayResult.Error("GATEWAY_TLS_BLOCKED", "Не удалось установить защищённое соединение.")
        is java.io.IOException -> GatewayResult.Error("GATEWAY_UNREACHABLE", "Нет связи с платёжным шлюзом.")
        else -> GatewayResult.Error("NETWORK_ERROR", "Не удалось выполнить запрос.")
    }
}
