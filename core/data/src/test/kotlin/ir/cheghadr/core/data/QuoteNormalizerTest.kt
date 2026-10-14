package ir.cheghadr.core.data

import ir.cheghadr.core.data.catalog.AssetCatalog
import ir.cheghadr.core.data.provider.QuoteNormalizer
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.PriceUnit
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.RawQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class QuoteNormalizerTest {

    private val asset = AssetCatalog.byId["crypto:BTC"]!!

    @Test
    fun `valid quote passes through`() {
        val raw = RawQuote(
            price = BigDecimal("65000.25"),
            changePercent = BigDecimal("1.2"),
            changeAbs = BigDecimal("780"),
            volume24h = BigDecimal("1000000"),
            priceTime = 1_700_000_000_000L,
        )
        val now = 1_700_000_060_000L
        val q = QuoteNormalizer.normalize(asset, raw, now)!!
        assertEquals("crypto:BTC", q.assetId)
        assertEquals(0, BigDecimal("65000.25").compareTo(q.price))
        assertNotNull(q.changePercent)
        assertEquals(1_700_000_000_000L, q.priceTime)
        assertEquals(now, q.fetchTime)
        assertEquals(ProviderId.COIN_PAPRIKA, q.providerId)
    }

    @Test
    fun `negative and zero prices rejected`() {
        assertNull(QuoteNormalizer.normalize(asset, RawQuote(price = BigDecimal.ZERO), 1L))
        assertNull(QuoteNormalizer.normalize(asset, RawQuote(price = BigDecimal("-5")), 1L))
        assertNull(QuoteNormalizer.normalize(asset, RawQuote(price = null), 1L))
    }

    @Test
    fun `absurd magnitude rejected`() {
        assertNull(QuoteNormalizer.normalize(asset, RawQuote(price = BigDecimal("1E16")), 1L))
    }

    @Test
    fun `implausible change values dropped but quote kept`() {
        val raw = RawQuote(
            price = BigDecimal("100"),
            changePercent = BigDecimal("150"), // beyond ±99%
            changeAbs = BigDecimal("5000"), // > 10× price
        )
        val q = QuoteNormalizer.normalize(asset, raw, 1L)!!
        assertNull(q.changePercent)
        assertNull(q.changeAbs)
        assertEquals(0, BigDecimal("100").compareTo(q.price))
    }

    @Test
    fun `future timestamps rejected`() {
        val raw = RawQuote(price = BigDecimal.ONE, priceTime = 2_000_000_000_000L)
        val q = QuoteNormalizer.normalize(asset, raw, 1_000_000_000_000L)!!
        assertNull(q.priceTime)
    }

    @Test
    fun `history normalization dedupes and sorts`() {
        val points = listOf(
            HistoryPoint(3L, BigDecimal("3")),
            HistoryPoint(1L, BigDecimal("1")),
            HistoryPoint(2L, BigDecimal("2")),
            HistoryPoint(2L, BigDecimal("2.5")), // duplicate timestamp, last wins
            HistoryPoint(0L, BigDecimal("9")), // invalid timestamp
            HistoryPoint(4L, BigDecimal("-1")), // invalid price
        )
        val cleaned = QuoteNormalizer.normalizeHistory(points)
        assertEquals(listOf(1L, 2L, 3L), cleaned.map { it.timestamp })
        assertEquals(0, BigDecimal("2.5").compareTo(cleaned[1].price))
    }

    @Test
    fun `change percent computation`() {
        val pct = QuoteNormalizer.computeChangePercent(BigDecimal("110"), BigDecimal("100"))!!
        assertEquals(0, BigDecimal("10").compareTo(pct))
        assertNull(QuoteNormalizer.computeChangePercent(BigDecimal.ONE, BigDecimal.ZERO))
    }
}

class AssetCatalogTest {

    @Test
    fun `catalog ids are unique`() {
        assertEquals(AssetCatalog.all.size, AssetCatalog.byId.size)
    }

    @Test
    fun `available assets exist for every active provider`() {
        assertTrue(AssetCatalog.forProvider(ProviderId.COIN_PAPRIKA).isNotEmpty())
        assertTrue(AssetCatalog.forProvider(ProviderId.OPEN_ER_API).isNotEmpty())
        assertTrue(AssetCatalog.forProvider(ProviderId.FRANKFURTER).isNotEmpty())
        assertTrue(AssetCatalog.forProvider(ProviderId.GOLD_API).isNotEmpty())
        // CoinGecko stays registered for provider isolation but currently
        // serves no catalog assets (crypto defaults to CoinPaprika).
        assertTrue(AssetCatalog.forProvider(ProviderId.COINGECKO).isEmpty())
    }

    @Test
    fun `tgju assets ship unavailable`() {
        val tgjuAssets = AssetCatalog.all.filter { it.providerId == ProviderId.TGJU }
        assertTrue(tgjuAssets.isNotEmpty())
        tgjuAssets.forEach {
            assertFalse("asset ${it.id} must ship unavailable", it.available)
            assertNotNull(it.unavailableReasonFa)
            assertNotNull(it.unavailableReasonEn)
        }
    }

    @Test
    fun `tgju symbol map matches catalog`() {
        val mapped = AssetCatalog.all.filter { it.providerId == ProviderId.TGJU }
        assertEquals(mapped.size, AssetCatalog.byTgjuSymbol.size)
    }

    @Test
    fun `categories covered`() {
        val categories = AssetCatalog.all.map { it.category }.toSet()
        assertTrue(AssetCategory.CRYPTO in categories)
        assertTrue(AssetCategory.CURRENCY in categories)
        assertTrue(AssetCategory.GOLD in categories)
        assertTrue(AssetCategory.COIN in categories)
    }

    @Test
    fun `unit sanity`() {
        assertEquals(PriceUnit.GRAM, AssetCatalog.byId["gold:GRAM18"]!!.unit)
        assertEquals(PriceUnit.RIAL, AssetCatalog.byId["fx:USD"]!!.unit)
        assertEquals(PriceUnit.UNIT, AssetCatalog.byId["crypto:BTC"]!!.unit)
    }
}
