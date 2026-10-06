package com.example.ui.payments

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.payments.CardVault
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
 * Secure card mini app.
 *
 * Cards are displayed as a swipeable pager, similar to a bank application. A
 * card can only appear here after a successful YooKassa payment: this screen
 * never accepts, stores, or fabricates PAN/CVC data. The local vault is merely
 * an app-private OS cache of non-sensitive descriptors returned by the gateway
 * (brand, masked number, expiry and selected-card state).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardHolderScreen(
    isProUser: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    periodEndMillis: Long? = null,
    autoRenewInitially: Boolean = false,
    onCancelSubscription: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cards: SnapshotStateList<SavedCard> = remember { CardVault.cards(context).toMutableStateList() }
    val pagerState = rememberPagerState(pageCount = { cards.size })

    var autoPay by remember { mutableStateOf(CardVault.isAutoPayEnabled(context) && autoRenewInitially) }
    var cancelAtPeriodEnd by remember { mutableStateOf(!autoRenewInitially) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedCard?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var enabledPaymentMethods by remember { mutableStateOf<Set<String>>(emptySet()) }
    var paymentMethodsLoaded by remember { mutableStateOf(false) }
    var paymentMethodsError by remember { mutableStateOf<String?>(null) }
    var paymentMethodsRetry by remember { mutableIntStateOf(0) }

    BackHandler { onDismiss() }

    fun refreshLocal() {
        cards.clear()
        cards.addAll(CardVault.cards(context))
    }

    fun refreshFromGateway() {
        if (!PaymentGateway.isConfigured) return
        scope.launch {
            refreshing = true
            try {
                when (val result = PaymentGateway.cards(context)) {
                    is GatewayResult.Success -> {
                        CardVault.syncFromGateway(context, result.data.cards.map { it.toSavedCard() })
                        refreshLocal()
                        result.data.subscription?.let {
                            autoPay = it.autoRenew
                            cancelAtPeriodEnd = it.cancelAtPeriodEnd
                        }
                    }
                    is GatewayResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    GatewayResult.NotConfigured -> Unit
                }
            } finally {
                refreshing = false
            }
        }
    }

    /** Opens a hosted checkout without ever collecting card details in-app. */
    fun openYooKassaCheckout(paymentMethod: String, saveCard: Boolean) {
        if (!PaymentGateway.isConfigured || paymentMethod !in enabledPaymentMethods) {
            Toast.makeText(
                context,
                "Этот способ оплаты сейчас недоступен.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        busy = true
        scope.launch {
            try {
                when (val result = PaymentGateway.cardCheckout(context, saveCard, paymentMethod)) {
                    is GatewayResult.Success -> {
                        val payment = result.data.payment
                        val url = payment?.confirmationUrl
                        if (url == null) {
                            Toast.makeText(context, "ЮKassa не вернула ссылку для оплаты.", Toast.LENGTH_LONG).show()
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            Toast.makeText(
                                context,
                                if (paymentMethod == "sbp") {
                                    "Подтвердите оплату в приложении банка. После возврата карты и подписка обновятся."
                                } else {
                                    "Подтвердите операцию на защищённой странице ЮKassa."
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    is GatewayResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    GatewayResult.NotConfigured -> Unit
                }
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshFromGateway() }
    LaunchedEffect(paymentMethodsRetry) {
        paymentMethodsLoaded = false
        when (val result = PaymentGateway.paymentMethods()) {
            is GatewayResult.Success -> {
                enabledPaymentMethods = result.data.methods.map { it.id }.toSet()
                paymentMethodsError = null
            }
            is GatewayResult.Error -> {
                enabledPaymentMethods = emptySet()
                paymentMethodsError = result.message
            }
            GatewayResult.NotConfigured -> {
                enabledPaymentMethods = emptySet()
                paymentMethodsError = "Шлюз платежей не настроен для этой сборки."
            }
        }
        paymentMethodsLoaded = true
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshFromGateway()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_cards_screen"),
        containerColor = scpBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Мои карты",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "ЮKassa • выбор и автоплатёж",
                            color = scpNeonPurple.copy(alpha = 0.82f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("my_cards_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshFromGateway() },
                        enabled = !refreshing,
                        modifier = Modifier.testTag("my_cards_refresh")
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                color = scpTerminalGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = scpTerminalGreen)
                        }
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
            SubscriptionStatusCard(
                isProUser = isProUser,
                periodEndMillis = periodEndMillis,
                autoPay = autoPay,
                cancelAtPeriodEnd = cancelAtPeriodEnd,
                hasCard = cards.isNotEmpty(),
                onToggleAutoPay = { enabled ->
                    if (enabled && "bank_card" !in enabledPaymentMethods) {
                        Toast.makeText(context, "Автопродление картой сейчас недоступно.", Toast.LENGTH_LONG).show()
                        return@SubscriptionStatusCard
                    }
                    autoPay = enabled
                    cancelAtPeriodEnd = !enabled
                    CardVault.setAutoPayEnabled(context, enabled)
                    if (PaymentGateway.isConfigured) {
                        scope.launch {
                            if (enabled) PaymentGateway.resumeSubscription(context)
                            else PaymentGateway.cancelSubscription(context, immediate = false)
                        }
                    }
                },
                onCancelClick = { showCancelDialog = true }
            )

            SectionLabel("СОХРАНЁННЫЕ КАРТЫ")
            if (cards.isEmpty()) {
                EmptyCardsPlaceholder()
            } else {
                Text(
                    text = "Свайпните, чтобы выбрать карту для автоплатежа.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(end = 34.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("saved_cards_pager")
                ) { page ->
                    val card = cards[page]
                    CreditCardVisual(
                        card = card,
                        labelText = if (card.isDefault && autoPay) "MalO Pro • автоплатёж" else "MalO Pro"
                    )
                }

                PagerDots(count = cards.size, selectedIndex = pagerState.currentPage)

                val selectedCard = cards.getOrNull(pagerState.currentPage)
                if (selectedCard != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                CardVault.makeDefault(context, selectedCard.cardId)
                                refreshLocal()
                                if (PaymentGateway.isConfigured) {
                                    scope.launch { PaymentGateway.makeCardDefault(context, selectedCard.cardId) }
                                }
                            },
                            enabled = !selectedCard.isDefault,
                            border = BorderStroke(1.dp, if (selectedCard.isDefault) Color.DarkGray else scpTerminalGreen),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (selectedCard.isDefault) Color.Gray else scpTerminalGreen
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_default_card")
                        ) {
                            Text(
                                text = if (selectedCard.isDefault) "Выбрана для автоплатежа" else "Выбрать для автоплатежа",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = { pendingDelete = selectedCard },
                            border = BorderStroke(1.dp, scpErrorRed.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = scpErrorRed),
                            modifier = Modifier.testTag("delete_card_${selectedCard.cardId}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить карту", modifier = Modifier.size(17.dp))
                        }
                    }
                    if (selectedCard.isExpired) {
                        Text(
                            text = "Срок действия выбранной карты истёк. Добавьте новую карту через ЮKassa.",
                            color = scpErrorRed,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if ("bank_card" in enabledPaymentMethods) {
                Button(
                    onClick = { openYooKassaCheckout(paymentMethod = "bank_card", saveCard = true) },
                    enabled = !busy && paymentMethodsLoaded,
                    colors = ButtonDefaults.buttonColors(containerColor = scpNeonPurple),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("add_card_yookassa")
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.AddCard, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Добавить карту через ЮKassa",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (paymentMethodsLoaded) {
                Column {
                    Text(
                        text = paymentMethodsError ?: "Добавление карт ЮKassa сейчас недоступно.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (paymentMethodsError != null) {
                        TextButton(onClick = { paymentMethodsRetry++ }) {
                            Text("Повторить загрузку способов оплаты")
                        }
                    }
                }
            }

            SecureCheckoutNotice()

            if ("sbp" in enabledPaymentMethods) {
                OutlinedButton(
                    onClick = { openYooKassaCheckout(paymentMethod = "sbp", saveCard = false) },
                    enabled = !busy && paymentMethodsLoaded,
                    border = BorderStroke(1.dp, scpTerminalGreen.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scpTerminalGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("renew_with_sbp")
                ) {
                    Text(
                        text = "Продлить подписку через СБП",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "СБП — разовая оплата в приложении банка. Для автоматического продления выберите сохранённую карту.",
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = scpSurface,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = scpErrorRed) },
            title = { Text("Отменить подписку Pro?", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = buildString {
                        append("Автоплатёж будет отключён. ")
                        if (periodEndMillis != null && periodEndMillis > System.currentTimeMillis()) {
                            append("Доступ сохранится до ${formatDate(periodEndMillis)}.")
                        } else {
                            append("Тариф Base вернётся немедленно.")
                        }
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    autoPay = false
                    cancelAtPeriodEnd = true
                    CardVault.setAutoPayEnabled(context, false)
                    if (PaymentGateway.isConfigured) scope.launch {
                        PaymentGateway.cancelSubscription(context, immediate = false)
                    }
                    onCancelSubscription?.invoke()
                }, modifier = Modifier.testTag("confirm_cancel_subscription")) {
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Text(
                    "Токен автоплатежа будет удалён из сервиса. Номер карты не хранится в приложении.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    CardVault.remove(context, card.cardId)
                    refreshLocal()
                    if (PaymentGateway.isConfigured) scope.launch {
                        PaymentGateway.deleteCard(context, card.cardId)
                    }
                    if (cards.isEmpty()) {
                        autoPay = false
                        CardVault.setAutoPayEnabled(context, false)
                    }
                    pendingDelete = null
                }) { Text("Удалить", color = scpErrorRed, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Отмена", color = Color.LightGray, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

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
            modifier = Modifier.padding(16.dp),
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
                            !isProUser -> "Добавьте карту или оплатите через СБП."
                            periodEndMillis != null && cancelAtPeriodEnd -> "Отменена: доступ до ${formatDate(periodEndMillis)}"
                            periodEndMillis != null -> "Следующее списание: ${formatDate(periodEndMillis)}"
                            else -> "Продление настраивается в этом разделе."
                        },
                        color = Color.LightGray.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoMode,
                    contentDescription = null,
                    tint = if (autoPay) scpTerminalGreen else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Автоматическое продление", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = if (hasCard) "Списание с выбранной карты каждые 30 дней" else "Добавьте карту, чтобы включить",
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
                ) { Text("Отменить подписку", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
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
            modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
            Text("Нет сохранённых карт", color = Color.LightGray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Text(
                text = "Добавьте карту на защищённой странице ЮKassa. Здесь появятся только её маска и платёжная система.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
            AcceptedBrandsRow(height = 16.dp)
        }
    }
}

@Composable
private fun SecureCheckoutNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Номер карты, срок действия и CVC вводятся только на странице ЮKassa. MalO хранит на сервере ЮKassa-токен, а в приложении — только маску карты.",
            color = Color.Gray,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PagerDots(count: Int, selectedIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == selectedIndex) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (index == selectedIndex) scpTerminalGreen else Color.Gray.copy(alpha = 0.5f))
            )
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

internal fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))
