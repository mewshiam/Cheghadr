package ir.cheghadr.core.data.provider

import io.ktor.client.HttpClient
import ir.cheghadr.core.data.catalog.AssetCatalog
import ir.cheghadr.core.data.network.parseTgjuWidgetJson
import ir.cheghadr.core.data.preferences.SettingsRepository
import ir.cheghadr.core.model.Asset
import ir.cheghadr.core.model.AssetCategory
import ir.cheghadr.core.model.HistoryPoint
import ir.cheghadr.core.model.ProviderErrors
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.ProviderResult
import ir.cheghadr.core.model.RawQuote
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TGJU-compatible provider for Iranian market data (free-market USD, gold,
 * coins, Tehran indices).
 *
 * ⚠️ Disabled by default. TGJU's terms (https://gem.tgju.org/terms) require
 * written permission for copying/redistribution. The user must:
 *   1. secure authorization (or an official API subscription) themselves, and
 *   2. provide an endpoint URL returning the standard TGJU web-service JSON
 *      schema (see docs/DATA_SOURCES.md §3.1 for the expected schema).
 *
 * No endpoint is hard-coded and nothing is scraped without the user's own
 * authorized source. See the blueprint's data-source rules.
 */
@Singleton
class TgjuProvider @Inject constructor(
    private val client: HttpClient,
    private val settings: SettingsRepository,
) : MarketDataProviderBase() {

    override val id = ProviderId.TGJU
    override val displayNameFa = "TGJU (منبع سفارشی)"
    override val displayNameEn = "TGJU (custom source)"
    override val attributionFa = "داده‌های بازار ایران از منبع TGJU-compatible شما"
    override val attributionEn = "Iranian market data from your TGJU-compatible source"
    override val requiresConfiguration = true
    override val supportedCategories = setOf(
        AssetCategory.CURRENCY,
        AssetCategory.GOLD,
        AssetCategory.COIN,
        AssetCategory.STOCK,
        AssetCategory.COMMODITY,
    )

    override suspend fun isAvailable(): Boolean {
        val s = settings.settings.first()
        return s.tgjuEnabled && s.tgjuBaseUrl.isNotBlank()
    }

    override suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>> = runCatching {
        val s = settings.settings.first()
        if (!s.tgjuEnabled || s.tgjuBaseUrl.isBlank()) {
            return@runCatching ProviderResult.Failure(id, ProviderErrors.NO_CONFIGURATION)
        }
        val body = client.getText(id, s.tgjuBaseUrl)
        val parsed = parseTgjuWidgetJson(body)
        // Map TGJU symbols to canonical asset ids; unknown symbols are ignored.
        val mapped = parsed.mapNotNull { (symbol, raw) ->
            AssetCatalog.byTgjuSymbol[symbol]?.let { it.id to raw }
        }.toMap()
        if (mapped.isEmpty()) {
            ProviderResult.Failure(id, ProviderErrors.BAD_DATA, isDataError = true)
        } else {
            ProviderResult.Success(mapped)
        }
    }.toProviderResult()

    override suspend fun history(asset: Asset, days: Int): ProviderResult<List<HistoryPoint>> =
        ProviderResult.Failure(id, ProviderErrors.NOT_SUPPORTED)
}
