package ir.cheghadr.core.model

import java.math.BigDecimal

/**
 * Identifiers of the data providers integrated into «چقدر؟».
 *
 * Active, keyless providers:
 *  - [COIN_PAPRIKA]  crypto spot quotes + daily history (free tier, no key; verified 2026-10-14).
 *  - [OPEN_ER_API]   official FX rates incl. IRR (open endpoint of ExchangeRate-API).
 *  - [FRANKFURTER]   ECB reference-rate history for a subset of currencies.
 *  - [GOLD_API]      gold (XAU) / silver (XAG) spot per troy ounce (gold-api.com, no key).
 *  - [COINGECKO]     crypto spot + history (public API; strict rate limits — optional
 *                    demo key relaxes them; currently unmapped as the crypto catalog
 *                    defaults to CoinPaprika, kept registered per provider isolation).
 *
 * Conditional provider:
 *  - [TGJU] Iranian market data. Disabled by default: requires a user-supplied
 *    endpoint and compliance with TGJU's terms (written permission for
 *    redistribution). See docs/DATA_SOURCES.md.
 *
 * [APP] marks values computed locally from other providers (e.g. cross rates);
 * such quotes always disclose their inputs.
 */
enum class ProviderId {
    COIN_PAPRIKA,
    COINGECKO,
    OPEN_ER_API,
    FRANKFURTER,
    GOLD_API,
    TGJU,
    APP,
}

/** Raw, provider-specific observation before normalization. */
data class RawQuote(
    val price: BigDecimal?,
    val changeAbs: BigDecimal? = null,
    val changePercent: BigDecimal? = null,
    val high24h: BigDecimal? = null,
    val low24h: BigDecimal? = null,
    val volume24h: BigDecimal? = null,
    val marketCap: BigDecimal? = null,
    /** Provider-side price timestamp, epoch millis, if exposed. */
    val priceTime: Long? = null,
)

/** Outcome of a provider call. */
sealed interface ProviderResult<out T> {
    data class Success<T>(val value: T) : ProviderResult<T>
    data class Failure(
        val providerId: ProviderId,
        val message: String,
        val cause: Throwable? = null,
        /** True when the failure was caused by malformed data (parse/validation). */
        val isDataError: Boolean = false,
    ) : ProviderResult<Nothing>
}

/**
 * Contract every market-data provider implements. Implementations must be
 * isolated: enabling, disabling or replacing a provider never rewrites the app.
 */
interface MarketDataProvider {
    val id: ProviderId
    val displayNameFa: String
    val displayNameEn: String
    val attributionFa: String
    val attributionEn: String
    /** True when the provider needs user configuration (URL/key) before use. */
    val requiresConfiguration: Boolean
    val supportedCategories: Set<AssetCategory>

    /** Whether the provider is currently usable. */
    suspend fun isAvailable(): Boolean

    /**
     * Latest quotes keyed by canonical asset id. Implementations should use
     * bulk endpoints rather than one request per asset.
     */
    suspend fun latestQuotes(): ProviderResult<Map<String, RawQuote>>

    /**
     * Historical series for one asset. [days] <= 0 means "maximum available".
     * Returns [ProviderResult.Failure] when history is not supported for the asset.
     */
    suspend fun history(asset: Asset, days: Int): ProviderResult<List<HistoryPoint>>
}

/** Standard, human-readable provider errors. */
object ProviderErrors {
    const val NO_CONFIGURATION = "Provider is not configured"
    const val NETWORK = "Network error"
    const val BAD_DATA = "Malformed provider response"
    const val RATE_LIMITED = "Rate limited by provider"
    const val NOT_SUPPORTED = "Not supported by this provider"
}
