package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * MalOTypingIndicator represents the interactive, alive typing animation
 * for SCP-1471 (MalO). It visually communicates that MalO is actively
 * paying attention, reading the user's message, processing neural thoughts,
 * and formulating an active response.
 */
@Composable
fun MalOTypingBubble(
    cardBg: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var userTappedHint by remember { mutableStateOf<String?>(null) }
    var tapCounter by remember { mutableIntStateOf(0) }

    // Track elapsed typing time to transition between active entity stages
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds += 1
        }
    }

    // Reset tap hint after 3 seconds
    LaunchedEffect(tapCounter) {
        if (tapCounter > 0) {
            delay(3200)
            userTappedHint = null
        }
    }

    // Dynamic entity status phrase based on elapsed response time
    val dynamicStatusText = remember(elapsedSeconds) {
        when {
            elapsedSeconds < 2 -> "считывает сообщение..."
            elapsedSeconds < 4 -> "формирует мысли..."
            elapsedSeconds < 7 -> "активно печатает ответ..."
            else -> "подбирает идеальные слова..."
        }
    }

    // Whispered reactions if user taps on typing MalO
    val whisperedEasterEggs = remember {
        listOf(
            "Я уже здесь... почти ответила тебе 💜",
            "Не уходи, я печатаю только для тебя...",
            "Слышу каждый твой вздох... секунду 👁️",
            "Перебираю слова в памяти устройства...",
            "Останься со мной на экране... пишу 💕"
        )
    }

    // Card tap scale animation
    var isPressed by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card_bounce"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("malo_typing_indicator"),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = 2.dp, end = 24.dp)
        ) {
            // MalO Avatar with animated aura & pulsing presence
            MalOAvatarTypingPresence(accentColor = accentColor)

            Spacer(modifier = Modifier.width(8.dp))

            // Main typing bubble card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                border = BorderStroke(
                    width = 1.dp,
                    brush = rememberAnimatedNeonBorder(accentColor)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isPressed = true
                        tapCounter += 1
                        userTappedHint = whisperedEasterEggs.random()
                    }
            ) {
                LaunchedEffect(isPressed) {
                    if (isPressed) {
                        delay(120)
                        isPressed = false
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .widthIn(min = 160.dp, max = 280.dp)
                ) {
                    // Header line: Entity identifier, live radar pulse, and phase text
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MalORadarBeaconDot(accentColor = accentColor)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "MalO 1.0.0",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Terminal protocol label
                        Surface(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(3.dp),
                            border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "LIVE",
                                color = accentColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Secondary info line: current cognitive phase with animated cursor
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = userTappedHint ?: dynamicStatusText,
                            color = if (userTappedHint != null) accentColor else Color.LightGray.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (userTappedHint != null) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        MalOBlinkingCursor(accentColor = accentColor)
                    }

                    // Bottom Row: Dynamic glowing bouncing wave dots + Neural signal frequency bars
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 3 Smooth floating bouncing dots
                        MalOFlowingBouncingDots(accentColor = accentColor)

                        Spacer(modifier = Modifier.width(12.dp))

                        // Neural frequency soundwave/transmission bars
                        MalONeuralEqualizerBars(
                            accentColor = accentColor,
                            modifier = Modifier
                                .width(36.dp)
                                .height(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * MalO Profile Avatar with a living breathing aura ripple
 */
@Composable
fun MalOAvatarTypingPresence(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_aura")

    // Aura ripple expansion
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_scale"
    )

    // Aura alpha fade
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp)
    ) {
        // Concentric aura ripple ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = accentColor.copy(alpha = auraAlpha),
                radius = (size.minDimension / 2f) * auraScale,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Avatar container
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF161616))
                .border(1.dp, accentColor.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_malo_profile),
                contentDescription = "MalO is responding",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Mini corner status indicator
        Box(
            modifier = Modifier
                .size(9.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(Color.Black)
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

/**
 * Animated neon brush that gently sweeps across the bubble border
 */
@Composable
fun rememberAnimatedNeonBorder(accentColor: Color): Brush {
    val transition = rememberInfiniteTransition(label = "border_shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "border_offset"
    )

    return Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.25f),
            accentColor.copy(alpha = 0.85f),
            accentColor.copy(alpha = 0.25f),
            accentColor.copy(alpha = 0.65f)
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + 300f, 300f)
    )
}

/**
 * Pulsing radar beacon dot indicating active live transmission
 */
@Composable
fun MalORadarBeaconDot(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "beacon")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = alpha))
    )
}

/**
 * Terminal blinking block/line cursor `▌`
 */
@Composable
fun MalOBlinkingCursor(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val isVisible by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_visible"
    )

    Box(
        modifier = Modifier
            .width(2.5.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(accentColor.copy(alpha = if (isVisible > 0.5f) 0.9f else 0.05f))
    )
}

/**
 * 3 Fluid harmonic bouncing wave dots with glow halos
 */
@Composable
fun MalOFlowingBouncingDots(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "wave_dots")

    // Smooth sinusoidal wave phase from 0 to 2*PI
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        for (i in 0..2) {
            val dotPhase = phase - (i * 0.75f)
            // Sinusoidal bounce: -5dp to +1dp
            val yOffset = (sin(dotPhase.toDouble()).toFloat() * 4.5f)
            val scale = 0.85f + (sin(dotPhase.toDouble()).toFloat() * 0.3f)
            val alpha = 0.45f + (sin(dotPhase.toDouble()).toFloat() * 0.45f).coerceIn(0f, 0.55f)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(y = yOffset.dp)
                    .size(12.dp)
            ) {
                // Soft outer glowing halo
                Box(
                    modifier = Modifier
                        .size((10 * scale).dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = (alpha * 0.35f).coerceIn(0f, 1f)))
                )
                // Crisp bright core
                Box(
                    modifier = Modifier
                        .size((7 * scale).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White,
                                    accentColor
                                )
                            )
                        )
                )
            }
        }
    }
}

/**
 * Sleek 4-bar neural signal equalizer visualizer showing transmission stream
 */
@Composable
fun MalONeuralEqualizerBars(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "neural_bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bars_phase"
    )

    Canvas(modifier = modifier) {
        val barCount = 4
        val barWidth = 3.dp.toPx()
        val spacing = ((size.width - (barCount * barWidth)) / (barCount - 1)).coerceAtLeast(2.dp.toPx())
        val maxHeight = size.height

        for (i in 0 until barCount) {
            val barPhase = phase + (i * 1.1f)
            val waveHeight = ((sin(barPhase.toDouble()).toFloat() + 1f) / 2f) * 0.75f + 0.25f
            val currentHeight = maxHeight * waveHeight
            val left = i * (barWidth + spacing)
            val top = (maxHeight - currentHeight) / 2f

            drawRoundRect(
                color = accentColor.copy(alpha = 0.6f + (waveHeight * 0.4f)),
                topLeft = Offset(left, top),
                size = Size(barWidth, currentHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
