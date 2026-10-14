package ir.cheghadr.core.data.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import ir.cheghadr.core.data.catalog.AssetCatalog
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.ProviderErrors
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.ProviderResult
import ir.cheghadr.core.model.RawQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Iranian market data scraped from tgju.org's public pages (user-requested
 * source; on by default, can be disabled in settings).
 *
 * How it works (structure verified live on 2026-10-14, see TgjuScraper docs):
 *  - Quotes: three section pages fetched in parallel — /currency, /gold-chart
 *    and /coin — each carrying server-rendered market tables plus the
 *    site-wide live sidebar (source for gc30 / oil_brent, which have no
 *    section row). Three requests cover the whole Iranian catalog.
 *  - History: the per-symbol profile page /profile/{symbol} renders ~25 daily
 *    OHLC rows and a live sidebar price.
 *  - tgju.org sits behind Cloudflare and intermittently answers profile URLs
 *    with a transient 404 (soft home-page body); every fetch is therefore
 *    retried a couple of times before surfacing an error.
 *  - Prices are quoted in rial (display layer converts to toman); oil_brent
 *    and gc30 are quoted in USD / index points respectively.
 *  - Requests are capped at the sync cadence (default 30 min) — no polling
 *    loops; failures back off exponentially (SyncCoordinator).
 */
@Singleton
class TgjuProvider @Inject constructor(
    private val client: HttpClient,
    private val settings: SettingsRepository,
) : MarketDataProviderBase() {

    override val id = ProviderId.TGJU
    override val displayNameFa = "TGJU"
    override val displayNameEn = "TGJU"
    override val attributionFa = "داده‌های بازار ایران از tgju.org"
    override val attributionEn = "Iranian market data from tgju.org"
    override val requiresConfiguration = false
    override val supportedCategories = setOf(
        AssetCategory.CURRENCY,
        AssetCategory.GOLD,
        AssetCategory.COIN,
        AssetCategory.STOCK,
        AssetCategory.COMMODITY,
    )

    override suspend fun isAvailable(): Boolean = settings.settings.first().tgjuEnabled

    override suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>> = runCatching {
        if (!isAvailable()) {
            return@runCatching ProviderResult.Failure(id, ProviderErrors.NO_CONFIGURATION)
        }
        val base = baseUrl()
        val fetchedAt = System.currentTimeMillis()
        val bodies = coroutineScope {
            SECTION_PATHS.map { path ->
                async(Dispatchers.IO) { fetchWithRetry("$base/$path") }
            }.awaitAll()
        }
        val quotes = mutableMapOf<String, RawQuote>()
        for (body in bodies) {
            for ((symbol, raw) in TgjuScraper.parseMarketRows(body, fetchedAt)) {
                AssetCatalog.byTgjuSymbol[symbol]?.let { asset ->
                    quotes[asset.id] = raw
                }
            }
            // gc30 / oil_brent only exist in the site-wide sidebar.
            for ((symbol, raw) in TgjuScraper.parseSidebar(body, SIDEBAR_SYMBOLS)) {
                AssetCatalog.byTgjuSymbol[symbol]?.let { asset ->
                    quotes[asset.id] = raw.copy(priceTime = fetchedAt)
                }
            }
        }
        if (quotes.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(quotes)
        }
    }.toProviderResult()

    override suspend fun history(asset: Asset, days: Int): ProviderResult<List<HistoryPoint>> = runCatching {
        if (asset.providerId != id || !asset.supportsHistory) {
            return@runCatching ProviderResult.Failure(id, ProviderErrors.NOT_SUPPORTED)
        }
        if (!isAvailable()) {
            return@runCatching ProviderResult.Failure(id, ProviderErrors.NO_CONFIGURATION)
        }
        val now = System.currentTimeMillis()
        val body = fetchWithRetry("${baseUrl()}/profile/${asset.providerSymbol}")
        val points = TgjuScraper.parseHistory(body, asset.providerSymbol, now)
        // The profile page serves a fixed ~25-day window; honor smaller ranges.
        val cutoff = if (days > 0) now - days * 86_400_000L else 0L
        val filtered = points.filter { it.timestamp >= cutoff }
        if (filtered.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(filtered)
        }
    }.toProviderResult()

    /** User-overridable base URL (mirrors); blank falls back to the site. */
    private suspend fun baseUrl(): String {
        val url = settings.settings.first().tgjuBaseUrl.trim().trimEnd('/')
        return if (url.isBlank()) DEFAULT_BASE else url
    }

    /**
     * tgju.org occasionally returns transient 404s (Cloudflare) for valid
     * URLs — retry a couple of times with a short delay before giving up.
     */
    private suspend fun fetchWithRetry(url: String): String {
        var lastError: Exception? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                return client.getText(id, url) {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }
        throw lastError ?: IllegalStateException("tgju fetch failed: $url")
    }

    private companion object {
        const val DEFAULT_BASE = "https://www.tgju.org"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_500L
        val SECTION_PATHS = listOf("currency", "gold-chart", "coin")
        val SIDEBAR_SYMBOLS = setOf("gc30", "oil_brent")
    }
}
