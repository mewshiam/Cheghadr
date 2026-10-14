package ir.cheghadr.core.data.provider

import io.ktor.client.HttpClient
import ir.cheghadr.core.data.catalog.AssetCatalog
import ir.cheghadr.core.data.network.parseCoinPaprikaHistorical
import ir.cheghadr.core.data.network.parseCoinPaprikaTickers
import ir.cheghadr.core.data.network.parseGoldApiSpot
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.ProviderErrors
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.ProviderResult
import ir.cheghadr.core.model.RawQuote
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CoinPaprika — https://api.coinpaprika.com/ free tier (no key).
 *
 * Primary crypto source: the bulk `/v1/tickers?quotes=USD` request covers the
 * whole catalog in ONE call (the blueprint's "avoid per-asset polling" rule),
 * and `/v1/tickers/{id}/historical` serves real daily/hourly history.
 * Verified live 2026-10-14; personal free tier, attribution appreciated.
 */
@Singleton
class CoinPaprikaProvider @Inject constructor(
    private val client: HttpClient,
) : MarketDataProviderBase() {

    override val id = ProviderId.COIN_PAPRIKA
    override val displayNameFa = "CoinPaprika"
    override val displayNameEn = "CoinPaprika"
    override val attributionFa = "داده‌های رمزارزها از CoinPaprika"
    override val attributionEn = "Crypto data by CoinPaprika"
    override val requiresConfiguration = false
    override val supportedCategories = setOf(AssetCategory.CRYPTO)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>> = runCatching {
        val assets = AssetCatalog.forProvider(ProviderId.COIN_PAPRIKA)
        if (assets.isEmpty()) {
            return@runCatching ProviderResult.Success(emptyMap<String, RawQuote>())
        }
        val byPaprikaId = assets.associateBy { it.providerSymbol }
        val body = client.getText(id, "$BASE/tickers?quotes=USD")
        val parsed = parseCoinPaprikaTickers(body, byPaprikaId.keys)
        val mapped = parsed.mapNotNull { (paprikaId, raw) ->
            val asset = byPaprikaId[paprikaId] ?: return@mapNotNull null
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
        val effectiveDays = if (days <= 0) MAX_DAYS else days.coerceAtMost(MAX_DAYS)
        val interval = if (effectiveDays <= 1) "1h" else "1d"
        val start = Instant.now().minusSeconds(effectiveDays * 86_400L)
            .truncatedTo(ChronoUnit.SECONDS)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"
        val body = client.getText(
            id,
            "$BASE/tickers/${asset.providerSymbol}/historical?start=$start&interval=$interval",
        )
        val points = parseCoinPaprikaHistorical(body)
        if (points.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(points)
        }
    }.toProviderResult()

    private companion object {
        const val BASE = "https://api.coinpaprika.com/v1"
        const val MAX_DAYS = 3650
    }
}

/**
 * gold-api.com — free spot prices for gold (XAU) and silver (XAG) per troy
 * ounce in USD. Verified live 2026-10-14; no key required.
 * No history endpoint: the app records device-side snapshots instead.
 */
@Singleton
class GoldApiProvider @Inject constructor(
    private val client: HttpClient,
) : MarketDataProviderBase() {

    override val id = ProviderId.GOLD_API
    override val displayNameFa = "GoldAPI"
    override val displayNameEn = "GoldAPI"
    override val attributionFa = "قیمت لحظه‌ای طلا و نقره از gold-api.com"
    override val attributionEn = "Gold & silver spot by gold-api.com"
    override val requiresConfiguration = false
    override val supportedCategories = setOf(AssetCategory.GOLD, AssetCategory.COMMODITY)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>> = runCatching {
        val assets = AssetCatalog.forProvider(ProviderId.GOLD_API)
        if (assets.isEmpty()) {
            return@runCatching ProviderResult.Success(emptyMap<String, RawQuote>())
        }
        val out = mutableMapOf<String, RawQuote>()
        for (asset in assets) {
            val body = client.getText(id, "$BASE/price/${asset.providerSymbol}")
            val raw = parseGoldApiSpot(body) ?: continue
            out[asset.id] = raw
        }
        if (out.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(out.toMap())
        }
    }.toProviderResult()

    override suspend fun history(asset: Asset, days: Int): ProviderResult<List<HistoryPoint>> =
        ProviderResult.Failure(id, ProviderErrors.NOT_SUPPORTED)

    private companion object {
        const val BASE = "https://api.gold-api.com"
    }
}
