package ir.cheghadr.feature.converter

import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.Quote
import ir.cheghadr.core.model.PriceUnit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure cross-rate conversion engine. Every asset is bridged through USD with
 * exact [BigDecimal] arithmetic; no binary floating point ever touches money.
 *
 * Catalog conventions:
 *  - `fx:*`   price = IRR per one unit (official rates, open.er-api).
 *  - `fxint:*` price = foreign units per one USD (ECB cross pairs).
 *  - `crypto:*` price = USD per one unit.
 */
object CurrencyConverter {

    /** USD value of a single unit of [asset]; null when quotes are missing. */
    fun usdValueOf(asset: Asset, quote: Quote?, usdIrr: Quote?): BigDecimal? {
        if (quote == null) return null
        return when {
            asset.id.startsWith("fx:USD") -> BigDecimal.ONE
            // Crypto and any USD-quoted asset are priced in USD directly.
            asset.id.startsWith("crypto:") || asset.unit == PriceUnit.USD -> quote.price
            asset.unit == PriceUnit.RIAL -> {
                // fx:X → IRR per X; USD value = price / (IRR per USD).
                val usd = usdIrr ?: return null
                quote.price.divide(usd.price, 12, RoundingMode.HALF_UP)
            }
            asset.id.startsWith("fxint:") -> {
                // price = X per USD → USD value of 1 X = 1 / price.
                if (quote.price.signum() <= 0) null
                else BigDecimal.ONE.divide(quote.price, 12, RoundingMode.HALF_UP)
            }
            else -> null
        }
    }

    /**
     * Converts [amount] of [from] into [to]. Returns null when any required
     * quote is missing.
     */
    fun convert(
        amount: BigDecimal,
        from: Pair<Asset, Quote?>,
        to: Pair<Asset, Quote?>,
        usdIrr: Quote?,
        scale: Int = 8,
    ): BigDecimal? {
        val fromUsd = usdValueOf(from.first, from.second, usdIrr) ?: return null
        val toUsd = usdValueOf(to.first, to.second, usdIrr) ?: return null
        if (toUsd.signum() <= 0) return null
        return amount.multiply(fromUsd)
            .divide(toUsd, scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
    }

    /** The quoted unit word for the result (toman/rial/usd/…). */
    fun resultUnitLabel(asset: Asset): String = asset.quoteCurrency
}
