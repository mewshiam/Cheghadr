package ir.cheghadr.core.data.provider

import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.Quote
import ir.cheghadr.core.model.RawQuote
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure validation & normalization of raw provider observations.
 * Malformed or implausible values are rejected — never displayed as prices,
 * and never overwriting previously cached valid data.
 */
object QuoteNormalizer {

    /** No legitimate price in our catalog exceeds this magnitude. */
    private val MAX_PRICE = BigDecimal("1E15")

    /** Percentage changes beyond ±99% are treated as malformed. */
    private const val MAX_PERCENT = 99.0

    /**
     * Validates one raw quote for [asset]. Returns null when the observation
     * must be rejected entirely.
     */
    fun normalize(asset: Asset, raw: RawQuote, fetchedAt: Long): Quote? {
        val price = raw.price ?: return null
        if (price.signum() <= 0) return null
        if (price.abs() > MAX_PRICE) return null

        val changePercent = raw.changePercent?.let { pct ->
            if (pct.abs().toDouble() > MAX_PERCENT) null else pct
        }
        val changeAbs = raw.changeAbs?.let { abs ->
            // An absolute change larger than 10× the price is implausible.
            if (abs.abs() > price.abs().multiply(BigDecimal.TEN)) null else abs
        }

        return Quote(
            assetId = asset.id,
            price = price.stripTrailingZeros(),
            changeAbs = changeAbs?.stripTrailingZeros(),
            changePercent = changePercent?.stripTrailingZeros(),
            high24h = raw.high24h?.takeIf { it.signum() > 0 }?.stripTrailingZeros(),
            low24h = raw.low24h?.takeIf { it.signum() > 0 }?.stripTrailingZeros(),
            volume24h = raw.volume24h?.takeIf { it.signum() >= 0 }?.stripTrailingZeros(),
            marketCap = raw.marketCap?.takeIf { it.signum() >= 0 }?.stripTrailingZeros(),
            priceTime = raw.priceTime?.takeIf { it in 0..fetchedAt + 86_400_000L },
            fetchTime = fetchedAt,
            providerId = asset.providerId,
        )
    }

    /**
     * Cleans a provider history series: drops non-positive prices, dedupes
     * timestamps (keeps the last observation) and sorts chronologically.
     */
    fun normalizeHistory(points: List<HistoryPoint>): List<HistoryPoint> {
        val byTime = LinkedHashMap<Long, BigDecimal>()
        for (p in points) {
            if (p.timestamp <= 0L) continue
            if (p.price.signum() <= 0) continue
            byTime[p.timestamp] = p.price
        }
        return byTime.entries
            .sortedBy { it.key }
            .map { HistoryPoint(it.key, it.value) }
    }

    /**
     * Computes a change series against a reference value (used to derive
     * changes for providers that do not expose daily change fields).
     */
    fun computeChangePercent(current: BigDecimal, reference: BigDecimal): BigDecimal? {
        if (reference.signum() <= 0) return null
        return current.subtract(reference)
            .divide(reference, 6, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .stripTrailingZeros()
    }
}

/**
 * Provider selection per category/asset. Providers are isolated: enabling,
 * disabling or replacing one never rewrites the app.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    private val coinPaprika: CoinPaprikaProvider,
    private val coinGecko: CoinGeckoProvider,
    private val openErApi: OpenErApiProvider,
    private val frankfurter: FrankfurterProvider,
    private val goldApi: GoldApiProvider,
    private val tgju: TgjuProvider,
) {
    val all: List<ir.cheghadr.core.model.MarketDataProvider> =
        listOf(coinPaprika, coinGecko, openErApi, frankfurter, goldApi, tgju)

    fun byId(providerId: ProviderId): ir.cheghadr.core.model.MarketDataProvider? =
        all.firstOrNull { it.id == providerId }

    suspend fun activeProviders(): List<ir.cheghadr.core.model.MarketDataProvider> =
        all.filter { it.isAvailable() }
}
