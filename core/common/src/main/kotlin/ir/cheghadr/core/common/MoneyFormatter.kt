package ir.cheghadr.core.common

import java.math.BigDecimal

/** Which digits to render numbers with. */
enum class DigitStyle { PERSIAN, LATIN }

/** App display language. */
enum class AppLanguage { PERSIAN, ENGLISH }

/**
 * Deterministic, locale-aware formatting of prices and percentages.
 *
 * All functions are pure and unit tested. Persian output uses extended
 * Arabic-Indic digits (۰-۹, U+06F0..U+06F9) and the Arabic thousands
 * separator «٬»; English output uses Latin digits and commas.
 */
object MoneyFormatter {

    private val persianDigits = charArrayOf(
        '۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹',
    )

    const val PERSIAN_GROUPING_SEPARATOR = '٬'
    const val PERSIAN_DECIMAL_SEPARATOR = '٫'
    const val LATIN_GROUPING_SEPARATOR = ','
    const val LATIN_DECIMAL_SEPARATOR = '.'

    /** Formats [value] with grouping and a smart number of fraction digits. */
    fun formatPrice(
        value: BigDecimal,
        style: DigitStyle,
        minFraction: Int? = null,
        maxFraction: Int? = null,
    ): String {
        val range = defaultFractionDigits(value)
        val min = minFraction ?: range.first
        val max = maxFraction ?: range.second
        val grouped = groupDigits(value, min, max)
        return localizeDigitsAndSeparators(grouped, style)
    }

    /** Formats a signed absolute change, e.g. «+۱۲٬۳۴۵» / «-12,345». */
    fun formatChangeAbs(value: BigDecimal, style: DigitStyle): String {
        val sign = if (value.signum() >= 0) "+" else "−"
        val magnitude = groupDigits(value.abs(), 0, 2)
        return sign + localizeDigitsAndSeparators(magnitude, style)
    }

    /** Formats a percentage, e.g. «۱٫۲٪» / «1.2%». */
    fun formatPercent(value: BigDecimal, style: DigitStyle, withSign: Boolean = true): String {
        val sign = when {
            !withSign -> ""
            value.signum() > 0 -> "+"
            value.signum() < 0 -> "−"
            else -> ""
        }
        val magnitude = groupDigits(value.abs(), 0, 2)
        val percentSign = if (style == DigitStyle.PERSIAN) '٪' else '%'
        return sign + localizeDigitsAndSeparators(magnitude, style) + percentSign
    }

    /**
     * Converts a rial-denominated amount to toman (divide by 10) — displayed
     * values are relabeled only through this explicit, tested conversion.
     */
    fun rialToToman(rial: BigDecimal): BigDecimal = rial.divide(BigDecimal.TEN)

    fun tomanToRial(toman: BigDecimal): BigDecimal = toman.multiply(BigDecimal.TEN)

    /** Converts a formatted-agnostic BigDecimal to a compact string with up to 8 decimals. */
    fun plain(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

    // ---------------------------------------------------------------- internals

    /**
     * Smart fraction digits:
     *  - |v| >= 100_000        → 0..0   (rial/toman-scale prices)
     *  - |v| >= 1_000          → 0..2   (large USD prices)
     *  - |v| >= 1              → 2..4   (regular prices)
     *  - |v| > 0               → 4..8   (small crypto prices)
     */
    internal fun defaultFractionDigits(value: BigDecimal): Pair<Int, Int> {
        val abs = value.abs()
        return when {
            abs >= BigDecimal.valueOf(100_000L) -> 0 to 0
            abs >= BigDecimal.valueOf(1_000L) -> 0 to 2
            abs >= BigDecimal.ONE -> 2 to 4
            abs > BigDecimal.ZERO -> 4 to 8
            else -> 0 to 0
        }
    }

    /** Groups the integer part in 3-digit chunks; applies fraction bounds. */
    internal fun groupDigits(value: BigDecimal, minFraction: Int, maxFraction: Int): String {
        require(maxFraction >= minFraction) { "maxFraction < minFraction" }
        val scaled = value.setScale(maxFraction, java.math.RoundingMode.HALF_UP)
        val plain = scaled.toPlainString()
        val (rawInt, rawFrac) = if (plain.contains('.')) plain.split('.', limit = 2) else listOf(plain, "")
        val groupedInt = rawInt.reversed().chunked(3).joinToString(",").reversed()
        var frac = rawFrac.trimEnd('0')
        if (frac.length < minFraction) frac = frac.padEnd(minFraction, '0')
        return if (frac.isEmpty()) groupedInt else "$groupedInt.$frac"
    }

    /** Maps digits/separators of a latin-formatted string to the requested style. */
    fun localizeDigitsAndSeparators(formatted: String, style: DigitStyle): String {
        if (style == DigitStyle.LATIN) return formatted
        return buildString(formatted.length) {
            for (c in formatted) {
                when {
                    c.isDigit() -> append(persianDigits[c - '0'])
                    c == '.' -> append(PERSIAN_DECIMAL_SEPARATOR)
                    c == ',' -> append(PERSIAN_GROUPING_SEPARATOR)
                    else -> append(c)
                }
            }
        }
    }

    /** Localizes an arbitrary integer (counts, ids) to the requested digit style. */
    fun localizeInt(value: Int, style: DigitStyle): String =
        localizeDigitsAndSeparators(value.toString(), style)

    /** Formats a time-of-day as HH:mm with the requested digits. */
    fun formatTime(hour: Int, minute: Int, style: DigitStyle): String {
        val text = "%02d:%02d".format(hour, minute)
        return localizeDigitsAndSeparators(text, style)
    }
}
