package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.payments.AccountGateway
import com.example.payments.GatewayResult
import com.example.payments.KoFiIntentResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Email account + prepaid Ko-fi checkout. The server alone grants Pro. */
@Composable
fun AccountSubscriptionScreen(isProUser: Boolean, onProPurchased: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(AccountGateway.hasSession(context)) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var requested by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf<KoFiIntentResponse?>(null) }
    BackHandler { onDismiss() }

    // When the webhook arrives, refresh the authoritative account entitlement.
    LaunchedEffect(signedIn, intent?.paymentToken) {
        val payment = intent ?: return@LaunchedEffect
        if (!signedIn) return@LaunchedEffect
        while (true) {
            delay(5_000)
            when (val result = AccountGateway.intentStatus(context, payment.paymentToken)) {
                is GatewayResult.Success -> {
                    if (result.data.status == "paid") {
                        intent = null
                        message = "Оплата подтверждена. Синхронизируем Pro."
                        onProPurchased()
                        return@LaunchedEffect
                    }
                    if (result.data.status == "expired") {
                        intent = null
                        message = "Код истёк. Если вы уже заплатили, потребуется ручная проверка."
                        return@LaunchedEffect
                    }
                }
                is GatewayResult.Error -> if (result.code == "UNAUTHORIZED") {
                    AccountGateway.clearSession(context)
                    signedIn = false
                    onProPurchased()
                    message = "Сессия истекла. Войдите снова."
                    return@LaunchedEffect
                }
                else -> Unit
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MalO Pro", style = MaterialTheme.typography.headlineMedium)
        Text(if (isProUser) "Подписка активна" else "Бесплатный тариф")
        if (!AccountGateway.isConfigured) Text("Для входа нужен настроенный HTTPS-адрес шлюза.")
        if (!signedIn) {
            Text("Войдите по email, чтобы сохранить подписку при смене устройства.")
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && AccountGateway.isConfigured, onClick = {
                busy = true
                scope.launch {
                    when (val result = AccountGateway.requestCode(email.trim())) {
                        is GatewayResult.Success -> { requested = true; message = "Проверьте почту: код действует 10 минут." }
                        is GatewayResult.Error -> message = result.message
                        GatewayResult.NotConfigured -> message = "Шлюз не настроен."
                    }
                    busy = false
                }
            }) { Text("Получить код") }
            if (requested) {
                OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) },
                    label = { Text("Код из письма") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && code.length == 8, onClick = {
                    busy = true
                    scope.launch {
                        when (val result = AccountGateway.login(context, email.trim(), code)) {
                            is GatewayResult.Success -> { signedIn = true; code = ""; message = "Вход выполнен."; onProPurchased() }
                            is GatewayResult.Error -> message = result.message
                            GatewayResult.NotConfigured -> message = "Шлюз не настроен."
                        }
                        busy = false
                    }
                }) { Text("Войти") }
            }
        } else {
            Text("Вход выполнен. Оплата Ko-fi разовая, без автопродления.")
            if (intent == null) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        when (val result = AccountGateway.intent(context)) {
                            is GatewayResult.Success -> { intent = result.data; message = "Скопируйте код и вставьте его целиком в сообщение Ko-fi." }
                            is GatewayResult.Error -> {
                                if (result.code == "UNAUTHORIZED") {
                                    AccountGateway.clearSession(context)
                                    signedIn = false
                                    onProPurchased()
                                }
                                message = result.message
                            }
                            GatewayResult.NotConfigured -> message = "Шлюз не настроен."
                        }
                        busy = false
                    }
                }) { Text("Создать код оплаты Ko-fi") }
            }
            intent?.let { payment ->
                Text("Сумма: ${payment.amount} ${payment.currency}. Код действует до ${payment.expiresAt}.")
                Text(payment.paymentToken)
                Text("Вставьте только этот код в поле сообщения на Ko-fi. Если код не указан или истёк, потребуется ручная проверка.")
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Код платежа MalO", payment.paymentToken))
                    message = "Код скопирован."
                }) { Text("Копировать код") }
                Button(onClick = {
                    val uri = Uri.parse(payment.paymentUrl)
                    if (uri.scheme == "https" && uri.host == "ko-fi.com") {
                        val opened = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
                        if (!opened) message = "Не удалось открыть Ko-fi."
                    } else message = "Некорректный адрес Ko-fi."
                }) { Text("Открыть Ko-fi") }
                TextButton(onClick = { intent = null }) { Text("Создать другой код") }
            }
            TextButton(enabled = !busy, onClick = { onProPurchased(); message = "Проверяем статус подписки." }) { Text("Обновить статус") }
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = AccountGateway.logout(context)
                    signedIn = false
                    intent = null
                    onProPurchased()
                    message = if (result is GatewayResult.Success) "Вы вышли из аккаунта." else "Локальный вход удалён; не удалось подтвердить выход на сервере."
                    busy = false
                }
            }) { Text("Выйти") }
        }
        if (message.isNotBlank()) Text(message)
        TextButton(onClick = onDismiss) { Text("Назад") }
    }
}
