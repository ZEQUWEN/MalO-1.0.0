package com.example.ui.payments

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.payments.CardBrand
import com.example.payments.SavedCard

/**
 * The physical-looking card used by the card holder.
 *
 * Styling follows the SCP-1471 terminal palette: near-black plastic, a neon
 * purple sheen and a brand mark that reacts live to the typed BIN.
 */
@Composable
fun CreditCardVisual(
    brand: CardBrand,
    numberText: String,
    holderName: String,
    expiryText: String,
    modifier: Modifier = Modifier,
    isDefault: Boolean = false,
    isExpired: Boolean = false,
    labelText: String = "MalO Pro • автоплатёж",
    dimmed: Boolean = false
) {
    val accent by animateColorAsState(
        targetValue = when (brand) {
            CardBrand.VISA -> Color(0xFF2A5BD7)
            CardBrand.MASTERCARD -> Color(0xFFF79E1B)
            CardBrand.MIR -> Color(0xFF4DB45E)
            CardBrand.AMEX -> Color(0xFF2E77BC)
            CardBrand.UNIONPAY -> Color(0xFFE21836)
            CardBrand.JCB -> Color(0xFF0E4C96)
            else -> Color(0xFFBB86FC)
        },
        animationSpec = tween(450),
        label = "card_accent"
    )

    val shimmer = rememberInfiniteTransition(label = "card_shimmer")
    val shimmerShift by shimmer.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Restart),
        label = "card_shimmer_shift"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f) // ISO/IEC 7810 ID-1
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF17141F),
                        Color(0xFF221B33),
                        accent.copy(alpha = if (dimmed) 0.18f else 0.34f)
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = if (dimmed) 0.25f else 0.55f), RoundedCornerShape(18.dp))
    ) {
        // Moving sheen + faint guilloché lines.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineColor = accent.copy(alpha = 0.10f)
            var y = size.height * 0.15f
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y - size.height * 0.35f),
                    strokeWidth = 1.2f
                )
                y += size.height * 0.16f
            }
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                    start = Offset(size.width * (shimmerShift - 0.35f), 0f),
                    end = Offset(size.width * (shimmerShift + 0.35f), size.height)
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "MalO",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = labelText,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedPaymentBrandLogo(brand = brand, height = 24.dp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                EmvChip()
                Spacer(modifier = Modifier.width(10.dp))
                ContactlessGlyph(tint = Color.White.copy(alpha = 0.55f))
            }

            Text(
                text = numberText.ifBlank { "•••• •••• •••• ••••" },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "ДЕРЖАТЕЛЬ",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = holderName.ifBlank { "CARD HOLDER" },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isExpired) "ИСТЕКЛА" else "ДЕЙСТВИТЕЛЬНА ДО",
                        color = if (isExpired) Color(0xFFFF5252) else Color.White.copy(alpha = 0.45f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = expiryText.ifBlank { "••/••" },
                        color = if (isExpired) Color(0xFFFF5252) else Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (isDefault) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 46.dp, end = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF00FFC4).copy(alpha = 0.18f))
                    .border(0.5.dp, Color(0xFF00FFC4), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ОСНОВНАЯ",
                    color = Color(0xFF00FFC4),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** Convenience overload for a stored card. */
@Composable
fun CreditCardVisual(
    card: SavedCard,
    modifier: Modifier = Modifier,
    labelText: String = "MalO Pro • автоплатёж"
) {
    CreditCardVisual(
        brand = card.brand,
        numberText = card.maskedNumber,
        holderName = card.holderName.orEmpty(),
        expiryText = card.expiryFormatted,
        modifier = modifier,
        isDefault = card.isDefault,
        isExpired = card.isExpired,
        labelText = labelText
    )
}

@Composable
private fun EmvChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE9C46A), Color(0xFFC9A227), Color(0xFFF2DFA6))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1f)
            val contact = Color(0xFF8A6D1F).copy(alpha = 0.8f)
            drawLine(contact, Offset(0f, size.height * 0.33f), Offset(size.width, size.height * 0.33f), stroke.width)
            drawLine(contact, Offset(0f, size.height * 0.66f), Offset(size.width, size.height * 0.66f), stroke.width)
            drawLine(contact, Offset(size.width * 0.33f, 0f), Offset(size.width * 0.33f, size.height), stroke.width)
            drawLine(contact, Offset(size.width * 0.66f, 0f), Offset(size.width * 0.66f, size.height), stroke.width)
        }
    }
}

@Composable
private fun ContactlessGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val center = Offset(size.width * 0.1f, size.height / 2f)
        for (i in 1..3) {
            val radius = size.minDimension * 0.22f * i
            drawArc(
                color = tint,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 1.6f)
            )
        }
    }
}
