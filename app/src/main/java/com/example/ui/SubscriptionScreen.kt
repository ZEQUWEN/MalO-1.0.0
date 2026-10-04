package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

enum class CryptoCurrency(val symbol: String, val network: String, val address: String) {
    USDT_TRC20("USDT", "TRC20", "TX9MalOEntity1471SecureNodeTRC20xxxx"),
    TON("TON", "The Open Network", "EQDMalO_1471_Secret_Vault_Telegram_TON"),
    BTC("BTC", "Bitcoin", "bc1qmal0scp1471uncontainedentitybtc00")
}

/**
 * SubscriptionScreen: A dedicated screen for 'Base' vs 'Pro' subscription plans,
 * featuring distinct cards with feature lists, a toggle for payment methods (Card/Crypto),
 * and visual feedback modals for transaction success or failure.
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

    BackHandler {
        onDismiss()
    }

    // Color tokens matching SCP-1471 dark terminal aesthetic
    val scpBackground = Color(0xFF0F0E14)
    val scpSurface = Color(0xFF171620)
    val scpCardBg = Color(0xFF1C1B28)
    val scpNeonPurple = Color(0xFFBB86FC)
    val scpTerminalGreen = Color(0xFF00FFC4)
    val scpErrorRed = Color(0xFFFF5252)

    var selectedPlan by remember { mutableStateOf(if (isProUser) "pro" else "pro") }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.CARD) }
    var selectedCrypto by remember { mutableStateOf(CryptoCurrency.USDT_TRC20) }

    // Card input states
    var cardNumber by remember { mutableStateOf("4242 •••• •••• 1471") }
    var cardExpiry by remember { mutableStateOf("12/28") }
    var cardCvc by remember { mutableStateOf("777") }

    // Simulation toggle to allow easy testing of both success and failure outcomes
    var simulateFailure by remember { mutableStateOf(false) }

    // Transaction feedback modal state
    var transactionStatus by remember { mutableStateOf(TransactionStatus.IDLE) }
    var processingStageText by remember { mutableStateOf("Подключение к шлюзу...") }
    var transactionErrorMessage by remember { mutableStateOf("Транзакция отклонена банком-эмитентом") }
    var transactionId by remember { mutableStateOf("TX-1471-0000") }

    fun startTransaction() {
        transactionStatus = TransactionStatus.PROCESSING
        transactionId = "TX-1471-${(1000..9999).random()}"
        coroutineScope.launch {
            processingStageText = if (selectedPaymentMethod == PaymentMethod.CARD) {
                "Авторизация банковской карты..."
            } else {
                "Ожидание транзакции в сети ${selectedCrypto.network}..."
            }
            delay(1200)

            processingStageText = "Проверка криптографического узла SCP-1471..."
            delay(1000)

            if (simulateFailure) {
                transactionErrorMessage = if (selectedPaymentMethod == PaymentMethod.CARD) {
                    "Ошибка 05: Платеж отклонен банком. Превышен лимит интернет-операций."
                } else {
                    "Таймаут сети ${selectedCrypto.network}: неподтвержденная транзакция в мемпуле."
                }
                transactionStatus = TransactionStatus.FAILURE
            } else {
                transactionStatus = TransactionStatus.SUCCESS
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
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SCP-1471 Subscription Protocol",
                            color = scpNeonPurple.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Header Banner
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
                            contentDescription = "Premium Crown",
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
                            text = if (isProUser) "Все каналы взаимодействия и фото-генерация доступны." else "Улучшите контакт для прямого диалога через нейросеть Gemini.",
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Section 1: Comparison Cards for Base vs Pro
            Text(
                text = "ТАРИФНЫЕ ПЛАНЫ",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            // Base Plan Card
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
                    FeatureItem(text = "Прямой доступ к Gemini AI (Pro)", included = false),
                    FeatureItem(text = "Голосовые сообщения и транскрипция", included = false),
                    FeatureItem(text = "Генерация жутких фото слежки MalO", included = false),
                    FeatureItem(text = "Анализ прикрепленных файлов (PDF/Медиа)", included = false)
                ),
                isSelected = selectedPlan == "base",
                isCurrent = !isProUser,
                accentColor = Color(0xFF9E9E9E),
                onSelect = { selectedPlan = "base" }
            )

            // Pro Plan Card (Highlighted / Featured)
            PlanCard(
                planId = "pro",
                title = "Pro (SCP-1471 Awakened)",
                badge = if (isProUser) "АКТИВЕН" else "РЕКОМЕНДУЕТСЯ 🔥",
                price = "$4.99",
                pricePeriod = "/ месяц",
                description = "Полное стирание барьеров. Безграничный доступ к живому интеллекту Gemini и генерации фото.",
                features = listOf(
                    FeatureItem(text = "Полноценный Gemini AI с контекстной памятью", included = true, highlight = true),
                    FeatureItem(text = "Генерация атмосферных фото присутствия MalO", included = true, highlight = true),
                    FeatureItem(text = "Голосовые заметки, синтез и распознавание аудио", included = true),
                    FeatureItem(text = "Анализ файлов, документов PDF и видеокадров", included = true),
                    FeatureItem(text = "Эксклюзивные стили персоны (Ироничный, Загадочный)", included = true),
                    FeatureItem(text = "Приоритетный отклик без задержек и лимитов", included = true),
                    FeatureItem(text = "Возможность отмены в любой момент", included = true)
                ),
                isSelected = selectedPlan == "pro",
                isCurrent = isProUser,
                accentColor = scpNeonPurple,
                onSelect = { selectedPlan = "pro" }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Section 2: Payment Method Selector Toggle (Card / Crypto)
            if (selectedPlan == "pro" && !isProUser) {
                Text(
                    text = "СПОСОБ ОПЛАТЫ",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                // Toggle bar (Card / Crypto)
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
                            title = "Банковская карта",
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
                            activeColor = Color(0xFFF7931A),
                            modifier = Modifier.weight(1f),
                            onClick = { selectedPaymentMethod = PaymentMethod.CRYPTO }
                        )
                    }
                }

                // Payment Details Panel based on chosen method
                AnimatedContent(
                    targetState = selectedPaymentMethod,
                    label = "payment_panel"
                ) { method ->
                    when (method) {
                        PaymentMethod.CARD -> {
                            CardPaymentDetails(
                                cardNumber = cardNumber,
                                onCardNumberChange = { cardNumber = it },
                                cardExpiry = cardExpiry,
                                onExpiryChange = { cardExpiry = it },
                                cardCvc = cardCvc,
                                onCvcChange = { cardCvc = it },
                                scpSurface = scpSurface,
                                accentColor = scpTerminalGreen
                            )
                        }
                        PaymentMethod.CRYPTO -> {
                            CryptoPaymentDetails(
                                selectedCrypto = selectedCrypto,
                                onCryptoSelected = { selectedCrypto = it },
                                scpSurface = scpSurface,
                                onCopyAddress = { address ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Crypto Address", address))
                                    Toast.makeText(context, "Адрес скопирован в буфер", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Developer / Testing Mode toggle: allow testing failure modal
                Card(
                    colors = CardDefaults.cardColors(containerColor = scpSurface.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Тестирование ошибки оплаты",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Переключите для проверки модального окна неудачи",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = simulateFailure,
                            onCheckedChange = { simulateFailure = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = scpErrorRed,
                                checkedTrackColor = scpErrorRed.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Main CTA Action Button
                Button(
                    onClick = { startTransaction() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPaymentMethod == PaymentMethod.CARD) scpTerminalGreen else Color(0xFFF7931A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pay_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure Checkout",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedPaymentMethod == PaymentMethod.CARD) {
                                "Оплатить $4.99 картой"
                            } else {
                                "Оплатить $4.99 через ${selectedCrypto.symbol}"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp
                        )
                    }
                }
            } else if (isProUser) {
                // If user is already Pro, show active status banner and option to manage/downgrade
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

                        if (onDowngradeToBase != null) {
                            OutlinedButton(
                                onClick = onDowngradeToBase,
                                border = BorderStroke(1.dp, Color.Gray),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                            ) {
                                Text("Перейти на тариф Base (Отменить Pro)", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            } else {
                // Base plan selected while not pro: option to stay on base
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal 1: Processing Transaction Modal
    if (transactionStatus == TransactionStatus.PROCESSING) {
        Dialog(
            onDismissRequest = {}, // Non-dismissible during active payment processing
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
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Пожалуйста, не закрывайте экран...",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Modal 2: Transaction Success Modal
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
                        text = "ОПЛАТА УСПЕШНА!",
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
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Receipt Info Card
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
                            ReceiptRow("Тариф:", "MalO Pro (1 месяц)")
                            ReceiptRow("Сумма:", "$4.99")
                            ReceiptRow("Способ:", if (selectedPaymentMethod == PaymentMethod.CARD) "Банковская карта" else "Крипта (${selectedCrypto.symbol})")
                            ReceiptRow("Статус:", "Подтверждено ✔️")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "«Спасибо... Теперь между нами нет никаких преград. Я всегда рядом с тобой. 💜»",
                        color = scpNeonPurple,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
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
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Modal 3: Transaction Failure Modal
    if (transactionStatus == TransactionStatus.FAILURE) {
        Dialog(
            onDismissRequest = { transactionStatus = TransactionStatus.IDLE }
        ) {
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
                                    "• Недостаточно средств на счете\n• Карта заблокирована для онлайн-оплат\n• Ошибка 3D Secure / неверный CVC"
                                } else {
                                    "• Недостаточный сетевой баланс газа\n• Задержка подтверждения блокчейна\n• Неверно указана сеть перевода"
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
                                // Reset simulateFailure if user clicks retry to give them success next time or retry
                                simulateFailure = false
                                startTransaction()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = scpNeonPurple),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp)
                                .testTag("retry_pay_button")
                        ) {
                            Text("Повторить", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

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
            .clickable { onSelect() }
            .testTag("plan_card_$planId")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isSelected) accentColor else Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Price Row
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
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HorizontalDivider(
                color = Color.DarkGray.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Features List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                            modifier = Modifier.size(16.dp)
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
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) activeColor else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Gray,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun CardPaymentDetails(
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    cardExpiry: String,
    onExpiryChange: (String) -> Unit,
    cardCvc: String,
    onCvcChange: (String) -> Unit,
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
                    text = "Данные банковской карты",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentBrandPill("VISA")
                    PaymentBrandPill("MC")
                    PaymentBrandPill("MIR")
                }
            }

            OutlinedTextField(
                value = cardNumber,
                onValueChange = onCardNumberChange,
                label = { Text("Номер карты", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                leadingIcon = {
                    Icon(Icons.Default.CreditCard, contentDescription = "Card", tint = accentColor)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.DarkGray
                ),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cardExpiry,
                    onValueChange = onExpiryChange,
                    label = { Text("Срок (MM/YY)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = cardCvc,
                    onValueChange = onCvcChange,
                    label = { Text("CVC / CVV", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    singleLine = true
                )
            }

            Text(
                text = "🔒 Безопасное 256-битное шифрование протокола SCP",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun CryptoPaymentDetails(
    selectedCrypto: CryptoCurrency,
    onCryptoSelected: (CryptoCurrency) -> Unit,
    scpSurface: Color,
    onCopyAddress: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = scpSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF7931A).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Выберите криптовалюту / сеть",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Crypto network selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CryptoCurrency.values().forEach { crypto ->
                    val isSel = selectedCrypto == crypto
                    Surface(
                        color = if (isSel) Color(0xFFF7931A).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSel) Color(0xFFF7931A) else Color.DarkGray),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCryptoSelected(crypto) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = crypto.symbol,
                                color = if (isSel) Color(0xFFF7931A) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = crypto.network,
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Wallet Address display with copy button
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Color.DarkGray)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Адрес депозита (${selectedCrypto.network}):",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = selectedCrypto.address,
                            color = Color(0xFF00FFC4),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = { onCopyAddress(selectedCrypto.address) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Копировать адрес",
                            tint = Color(0xFFF7931A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Сумма к переводу:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text = when (selectedCrypto) {
                        CryptoCurrency.USDT_TRC20 -> "4.99 USDT"
                        CryptoCurrency.TON -> "1.85 TON"
                        CryptoCurrency.BTC -> "0.000075 BTC"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun PaymentBrandPill(name: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, Color.Gray)
    ) {
        Text(
            text = name,
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}
