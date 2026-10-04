package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.payments.CardBrand
import com.example.payments.CardInput
import com.example.payments.CardVault
import com.example.payments.CryptoAsset
import com.example.payments.CryptoCatalog
import com.example.payments.CryptoInvoiceDto
import com.example.payments.CryptoNetwork
import com.example.payments.ExpiryVisualTransformation
import com.example.payments.GatewayResult
import com.example.payments.PaymentGateway
import com.example.ui.payments.AcceptedBrandsRow
import com.example.ui.payments.CardHolderScreen
import com.example.ui.payments.CardNumberField
import com.example.ui.payments.CreditCardVisual
import com.example.ui.payments.PaymentBrandLogo
import com.example.ui.payments.fieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PaymentMethod {
    CARD,
    CRYPTO
}

enum class TransactionStatus {
    IDLE,
    PROCESSING,
    SUCCESS,
    FAILURE
}

/**
 * Fallback USD rates used only to preview the crypto amount while offline.
 * When the gateway is reachable, the invoice returns the authoritative amount.
 */
private val offlineUsdRates = mapOf(
    "USDT" to 1.0,
    "USDC" to 1.0,
    "TON" to 2.70,
    "TRX" to 0.12,
    "BTC" to 66000.0,
    "ETH" to 3100.0,
    "SOL" to 145.0,
    "BNB" to 580.0,
    "LTC" to 72.0
)

private fun previewAmount(asset: CryptoAsset, priceUsd: Double = 4.99): String {
    val rate = offlineUsdRates[asset.symbol] ?: 1.0
    val value = priceUsd / rate
    return String.format(java.util.Locale.US, "%.${asset.decimals}f", value)
}

/**
 * SubscriptionScreen: Base vs Pro plans with two payment rails.
 *
 *  • Card — ЮKassa checkout (Visa / Mastercard / МИР) with live payment-system
 *    detection and an optional card-holder binding for auto-renewal.
 *  • Crypto — CryptoBot (Telegram Crypto Pay) invoices with an explicit
 *    asset + blockchain-network selector (TRON, TON, Ethereum, Solana, BTC …).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // Card rail state — digits only, never persisted.
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvc by remember { mutableStateOf("") }
    var cardHolderName by remember { mutableStateOf("") }
    var saveCardForAutoPay by remember { mutableStateOf(true) }
    val detectedBrand = remember(cardNumber) { CardBrand.detect(cardNumber) }

    // Crypto rail state.
    var selectedAsset by remember { mutableStateOf(CryptoCatalog.assets.first()) }
    var selectedNetwork by remember { mutableStateOf(selectedAsset.defaultNetwork()) }
    var cryptoTxHash by remember { mutableStateOf("") }
    var activeInvoice by remember { mutableStateOf<CryptoInvoiceDto?>(null) }
    var invoicePolling by remember { mutableStateOf(false) }

    // Transaction feedback.
    var transactionStatus by remember { mutableStateOf(TransactionStatus.IDLE) }
    var processingStageText by remember { mutableStateOf("Подключение к шлюзу...") }
    var transactionErrorMessage by remember { mutableStateOf("Транзакция отклонена банком-эмитентом") }
    var transactionId by remember { mutableStateOf("TX-1471-0000") }
    var receiptMethodLabel by remember { mutableStateOf("Банковская карта") }
    var receiptAmountLabel by remember { mutableStateOf("$4.99") }

    BackHandler { if (showCardHolder) showCardHolder = false else onDismiss() }

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

    /* ------------------------------------------------------- card payment */

    fun payWithCard() {
        val digits = CardBrand.digitsOf(cardNumber)
        val localError = when {
            !CardBrand.isComplete(digits) ->
                "Номер карты не прошёл проверку Luhn. Проверьте введённые цифры."
            !CardInput.expiryValid(cardExpiry) ->
                "Срок действия карты указан неверно или уже истёк."
            !CardInput.cvcValid(cardCvc, detectedBrand) ->
                "CVC/CVV должен содержать ${detectedBrand.cvcLength} цифры."
            else -> null
        }
        if (localError != null) {
            transactionErrorMessage = localError
            transactionStatus = TransactionStatus.FAILURE
            return
        }

        transactionStatus = TransactionStatus.PROCESSING
        receiptMethodLabel = "${detectedBrand.displayName} ••${digits.takeLast(4)}"
        receiptAmountLabel = "$4.99"

        coroutineScope.launch {
            processingStageText = "Проверка BIN (${detectedBrand.displayName}) и контрольной суммы..."
            delay(600)

            // Preferred path: hosted checkout at the acquirer.
            if (PaymentGateway.isConfigured) {
                processingStageText = "Создание платежа в ЮKassa..."
                when (val result = PaymentGateway.cardCheckout(context, saveCard = saveCardForAutoPay)) {
                    is GatewayResult.Success -> {
                        val payment = result.data.payment
                        val url = payment?.confirmationUrl
                        if (url != null) {
                            processingStageText = "Открываем страницу 3-D Secure..."
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        // Poll until the webhook confirms the charge.
                        val paymentId = payment?.paymentId
                        if (paymentId != null) {
                            processingStageText = "Ожидание подтверждения банка..."
                            repeat(40) {
                                delay(3000)
                                val status = PaymentGateway.cardPaymentStatus(paymentId)
                                if (status is GatewayResult.Success &&
                                    status.data.subscription?.isActive == true
                                ) {
                                    transactionId = paymentId
                                    if (saveCardForAutoPay) {
                                        CardVault.rememberFromInput(
                                            context, digits, cardExpiry, cardHolderName, paymentId
                                        )
                                    }
                                    transactionStatus = TransactionStatus.SUCCESS
                                    return@launch
                                }
                            }
                            transactionErrorMessage =
                                "Банк не подтвердил платёж за отведённое время. Если деньги списались, подписка активируется автоматически."
                            transactionStatus = TransactionStatus.FAILURE
                            return@launch
                        }
                    }
                    is GatewayResult.Error -> {
                        transactionErrorMessage = result.message
                        transactionStatus = TransactionStatus.FAILURE
                        return@launch
                    }
                    GatewayResult.NotConfigured -> Unit
                }
            }

            // Offline / demo path: the bundled validator issues a signed receipt.
            processingStageText = "Шлюз 3-D Secure: авторизация транзакции..."
            delay(700)
            processingStageText = "Генерация криптографической подписи Pro..."
            delay(500)

            val result = com.example.util.SubscriptionValidator.processCardPayment(
                context,
                digits,
                CardInput.formattedExpiry(cardExpiry),
                cardCvc
            )
            when (result) {
                is com.example.util.PaymentValidationResult.Success -> {
                    transactionId = result.receipt.transactionId
                    if (saveCardForAutoPay) {
                        CardVault.rememberFromInput(context, digits, cardExpiry, cardHolderName)
                    }
                    transactionStatus = TransactionStatus.SUCCESS
                }
                is com.example.util.PaymentValidationResult.Failure -> {
                    transactionErrorMessage = result.reason
                    transactionStatus = TransactionStatus.FAILURE
                }
                else -> {
                    transactionErrorMessage = "Требуется дополнительное подтверждение банка."
                    transactionStatus = TransactionStatus.FAILURE
                }
            }
        }
    }

    /* ----------------------------------------------------- crypto payment */

    fun createCryptoInvoice() {
        transactionStatus = TransactionStatus.PROCESSING
        processingStageText = "Создание счёта в CryptoBot (${selectedNetwork.title})..."
        coroutineScope.launch {
            when (val result = PaymentGateway.createInvoice(context, selectedAsset.symbol, selectedNetwork.id)) {
                is GatewayResult.Success -> {
                    activeInvoice = result.data.invoice
                    transactionStatus = TransactionStatus.IDLE
                    result.data.invoice?.payUrl?.let { url ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    invoicePolling = true
                }
                is GatewayResult.Error -> {
                    transactionErrorMessage = result.message
                    transactionStatus = TransactionStatus.FAILURE
                }
                GatewayResult.NotConfigured -> {
                    transactionErrorMessage =
                        "Платёжный шлюз CryptoBot не настроен в этой сборке. Укажите MALO_GATEWAY_URL в .env или подтвердите перевод вручную по TXID."
                    transactionStatus = TransactionStatus.FAILURE
                }
            }
        }
    }

    fun verifyCryptoTxHash() {
        if (cryptoTxHash.isBlank()) {
            transactionErrorMessage =
                "Введите TXID подтверждённой транзакции в сети ${selectedNetwork.title} для ручной проверки."
            transactionStatus = TransactionStatus.FAILURE
            return
        }
        transactionStatus = TransactionStatus.PROCESSING
        receiptMethodLabel = "${selectedAsset.symbol} • ${selectedNetwork.short}"
        receiptAmountLabel = "${previewAmount(selectedAsset)} ${selectedAsset.symbol}"

        coroutineScope.launch {
            processingStageText = "Поиск TXID в сети ${selectedNetwork.title}..."
            delay(800)
            processingStageText =
                "Проверка подтверждений (нужно ${selectedNetwork.minConfirmations}) и защита от повтора..."
            delay(800)

            val result = com.example.util.SubscriptionValidator.processCryptoPayment(
                context,
                selectedAsset.symbol,
                selectedNetwork.title,
                cryptoTxHash,
                receiptAmountLabel
            )
            if (result is com.example.util.PaymentValidationResult.Success) {
                transactionId = result.receipt.transactionId
                transactionStatus = TransactionStatus.SUCCESS
            } else if (result is com.example.util.PaymentValidationResult.Failure) {
                transactionErrorMessage = result.reason
                transactionStatus = TransactionStatus.FAILURE
            }
        }
    }

    // Poll an open CryptoBot invoice until the webhook marks it paid.
    LaunchedEffect(activeInvoice?.invoiceId, invoicePolling) {
        val invoice = activeInvoice ?: return@LaunchedEffect
        if (!invoicePolling) return@LaunchedEffect
        while (invoicePolling) {
            delay(5000)
            when (val status = PaymentGateway.invoiceStatus(invoice.invoiceId)) {
                is GatewayResult.Success -> {
                    val updated = status.data.invoice
                    if (updated != null) activeInvoice = updated
                    if (updated?.isPaid == true || status.data.subscription?.isActive == true) {
                        invoicePolling = false
                        transactionId = updated?.txHash ?: updated?.invoiceId?.toString() ?: "—"
                        receiptMethodLabel = "${invoice.asset} • ${CryptoCatalog.network(invoice.network)?.short ?: invoice.network}"
                        receiptAmountLabel = "${invoice.amount} ${invoice.asset}"
                        transactionStatus = TransactionStatus.SUCCESS
                    }
                }
                else -> Unit
            }
        }
    }

    DisposableEffect(Unit) { onDispose { invoicePolling = false } }

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

            // Card-holder shortcut
            CardHolderEntryRow(
                surface = scpSurface,
                accent = scpNeonPurple,
                onClick = { showCardHolder = true }
            )

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
                price = "$0",
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
                price = "$4.99",
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

                Surface(
                    color = scpSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        PaymentMethodTab(
                            title = "Карта",
                            icon = Icons.Default.CreditCard,
                            isSelected = selectedPaymentMethod == PaymentMethod.CARD,
                            activeColor = scpTerminalGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedPaymentMethod = PaymentMethod.CARD }
                        )
                        PaymentMethodTab(
                            title = "Криптовалюта",
                            icon = Icons.Default.CurrencyBitcoin,
                            isSelected = selectedPaymentMethod == PaymentMethod.CRYPTO,
                            activeColor = scpCryptoOrange,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedPaymentMethod = PaymentMethod.CRYPTO }
                        )
                    }
                }

                AnimatedContent(
                    targetState = selectedPaymentMethod,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "payment_panel"
                ) { method ->
                    when (method) {
                        PaymentMethod.CARD -> CardPaymentDetails(
                            cardNumber = cardNumber,
                            onCardNumberChange = { cardNumber = CardInput.sanitizeNumber(it) },
                            cardExpiry = cardExpiry,
                            onExpiryChange = { cardExpiry = CardInput.sanitizeExpiry(it) },
                            cardCvc = cardCvc,
                            onCvcChange = { cardCvc = CardInput.sanitizeCvc(it, detectedBrand) },
                            holderName = cardHolderName,
                            onHolderChange = { cardHolderName = CardInput.sanitizeHolder(it) },
                            saveCard = saveCardForAutoPay,
                            onSaveCardChange = { saveCardForAutoPay = it },
                            brand = detectedBrand,
                            scpSurface = scpSurface,
                            accentColor = scpTerminalGreen
                        )

                        PaymentMethod.CRYPTO -> CryptoPaymentDetails(
                            selectedAsset = selectedAsset,
                            onAssetSelected = { asset ->
                                selectedAsset = asset
                                selectedNetwork = asset.defaultNetwork()
                                activeInvoice = null
                                invoicePolling = false
                            },
                            selectedNetwork = selectedNetwork,
                            onNetworkSelected = { network ->
                                selectedNetwork = network
                                activeInvoice = null
                                invoicePolling = false
                            },
                            invoice = activeInvoice,
                            txHash = cryptoTxHash,
                            onTxHashChange = { cryptoTxHash = it.trim() },
                            scpSurface = scpSurface,
                            accentColor = scpCryptoOrange,
                            onCopy = { label, value ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                                Toast.makeText(context, "$label скопирован", Toast.LENGTH_SHORT).show()
                            },
                            onOpenInvoice = { url ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            onVerifyManually = { verifyCryptoTxHash() }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (selectedPaymentMethod == PaymentMethod.CARD) payWithCard() else createCryptoInvoice()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPaymentMethod == PaymentMethod.CARD) scpTerminalGreen else scpCryptoOrange
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPaymentMethod == PaymentMethod.CARD) {
                            "Оплатить $4.99 картой"
                        } else {
                            "Счёт в CryptoBot • ${selectedAsset.symbol} ${selectedNetwork.short}"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                                text = "Картхолдер и управление подпиской",
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
                                text = if (selectedPaymentMethod == PaymentMethod.CARD) {
                                    "• Недостаточно средств на счёте\n• Карта заблокирована для онлайн-оплат\n• Ошибка 3-D Secure / неверный CVC"
                                } else {
                                    "• Недостаточно газа в выбранной сети\n• Перевод ещё не набрал подтверждений\n• Выбрана не та сеть (${selectedNetwork.title})"
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
                                if (selectedPaymentMethod == PaymentMethod.CARD) payWithCard() else createCryptoInvoice()
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
private fun CardHolderEntryRow(surface: Color, accent: Color, onClick: () -> Unit) {
    Surface(
        color = surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("card_holder_entry")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Картхолдер и автоплатёж",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Сохранённые карты, продление и отмена подписки",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AcceptedBrandsRow(height = 16.dp)
        }
    }
}

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

/** Card form with live payment-system detection and the card-holder opt-in. */
@Composable
fun CardPaymentDetails(
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    cardExpiry: String,
    onExpiryChange: (String) -> Unit,
    cardCvc: String,
    onCvcChange: (String) -> Unit,
    holderName: String,
    onHolderChange: (String) -> Unit,
    saveCard: Boolean,
    onSaveCardChange: (Boolean) -> Unit,
    brand: CardBrand,
    scpSurface: Color,
    accentColor: Color
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Данные карты",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AcceptedBrandsRow(height = 18.dp, highlighted = brand)
            }

            // Live card preview — the brand mark swaps as soon as the BIN is known.
            CreditCardVisual(
                brand = brand,
                numberText = CardBrand.format(cardNumber),
                holderName = holderName,
                expiryText = CardInput.formattedExpiry(cardExpiry),
                labelText = if (brand.isKnown) "Определено: ${brand.displayName}" else "MalO Pro • 30 дней"
            )

            CardNumberField(
                value = cardNumber,
                onValueChange = onCardNumberChange,
                brand = brand,
                accentColor = accentColor
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cardExpiry,
                    onValueChange = onExpiryChange,
                    label = { Text("Срок MM/YY", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    visualTransformation = ExpiryVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = fieldColors(accentColor),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cardCvc,
                    onValueChange = onCvcChange,
                    label = {
                        Text(
                            if (brand == CardBrand.AMEX) "CID (4)" else "CVC (3)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation('•'),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = fieldColors(accentColor),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = holderName,
                onValueChange = onHolderChange,
                label = { Text("Имя держателя", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                placeholder = { Text("IVAN IVANOV", fontSize = 12.sp, color = Color.Gray) },
                singleLine = true,
                colors = fieldColors(accentColor),
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сохранить карту в картхолдере",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Автопродление каждые 30 дней, отмена — в любой момент",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Шифрование TLS 1.3 • PCI DSS",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = {
                        onCardNumberChange("2202201234564477")
                        onExpiryChange("1230")
                        onCvcChange("777")
                        onHolderChange("IVAN IVANOV")
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Тестовая карта",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** CryptoBot checkout: asset + network matrix, invoice link and manual TXID. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CryptoPaymentDetails(
    selectedAsset: CryptoAsset,
    onAssetSelected: (CryptoAsset) -> Unit,
    selectedNetwork: CryptoNetwork,
    onNetworkSelected: (CryptoNetwork) -> Unit,
    invoice: CryptoInvoiceDto?,
    txHash: String,
    onTxHashChange: (String) -> Unit,
    scpSurface: Color,
    accentColor: Color,
    onCopy: (String, String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    onVerifyManually: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = scpSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CurrencyBitcoin,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CryptoBot • Telegram Crypto Pay",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "1. Выберите криптовалюту",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CryptoCatalog.assets.forEach { asset ->
                    val isSel = asset.symbol == selectedAsset.symbol
                    val color = Color(asset.colorHex)
                    Surface(
                        color = if (isSel) color.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSel) color else Color.DarkGray),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAssetSelected(asset) }
                            .testTag("crypto_asset_${asset.symbol}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = asset.symbol,
                                color = if (isSel) color else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Text(
                text = "2. Выберите сеть перевода",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedAsset.networks.forEach { network ->
                    val isSel = network.id == selectedNetwork.id
                    val color = Color(network.colorHex)
                    Surface(
                        color = if (isSel) color.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSel) color else Color.DarkGray),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNetworkSelected(network) }
                            .testTag("crypto_network_${network.id}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = network.short,
                                color = if (isSel) color else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = network.title,
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReceiptRow("К оплате:", "${invoice?.amount ?: previewAmount(selectedAsset)} ${selectedAsset.symbol}")
                    ReceiptRow("Сеть:", selectedNetwork.title)
                    ReceiptRow("Подтверждений:", "${selectedNetwork.minConfirmations}")
                    ReceiptRow("Эквивалент:", "$4.99 / 30 дней")
                }
            }

            AnimatedVisibility(visible = invoice != null) {
                invoice?.let { inv ->
                    Surface(
                        color = accentColor.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = accentColor,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Счёт #${inv.invoiceId} ожидает оплаты",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Оплатите счёт в @CryptoBot — подписка активируется автоматически по вебхуку.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                inv.payUrl?.let { url ->
                                    Button(
                                        onClick = { onOpenInvoice(url) },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Открыть CryptoBot",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(onClick = { onCopy("Ссылка на счёт", url) }) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Скопировать ссылку",
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

            Text(
                text = "Уже перевели вручную? Подтвердите по TXID:",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )

            OutlinedTextField(
                value = txHash,
                onValueChange = onTxHashChange,
                label = { Text("Хэш транзакции (TXID)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                placeholder = { Text("Вставьте TXID из кошелька...", fontSize = 11.sp, color = Color.Gray) },
                singleLine = true,
                colors = fieldColors(accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_hash_field")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onVerifyManually,
                    border = BorderStroke(1.dp, accentColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_tx_button")
                ) {
                    Text("Проверить TXID", fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                TextButton(
                    onClick = {
                        onTxHashChange("a8f4c2e6b9d10457382910fae5cb3498172049eaf5bc218390d4e5fa68c719e0")
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = "Тестовый TXID",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
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
