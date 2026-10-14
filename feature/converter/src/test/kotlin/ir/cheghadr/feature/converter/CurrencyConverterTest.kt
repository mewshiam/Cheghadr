package ir.cheghadr.feature.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CurrencyConverterTest {

    private fun crypto(symbol: String) = ir.cheghadr.core.model.Asset(
        id = "crypto:$symbol",
        category = ir.cheghadr.core.model.AssetCategory.CRYPTO,
        symbol = symbol,
        nameFa = symbol,
        nameEn = symbol,
        providerId = ir.cheghadr.core.model.ProviderId.COINGECKO,
        providerSymbol = symbol,
        quoteCurrency = "USD",
        unit = ir.cheghadr.core.model.PriceUnit.UNIT,
    )

    private fun fx(symbol: String) = ir.cheghadr.core.model.Asset(
        id = "fx:$symbol",
        category = ir.cheghadr.core.model.AssetCategory.CURRENCY,
        symbol = symbol,
        nameFa = symbol,
        nameEn = symbol,
        providerId = ir.cheghadr.core.model.ProviderId.OPEN_ER_API,
        providerSymbol = symbol,
        quoteCurrency = "IRR",
        unit = ir.cheghadr.core.model.PriceUnit.RIAL,
    )

    private fun fxint(symbol: String) = ir.cheghadr.core.model.Asset(
        id = "fxint:$symbol-USD",
        category = ir.cheghadr.core.model.AssetCategory.CURRENCY,
        symbol = "$symbol/USD",
        nameFa = symbol,
        nameEn = symbol,
        providerId = ir.cheghadr.core.model.ProviderId.FRANKFURTER,
        providerSymbol = symbol,
        quoteCurrency = symbol,
        unit = ir.cheghadr.core.model.PriceUnit.UNIT,
    )

    private val usd = fx("USD")
    private val eur = fx("EUR")
    private val btc = crypto("BTC")

    private val now = 1_700_000_000_000L
    private fun quote(price: String, assetId: String = "") = ir.cheghadr.core.model.Quote(
        assetId = assetId,
        price = BigDecimal(price),
        fetchTime = now,
        providerId = ir.cheghadr.core.model.ProviderId.OPEN_ER_API,
    )

    private val usdIrrQuote = quote("420000", "fx:USD")
    private val eurIrrQuote = quote("456521.7391304348") // 420000 / 0.92, scale-10 exact fixture

    @Test
    fun `usd value of usd is one`() {
        assertEquals(0, BigDecimal.ONE.compareTo(CurrencyConverter.usdValueOf(usd, usdIrrQuote, usdIrrQuote)))
    }

    @Test
    fun `usd value of euro via cross rate`() {
        // 1 EUR = 456521.739… IRR; USD = 420000 IRR → 1 EUR = 1.086956521739 USD
        val v = CurrencyConverter.usdValueOf(eur, eurIrrQuote, usdIrrQuote)!!
        assertEquals(0, BigDecimal("1.086956521739").compareTo(v))
    }

    @Test
    fun `usd value of crypto is price`() {
        val q = quote("65000", "crypto:BTC").copy(providerId = ir.cheghadr.core.model.ProviderId.COINGECKO)
        assertEquals(0, BigDecimal("65000").compareTo(CurrencyConverter.usdValueOf(btc, q, null)))
    }

    @Test
    fun `usd value of international pair is inverse`() {
        // EUR per USD = 0.92 → 1 EUR = 1.087 USD
        val e = fxint("EUR")
        val q = quote("0.92", "fxint:EUR-USD")
        val v = CurrencyConverter.usdValueOf(e, q, null)!!
        assertEquals(0, BigDecimal.ONE.divide(BigDecimal("0.92"), 12, java.math.RoundingMode.HALF_UP).compareTo(v))
    }

    @Test
    fun `convert dollar to euro through official rates`() {
        // 100 USD → EUR: 100 × 1 / 1.086956… = 92.0
        val result = CurrencyConverter.convert(
            BigDecimal.valueOf(100),
            usd to usdIrrQuote,
            eur to eurIrrQuote,
            usdIrrQuote,
        )!!
        assertEquals(0, BigDecimal("92").compareTo(result.setScale(0, java.math.RoundingMode.HALF_UP)))
    }

    @Test
    fun `convert euro to toman-quoted dollar`() {
        // 200 EUR → USD: 200 × 1.086956521739 = 217.3913043478 → scale 8
        val result = CurrencyConverter.convert(
            BigDecimal.valueOf(200),
            eur to eurIrrQuote,
            usd to usdIrrQuote,
            usdIrrQuote,
        )!!
        assertEquals(0, BigDecimal("217.39130435").compareTo(result))
    }

    @Test
    fun `convert btc to dollar`() {
        val btcQuote = quote("65000", "crypto:BTC")
        val result = CurrencyConverter.convert(
            BigDecimal("0.5"),
            btc to btcQuote,
            usd to usdIrrQuote,
            usdIrrQuote,
        )!!
        assertEquals(0, BigDecimal("32500").compareTo(result.setScale(0, java.math.RoundingMode.HALF_UP)))
    }

    @Test
    fun `missing quotes yield null`() {
        assertNull(CurrencyConverter.convert(BigDecimal.ONE, usd to usdIrrQuote, eur to null, usdIrrQuote))
        assertNull(CurrencyConverter.usdValueOf(eur, eurIrrQuote, null))
        assertNull(CurrencyConverter.usdValueOf(eur, null, usdIrrQuote))
    }
}
