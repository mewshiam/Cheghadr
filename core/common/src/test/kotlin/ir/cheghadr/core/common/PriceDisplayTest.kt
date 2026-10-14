package ir.cheghadr.core.common

import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.PriceUnit
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PriceDisplayTest {

    private val usdIrr = Asset(
        id = "fx:USD",
        category = AssetCategory.CURRENCY,
        symbol = "USD",
        nameFa = "دلار",
        nameEn = "US Dollar",
        providerId = ProviderId.OPEN_ER_API,
        providerSymbol = "USD",
        quoteCurrency = "IRR",
        unit = PriceUnit.RIAL,
    )

    private val btc = usdIrr.copy(
        id = "crypto:BTC",
        category = AssetCategory.CRYPTO,
        symbol = "BTC",
        quoteCurrency = "USD",
        unit = PriceUnit.USD,
    )

    private val gram18 = usdIrr.copy(
        id = "gold:GRAM18",
        category = AssetCategory.GOLD,
        unit = PriceUnit.GRAM,
    )

    @Test
    fun `rial rendered as toman by default`() {
        val quote = Quote("fx:USD", BigDecimal.valueOf(420000), fetchTime = 0, providerId = ProviderId.OPEN_ER_API)
        val settings = DisplaySettings()
        assertEquals("۴۲٬۰۰۰", PriceDisplay.priceNumber(usdIrr, quote, settings))
        assertEquals("تومان", PriceDisplay.unitWord(usdIrr, settings))
    }

    @Test
    fun `rial rendered as rial when disabled`() {
        val quote = Quote("fx:USD", BigDecimal.valueOf(420000), fetchTime = 0, providerId = ProviderId.OPEN_ER_API)
        val settings = DisplaySettings(displayToman = false)
        assertEquals("۴۲۰٬۰۰۰", PriceDisplay.priceNumber(usdIrr, quote, settings))
        assertEquals("ریال", PriceDisplay.unitWord(usdIrr, settings))
    }

    @Test
    fun `usd prices get symbol prefix`() {
        val quote = Quote("crypto:BTC", BigDecimal.valueOf(83863), fetchTime = 0, providerId = ProviderId.COINGECKO)
        assertEquals("$۸۳٬۸۶۳", PriceDisplay.priceNumber(btc, quote, DisplaySettings()))
        assertEquals("$83,863", PriceDisplay.priceNumber(btc, quote, DisplaySettings(digits = DigitStyle.LATIN)))
    }

    @Test
    fun `gold unit word and no currency symbol`() {
        val quote = Quote("gold:GRAM18", BigDecimal.valueOf(3500000), fetchTime = 0, providerId = ProviderId.TGJU)
        val settings = DisplaySettings()
        // Rial-quoted gold converts to toman; the "per gram" context lives in the asset name.
        assertEquals("۳۵۰٬۰۰۰", PriceDisplay.priceNumber(gram18, quote, settings))
        assertEquals("تومان", PriceDisplay.unitWord(gram18, settings))
    }

    @Test
    fun `null quote yields null`() {
        assertNull(PriceDisplay.priceNumber(usdIrr, null, DisplaySettings()))
        assertNull(PriceDisplay.changeLabel(null, DisplaySettings()))
    }

    @Test
    fun `displayed price converts rial to toman`() {
        val quote = Quote("fx:USD", BigDecimal.valueOf(420000), fetchTime = 0, providerId = ProviderId.OPEN_ER_API)
        assertEquals(
            0,
            BigDecimal.valueOf(42000).compareTo(
                PriceDisplay.displayedPrice(usdIrr, quote, DisplaySettings(displayToman = true)),
            ),
        )
        assertEquals(
            0,
            BigDecimal.valueOf(420000).compareTo(
                PriceDisplay.displayedPrice(usdIrr, quote, DisplaySettings(displayToman = false)),
            ),
        )
    }

    @Test
    fun `names per language`() {
        assertEquals("دلار", PriceDisplay.assetName(usdIrr, DisplaySettings()))
        assertEquals("US Dollar", PriceDisplay.assetName(usdIrr, DisplaySettings(language = AppLanguage.ENGLISH)))
    }
}
