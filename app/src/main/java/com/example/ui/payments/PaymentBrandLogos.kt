package com.example.ui.payments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.payments.CardBrand

/**
 * Vector-free, dependency-free renderings of the payment-system marks.
 *
 * They are drawn with Compose primitives (Canvas + typography) instead of
 * bundled bitmaps so they stay crisp at any density and carry no trademark
 * asset files in the repository.
 */

private val VisaBlue = Color(0xFF1A1F71)
private val VisaGold = Color(0xFFF7B600)
private val McRed = Color(0xFFEB001B)
private val McYellow = Color(0xFFF79E1B)
private val McOverlap = Color(0xFFFF5F00)
private val MirGreen = Color(0xFF0F754E)
private val MirLightGreen = Color(0xFF4DB45E)
private val AmexBlue = Color(0xFF2E77BC)
private val UnionRed = Color(0xFFE21836)
private val UnionBlue = Color(0xFF00447C)
private val UnionGreen = Color(0xFF007B84)
private val JcbBlue = Color(0xFF0E4C96)
private val JcbRed = Color(0xFFD40511)
private val JcbGreen = Color(0xFF00A650)
private val MaestroRed = Color(0xFFEB001B)
private val MaestroBlue = Color(0xFF0099DF)
private val DiscoverOrange = Color(0xFFF76B1C)

/** White plate every scheme mark sits on, as on a physical card. */
@Composable
private fun LogoPlate(
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(0.5.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun VisaLogo(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    LogoPlate(modifier = modifier.height(height)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "VISA",
                color = VisaBlue,
                fontSize = (height.value * 0.52f).sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (height.value * 0.045f).sp,
                fontFamily = FontFamily.SansSerif
            )
            // Signature gold underline of the Visa wordmark.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .height(1.5.dp)
                    .background(VisaGold)
            )
        }
    }
}

@Composable
fun MastercardLogo(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    LogoPlate(modifier = modifier.height(height)) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(height * 1.45f)
        ) {
            val radius = size.height / 2f
            val leftCenter = Offset(radius * 1.05f, size.height / 2f)
            val rightCenter = Offset(size.width - radius * 1.05f, size.height / 2f)

            drawCircle(color = McRed, radius = radius, center = leftCenter)
            drawCircle(color = McYellow, radius = radius, center = rightCenter)

            // The overlapping lens is the third Mastercard colour.
            val left = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        offset = Offset(leftCenter.x - radius, leftCenter.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                )
            }
            val right = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        offset = Offset(rightCenter.x - radius, rightCenter.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                )
            }
            val lens = Path()
            lens.op(left, right, PathOperation.Intersect)
            drawPath(lens, McOverlap)
        }
    }
}

@Composable
fun MirLogo(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    LogoPlate(modifier = modifier.height(height)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Stylised НСПК ribbon.
            Box(
                modifier = Modifier
                    .height(height * 0.46f)
                    .width(height * 0.17f)
                    .clip(RoundedCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
                    .background(Brush.verticalGradient(listOf(MirLightGreen, MirGreen)))
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "МИР",
                color = MirGreen,
                fontSize = (height.value * 0.5f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (height.value * 0.02f).sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun WordmarkLogo(
    text: String,
    textColor: Color,
    background: Color,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    accents: List<Color> = emptyList()
) {
    LogoPlate(modifier = modifier.height(height), background = background) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            accents.forEach { accent ->
                Box(
                    modifier = Modifier
                        .size(height * 0.3f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(1.5.dp))
            }
            if (accents.isNotEmpty()) Spacer(modifier = Modifier.width(1.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = (height.value * 0.45f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (height.value * 0.02f).sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MaestroLogo(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    LogoPlate(modifier = modifier.height(height)) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(height * 1.45f)
        ) {
            val radius = size.height / 2f
            drawCircle(MaestroRed, radius, Offset(radius * 1.05f, size.height / 2f))
            drawCircle(MaestroBlue, radius, Offset(size.width - radius * 1.05f, size.height / 2f))
        }
    }
}

/** Dispatches to the correct mark; falls back to a neutral chip for unknown BINs. */
@Composable
fun PaymentBrandLogo(
    brand: CardBrand,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp
) {
    when (brand) {
        CardBrand.VISA -> VisaLogo(modifier, height)
        CardBrand.MASTERCARD -> MastercardLogo(modifier, height)
        CardBrand.MIR -> MirLogo(modifier, height)
        CardBrand.MAESTRO -> MaestroLogo(modifier, height)
        CardBrand.AMEX -> WordmarkLogo("AMEX", Color.White, AmexBlue, modifier, height)
        CardBrand.UNIONPAY -> WordmarkLogo(
            text = "UnionPay",
            textColor = UnionBlue,
            background = Color.White,
            modifier = modifier,
            height = height,
            accents = listOf(UnionRed, UnionBlue, UnionGreen)
        )
        CardBrand.JCB -> WordmarkLogo(
            text = "JCB",
            textColor = JcbBlue,
            background = Color.White,
            modifier = modifier,
            height = height,
            accents = listOf(JcbBlue, JcbRed, JcbGreen)
        )
        CardBrand.DISCOVER -> WordmarkLogo("DISCOVER", Color.White, DiscoverOrange, modifier, height)
        CardBrand.UNKNOWN -> UnknownBrandChip(modifier, height)
    }
}

@Composable
private fun UnknownBrandChip(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CARD",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = (height.value * 0.40f).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Logo that cross-fades whenever the detected scheme changes — used as the
 * trailing icon of the card-number field for live BIN detection.
 */
@Composable
fun AnimatedPaymentBrandLogo(
    brand: CardBrand,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp
) {
    AnimatedContent(
        targetState = brand,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.82f)) togetherWith
                (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.82f))
        },
        label = "card_brand_logo"
    ) { target ->
        PaymentBrandLogo(brand = target, modifier = modifier, height = height)
    }
}

/** Row of accepted schemes shown above the card form. */
@Composable
fun AcceptedBrandsRow(
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
    highlighted: CardBrand = CardBrand.UNKNOWN,
    brands: List<CardBrand> = listOf(CardBrand.VISA, CardBrand.MASTERCARD, CardBrand.MIR)
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        brands.forEachIndexed { index, brand ->
            if (index > 0) Spacer(modifier = Modifier.width(5.dp))
            val dimmed = highlighted.isKnown && highlighted != brand
            Box(modifier = Modifier.alpha(if (dimmed) 0.35f else 1f)) {
                PaymentBrandLogo(brand = brand, height = height)
            }
        }
    }
}
