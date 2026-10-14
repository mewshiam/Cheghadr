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
        assertTrue(AssetCatalog.forProvider(ProviderId.TGJU).isNotEmpty())
        // CoinGecko stays registered for provider isolation but currently
        // serves no catalog assets (crypto defaults to CoinPaprika).
        assertTrue(AssetCatalog.forProvider(ProviderId.COINGECKO).isEmpty())
    }

    @Test
    fun `tgju assets ship live from the tgju org scraper`() {
        val tgjuAssets = AssetCatalog.all.filter { it.providerId == ProviderId.TGJU }
        assertTrue(tgjuAssets.isNotEmpty())
        tgjuAssets.forEach {
            assertTrue("asset ${it.id} must ship available", it.available)
        }
    }

    @Test
    fun `tgju symbols match the live site`() {
        // Slugs observed on tgju.org section/profile pages 2026-10-14.
        val expected = mapOf(
            "fx-fm:USD" to "price_dollar_rl",
            "fx-fm:EUR" to "price_eur",
            "fx-fm:AED" to "price_aed",
            "fx-fm:TRY" to "price_try",
            "gold:GRAM18" to "geram18",
            "gold:GRAM24" to "geram24",
            "gold:MESGHAL" to "mesghal",
            "coin:EMAMI" to "sekee",
            "coin:BAHAR" to "sekeb",
            "coin:BAHAR_HALF" to "nim",
            "coin:BAHAR_QUARTER" to "rob",
            "coin:BAHAR_GERAMI" to "gerami",
            "stock:TEDPIX" to "gc30",
            "commodity:OIL_BRENT" to "oil_brent",
        )
        expected.forEach { (assetId, slug) ->
            assertEquals("$assetId must map to slug $slug", slug, AssetCatalog.byId[assetId]!!.providerSymbol)
        }
    }

    @Test
    fun `tgju history support matches page capabilities`() {
        // Profile pages serve a ~25-day OHLC table for these; oil_brent has
        // no profile page (redirects) so it stays spot-only.
        assertTrue(AssetCatalog.byId["fx-fm:USD"]!!.supportsHistory)
        assertTrue(AssetCatalog.byId["gold:GRAM18"]!!.supportsHistory)
        assertTrue(AssetCatalog.byId["stock:TEDPIX"]!!.supportsHistory)
        assertFalse(AssetCatalog.byId["commodity:OIL_BRENT"]!!.supportsHistory)
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
