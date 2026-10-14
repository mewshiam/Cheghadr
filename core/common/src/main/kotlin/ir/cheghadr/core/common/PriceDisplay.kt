package ir.cheghadr.core.common

import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.PriceUnit
import ir.cheghadr.core.model.Quote
import java.math.BigDecimal

/** How prices are rendered across the app. */
data class DisplaySettings(
    val language: AppLanguage = AppLanguage.PERSIAN,
    val digits: DigitStyle = DigitStyle.PERSIAN,
    val displayToman: Boolean = true,
)

/**
 * Presentation-layer price formatting shared by all screens and widgets.
 * Pure and unit tested.
 */
object PriceDisplay {

    /** Number + currency symbol, without the unit word (e.g. «۴۲٬۰۰۰», «$83,863»). */
    fun priceNumber(asset: Asset, quote: Quote?, settings: DisplaySettings): String? {
        if (quote == null) return null
        val value = displayedPrice(asset, quote, settings) ?: return null
        val formatted = MoneyFormatter.formatPrice(value, settings.digits)
        return when (asset.unit) {
            PriceUnit.USD -> "$$formatted"
            PriceUnit.EUR -> "€$formatted"
            else -> formatted
        }
    }

    /** The numeric value actually displayed, applying the toman conversion
     *  to every rial-quoted asset (currencies, gold, coins). */
    fun displayedPrice(asset: Asset, quote: Quote, settings: DisplaySettings): BigDecimal? {
        return if (asset.quoteCurrency == "IRR" && settings.displayToman) {
            MoneyFormatter.rialToToman(quote.price)
        } else {
            quote.price
        }
    }

    /** Localized unit word for the quote (تومان / ریال / دلار / …). Money
     *  units take precedence for rial- and dollar-quoted assets; weight words
     *  describe per-unit pricing for weight-quoted assets. */
    fun unitWord(asset: Asset, settings: DisplaySettings): String {
        val persian = settings.language == AppLanguage.PERSIAN
        return when {
            asset.quoteCurrency == "IRR" -> if (settings.displayToman) {
                if (persian) "تومان" else "toman"
            } else {
                if (persian) "ریال" else "rial"
            }
            asset.quoteCurrency == "PTS" -> ""
            asset.unit == PriceUnit.USD -> if (persian) "دلار" else "USD"
            asset.unit == PriceUnit.EUR -> if (persian) "یورو" else "EUR"
            else -> when (asset.unit) {
                PriceUnit.GRAM -> if (persian) "گرم" else "gram"
                PriceUnit.MITHQAL -> if (persian) "مثقال" else "mithqal"
                PriceUnit.OUNCE -> if (persian) "انس" else "oz"
                PriceUnit.BARREL -> if (persian) "بشکه" else "barrel"
                PriceUnit.RIAL, PriceUnit.TOMAN -> if (persian) "تومان" else "toman"
                PriceUnit.USD -> if (persian) "دلار" else "USD"
                PriceUnit.EUR -> if (persian) "یورو" else "EUR"
                PriceUnit.UNIT -> ""
            }
        }
    }

    /** Signed change label for badges. */
    fun changeLabel(quote: Quote?, settings: DisplaySettings): String? =
        quote?.changePercent?.let { MoneyFormatter.formatPercent(it, settings.digits) }

    fun changeIsUp(quote: Quote?): Boolean? =
        quote?.changePercent?.let { it.signum() > 0 }

    /** Display name for the current language. */
    fun assetName(asset: Asset, settings: DisplaySettings): String =
        if (settings.language == AppLanguage.PERSIAN) asset.nameFa else asset.nameEn
}
