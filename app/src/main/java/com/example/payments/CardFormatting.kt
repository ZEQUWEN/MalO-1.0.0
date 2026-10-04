package com.example.payments

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Calendar

/**
 * Visual transformation that renders the raw digit stream as grouped PAN
 * (`4242 4242 4242 4242`) while the state keeps only digits. Grouping follows
 * the detected [CardBrand], so AmEx becomes 4-6-5.
 */
class CardNumberVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = CardBrand.digitsOf(text.text)
        val brand = CardBrand.detect(digits)
        val groups = brand.numberGroups

        val out = StringBuilder()
        var groupIndex = 0
        var inGroup = 0
        for (i in digits.indices) {
            val groupSize = groups.getOrElse(groupIndex) { 4 }
            if (inGroup == groupSize) {
                out.append(' ')
                groupIndex++
                inGroup = 0
            }
            out.append(digits[i])
            inGroup++
        }

        val separatorPositions = mutableListOf<Int>()
        out.forEachIndexed { index, c -> if (c == ' ') separatorPositions.add(index) }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var transformed = offset
                for (position in separatorPositions) {
                    if (position < transformed) transformed++ else break
                }
                return transformed.coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                var original = offset
                for (position in separatorPositions) {
                    if (position < offset) original-- else break
                }
                return original.coerceIn(0, digits.length)
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

/** Renders `1228` as `12/28`. */
class ExpiryVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(4)
        val out = when {
            digits.length <= 2 -> digits
            else -> "${digits.substring(0, 2)}/${digits.substring(2)}"
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                (if (offset <= 2) offset else offset + 1).coerceIn(0, out.length)

            override fun transformedToOriginal(offset: Int): Int =
                (if (offset <= 2) offset else offset - 1).coerceIn(0, digits.length)
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

object CardInput {

    /** Keeps only digits and clamps to the maximum PAN length of the scheme. */
    fun sanitizeNumber(raw: String): String {
        val digits = CardBrand.digitsOf(raw)
        val brand = CardBrand.detect(digits)
        return digits.take(if (brand == CardBrand.AMEX) 15 else brand.maxLength)
    }

    /**
     * Keeps `MMYY` digits and auto-corrects an impossible month
     * (typing `9` first becomes `09`).
     */
    fun sanitizeExpiry(raw: String): String {
        var digits = raw.filter { it.isDigit() }.take(4)
        if (digits.length == 1 && digits[0] > '1') digits = "0$digits"
        if (digits.length >= 2) {
            val month = digits.substring(0, 2).toIntOrNull() ?: 0
            if (month == 0) digits = "01" + digits.drop(2)
            if (month > 12) digits = "12" + digits.drop(2)
        }
        return digits
    }

    fun sanitizeCvc(raw: String, brand: CardBrand): String =
        raw.filter { it.isDigit() }.take(brand.cvcLength)

    fun sanitizeHolder(raw: String): String =
        raw.filter { it.isLetter() || it == ' ' || it == '-' || it == '\'' }.take(26).trim().uppercase()

    fun formattedExpiry(digits: String): String {
        val clean = digits.filter { it.isDigit() }
        return if (clean.length <= 2) clean else "${clean.substring(0, 2)}/${clean.substring(2)}"
    }

    /** True when `MMYY` is a valid, non-expired date. */
    fun expiryValid(digits: String): Boolean {
        val clean = digits.filter { it.isDigit() }
        if (clean.length != 4) return false
        val month = clean.substring(0, 2).toIntOrNull() ?: return false
        val year = clean.substring(2, 4).toIntOrNull() ?: return false
        if (month !in 1..12) return false

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR) % 100
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        return year > currentYear || (year == currentYear && month >= currentMonth)
    }

    fun cvcValid(cvc: String, brand: CardBrand): Boolean =
        cvc.length == brand.cvcLength && cvc.all { it.isDigit() }
}
