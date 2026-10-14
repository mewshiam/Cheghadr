package ir.cheghadr.core.data.provider

import io.ktor.client.HttpClient
import ir.cheghadr.core.data.catalog.AssetCatalog
import ir.cheghadr.core.data.network.FrankfurterLatest
import ir.cheghadr.core.data.network.OpenErApiLatest
import ir.cheghadr.core.data.network.ProviderHttpException
import ir.cheghadr.core.data.network.ProviderJson
import ir.cheghadr.core.data.network.parseCoinGeckoMarketChart
import ir.cheghadr.core.data.network.parseCoinGeckoSimplePrice
import ir.cheghadr.core.data.network.toOfficialRates
import ir.cheghadr.core.data.network.toQuotes
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.ProviderErrors
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.ProviderResult
import ir.cheghadr.core.model.RawQuote
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CoinGecko public API — crypto spot quotes and market-chart history.
 * Endpoint docs: https://docs.coingecko.com/reference/introduction (public plan,
 * no key required, attribution required). Verified live 2026-10-14.
 */
@Singleton
class CoinGeckoProvider @Inject constructor(
    private val client: HttpClient,
    private val settings: SettingsRepository,
) : MarketDataProviderBase() {

    override val id = ProviderId.COINGECKO
    override val displayNameFa = "CoinGecko"
    override val displayNameEn = "CoinGecko"
    override val attributionFa = "داده‌های رمزارزها از CoinGecko"
    override val attributionEn = "Crypto data by CoinGecko"
    override val requiresConfiguration = false
    override val supportedCategories = setOf(AssetCategory.CRYPTO)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>> = runCatching {
        val cryptoAssets = AssetCatalog.forProvider(ProviderId.COINGECKO)
        if (cryptoAssets.isEmpty()) {
            // The crypto catalog currently defaults to CoinPaprika; CoinGecko
            // stays registered (provider isolation) but serves no assets.
            return@runCatching ProviderResult.Success(emptyMap<String, RawQuote>())
        }
        val ids = cryptoAssets.joinToString(",") { it.providerSymbol }
        val apiKey = settings.settings.first().coingeckoApiKey
        val body = client.getText(id, "$BASE/simple/price?ids=$ids&vs_currencies=usd" +
            "&include_24hr_change=true&include_24hr_vol=true&include_market_cap=true") {
            apiKeyHeader("x-cg-demo-api-key", apiKey)
        }
        val parsed = parseCoinGeckoSimplePrice(body)
        val byCgId = cryptoAssets.associateBy { it.providerSymbol }
        val mapped = parsed.mapNotNull { (cgId, raw) ->
            val asset = byCgId[cgId] ?: return@mapNotNull null
            asset.id to raw
        }.toMap()
        if (mapped.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(mapped)
        }
    }.toProviderResult()

    override suspend fun history(asset: Asset, days: Int): ProviderResult<List<HistoryPoint>> = runCatching {
        if (asset.providerId != id) {
            return@runCatching ProviderResult.Failure(id, ProviderErrors.NOT_SUPPORTED)
        }
        val effectiveDays = if (days <= 0) 365 else days
        if (effectiveDays > 365) {
            // The public endpoint only serves the last 365 days.
            return@runCatching ProviderResult.Failure(id, ProviderErrors.NOT_SUPPORTED)
        }
        val apiKey = settings.settings.first().coingeckoApiKey
        val body = client.getText(
            id,
            "$BASE/coins/${asset.providerSymbol}/market_chart?vs_currency=usd&days=$effectiveDays",
        ) {
            apiKeyHeader("x-cg-demo-api-key", apiKey)
        }
        val points = parseCoinGeckoMarketChart(body)
        if (points.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(points.map { HistoryPoint(it.first, it.second) })
        }
    }.toProviderResult()

    private companion object {
        const val BASE = "https://api.coingecko.com/api/v3"
    }
}

/**
 * open.er-api.com — official (bank-reference) FX rates including IRR.
 * Verified live 2026-10-14; updates roughly daily; no key required.
 * Attribution: https://www.exchangerate-api.com (free open endpoint).
 */
@Singleton
class OpenErApiProvider @Inject constructor(
    private val client: HttpClient,
) : MarketDataProviderBase() {

    override val id = ProviderId.OPEN_ER_API
    override val displayNameFa = "ExchangeRate-API"
    override val displayNameEn = "ExchangeRate-API"
    override val attributionFa = "نرخ‌های رسمی ارز از ExchangeRate-API (open.er-api.com)"
    override val attributionEn = "Official FX rates by ExchangeRate-API (open.er-api.com)"
    override val requiresConfiguration = false
    override val supportedCategories = setOf(AssetCategory.CURRENCY)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>> = runCatching {
        val body = client.getText(id, "$BASE/latest/USD")
        val dto = ProviderJson.json.decodeFromString(OpenErApiLatest.serializer(), body)
        val symbols = AssetCatalog.forProvider(ProviderId.OPEN_ER_API).map { it.providerSymbol }
        val mapped = dto.toOfficialRates(symbols)
        if (mapped.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(mapped)
        }
    }.toProviderResult()

    override suspend fun history(asset: Asset, days: Int): ProviderResult<List<HistoryPoint>> =
        ProviderResult.Failure(id, ProviderErrors.NOT_SUPPORTED)

    private companion object {
        const val BASE = "https://open.er-api.com/v6"
    }
}

/**
 * Frankfurter — ECB reference rates; used for international cross pairs
 * (EUR/GBP/CHF/JPY per USD) with real daily history.
 * Verified live 2026-10-14 (api.frankfurter.dev/v1); no key required.
 */
@Singleton
class FrankfurterProvider @Inject constructor(
    private val client: HttpClient,
) : MarketDataProviderBase() {

    override val id = ProviderId.FRANKFURTER
    override val displayNameFa = "Frankfurter (ECB)"
    override val displayNameEn = "Frankfurter (ECB)"
    override val attributionFa = "نرخ‌های ارزی اروپا از بانک مرکزی اروپا از طریق Frankfurter"
    override val attributionEn = "ECB reference rates via Frankfurter"
    override val requiresConfiguration = false
    override val supportedCategories = setOf(AssetCategory.CURRENCY)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>> = runCatching {
        val symbols = AssetCatalog.forProvider(ProviderId.FRANKFURTER)
            .joinToString(",") { it.providerSymbol }
        val body = client.getText(id, "$BASE/latest?from=USD&to=$symbols")
        val dto = ProviderJson.json.decodeFromString(FrankfurterLatest.serializer(), body)
        val mapped = dto.toQuotes(AssetCatalog.forProvider(ProviderId.FRANKFURTER).map { it.providerSymbol })
        if (mapped.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(mapped)
        }
    }.toProviderResult()

    override suspend fun history(asset: Asset, days: Int): ProviderResult<List<HistoryPoint>> = runCatching {
        if (asset.providerId != id) {
            return@runCatching ProviderResult.Failure(id, ProviderErrors.NOT_SUPPORTED)
        }
        val end = LocalDate.now()
        val start = end.minusDays(if (days <= 0) 365L else days.toLong())
        val body = client.getText(
            id,
            "$BASE/${start}..${end}?from=USD&to=${asset.providerSymbol}",
        )
        val root = ProviderJson.json.parseToJsonElement(body).jsonObjectSafe()
        val rates = root?.get("rates")?.jsonObjectSafe() ?: emptyMap()
        val points = mutableListOf<HistoryPoint>()
        for ((dateStr, payload) in rates) {
            val rate = payload.jsonObjectSafe()?.get(asset.providerSymbol)
                ?.let { runCatching { it.jsonPrimitive.contentOrNull?.toDoubleOrNull() }.getOrNull() }
                ?: continue
            if (rate <= 0.0) continue
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            points += HistoryPoint(date.toEpochDay() * 86_400_000L, BigDecimal.valueOf(rate))
        }
        if (points.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(points.sortedBy { it.timestamp })
        }
    }.toProviderResult()

    private companion object {
        const val BASE = "https://api.frankfurter.dev/v1"
    }
}

/** Shared plumbing: uniform exception → ProviderResult mapping. */
abstract class MarketDataProviderBase : ir.cheghadr.core.model.MarketDataProvider {

    protected fun <T> Result<ProviderResult<T>>.toProviderResult(): ProviderResult<T> =
        fold(
            onSuccess = { it },
            onFailure = { e ->
                val message = when (e) {
                    is ProviderHttpException -> when {
                        e.status == 429 -> ProviderErrors.RATE_LIMITED
                        e.status < 0 -> ProviderErrors.NETWORK
                        else -> e.message
                    }
                    else -> e.message ?: ProviderErrors.NETWORK
                }
                ProviderResult.Failure(id, message, e)
            },
        )
}

private fun kotlinx.serialization.json.JsonElement?.jsonObjectSafe() =
    runCatching { this?.let { el -> (el as kotlinx.serialization.json.JsonObject) } }.getOrNull()
