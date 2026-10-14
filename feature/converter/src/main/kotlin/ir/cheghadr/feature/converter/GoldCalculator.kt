package ir.cheghadr.feature.converter

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure gold calculator: weight conversions (gram / mithqal / ounce), purity
 * conversions (18k / 21k / 24k) and coin intrinsic-value estimates.
 *
 * Market conventions used (published Central Bank of Iran coin specs):
 *  - 1 mithqal = 4.6083 g; 1 troy ounce = 31.1034768 g.
 *  - Full Bahar Azadi / Emami coins weigh 8.133 g at 21 karat; halves 4.066 g,
 *    quarters 2.033 g, gerami 1.016 g.
 *
 * Retail estimates are clearly labeled as estimates: they exclude the market
 * premium (bubble) and shop-level pricing.
 */
object GoldCalculator {

    val MITHQAL_GRAMS: BigDecimal = BigDecimal("4.6083")
    val OUNCE_GRAMS: BigDecimal = BigDecimal("31.1034768")
    val GRAMS_PER_TOLA: BigDecimal = BigDecimal("11.6638")

    data class CoinSpec(
        val assetId: String,
        val nameFa: String,
        val nameEn: String,
        val grams: BigDecimal,
        val karat: Int,
    )

    val COINS: List<CoinSpec> = listOf(
        CoinSpec("coin:EMAMI", "سکه امامی", "Emami coin", BigDecimal("8.133"), 21),
        CoinSpec("coin:EMAMI_HALF", "نیم سکه امامی", "Half Emami", BigDecimal("4.066"), 21),
        CoinSpec("coin:EMAMI_QUARTER", "ربع سکه امامی", "Quarter Emami", BigDecimal("2.033"), 21),
        CoinSpec("coin:BAHAR", "سکه بهار آزادی", "Bahar Azadi", BigDecimal("8.133"), 21),
        CoinSpec("coin:BAHAR_HALF", "نیم بهار آزادی", "Half Bahar Azadi", BigDecimal("4.066"), 21),
        CoinSpec("coin:BAHAR_QUARTER", "ربع بهار آزادی", "Quarter Bahar Azadi", BigDecimal("2.033"), 21),
        CoinSpec("coin:BAHAR_GERAMI", "سکه گرمی", "Gerami coin", BigDecimal("1.016"), 21),
    )

    // ------------------------------------------------------------ weight

    fun gramsToMithqal(grams: BigDecimal): BigDecimal =
        grams.divide(MITHQAL_GRAMS, 6, RoundingMode.HALF_UP)

    fun mithqalToGrams(mithqal: BigDecimal): BigDecimal =
        mithqal.multiply(MITHQAL_GRAMS)

    fun gramsToOunce(grams: BigDecimal): BigDecimal =
        grams.divide(OUNCE_GRAMS, 6, RoundingMode.HALF_UP)

    fun ounceToGrams(ounce: BigDecimal): BigDecimal =
        ounce.multiply(OUNCE_GRAMS)

    // ------------------------------------------------------------ purity

    /** Converts a per-gram price between purities (both per one gram). */
    fun convertPurity(gramPrice: BigDecimal, fromKarat: Int, toKarat: Int): BigDecimal =
        gramPrice.multiply(BigDecimal.valueOf(toKarat.toLong()))
            .divide(BigDecimal.valueOf(fromKarat.toLong()), 6, RoundingMode.HALF_UP)

    /** Pure-gold (24k) grams contained in a given weight at [karat]. */
    fun pureGrams(grams: BigDecimal, karat: Int): BigDecimal =
        grams.multiply(BigDecimal(karat)).divide(BigDecimal(24), 6, RoundingMode.HALF_UP)

    /** Intrinsic (melt) value of a coin from the 18k gram price — an estimate. */
    fun coinIntrinsicValue(coin: CoinSpec, gram18: BigDecimal): BigDecimal {
        val gram24 = convertPurity(gram18, fromKarat = 18, toKarat = 24)
        return pureGrams(coin.grams, coin.karat).multiply(gram24).setScale(0, RoundingMode.HALF_UP)
    }

    // ------------------------------------------------------------ retail estimate

    data class RetailBreakdown(
        val base: BigDecimal,
        val makingCharge: BigDecimal,
        val tax: BigDecimal,
        val discount: BigDecimal,
        val total: BigDecimal,
    )

    /**
     * Retail estimate breakdown. Charges are applied sequentially and exactly
     * once each (no double counting):
     *  making charge = base × pct;
     *  tax = (base + making) × pct;
     *  discount = base × pct (subtracted last).
     */
    fun retailBreakdown(
        base: BigDecimal,
        makingChargePercent: BigDecimal = BigDecimal.ZERO,
        taxPercent: BigDecimal = BigDecimal.ZERO,
        discountPercent: BigDecimal = BigDecimal.ZERO,
    ): RetailBreakdown {
        val making = base.multiply(makingChargePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        val tax = base.add(making).multiply(taxPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        val discount = base.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        val total = base.add(making).add(tax).subtract(discount).max(BigDecimal.ZERO)
        return RetailBreakdown(base, making, tax, discount, total)
    }
}
