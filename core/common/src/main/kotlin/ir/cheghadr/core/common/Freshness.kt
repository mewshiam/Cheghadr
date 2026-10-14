package ir.cheghadr.core.common

import ir.cheghadr.core.model.DataFreshness
import ir.cheghadr.core.model.Quote

/**
 * Classifies quote freshness from timestamps. Pure and unit tested.
 *
 * Freshness is computed at read time (never stored) so that it naturally
 * degrades as time passes and cached data becomes stale.
 */
object FreshnessClassifier {

    /** Marketable quote age thresholds per category (millis). Crypto trades 24/7,
     * official FX updates roughly daily, so the same age means different things. */
    private const val CRYPTO_STALE_MS = 30 * 60_000L
    private const val CRYPTO_VERY_STALE_MS = 6 * 60 * 60_000L
    private const val FX_STALE_MS = 30 * 60_000L
    private const val FX_VERY_STALE_MS = 48 * 60 * 60_000L
    private const val DEFAULT_STALE_MS = 30 * 60_000L
    private const val DEFAULT_VERY_STALE_MS = 24 * 60 * 60_000L

    fun classify(quote: Quote?, now: Long, categoryIsCryptoOrAlwaysOpen: Boolean = false): DataFreshness {
        if (quote == null) return DataFreshness.UNKNOWN
        val effective = quote.priceTime ?: quote.fetchTime
        val age = now - effective
        if (age < 0) return DataFreshness.FRESH // clock skew — treat as fresh
        val (stale, veryStale) = if (categoryIsCryptoOrAlwaysOpen) {
            CRYPTO_STALE_MS to CRYPTO_VERY_STALE_MS
        } else {
            FX_STALE_MS to FX_VERY_STALE_MS
        }
        return when {
            age <= stale -> DataFreshness.FRESH
            age <= veryStale -> DataFreshness.STALE
            else -> DataFreshness.VERY_STALE
        }
    }

    /** Age of the quote in human-readable form; caller localizes. */
    fun ageMinutes(quote: Quote, now: Long): Long = (now - (quote.priceTime ?: quote.fetchTime)) / 60_000L
}
