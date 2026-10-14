package ir.cheghadr.feature.converter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class GoldCalculatorTest {

    // ------------------------------------------------------------ weight

    @Test
    fun `mithqal gram conversion`() {
        val grams = GoldCalculator.mithqalToGrams(BigDecimal.ONE)
        assertEquals(0, BigDecimal("4.6083").compareTo(grams))
        // One gram expressed in mithqals: 1 / 4.6083 = 0.216999761… → 0.217000
        val mithqal = GoldCalculator.gramsToMithqal(BigDecimal.ONE)
        assertEquals(0, BigDecimal("0.217000").compareTo(mithqal))
    }

    @Test
    fun `ounce gram conversion`() {
        assertEquals(0, BigDecimal("31.1034768").compareTo(GoldCalculator.ounceToGrams(BigDecimal.ONE)))
        assertEquals(0, BigDecimal("1").compareTo(GoldCalculator.gramsToOunce(GoldCalculator.OUNCE_GRAMS)))
    }

    // ------------------------------------------------------------ purity

    @Test
    fun `purity conversion 18k to 24k`() {
        // 18k gram price 3,500,000 → 24k = × 24/18 = 4,666,666.666…
        val g24 = GoldCalculator.convertPurity(BigDecimal("3500000"), 18, 24)
        assertEquals(0, BigDecimal("4666666.666667").compareTo(g24))
        // Round trip back to 18k.
        val back = GoldCalculator.convertPurity(g24, 24, 18)
        assertEquals(0, BigDecimal("3500000").compareTo(back.setScale(0, RoundingMode.HALF_UP)))
    }

    @Test
    fun `pure grams for karat`() {
        // 8.133 g of 21k contains 8.133 × 21/24 = 7.1163… g pure gold.
        val pure = GoldCalculator.pureGrams(BigDecimal("8.133"), 21)
        assertEquals(0, BigDecimal("7.116375").compareTo(pure))
    }

    @Test
    fun `coin intrinsic value from 18k gram price`() {
        val gram18 = BigDecimal("3500000")
        // 24k gram = 4,666,666.666667; pure grams 7.116375 → ≈ 33,209,750
        val value = GoldCalculator.coinIntrinsicValue(GoldCalculator.COINS.first { it.assetId == "coin:EMAMI" }, gram18)
        val expected = BigDecimal("7.116375").multiply(BigDecimal("4666666.666667")).setScale(0, RoundingMode.HALF_UP)
        assertEquals(0, expected.compareTo(value))
        // Gerami coin is far lighter than a full coin.
        val gerami = GoldCalculator.coinIntrinsicValue(GoldCalculator.COINS.first { it.assetId == "coin:BAHAR_GERAMI" }, gram18)
        assertEquals(true, value > gerami.multiply(BigDecimal.valueOf(7)))
    }

    // ------------------------------------------------------------ retail

    @Test
    fun `retail breakdown applies each charge exactly once`() {
        val b = GoldCalculator.retailBreakdown(
            base = BigDecimal("10000000"),
            makingChargePercent = BigDecimal("7"),
            taxPercent = BigDecimal("9"),
            discountPercent = BigDecimal("2"),
        )
        assertEquals(0, BigDecimal("700000").compareTo(b.makingCharge))
        // Tax applies to base + making = 10,700,000 → 963,000.
        assertEquals(0, BigDecimal("963000").compareTo(b.tax))
        assertEquals(0, BigDecimal("200000").compareTo(b.discount))
        // Total = 10,000,000 + 700,000 + 963,000 − 200,000 = 11,463,000.
        assertEquals(0, BigDecimal("11463000").compareTo(b.total))
    }

    @Test
    fun `retail breakdown with no charges is base`() {
        val b = GoldCalculator.retailBreakdown(BigDecimal("500000"))
        assertEquals(0, BigDecimal("500000").compareTo(b.total))
        assertEquals(0, BigDecimal.ZERO.compareTo(b.makingCharge))
        assertEquals(0, BigDecimal.ZERO.compareTo(b.tax))
        assertEquals(0, BigDecimal.ZERO.compareTo(b.discount))
    }

    @Test
    fun `coin specs cover canonical set`() {
        assertEquals(7, GoldCalculator.COINS.size)
        assertEquals(0, BigDecimal("8.133").compareTo(GoldCalculator.COINS.first { it.assetId == "coin:BAHAR" }.grams))
        assertEquals(21, GoldCalculator.COINS.first { it.assetId == "coin:EMAMI" }.karat)
    }
}
