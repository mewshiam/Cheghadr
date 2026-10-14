package ir.cheghadr.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatterTest {

    @Test
    fun `persian digits and separators`() {
        assertEquals("۸۳٬۸۶۳", MoneyFormatter.formatPrice(BigDecimal.valueOf(83863), DigitStyle.PERSIAN))
        assertEquals("2,519.25", MoneyFormatter.formatPrice(BigDecimal("2519.25"), DigitStyle.LATIN))
        assertEquals("۲٬۵۱۹٫۲۵", MoneyFormatter.formatPrice(BigDecimal("2519.25"), DigitStyle.PERSIAN))
    }

    @Test
    fun `large rial scale has no fraction`() {
        assertEquals("420,000", MoneyFormatter.formatPrice(BigDecimal.valueOf(420000), DigitStyle.LATIN))
        assertEquals("۱٬۲۳۴٬۵۶۷", MoneyFormatter.formatPrice(BigDecimal.valueOf(1234567), DigitStyle.PERSIAN))
    }

    @Test
    fun `small crypto values keep precision`() {
        assertEquals("0.00001234", MoneyFormatter.formatPrice(BigDecimal("0.00001234"), DigitStyle.LATIN))
        assertEquals("۰٫۰۰۰۰۱۲۳۴", MoneyFormatter.formatPrice(BigDecimal("0.00001234"), DigitStyle.PERSIAN))
    }

    @Test
    fun `explicit fraction bounds are respected`() {
        assertEquals("2,519.3", MoneyFormatter.formatPrice(BigDecimal("2519.25"), DigitStyle.LATIN, minFraction = 0, maxFraction = 1))
        assertEquals("2,519.250", MoneyFormatter.formatPrice(BigDecimal("2519.25"), DigitStyle.LATIN, minFraction = 3, maxFraction = 3))
    }

    @Test
    fun `signed change formatting`() {
        assertEquals("+12,345", MoneyFormatter.formatChangeAbs(BigDecimal.valueOf(12345), DigitStyle.LATIN))
        assertEquals("−۱۲٬۳۴۵", MoneyFormatter.formatChangeAbs(BigDecimal.valueOf(-12345), DigitStyle.PERSIAN))
        assertEquals("+0.81%", MoneyFormatter.formatPercent(BigDecimal("0.81"), DigitStyle.LATIN))
        assertEquals("−۱٫۲٪", MoneyFormatter.formatPercent(BigDecimal.valueOf(-1.2), DigitStyle.PERSIAN))
        assertEquals("+۱٪", MoneyFormatter.formatPercent(BigDecimal.valueOf(1.0), DigitStyle.PERSIAN))
    }

    @Test
    fun `rial toman conversion`() {
        assertEquals(
            BigDecimal("42000"),
            MoneyFormatter.rialToToman(BigDecimal.valueOf(420000)),
        )
        assertEquals(
            BigDecimal.valueOf(420000),
            MoneyFormatter.tomanToRial(BigDecimal.valueOf(42000)),
        )
    }

    @Test
    fun `grouping follows three digit chunks`() {
        assertEquals("1,234,567,890", MoneyFormatter.groupDigitsInternal(BigDecimal.valueOf(1234567890)))
        assertEquals("0.5", MoneyFormatter.groupDigitsInternal(BigDecimal("0.5"), 1, 2))
    }

    @Test
    fun `time formatting`() {
        assertEquals("09:05", MoneyFormatter.formatTime(9, 5, DigitStyle.LATIN))
        assertEquals("۰۹:۰۵", MoneyFormatter.formatTime(9, 5, DigitStyle.PERSIAN))
    }

    private fun MoneyFormatter.groupDigitsInternal(
        value: BigDecimal,
        minFraction: Int = 0,
        maxFraction: Int = 2,
    ) = groupDigits(value, minFraction, maxFraction)
}
