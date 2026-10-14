package ir.cheghadr.core.data.network

import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.RawQuote
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal

/**
 * DTOs + pure parsing functions for every provider. Parsers are pure and
 * covered by unit tests with fixtures captured from the live endpoints
 * (docs/DATA_SOURCES.md, verified 2026-10-14).
 */
object ProviderJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
}

// ------------------------------------------------------------- open.er-api

/** GET https://open.er-api.com/v6/latest/USD (observed 2026-10-14). */
@Serializable
data class OpenErApiLatest(
    val result: String? = null,
    @SerialName("base_code") val baseCode: String? = null,
    @SerialName("time_last_update_unix") val timeLastUpdateUnix: Long? = null,
    val rates: Map<String, Double> = emptyMap(),
)

/**
 * Computes IRR-quoted official rates from the USD-base table.
 * Returns (canonicalAssetId → RawQuote).
 */
fun OpenErApiLatest.toOfficialRates(symbols: List<String>): Map<String, RawQuote> {
    if (result != "success") return emptyMap()
    val irrPerUsd = rates["IRR"] ?: return emptyMap()
    val priceTime = (timeLastUpdateUnix ?: 0L) * 1000L
    val out = mutableMapOf<String, RawQuote>()
    for (symbol in symbols) {
        val rate = rates[symbol] ?: continue
        if (rate <= 0.0) continue
        // For USD: 1 USD = irrPerUsd rial. For other currencies: 1 unit = IRR / (units per USD).
        val irrPerUnit = if (symbol == "USD") {
            irrPerUsd.toDouble()
        } else {
            irrPerUsd / rate
        }
        if (irrPerUnit <= 0.0 || !irrPerUnit.isFinite()) continue
        out["fx:$symbol"] = RawQuote(
            price = BigDecimal.valueOf(irrPerUnit).stripTrailingZeros(),
            priceTime = if (priceTime > 0) priceTime else null,
        )
    }
    return out
}

// ------------------------------------------------------------- Frankfurter

/** GET https://api.frankfurter.dev/v1/latest?from=USD&to=EUR,GBP (observed 2026-10-14). */
@Serializable
data class FrankfurterLatest(
    val amount: Double? = null,
    val base: String? = null,
    val date: String? = null,
    val rates: Map<String, Double> = emptyMap(),
)

/** GET https://api.frankfurter.dev/v1/{start}..{end}?from=USD&to=EUR */
@Serializable
data class FrankfurterHistory(
    val amount: Double? = null,
    val base: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val rates: Map<String, Map<String, Double>> = emptyMap(),
)

fun FrankfurterLatest.toQuotes(targetSymbols: List<String>): Map<String, RawQuote> {
    val out = mutableMapOf<String, RawQuote>()
    for (symbol in targetSymbols) {
        val rate = rates[symbol] ?: continue
        if (rate <= 0.0) continue
        out["fxint:$symbol-USD"] = RawQuote(price = BigDecimal.valueOf(rate).stripTrailingZeros())
    }
    return out
}

// ------------------------------------------------------------- CoinGecko

/**
 * GET /api/v3/simple/price?ids=...&vs_currencies=usd&include_24hr_change=true
 * Response has dynamic coin-id keys, so it is parsed as a JsonObject.
 * Observed 2026-10-14:
 * {"bitcoin":{"usd":83863,"usd_market_cap":1685455271599.43,"usd_24h_vol":1041407319399.58,"usd_24h_change":0.81}}
 */
fun parseCoinGeckoSimplePrice(body: String): Map<String, RawQuote> {
    val out = mutableMapOf<String, RawQuote>()
    val root = runCatching { ProviderJson.json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return out
    for ((coinId, payload) in root) {
        val obj = runCatching { payload.jsonObject }.getOrNull() ?: continue
        val usd = obj["usd"]?.jsonPrimitive?.doubleOrNull ?: continue
        if (usd <= 0.0) continue
        out[coinId] = RawQuote(
            price = BigDecimal.valueOf(usd).stripTrailingZeros(),
            changePercent = obj["usd_24h_change"]?.jsonPrimitive?.doubleOrNull?.let {
                BigDecimal.valueOf(it).stripTrailingZeros()
            },
            changeAbs = null,
            high24h = null,
            low24h = null,
            volume24h = obj["usd_24h_vol"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
            marketCap = obj["usd_market_cap"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
            priceTime = null,
        )
    }
    return out
}

/**
 * GET /api/v3/coins/{id}/market_chart?vs_currency=usd&days=N
 * Response: {"prices":[[ms,price],...]}
 */
fun parseCoinGeckoMarketChart(body: String): List<Pair<Long, BigDecimal>> {
    val root = runCatching { ProviderJson.json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
    val prices = runCatching { root["prices"]?.jsonArray }.getOrNull() ?: return emptyList()
    val out = mutableListOf<Pair<Long, BigDecimal>>()
    for (point in prices) {
        val arr = runCatching { point.jsonArray }.getOrNull() ?: continue
        if (arr.size < 2) continue
        val ts = arr[0].jsonPrimitive.contentOrNull?.toLongOrNull() ?: continue
        val price = arr[1].jsonPrimitive.contentOrNull?.toDoubleOrNull() ?: continue
        if (ts <= 0L || price <= 0.0) continue
        out += ts to BigDecimal.valueOf(price).stripTrailingZeros()
    }
    return out.sortedBy { it.first }
}

// ------------------------------------------------------------- CoinPaprika

/**
 * GET https://api.coinpaprika.com/v1/tickers?quotes=USD (observed 2026-10-14).
 * Huge array of all coins; only entries whose id is in [wantedIds] are kept:
 * [{"id":"btc-bitcoin","symbol":"BTC","rank":1,"last_updated":"2026-…",
 *    "quotes":{"USD":{"price":83863.0,"volume_24h":1.04E12,"market_cap":1.68E12,
 *    "percent_change_24h":0.81,…}}},…]
 */
fun parseCoinPaprikaTickers(body: String, wantedIds: Set<String>): Map<String, RawQuote> {
    val out = mutableMapOf<String, RawQuote>()
    val root = runCatching { ProviderJson.json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return out
    for (item in root) {
        val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
        val paprikaId = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
        if (paprikaId !in wantedIds) continue
        val usd = runCatching { obj["quotes"]?.jsonObject?.get("USD")?.jsonObject }.getOrNull() ?: continue
        val price = usd["price"]?.jsonPrimitive?.doubleOrNull ?: continue
        if (price <= 0.0) continue
        val priceTime = obj["last_updated"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        out[paprikaId] = RawQuote(
            price = BigDecimal.valueOf(price).stripTrailingZeros(),
            changePercent = usd["percent_change_24h"]?.jsonPrimitive?.doubleOrNull
                ?.let { BigDecimal.valueOf(it).stripTrailingZeros() },
            volume24h = usd["volume_24h"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
            marketCap = usd["market_cap"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
            priceTime = priceTime,
        )
    }
    return out
}

/**
 * GET /v1/tickers/{id}/historical?start=…&interval=1d|1h (observed 2026-10-14).
 * [{"timestamp":"2026-09-01T00:00:00Z","price":61234.5,"volume_24h":…},…]
 */
fun parseCoinPaprikaHistorical(body: String): List<HistoryPoint> {
    val root = runCatching { ProviderJson.json.parseToJsonElement(body).jsonArray }.getOrNull()
        ?: return emptyList()
    val out = mutableListOf<HistoryPoint>()
    for (item in root) {
        val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
        val ts = obj["timestamp"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: continue
        val price = obj["price"]?.jsonPrimitive?.doubleOrNull ?: continue
        if (ts <= 0L || price <= 0.0) continue
        out += HistoryPoint(ts, BigDecimal.valueOf(price).stripTrailingZeros())
    }
    return out.sortedBy { it.timestamp }
}

// ------------------------------------------------------------- gold-api.com

/**
 * GET https://api.gold-api.com/price/XAU (observed 2026-10-14):
 * {"name":"Gold","price":2662.023,"symbol":"XAU","updatedAt":"2026-10-14T…Z"}
 */
fun parseGoldApiSpot(body: String): RawQuote? {
    val obj = runCatching { ProviderJson.json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val price = obj["price"]?.jsonPrimitive?.doubleOrNull ?: return null
    if (price <= 0.0) return null
    val priceTime = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
    return RawQuote(
        price = BigDecimal.valueOf(price).stripTrailingZeros(),
        priceTime = priceTime,
    )
}

// ------------------------------------------------------------- TGJU

// The TGJU integration scrapes tgju.org HTML directly; all parsing lives in
// provider/TgjuScraper.kt (Jsoup-based, pure and unit-tested). Number/date
// helpers are also there (parseTgjuNumber, parseJalaliDate, …).

/** Standard error type for provider-level exceptions. */
class ProviderHttpException(
    val providerId: ProviderId,
    val status: Int,
    override val message: String,
) : Exception(message)
