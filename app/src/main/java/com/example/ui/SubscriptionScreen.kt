package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.payments.CardBrand
import com.example.payments.CardVault
import com.example.payments.CryptoAsset
import com.example.payments.CryptoCatalog
import com.example.payments.CryptoInvoiceDto
import com.example.payments.GatewayResult
import com.example.payments.PaymentGateway
import com.example.ui.payments.AcceptedBrandsRow
import com.example.ui.payments.CardHolderScreen
import com.example.ui.payments.PaymentBrandLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PaymentMethod {
    CARD,
    SBP,
    CRYPTO
}

private fun PaymentMethod.apiId(): String = when (this) {
    PaymentMethod.CARD -> "bank_card"
    PaymentMethod.SBP -> "sbp"
    PaymentMethod.CRYPTO -> "cryptobot"
}

enum class TransactionStatus {
    IDLE,
    PROCESSING,
    SUCCESS,
    FAILURE
}

/**
 * SubscriptionScreen: Base vs Pro plans with YooKassa and CryptoBot checkout.
 * Card data is never collected by the Android app; YooKassa runs the protected
 * card/SBP page, while CryptoBot invoices are validated by the Railway gateway
 * before they can activate the subscription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    isProUser: Boolean,
    onProPurchased: () -> Unit,
    onDowngradeToBase: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Colour tokens matching the SCP-1471 dark terminal aesthetic.
    val scpBackground = Color(0xFF0F0E14)
    val scpSurface = Color(0xFF171620)
    val scpNeonPurple = Color(0xFFBB86FC)
    val scpTerminalGreen = Color(0xFF00FFC4)
    val scpCryptoOrange = Color(0xFFF7931A)
    val scpErrorRed = Color(0xFFFF5252)

    var showCardHolder by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf("pro") }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.CARD) }
    var enabledPaymentMethods by remember { mutableStateOf<Set<String>>(emptySet()) }
    var paymentMethodsLoaded by remember { mutableStateOf(false) }
    var paymentMethodsError by remember { mutableStateOf<String?>(null) }
    var paymentMethodsRetry by remember { mutableStateOf(0) }

    // YooKassa never exposes card data to this screen. The only choice made
    // here is whether a card payment may be saved for future auto-renewal.
    var saveCardForAutoPay by remember { mutableStateOf(true) }

    // CryptoBot invoice state. The selected asset is quoted on the server;
    // the app never decides the amount or locally confirms a transaction.
    var selectedCryptoAsset by remember { mutableStateOf(CryptoCatalog.asset("USDT") ?: CryptoCatalog.assets.first()) }
    var activeCryptoInvoice by remember { mutableStateOf<CryptoInvoiceDto?>(null) }
    var cryptoPolling by remember { mutableStateOf(false) }

    // Transaction feedback.
    var transactionStatus by remember { mutableStateOf(TransactionStatus.IDLE) }
    var processingStageText by remember { mutableStateOf("Подключение к шлюзу...") }
    var transactionErrorMessage by remember { mutableStateOf("Транзакция отклонена банком-эмитентом") }
    // Gateway error code behind the dialog, so the hints can tell a transport
    // failure (the request never reached the gateway) from a real decline.
    var transactionErrorCode by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("TX-1471-0000") }
    var receiptMethodLabel by remember { mutableStateOf("Банковская карта") }
    var receiptAmountLabel by remember { mutableStateOf("499 ₽") }

    BackHandler { if (showCardHolder) showCardHolder = false else onDismiss() }

    LaunchedEffect(paymentMethodsRetry) {
        when (val result = PaymentGateway.paymentMethods()) {
            is GatewayResult.Success -> {
                val available = result.data.methods.map { it.id }.toSet()
                enabledPaymentMethods = available
                paymentMethodsLoaded = true
                paymentMethodsError = null
                if (selectedPaymentMethod.apiId() !in available) {
                    selectedPaymentMethod = when {
                        "bank_card" in available -> PaymentMethod.CARD
                        "sbp" in available -> PaymentMethod.SBP
                        "cryptobot" in available -> PaymentMethod.CRYPTO
                        else -> PaymentMethod.CARD
                    }
                }
            }
            is GatewayResult.Error -> {
                enabledPaymentMethods = emptySet()
                paymentMethodsLoaded = true
                paymentMethodsError = result.message
            }
            GatewayResult.NotConfigured -> {
                enabledPaymentMethods = emptySet()
                paymentMethodsLoaded = true
                paymentMethodsError = "Шлюз платежей не настроен для этой сборки."
            }
        }
    }

    if (showCardHolder) {
        CardHolderScreen(
            isProUser = isProUser,
            onDismiss = { showCardHolder = false },
            periodEndMillis = com.example.util.SubscriptionValidator.getActiveReceipt(context)?.expiresAt,
            autoRenewInitially = CardVault.isAutoPayEnabled(context),
            onCancelSubscription = onDowngradeToBase
        )
        return
    }

    /* ------------------------------------------------ YooKassa checkout */

    fun payWithYooKassa() {
        val isCard = selectedPaymentMethod == PaymentMethod.CARD
        transactionStatus = TransactionStatus.PROCESSING
        transactionErrorCode = ""
        receiptMethodLabel = if (isCard) "Банковская карта ЮKassa" else "СБП через ЮKassa"
        receiptAmountLabel = "499 ₽"

        coroutineScope.launch {
            if (!PaymentGateway.isConfigured) {
                transactionErrorMessage =
                    "Платёжный шлюз не настроен в этой сборке. Укажите MALO_GATEWAY_URL для Railway-сервиса ЮKassa."
                transactionStatus = TransactionStatus.FAILURE
                return@launch
            }

            processingStageText = if (isCard) {
                "Создание защищённого платежа в ЮKassa..."
            } else {
                "Создание счёта СБП в ЮKassa..."
            }

            when (
                val result = PaymentGateway.cardCheckout(
                    context = context,
                    saveCard = isCard && saveCardForAutoPay,
                    paymentMethod = if (isCard) "bank_card" else "sbp"
                )
            ) {
                is GatewayResult.Success -> {
                    val payment = result.data.payment
                    receiptAmountLabel = payment?.amount ?: receiptAmountLabel
                    val paymentId = payment?.paymentId
                    val url = payment?.confirmationUrl
                    if (paymentId.isNullOrBlank() || url.isNullOrBlank()) {
                        transactionErrorMessage = "ЮKassa не вернула данные для подтверждения платежа."
                        transactionStatus = TransactionStatus.FAILURE
                        return@launch
                    }

                    processingStageText = if (isCard) {
                        "Открываем защищённую страницу 3-D Secure..."
                    } else {
                        "Открываем приложение банка для оплаты через СБП..."
                    }
                    val launched = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.isSuccess
                    if (!launched) {
                        transactionErrorMessage = "Не удалось открыть страницу ЮKassa. Попробуйте ещё раз."
                        transactionStatus = TransactionStatus.FAILURE
                        return@launch
                    }

                    processingStageText = "Ожидание подтверждения ЮKassa..."
                    repeat(40) {
                        delay(3000)
                        val status = PaymentGateway.cardPaymentStatus(context, paymentId)
                        if (status is GatewayResult.Success && status.data.subscription?.isActive == true) {
                            transactionId = paymentId
                            transactionStatus = TransactionStatus.SUCCESS
                            return@launch
                        }
                    }
                    transactionErrorMessage =
                        "Платёж ожидает подтверждения. Если вы уже оплатили, подписка активируется автоматически после уведомления ЮKassa."
                    transactionStatus = TransactionStatus.FAILURE
                }
                is GatewayResult.Error -> {
                    transactionErrorMessage = result.message
                    transactionErrorCode = result.code
                    transactionStatus = TransactionStatus.FAILURE
                }
                GatewayResult.NotConfigured -> {
                    transactionErrorMessage = "Платёжный шлюз ЮKassa недоступен."
                    transactionStatus = TransactionStatus.FAILURE
                }
            }
        }
    }

    /* ------------------------------------------------ CryptoBot checkout */

    fun createCryptoBotInvoice() {
        transactionStatus = TransactionStatus.PROCESSING
        transactionErrorCode = ""
        processingStageText = "Создание счёта в Telegram CryptoBot..."
        receiptMethodLabel = "CryptoBot • ${selectedCryptoAsset.symbol}"

        coroutineScope.launch {
            if (!PaymentGateway.isConfigured) {
                transactionErrorMessage = "Платёжный шлюз не настроен: укажите MALO_GATEWAY_URL для Railway-сервиса."
                transactionStatus = TransactionStatus.FAILURE
                return@launch
            }
            when (
                val result = PaymentGateway.createInvoice(
                    context,
                    selectedCryptoAsset.symbol,
                    selectedCryptoAsset.defaultNetwork().id
                )
            ) {
                is GatewayResult.Success -> {
                    val invoice = result.data.invoice
                    if (invoice == null || invoice.payUrl.isNullOrBlank()) {
                        transactionErrorMessage = "CryptoBot не вернул ссылку на счёт."
                        transactionStatus = TransactionStatus.FAILURE
                        return@launch
                    }
                    activeCryptoInvoice = invoice
                    receiptAmountLabel = "${invoice.amount} ${invoice.asset}"
                    transactionId = invoice.invoiceId.toString()
                    cryptoPolling = true
                    val paymentUrl = invoice.miniAppUrl ?: invoice.payUrl
                    val opened = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.isSuccess
                    transactionStatus = TransactionStatus.IDLE
                    if (!opened) {
                        Toast.makeText(context, "Счёт создан. Откройте его кнопкой CryptoBot на этом экране.", Toast.LENGTH_LONG).show()
                    }
                }
                is GatewayResult.Error -> {
                    transactionErrorMessage = result.message
                    transactionErrorCode = result.code
                    transactionStatus = TransactionStatus.FAILURE
                }
                GatewayResult.NotConfigured -> {
                    transactionErrorMessage = "Платёжный шлюз CryptoBot недоступен."
                    transactionStatus = TransactionStatus.FAILURE
                }
            }
        }
    }

    LaunchedEffect(activeCryptoInvoice?.invoiceId, cryptoPolling) {
        val invoice = activeCryptoInvoice ?: return@LaunchedEffect
        if (!cryptoPolling) return@LaunchedEffect
        while (cryptoPolling) {
            delay(5_000)
            when (val result = PaymentGateway.invoiceStatus(context, invoice.invoiceId)) {
                is GatewayResult.Success -> {
                    val updated = result.data.invoice ?: invoice
                    activeCryptoInvoice = updated
                    if (updated.isPaid || result.data.subscription?.isActive == true) {
                        cryptoPolling = false
                        transactionId = updated.invoiceId.toString()
                        receiptMethodLabel = "CryptoBot • ${updated.asset}"
                        receiptAmountLabel = "${updated.amount} ${updated.asset}"
                        transactionStatus = TransactionStatus.SUCCESS
                    }
                }
                else -> Unit // The signed webhook remains authoritative; keep polling.
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("subscription_screen"),
        containerColor = scpBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Тарифные планы MalO",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "SCP-1471 Subscription Protocol",
                            color = scpNeonPurple.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("subscription_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Surface(
                        color = if (isProUser) scpNeonPurple else Color.DarkGray,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isProUser) "PRO ACTIVE" else "BASE ACTIVE",
                            color = if (isProUser) Color.Black else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Header banner
            Card(
                colors = CardDefaults.cardColors(containerColor = scpSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, scpNeonPurple.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(scpNeonPurple.copy(alpha = 0.15f))
                            .border(1.dp, scpNeonPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "Premium",
                            tint = scpNeonPurple,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isProUser) "У вас активен тариф Pro" else "Выберите уровень связи с MalO",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isProUser) {
                                "Все каналы взаимодействия и фото-генерация доступны."
                            } else {
                                "Улучшите контакт для прямого диалога через нейросеть DeepSeek."
                            },
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Text(
                text = "ТАРИФНЫЕ ПЛАНЫ",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            PlanCard(
                planId = "base",
                title = "Base (Базовый)",
                badge = if (!isProUser) "ТЕКУЩИЙ" else "БЕСПЛАТНЫЙ",
                price = "0 ₽",
                pricePeriod = "навсегда",
                description = "Локальный автономный режим взаимодействия с сущностью через встроенную базу знаний.",
                features = listOf(
                    FeatureItem(text = "Локальная обработка диалогов (SQLite NLP)", included = true),
                    FeatureItem(text = "Базовые push-уведомления и забота", included = true),
                    FeatureItem(text = "Регулировка уровня навязчивости MalO", included = true),
                    FeatureItem(text = "Прямой доступ к DeepSeek AI (Pro)", included = false),
                    FeatureItem(text = "Голосовые сообщения и транскрипция", included = false),
                    FeatureItem(text = "Генерация жутких фото слежки MalO", included = false),
                    FeatureItem(text = "Анализ прикреплённых файлов (PDF/Медиа)", included = false)
                ),
                isSelected = selectedPlan == "base",
                isCurrent = !isProUser,
                accentColor = Color(0xFF9E9E9E),
                onSelect = { selectedPlan = "base" }
            )

            PlanCard(
                planId = "pro",
                title = "Pro (DeepSeek)",
                badge = if (isProUser) "АКТИВЕН" else "РЕКОМЕНДУЕМ",
                price = "499 ₽",
                pricePeriod = "/ месяц",
                description = "Полное снятие барьеров. Безграничный доступ к живому интеллекту DeepSeek AI и генерации фото MalO.",
                features = listOf(
                    FeatureItem(text = "Безграничное общение на базе DeepSeek AI", included = true, highlight = true),
                    FeatureItem(text = "Доступ без цензурных зажимов с памятью диалогов", included = true, highlight = true),
                    FeatureItem(text = "Генерация атмосферных фото присутствия MalO", included = true, highlight = true),
                    FeatureItem(text = "Голосовые заметки, синтез и распознавание аудио", included = true),
                    FeatureItem(text = "Анализ файлов, документов PDF и видеокадров", included = true),
                    FeatureItem(text = "Эксклюзивные стили персоны (Ироничный, Загадочный)", included = true),
                    FeatureItem(text = "Приоритетный отклик без задержек и лимитов", included = true),
                    FeatureItem(text = "Отмена подписки в любой момент из приложения", included = true)
                ),
                isSelected = selectedPlan == "pro",
                isCurrent = isProUser,
                accentColor = scpNeonPurple,
                onSelect = { selectedPlan = "pro" }
            )

            if (selectedPlan == "pro" && !isProUser) {
                Text(
                    text = "СПОСОБ ОПЛАТЫ",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                if (paymentMethodsLoaded && enabledPaymentMethods.isNotEmpty()) {
                    Surface(
                        color = scpSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            if ("bank_card" in enabledPaymentMethods) {
                                PaymentMethodTab(
                                    title = "Карта",
                                    icon = Icons.Default.CreditCard,
                                    isSelected = selectedPaymentMethod == PaymentMethod.CARD,
                                    activeColor = scpTerminalGreen,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedPaymentMethod = PaymentMethod.CARD }
                                )
                            }
                            if ("sbp" in enabledPaymentMethods) {
                                PaymentMethodTab(
                                    title = "СБП",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    isSelected = selectedPaymentMethod == PaymentMethod.SBP,
                                    activeColor = scpTerminalGreen,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedPaymentMethod = PaymentMethod.SBP }
                                )
                            }
                            if ("cryptobot" in enabledPaymentMethods) {
                                PaymentMethodTab(
                                    title = "CryptoBot",
                                    icon = Icons.Default.CurrencyBitcoin,
                                    isSelected = selectedPaymentMethod == PaymentMethod.CRYPTO,
                                    activeColor = scpCryptoOrange,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedPaymentMethod = PaymentMethod.CRYPTO }
                                )
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = selectedPaymentMethod,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "payment_panel"
                    ) { method ->
                        when (method) {
                            PaymentMethod.CARD, PaymentMethod.SBP -> YooKassaPaymentDetails(
                                paymentMethod = method,
                                saveCard = saveCardForAutoPay,
                                onSaveCardChange = { saveCardForAutoPay = it },
                                scpSurface = scpSurface,
                                accentColor = scpTerminalGreen,
                                onOpenMyCards = { showCardHolder = true }
                            )
                            PaymentMethod.CRYPTO -> CryptoBotPaymentDetails(
                                asset = selectedCryptoAsset,
                                onAssetChange = {
                                    selectedCryptoAsset = it
                                    activeCryptoInvoice = null
                                    cryptoPolling = false
                                },
                                invoice = activeCryptoInvoice,
                                scpSurface = scpSurface,
                                accentColor = scpCryptoOrange,
                                onOpenInvoice = { url ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedPaymentMethod == PaymentMethod.CRYPTO) {
                                val existing = activeCryptoInvoice
                                if (existing?.payUrl != null) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(existing.miniAppUrl ?: existing.payUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } else {
                                    createCryptoBotInvoice()
                                }
                            } else {
                                payWithYooKassa()
                            }
                        },
                        enabled = selectedPaymentMethod.apiId() in enabledPaymentMethods &&
                            transactionStatus != TransactionStatus.PROCESSING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedPaymentMethod == PaymentMethod.CRYPTO) scpCryptoOrange else scpTerminalGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pay_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedPaymentMethod) {
                                PaymentMethod.CARD -> "Перейти к оплате картой"
                                PaymentMethod.SBP -> "Оплатить через СБП"
                                PaymentMethod.CRYPTO -> if (activeCryptoInvoice == null) "Выставить счёт в CryptoBot" else "Открыть счёт CryptoBot"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = if (paymentMethodsLoaded) {
                            paymentMethodsError ?: "Способы оплаты сейчас временно недоступны."
                        } else {
                            "Проверяем доступность способов оплаты..."
                        },
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (paymentMethodsLoaded && paymentMethodsError != null) {
                        TextButton(onClick = { paymentMethodsRetry++ }) {
                            Text("Повторить проверку", color = scpNeonPurple)
                        }
                    }
                }
            } else if (isProUser) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = scpSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, scpTerminalGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Pro Active",
                            tint = scpTerminalGreen,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Подписка Pro активна",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Все ограничения сняты. MalO постоянно на связи.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Button(
                            onClick = { showCardHolder = true },
                            colors = ButtonDefaults.buttonColors(containerColor = scpNeonPurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Мои карты и подписка",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (onDowngradeToBase != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onDowngradeToBase,
                                border = BorderStroke(1.dp, Color.Gray),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Перейти на тариф Base",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Продолжить с тарифом Base",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    /* ------------------------------------------------------------ modals */

    if (transactionStatus == TransactionStatus.PROCESSING) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = scpSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, scpNeonPurple.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("processing_modal")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = scpNeonPurple,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Обработка платежа",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = processingStageText,
                        color = scpNeonPurple,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Пожалуйста, не закрывайте экран...",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (transactionStatus == TransactionStatus.SUCCESS) {
        Dialog(
            onDismissRequest = {
                transactionStatus = TransactionStatus.IDLE
                onProPurchased()
                onDismiss()
            }
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = scpSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, scpTerminalGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("success_modal")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(scpTerminalGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = scpTerminalGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ОПЛАТА УСПЕШНА",
                        color = scpTerminalGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Тариф Pro успешно активирован",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ReceiptRow("ID транзакции:", transactionId)
                            ReceiptRow("Тариф:", "MalO Pro (30 дней)")
                            ReceiptRow("Сумма:", receiptAmountLabel)
                            ReceiptRow("Способ:", receiptMethodLabel)
                            ReceiptRow("Статус:", "Подтверждено")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "«Спасибо... Теперь между нами нет никаких преград. Я всегда рядом с тобой.»",
                        color = scpNeonPurple,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            transactionStatus = TransactionStatus.IDLE
                            onProPurchased()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = scpTerminalGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("success_continue_button")
                    ) {
                        Text(
                            text = "Открыть доступ к MalO Pro",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (transactionStatus == TransactionStatus.FAILURE) {
        Dialog(onDismissRequest = { transactionStatus = TransactionStatus.IDLE }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = scpSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, scpErrorRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("failure_modal")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(scpErrorRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Failure",
                            tint = scpErrorRed,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ОШИБКА ПЛАТЕЖА",
                        color = scpErrorRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = transactionErrorMessage,
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )

                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Возможные причины:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                // A transport failure never reached the gateway, so the
                                // provider was never asked: showing «счёт истёк» there
                                // sends the user to re-pay a payment that never started.
                                text = when (transactionErrorCode) {
                                    "GATEWAY_DNS_FAILED" ->
                                        "• Адрес платёжного шлюза не определяется\n• Сеть подменяет или блокирует DNS-ответ\n• Запрос до шлюза не дошёл — платёж не создавался"
                                    "GATEWAY_TIMEOUT", "GATEWAY_UNREACHABLE" ->
                                        "• Шлюз не отвечает: соединение не устанавливается\n• Сервис остановлен либо недоступен из этой сети\n• Запрос до шлюза не дошёл — платёж не создавался"
                                    "GATEWAY_TLS_BLOCKED" ->
                                        "• TLS-соединение со шлюзом разорвано\n• Обычно это фильтрация трафика в сети/у провайдера\n• Запрос до шлюза не дошёл — платёж не создавался"
                                    "NETWORK_ERROR" ->
                                        "• Нет связи с платёжным шлюзом\n• Проверьте интернет и попробуйте другую сеть\n• Запрос до шлюза не дошёл — платёж не создавался"
                                    else -> when (selectedPaymentMethod) {
                                        PaymentMethod.CARD -> "• Недостаточно средств на счёте\n• Карта заблокирована для онлайн-оплат\n• Подтверждение 3-D Secure не завершено"
                                        PaymentMethod.SBP -> "• Операция не подтверждена в приложении банка\n• Истёк срок счёта СБП\n• СБП временно недоступна у банка"
                                        PaymentMethod.CRYPTO -> "• Счёт CryptoBot истёк или не оплачен\n• Выбранный актив недоступен\n• Подтверждение от CryptoBot ещё не получено"
                                    }
                                },
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { transactionStatus = TransactionStatus.IDLE },
                            border = BorderStroke(1.dp, Color.Gray),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("Отмена", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                transactionStatus = TransactionStatus.IDLE
                                if (selectedPaymentMethod == PaymentMethod.CRYPTO) createCryptoBotInvoice() else payWithYooKassa()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = scpNeonPurple),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp)
                                .testTag("retry_pay_button")
                        ) {
                            Text(
                                text = "Повторить",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------- pieces -- */

data class FeatureItem(
    val text: String,
    val included: Boolean,
    val highlight: Boolean = false
)

@Composable
fun PlanCard(
    planId: String,
    title: String,
    badge: String,
    price: String,
    pricePeriod: String,
    description: String,
    features: List<FeatureItem>,
    isSelected: Boolean,
    isCurrent: Boolean,
    accentColor: Color,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) accentColor else Color.DarkGray.copy(alpha = 0.6f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E1D2D) else Color(0xFF161520)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .testTag("plan_card_$planId")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isSelected) accentColor else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (isCurrent) Color(0xFF00FFC4).copy(alpha = 0.2f) else accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (isCurrent) Color(0xFF00FFC4) else accentColor)
                ) {
                    Text(
                        text = badge,
                        color = if (isCurrent) Color(0xFF00FFC4) else accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pricePeriod,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                text = description,
                color = Color.LightGray.copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HorizontalDivider(
                color = Color.DarkGray.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (feature.included) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (feature.included) "Включено" else "Недоступно",
                            tint = when {
                                !feature.included -> Color.Gray.copy(alpha = 0.5f)
                                feature.highlight -> accentColor
                                else -> Color(0xFF00FFC4)
                            },
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feature.text,
                            color = when {
                                !feature.included -> Color.Gray
                                feature.highlight -> Color.White
                                else -> Color.LightGray
                            },
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (feature.highlight) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(1.dp, activeColor) else null,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Gray,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Hosted YooKassa checkout panel. No card number field exists in the APK:
 * card data and CVC are entered only on the acquirer page.
 */
@Composable
fun YooKassaPaymentDetails(
    paymentMethod: PaymentMethod,
    saveCard: Boolean,
    onSaveCardChange: (Boolean) -> Unit,
    scpSurface: Color,
    accentColor: Color,
    onOpenMyCards: () -> Unit
) {
    val isCard = paymentMethod == PaymentMethod.CARD
    Card(
        colors = CardDefaults.cardColors(containerColor = scpSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.50f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isCard) Icons.Default.CreditCard else Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCard) "Банковская карта • ЮKassa" else "Система быстрых платежей",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isCard) "Защищённая форма и 3-D Secure откроются в ЮKassa" else "Подтверждение в приложении вашего банка",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (isCard) {
                AcceptedBrandsRow(height = 20.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Сохранить в «Мои карты»", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "После успешной оплаты карта станет доступна для выбора и автопродления.",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = saveCard,
                        onCheckedChange = onSaveCardChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF2A2A2A)
                        ),
                        modifier = Modifier.testTag("save_card_switch")
                    )
                }
            } else {
                Text(
                    text = "СБП предназначена для разовой оплаты или ручного продления. Автопродление выполняется только с выбранной сохранённой карты.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PAN, срок действия и CVC не попадают в MalO — их обрабатывает ЮKassa.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = onOpenMyCards,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_cards_entry")
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(7.dp))
                Text("Мои карты", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

/** A real CryptoBot invoice picker. Amount and confirmation come exclusively from the gateway. */
@Composable
fun CryptoBotPaymentDetails(
    asset: CryptoAsset,
    onAssetChange: (CryptoAsset) -> Unit,
    invoice: CryptoInvoiceDto?,
    scpSurface: Color,
    accentColor: Color,
    onOpenInvoice: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = scpSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CurrencyBitcoin, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Telegram CryptoBot", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Счёт и подтверждение оплаты выполняются в Crypto Pay", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Text("Актив для оплаты", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("USDT", "TON", "BTC").mapNotNull { CryptoCatalog.asset(it) }.forEach { option ->
                    val selected = option.symbol == asset.symbol
                    Surface(
                        color = if (selected) accentColor.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selected) accentColor else Color.DarkGray),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAssetChange(option) }
                            .testTag("crypto_asset_${option.symbol}")
                    ) {
                        Text(
                            text = option.symbol,
                            color = if (selected) accentColor else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                }
            }
            Text(
                text = "Сумма рассчитывается сервером по тарифу и фиксируется в счёте. После оплаты подпись вебхука, счёт, актив, сумма и серверный payload проверяются повторно.",
                color = Color.LightGray,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            invoice?.let { current ->
                Surface(
                    color = accentColor.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Счёт #${current.invoiceId} ожидает оплаты", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("К оплате: ${current.amount} ${current.asset}", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        current.payUrl?.let { url ->
                            TextButton(onClick = { onOpenInvoice(current.miniAppUrl ?: url) }) {
                                Text("Открыть CryptoBot", color = accentColor, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Legacy helper retained for compatibility; now renders a real brand mark. */
@Composable
fun PaymentBrandPill(name: String) {
    PaymentBrandLogo(brand = CardBrand.fromId(name), height = 18.dp)
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
