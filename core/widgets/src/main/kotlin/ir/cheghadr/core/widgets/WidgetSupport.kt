package ir.cheghadr.core.widgets

import android.content.Context
import ir.cheghadr.core.common.DigitStyle
import ir.cheghadr.core.common.MoneyFormatter
import ir.cheghadr.core.data.preferences.UserSettings
import ir.cheghadr.core.model.PriceUnit
import ir.cheghadr.core.model.Quote

/**
 * Shared helpers for rendering widget content: formatting and staleness.
 * Widgets always read from the Room cache; they never perform network calls
 * except through the explicit refresh action.
 */
object WidgetSupport {

    fun digitStyle(settings: UserSettings): DigitStyle = settings.digitStyle

    /** Formats a quote's price for widget display, honoring the toman setting. */
    fun priceWithUnit(
        context: Context,
        quote: Quote,
        unit: PriceUnit,
        quoteCurrency: String,
        settings: UserSettings,
    ): String = when {
        quoteCurrency == "IRR" -> rialPriceLabel(quote, settings) + " " + rialUnitWord(context, settings)
        quoteCurrency == "PTS" -> MoneyFormatter.formatPrice(quote.price, settings.digitStyle)
        unit == PriceUnit.USD -> "$" + MoneyFormatter.formatPrice(quote.price, settings.digitStyle)
        unit == PriceUnit.EUR -> "€" + MoneyFormatter.formatPrice(quote.price, settings.digitStyle)
        else -> MoneyFormatter.formatPrice(quote.price, settings.digitStyle)
    }

    /** Formats a rial-quoted price, converting to toman when enabled. */
    fun rialPriceLabel(quote: Quote, settings: UserSettings): String {
        val value = if (settings.displayToman) {
            MoneyFormatter.rialToToman(quote.price)
        } else {
            quote.price
        }
        return MoneyFormatter.formatPrice(value, settings.digitStyle)
    }

    fun rialUnitWord(context: Context, settings: UserSettings): String =
        if (settings.displayToman) {
            context.getString(R.string.widget_unit_toman)
        } else {
            context.getString(R.string.widget_unit_rial)
        }

    fun changeLabel(quote: Quote, settings: UserSettings): String? =
        quote.changePercent?.let { MoneyFormatter.formatPercent(it, settings.digitStyle) }

    fun isStale(quote: Quote?, now: Long = System.currentTimeMillis()): Boolean {
        if (quote == null) return true
        val effective = quote.priceTime ?: quote.fetchTime
        return now - effective > STALE_AFTER_MILLIS
    }

    private const val STALE_AFTER_MILLIS = 6 * 60 * 60_000L
}
