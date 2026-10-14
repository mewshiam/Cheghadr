package ir.cheghadr.core.data

import ir.cheghadr.core.data.network.OpenErApiLatest
import ir.cheghadr.core.data.network.parseCoinGeckoMarketChart
import ir.cheghadr.core.data.network.parseCoinGeckoSimplePrice
import ir.cheghadr.core.data.network.parseCoinPaprikaHistorical
import ir.cheghadr.core.data.network.parseCoinPaprikaTickers
import ir.cheghadr.core.data.network.parseGoldApiSpot
import ir.cheghadr.core.data.network.toOfficialRates
import ir.cheghadr.core.data.network.toQuotes
import ir.cheghadr.core.data.network.FrankfurterLatest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests using fixtures observed from the live endpoints on 2026-10-14
 * (docs/DATA_SOURCES.md).
 */
class ProviderParsingTest {

    // ------------------------------------------------------------- CoinGecko

    @Test
    fun `coingecko simple price fixture`() {
        val body = """
            {"bitcoin":{"usd":83863,"usd_market_cap":1685455271599.4314,
            "usd_24h_vol":1041407319399.5814,"usd_24h_change":0.811541073901561},
            "ethereum":{"usd":2519.29,"usd_market_cap":307578159943.8123,
            "usd_24h_vol":1023287841591.6744,"usd_24h_change":0.7410944437541346}}
        """.trimIndent()
        val parsed = parseCoinGeckoSimplePrice(body)
        assertEquals(2, parsed.size)
        val btc = parsed["bitcoin"]!!
        assertEquals(83863.0, btc.price!!.toDouble(), 1e-9)
        assertEquals(0.811541, btc.changePercent!!.toDouble(), 1e-5)
        assertNotNull(btc.volume24h)
        assertNotNull(btc.marketCap)
        assertNull(btc.priceTime)
    }

    @Test
    fun `coingecko market chart fixture`() {
        val body = """
            {"prices":[[1728864000000,65000.5],[1728950400000,67200.1],[1729036800000,66150.0]]}
        """.trimIndent()
        val points = parseCoinGeckoMarketChart(body)
        assertEquals(3, points.size)
        assertTrue(points[0].first < points[1].first)
        assertEquals(65000.5, points[0].second.toDouble(), 1e-9)
    }

    @Test
    fun `coingecko malformed body yields empty`() {
        assertTrue(parseCoinGeckoSimplePrice("not json").isEmpty())
        assertTrue(parseCoinGeckoSimplePrice("{\"prices\":[]}").isEmpty())
        // Zero/negative prices are dropped.
        assertTrue(parseCoinGeckoMarketChart("{\"prices\":[[1,0],[2,-5]]}").isEmpty())
    }

    // ------------------------------------------------------------- open.er-api

    @Test
    fun `open er api official rates fixture`() {
        val dto = OpenErApiLatest(
            result = "success",
            baseCode = "USD",
            timeLastUpdateUnix = 1791936152L,
            rates = mapOf(
                "USD" to 1.0,
                "EUR" to 0.92,
                "IRR" to 42000.0,
            ),
        )
        val rates = dto.toOfficialRates(listOf("USD", "EUR", "GBP"))
        // USD direct rate.
        assertEquals(42000.0, rates["fx:USD"]!!.price!!.toDouble(), 1e-6)
        // EUR cross rate: 42000 / 0.92 = 45652.17...
        assertEquals(42000.0 / 0.92, rates["fx:EUR"]!!.price!!.toDouble(), 1e-6)
        // GBP has no rate → absent.
        assertNull(rates["fx:GBP"])
        assertEquals(1791936152000L, rates["fx:USD"]!!.priceTime)
    }

    @Test
    fun `open er api failure result yields empty`() {
        val dto = OpenErApiLatest(result = "error", rates = mapOf("IRR" to 1.0))
        assertTrue(dto.toOfficialRates(listOf("USD")).isEmpty())
    }

    // ------------------------------------------------------------- Frankfurter

    @Test
    fun `frankfurter latest fixture`() {
        val dto = FrankfurterLatest(
            amount = 1.0,
            base = "USD",
            date = "2026-10-13",
            rates = mapOf("EUR" to 0.89381),
        )
        val quotes = dto.toQuotes(listOf("EUR", "CHF"))
        assertEquals(1, quotes.size)
        assertEquals(0.89381, quotes["fxint:EUR-USD"]!!.price!!.toDouble(), 1e-9)
    }

    // --- tgju.org HTML scraping is covered by TgjuScraperTest (live fixtures) ---
}

// ------------------------------------------------------------- CoinPaprika

class CoinPaprikaParsingTest {

    @Test
    fun `paprika tickers fixture`() {
        val body = """
            [{"id":"btc-bitcoin","name":"Bitcoin","symbol":"BTC","rank":1,
              "circulating_supply":19801681,"last_updated":"2026-10-14T06:35:38Z",
              "quotes":{"USD":{"price":83863.0,"volume_24h":1041407319399.58,
              "market_cap":1685455271599.43,"percent_change_1h":0.09,
              "percent_change_24h":0.81,"percent_change_7d":-1.2,
              "percent_change_30d":5.4,"last_updated":"2026-10-14T06:35:38Z"}}},
             {"id":"eth-ethereum","name":"Ethereum","symbol":"ETH","rank":2,
              "last_updated":"2026-10-14T06:35:38Z",
              "quotes":{"USD":{"price":2519.29,"volume_24h":1023287841591.67,
              "percent_change_24h":0.74}}},
             {"id":"xrp-xrp","name":"XRP","symbol":"XRP","rank":7,
              "last_updated":"2026-10-14T06:35:38Z",
              "quotes":{"USD":{"price":0.52,"percent_change_24h":-0.3}}}]
        """.trimIndent()
        val parsed = parseCoinPaprikaTickers(
            body,
            setOf("btc-bitcoin", "eth-ethereum", "unknown-coin"),
        )
        // Unmapped coins are filtered out; mapped ones survive.
        assertEquals(2, parsed.size)
        val btc = parsed["btc-bitcoin"]!!
        assertEquals(83863.0, btc.price!!.toDouble(), 1e-9)
        assertEquals(0.81, btc.changePercent!!.toDouble(), 1e-9)
        assertNotNull(btc.volume24h)
        assertNotNull(btc.marketCap)
        assertNotNull(btc.priceTime) // parsed from last_updated
    }

    @Test
    fun `paprika tickers rejects non-positive prices`() {
        val body = """
            [{"id":"bad-coin","quotes":{"USD":{"price":0}}},
             {"id":"neg-coin","quotes":{"USD":{"price":-1}}},
             {"id":"ok-coin","quotes":{"USD":{"price":2}}}]
        """.trimIndent()
        val parsed = parseCoinPaprikaTickers(body, setOf("bad-coin", "neg-coin", "ok-coin"))
        assertEquals(1, parsed.size)
        assertEquals(2.0, parsed["ok-coin"]!!.price!!.toDouble(), 1e-9)
    }

    @Test
    fun `paprika historical fixture`() {
        val body = """
            [{"timestamp":"2026-10-12T00:00:00Z","price":61234.5,"volume_24h":100.0},
             {"timestamp":"2026-10-13T00:00:00Z","price":62500.0},
             {"timestamp":"2026-10-14T00:00:00Z","price":83863.0}]
        """.trimIndent()
        val points = parseCoinPaprikaHistorical(body)
        assertEquals(3, points.size)
        assertEquals(0, java.math.BigDecimal("61234.5").compareTo(points[0].price))
        // Sorted ascending by timestamp.
        assertTrue(points[0].timestamp < points[2].timestamp)
    }

    @Test
    fun `paprika malformed body yields empty`() {
        assertTrue(parseCoinPaprikaTickers("not json", setOf("btc-bitcoin")).isEmpty())
        assertTrue(parseCoinPaprikaHistorical("{}").isEmpty())
    }
}

// ------------------------------------------------------------- gold-api.com

class GoldApiParsingTest {

    @Test
    fun `gold-api spot fixture`() {
        // Observed 2026-10-14: {"name":"Gold","price":2662.023,...}
        val body = """
            {"name":"Gold","price":2662.023,"symbol":"XAU",
             "updatedAt":"2026-10-14T06:00:00Z","updatedAtReadable":"October 2026"}
        """.trimIndent()
        val raw = parseGoldApiSpot(body)!!
        assertEquals(2662.023, raw.price!!.toDouble(), 1e-9)
        assertNotNull(raw.priceTime)
    }

    @Test
    fun `gold-api rejects non-positive and malformed`() {
        assertNull(parseGoldApiSpot("""{"price":0}"""))
        assertNull(parseGoldApiSpot("""{"price":-1}"""))
        assertNull(parseGoldApiSpot("""{"symbol":"XAU"}"""))
        assertNull(parseGoldApiSpot("not json"))
    }
}
