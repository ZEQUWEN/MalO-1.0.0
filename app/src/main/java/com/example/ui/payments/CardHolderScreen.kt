package com.example.ui.payments

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.payments.CardBrand
import com.example.payments.CardInput
import com.example.payments.CardNumberVisualTransformation
import com.example.payments.CardVault
import com.example.payments.ExpiryVisualTransformation
import com.example.payments.GatewayResult
import com.example.payments.PaymentGateway
import com.example.payments.SavedCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val scpBackground = Color(0xFF0F0E14)
private val scpSurface = Color(0xFF171620)
private val scpNeonPurple = Color(0xFFBB86FC)
private val scpTerminalGreen = Color(0xFF00FFC4)
private val scpErrorRed = Color(0xFFFF5252)

/**
 * Card holder: stored cards with a realistic card design, auto-payment control
 * and in-app subscription cancellation.
 *
 * @param isProUser current entitlement, used to render the subscription block
 * @param periodEndMillis when the paid period expires (`null` when inactive)
 * @param autoRenewInitially whether the gateway reports auto-renewal as on
 * @param onCancelSubscription invoked after a confirmed cancellation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardHolderScreen(
    isProUser: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    periodEndMillis: Long? = null,
    autoRenewInitially: Boolean = true,
    onCancelSubscription: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler { onDismiss() }

    val cards: SnapshotStateList<SavedCard> = remember { CardVault.cards(context).toMutableStateList() }
    var autoPay by remember { mutableStateOf(CardVault.isAutoPayEnabled(context) && autoRenewInitially) }
    var cancelAtPeriodEnd by remember { mutableStateOf(!autoRenewInitially) }
    var showAddForm by remember { mutableStateOf(cards.isEmpty()) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedCard?>(null) }
    var busy by remember { mutableStateOf(false) }

    // New card form state (digits only; the PAN is discarded after binding).
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val brand = remember(number) { CardBrand.detect(number) }

    // Pull the authoritative list from the gateway when it is configured.
    LaunchedEffect(Unit) {
        if (!PaymentGateway.isConfigured) return@LaunchedEffect
        when (val result = PaymentGateway.cards(context)) {
            is GatewayResult.Success -> {
                val remote = result.data.cards.map { it.toSavedCard() }
                CardVault.syncFromGateway(context, remote)
                cards.clear()
                cards.addAll(CardVault.cards(context))
                result.data.subscription?.let {
                    autoPay = it.autoRenew
                    cancelAtPeriodEnd = it.cancelAtPeriodEnd
                }
                showAddForm = cards.isEmpty()
            }
            else -> Unit
        }
    }

    fun refreshLocal() {
        cards.clear()
        cards.addAll(CardVault.cards(context))
    }

    fun submitCard() {
        val digits = CardBrand.digitsOf(number)
        formError = when {
            !CardBrand.isComplete(digits) -> "Проверьте номер карты — не сходится контрольная сумма (Luhn)."
            !CardInput.expiryValid(expiry) -> "Срок действия указан неверно или карта уже истекла."
            !CardInput.cvcValid(cvc, brand) -> "CVC/CVV должен содержать ${brand.cvcLength} цифры."
            holder.isBlank() -> "Укажите имя держателя, как на карте."
            else -> null
        }
        if (formError != null) return

        busy = true
        scope.launch {
            try {
                // With a configured gateway the PAN is never sent to us: we open
                // the acquirer's 3-D Secure page and only keep the returned token.
                var paymentMethodId: String? = null
                if (PaymentGateway.isConfigured) {
                    when (val result = PaymentGateway.cardCheckout(context, saveCard = true)) {
                        is GatewayResult.Success -> {
                            val payment = result.data.payment
                            paymentMethodId = payment?.paymentId
                            payment?.confirmationUrl?.let { url ->
                                runCatching {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url)
                                    )
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                            }
                        }
                        is GatewayResult.Error -> formError = result.message
                        GatewayResult.NotConfigured -> Unit
                    }
                }

                if (formError == null) {
                    CardVault.rememberFromInput(
                        context = context,
                        cardNumber = digits,
                        expiryDigits = expiry,
                        holderName = holder,
                        paymentMethodId = paymentMethodId,
                        makeDefault = true
                    )
                    CardVault.setAutoPayEnabled(context, true)
                    autoPay = true
                    refreshLocal()
                    number = ""; expiry = ""; cvc = ""; holder = ""
                    showAddForm = false
                    Toast.makeText(context, "Карта сохранена для автоплатежа", Toast.LENGTH_SHORT).show()
                }
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("card_holder_screen"),
        containerColor = scpBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Картхолдер",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Сохранённые карты и автоплатёж",
                            color = scpNeonPurple.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("card_holder_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scpSurface,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            /* ------------------------------------------- subscription block */
            SubscriptionStatusCard(
                isProUser = isProUser,
                periodEndMillis = periodEndMillis,
                autoPay = autoPay,
                cancelAtPeriodEnd = cancelAtPeriodEnd,
                hasCard = cards.isNotEmpty(),
                onToggleAutoPay = { enabled ->
                    autoPay = enabled
                    CardVault.setAutoPayEnabled(context, enabled)
                    if (PaymentGateway.isConfigured) {
                        scope.launch {
                            if (enabled) PaymentGateway.resumeSubscription(context)
                            else PaymentGateway.cancelSubscription(context, immediate = false)
                        }
                    }
                    cancelAtPeriodEnd = !enabled
                },
                onCancelClick = { showCancelDialog = true }
            )

            /* -------------------------------------------------- saved cards */
            SectionLabel("СОХРАНЁННЫЕ КАРТЫ")

            if (cards.isEmpty()) {
                EmptyCardsPlaceholder()
            } else {
                cards.forEach { card ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreditCardVisual(
                            card = card,
                            labelText = if (card.isDefault && autoPay) "MalO Pro • автоплатёж" else "MalO Pro"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    CardVault.makeDefault(context, card.cardId)
                                    refreshLocal()
                                    if (PaymentGateway.isConfigured) {
                                        scope.launch { PaymentGateway.makeCardDefault(context, card.cardId) }
                                    }
                                },
                                enabled = !card.isDefault,
                                border = BorderStroke(1.dp, if (card.isDefault) Color.DarkGray else scpTerminalGreen),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (card.isDefault) Color.Gray else scpTerminalGreen
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (card.isDefault) "Основная" else "Сделать основной",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            OutlinedButton(
                                onClick = { pendingDelete = card },
                                border = BorderStroke(1.dp, scpErrorRed.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = scpErrorRed),
                                modifier = Modifier.testTag("delete_card_${card.cardId}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить карту", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (card.isExpired) {
                            Text(
                                text = "⚠ Срок действия карты истёк — автоплатёж по ней не пройдёт.",
                                color = scpErrorRed,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            /* ---------------------------------------------------- add card */
            if (!showAddForm) {
                Button(
                    onClick = { showAddForm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = scpNeonPurple),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_card_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Привязать новую карту",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }

            AnimatedVisibility(visible = showAddForm) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("НОВАЯ КАРТА")

                    // Live preview reacting to BIN detection.
                    CreditCardVisual(
                        brand = brand,
                        numberText = CardBrand.format(number).ifBlank { "•••• •••• •••• ••••" },
                        holderName = holder,
                        expiryText = CardInput.formattedExpiry(expiry),
                        isDefault = cards.isEmpty(),
                        labelText = if (brand.isKnown) "Определено: ${brand.displayName}" else "MalO Pro • автоплатёж"
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = scpSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CardNumberField(
                                value = number,
                                onValueChange = { number = CardInput.sanitizeNumber(it); formError = null },
                                brand = brand,
                                accentColor = scpTerminalGreen
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = expiry,
                                    onValueChange = { expiry = CardInput.sanitizeExpiry(it); formError = null },
                                    label = { Text("MM/YY", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                    visualTransformation = ExpiryVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    colors = fieldColors(scpTerminalGreen),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = cvc,
                                    onValueChange = { cvc = CardInput.sanitizeCvc(it, brand); formError = null },
                                    label = {
                                        Text(
                                            if (brand == CardBrand.AMEX) "CID" else "CVC",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation('•'),
                                    singleLine = true,
                                    colors = fieldColors(scpTerminalGreen),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = holder,
                                onValueChange = { holder = CardInput.sanitizeHolder(it); formError = null },
                                label = { Text("Имя держателя", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                placeholder = { Text("IVAN IVANOV", fontSize = 12.sp, color = Color.Gray) },
                                singleLine = true,
                                colors = fieldColors(scpTerminalGreen),
                                modifier = Modifier.fillMaxWidth()
                            )

                            formError?.let {
                                Text(
                                    text = it,
                                    color = scpErrorRed,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (PaymentGateway.isConfigured) {
                                        "Номер карты вводится на странице банка-эквайера (3-D Secure). Приложение хранит только последние 4 цифры и токен."
                                    } else {
                                        "Демо-режим: приложение сохраняет только платёжную систему, срок и последние 4 цифры."
                                    },
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (cards.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showAddForm = false; formError = null },
                                        border = BorderStroke(1.dp, Color.Gray),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Отмена", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Button(
                                    onClick = { submitCard() },
                                    enabled = !busy,
                                    colors = ButtonDefaults.buttonColors(containerColor = scpTerminalGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(46.dp)
                                        .testTag("save_card_button")
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            color = Color.Black,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Сохранить карту",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    /* ----------------------------------------------------------- dialogs */

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = scpSurface,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = scpErrorRed) },
            title = {
                Text(
                    "Отменить подписку Pro?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = buildString {
                        append("Автоплатёж будет отключён. ")
                        if (periodEndMillis != null && periodEndMillis > System.currentTimeMillis()) {
                            append("Доступ к Pro сохранится до ")
                            append(formatDate(periodEndMillis))
                            append(", затем вернётся тариф Base.")
                        } else {
                            append("Тариф Base вернётся немедленно.")
                        }
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        autoPay = false
                        cancelAtPeriodEnd = true
                        CardVault.setAutoPayEnabled(context, false)
                        if (PaymentGateway.isConfigured) {
                            scope.launch { PaymentGateway.cancelSubscription(context, immediate = false) }
                        }
                        onCancelSubscription?.invoke()
                        Toast.makeText(context, "Подписка отменена. Автосписаний больше не будет.", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.testTag("confirm_cancel_subscription")
                ) {
                    Text("Да, отменить", color = scpErrorRed, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Оставить Pro", color = scpTerminalGreen, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = scpSurface,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = {
                Text(
                    "Удалить карту ${card.brand.displayName} ••${card.last4}?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Токен автоплатежа будет отозван. Если это основная карта, автопродление отключится.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    CardVault.remove(context, card.cardId)
                    refreshLocal()
                    if (PaymentGateway.isConfigured) {
                        scope.launch { PaymentGateway.deleteCard(context, card.cardId) }
                    }
                    if (cards.isEmpty()) {
                        autoPay = false
                        CardVault.setAutoPayEnabled(context, false)
                        showAddForm = true
                    }
                    pendingDelete = null
                }) {
                    Text("Удалить", color = scpErrorRed, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Отмена", color = Color.LightGray, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

/* ---------------------------------------------------------------- parts -- */

@Composable
private fun SubscriptionStatusCard(
    isProUser: Boolean,
    periodEndMillis: Long?,
    autoPay: Boolean,
    cancelAtPeriodEnd: Boolean,
    hasCard: Boolean,
    onToggleAutoPay: (Boolean) -> Unit,
    onCancelClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = scpSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, (if (isProUser) scpTerminalGreen else Color.DarkGray).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background((if (isProUser) scpTerminalGreen else Color.Gray).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isProUser) Icons.Default.CheckCircle else Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = if (isProUser) scpTerminalGreen else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isProUser) "Подписка Pro активна" else "Подписка не активна",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = when {
                            !isProUser -> "Оформите Pro, чтобы включить автоплатёж."
                            periodEndMillis != null && cancelAtPeriodEnd ->
                                "Отменена: доступ сохраняется до ${formatDate(periodEndMillis)}"
                            periodEndMillis != null -> "Следующее списание: ${formatDate(periodEndMillis)}"
                            else -> "Автопродление управляется в этом разделе."
                        },
                        color = Color.LightGray.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoMode,
                    contentDescription = null,
                    tint = if (autoPay) scpTerminalGreen else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Автоматическая оплата",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (hasCard) {
                            "Списание с основной карты каждые 30 дней"
                        } else {
                            "Нужна сохранённая карта"
                        },
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Switch(
                    checked = autoPay && hasCard,
                    enabled = hasCard,
                    onCheckedChange = onToggleAutoPay,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = scpTerminalGreen,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color(0xFF2A2A2A)
                    ),
                    modifier = Modifier.testTag("autopay_switch")
                )
            }

            if (isProUser) {
                OutlinedButton(
                    onClick = onCancelClick,
                    border = BorderStroke(1.dp, scpErrorRed.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scpErrorRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_subscription_button")
                ) {
                    Text("Отменить подписку", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun EmptyCardsPlaceholder() {
    Surface(
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
            Text(
                text = "Нет сохранённых карт",
                color = Color.LightGray,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Привяжите карту, чтобы подписка продлевалась автоматически.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AcceptedBrandsRow(height = 16.dp)
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.Gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp
    )
}

/** Card number field with live payment-system detection in the trailing slot. */
@Composable
internal fun CardNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    brand: CardBrand,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Номер карты", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
        placeholder = { Text("0000 0000 0000 0000", fontSize = 13.sp, color = Color.Gray) },
        leadingIcon = {
            Icon(
                Icons.Default.CreditCard,
                contentDescription = null,
                tint = if (brand.isKnown) accentColor else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                AnimatedPaymentBrandLogo(brand = brand, height = 20.dp)
            }
        },
        supportingText = {
            Text(
                text = if (brand.isKnown) "Платёжная система: ${brand.displayName}" else "Введите номер — система определится автоматически",
                color = if (brand.isKnown) accentColor else Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        },
        isError = isError,
        visualTransformation = CardNumberVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        colors = fieldColors(accentColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_number_field")
    )
}

@Composable
internal fun fieldColors(accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = accentColor,
    focusedBorderColor = accentColor,
    unfocusedBorderColor = Color.DarkGray,
    focusedLabelColor = accentColor,
    unfocusedLabelColor = Color.Gray,
    errorBorderColor = scpErrorRed
)

internal fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))
