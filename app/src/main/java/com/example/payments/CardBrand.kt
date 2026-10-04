package com.example.payments

/**
 * Payment-system (card scheme) detection from the typed PAN.
 *
 * The ranges below follow the public IIN/BIN allocations of each scheme, so the
 * UI can show the correct logo as soon as the first digits are entered — long
 * before the number is complete.
 */
enum class CardBrand(
    val id: String,
    val displayName: String,
    /** Digit groups used to format the PAN, e.g. 4-4-4-4. */
    val numberGroups: List<Int>,
    val cvcLength: Int,
    val maxLength: Int
) {
    VISA("VISA", "Visa", listOf(4, 4, 4, 4), 3, 19),
    MASTERCARD("MASTERCARD", "Mastercard", listOf(4, 4, 4, 4), 3, 16),
    MIR("MIR", "МИР", listOf(4, 4, 4, 4), 3, 19),
    MAESTRO("MAESTRO", "Maestro", listOf(4, 4, 4, 4), 3, 19),
    AMEX("AMEX", "American Express", listOf(4, 6, 5), 4, 15),
    UNIONPAY("UNIONPAY", "UnionPay", listOf(4, 4, 4, 4), 3, 19),
    JCB("JCB", "JCB", listOf(4, 4, 4, 4), 3, 19),
    DISCOVER("DISCOVER", "Discover", listOf(4, 4, 4, 4), 3, 19),
    UNKNOWN("UNKNOWN", "Карта", listOf(4, 4, 4, 4), 3, 19);

    val isKnown: Boolean get() = this != UNKNOWN

    companion object {

        /** Strips spaces, dashes and any stray characters from user input. */
        fun digitsOf(input: String): String = input.filter { it.isDigit() }

        /**
         * Detects the scheme from a partial or complete card number.
         * Returns [UNKNOWN] while the prefix is still ambiguous.
         */
        fun detect(input: String): CardBrand {
            val digits = digitsOf(input)
            if (digits.isEmpty()) return UNKNOWN

            val d1 = digits.take(1).toIntOrNull() ?: return UNKNOWN
            val d2 = digits.take(2).toIntOrNull()
            val d3 = digits.take(3).toIntOrNull()
            val d4 = digits.take(4).toIntOrNull()
            // МИР — current public 4-digit IIN prefix range 2200..2205.
            // This must be tested *before* Mastercard's 2-series logic. In
            // particular, 2205 (for example, 22051387…) is МИР, not Mastercard.
            if (d4 != null && digits.length >= 4 && d4 in 2200..2205) return MIR
            if (digits.length in 1..3 && digits.startsWith("220")) return MIR

            // Visa — always starts with 4.
            if (d1 == 4) return VISA

            // Mastercard — 51..55 and 2221..2720.
            if (d2 != null && digits.length >= 2 && d2 in 51..55) return MASTERCARD
            if (d4 != null && digits.length >= 4 && d4 in 2221..2720) return MASTERCARD

            // American Express — 34 / 37.
            if (d2 != null && digits.length >= 2 && (d2 == 34 || d2 == 37)) return AMEX

            // UnionPay — 62, 81.
            if (d2 != null && digits.length >= 2 && (d2 == 62 || d2 == 81)) return UNIONPAY

            // JCB — 3528..3589.
            if (d4 != null && digits.length >= 4 && d4 in 3528..3589) return JCB

            // Diners / Discover family — 6011, 644..649, 65.
            if (d4 != null && digits.length >= 4 && d4 == 6011) return DISCOVER
            if (d3 != null && digits.length >= 3 && d3 in 644..649) return DISCOVER
            if (d2 != null && digits.length >= 2 && d2 == 65) return DISCOVER

            // Maestro — 50, 56..58, 6759, 6220 (checked last, it overlaps a lot).
            if (d4 != null && digits.length >= 4 && (d4 == 6759 || d4 == 6220)) return MAESTRO
            if (d2 != null && digits.length >= 2 && (d2 == 50 || d2 in 56..58)) return MAESTRO

            // Keep 2-series pending until the 4-digit Mastercard range can be
            // established. Never classify every 2xxxxx prefix as Mastercard:
            // that is how МИР 2205 used to receive the wrong logo.
            if (d1 == 2 && digits.length < 4) return UNKNOWN

            return UNKNOWN
        }

        /** Luhn (mod-10) checksum used by every scheme above. */
        fun luhnValid(input: String): Boolean {
            val digits = digitsOf(input)
            if (digits.length < 12) return false
            var sum = 0
            var alternate = false
            for (i in digits.length - 1 downTo 0) {
                var n = digits[i].digitToInt()
                if (alternate) {
                    n *= 2
                    if (n > 9) n -= 9
                }
                sum += n
                alternate = !alternate
            }
            return sum % 10 == 0
        }

        /** True when the PAN is long enough for the detected scheme and passes Luhn. */
        fun isComplete(input: String): Boolean {
            val digits = digitsOf(input)
            val brand = detect(digits)
            val expected = brand.numberGroups.sum()
            val lengthOk = when (brand) {
                AMEX -> digits.length == 15
                MASTERCARD -> digits.length == 16
                UNKNOWN -> digits.length in 13..19
                else -> digits.length in 13..brand.maxLength && digits.length >= expected
            }
            return lengthOk && luhnValid(digits)
        }

        /** `4242 4242 4242 4242` style grouping that matches the detected scheme. */
        fun format(input: String): String {
            val digits = digitsOf(input)
            val brand = detect(digits)
            val builder = StringBuilder()
            var index = 0
            for (group in brand.numberGroups) {
                if (index >= digits.length) break
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(digits, index, minOf(index + group, digits.length))
                index += group
            }
            if (index < digits.length) {
                // Longer PANs (Maestro/МИР up to 19) keep 4-digit tails.
                var rest = index
                while (rest < digits.length) {
                    builder.append(' ')
                    builder.append(digits, rest, minOf(rest + 4, digits.length))
                    rest += 4
                }
            }
            return builder.toString()
        }

        /** Masked representation used by the card-holder UI: `•••• •••• •••• 4477`. */
        fun maskedNumber(last4: String, brand: CardBrand = UNKNOWN): String {
            val groups = brand.numberGroups
            val head = groups.dropLast(1).joinToString(" ") { "•".repeat(it) }
            return if (head.isEmpty()) last4 else "$head $last4"
        }

        fun fromId(id: String?): CardBrand =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNKNOWN
    }
}
