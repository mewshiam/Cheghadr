package ir.cheghadr.core.model

import java.math.BigDecimal

/**
 * Market categories supported by «چقدر؟».
 * Categories without a permissible, verified data source render an honest
 * "unavailable" state instead of fabricated prices.
 */
enum class AssetCategory {
    /** Foreign exchange rates (official and, where a provider permits, free-market). */
    CURRENCY,

    /** Gold (gram / mithqal / ounce, various purities). */
    GOLD,

    /** Iranian gold coins (Bahar Azadi, Emami, half, quarter, gram). */
    COIN,

    /** Cryptocurrencies. */
    CRYPTO,

    /** Equities and stock indices. */
    STOCK,

    /** Commodities (oil, silver, etc.). */
    COMMODITY,
}

/** Unit in which a quoted price is expressed. */
enum class PriceUnit {
    RIAL,
    TOMAN,
    USD,
    EUR,
    GRAM,
    MITHQAL,
    OUNCE,
    BARREL,
    UNIT,
}

/**
 * A tradeable/trackable market instrument with a stable canonical identifier.
 *
 * The [id] (e.g. `crypto:BTC`, `fx:USD-IRR`, `gold:GERAM18`) is the database key —
 * display names are never used as identifiers.
 */
data class Asset(
    val id: String,
    val category: AssetCategory,
    val symbol: String,
    val nameFa: String,
    val nameEn: String,
    val providerId: ProviderId,
    val providerSymbol: String,
    val quoteCurrency: String,
    val unit: PriceUnit,
    val iconUrl: String? = null,
    val aliases: List<String> = emptyList(),
    /**
     * False when no permissible source currently supplies this asset.
     * The UI must show an informative unavailable state instead of a price.
     */
    val available: Boolean = true,
    /** Optional explanation (fa/en) shown when [available] is false. */
    val unavailableReasonFa: String? = null,
    val unavailableReasonEn: String? = null,
    /** Whether historical series can be fetched for this asset. */
    val supportsHistory: Boolean = false,
    /** Implicit ordering inside its category. */
    val sortOrder: Int = 0,
)

/** A single normalized spot quote for an asset. All monetary values use [java.math.BigDecimal]. */
data class Quote(
    val assetId: String,
    val price: BigDecimal,
    val changeAbs: BigDecimal? = null,
    val changePercent: BigDecimal? = null,
    val high24h: BigDecimal? = null,
    val low24h: BigDecimal? = null,
    val volume24h: BigDecimal? = null,
    val marketCap: BigDecimal? = null,
    /** Provider-side timestamp of the price (epoch millis), when exposed. */
    val priceTime: Long? = null,
    /** Local timestamp of the successful fetch (epoch millis). */
    val fetchTime: Long,
    val providerId: ProviderId,
)

/** One point of a historical price series. Timestamps are UTC epoch millis. */
data class HistoryPoint(
    val timestamp: Long,
    val price: BigDecimal,
)

/** Freshness classification of a quote, derived from timestamps at read time. */
enum class DataFreshness { FRESH, STALE, VERY_STALE, UNKNOWN }

/** Health of an individual data provider. */
data class ProviderStatus(
    val providerId: ProviderId,
    val enabled: Boolean,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
    val consecutiveFailures: Int = 0,
)
