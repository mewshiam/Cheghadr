package ir.cheghadr.core.common

import ir.cheghadr.core.model.DataFreshness
import ir.cheghadr.core.model.ProviderId
import ir.cheghadr.core.model.Quote
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FreshnessClassifierTest {

    private fun quote(fetchTime: Long) = Quote(
        assetId = "test",
        price = BigDecimal.ONE,
        fetchTime = fetchTime,
        providerId = ProviderId.COINGECKO,
    )

    private val now = 1_000_000_000_000L

    @Test
    fun `null quote is unknown`() {
        assertEquals(DataFreshness.UNKNOWN, FreshnessClassifier.classify(null, now))
    }

    @Test
    fun `crypto freshness thresholds`() {
        assertEquals(DataFreshness.FRESH, FreshnessClassifier.classify(quote(now - 10 * 60_000), now, true))
        assertEquals(DataFreshness.STALE, FreshnessClassifier.classify(quote(now - 60 * 60_000), now, true))
        assertEquals(DataFreshness.VERY_STALE, FreshnessClassifier.classify(quote(now - 7 * 60 * 60_000), now, true))
    }

    @Test
    fun `fx freshness thresholds`() {
        assertEquals(DataFreshness.FRESH, FreshnessClassifier.classify(quote(now - 10 * 60_000), now, false))
        assertEquals(DataFreshness.STALE, FreshnessClassifier.classify(quote(now - 5 * 60 * 60_000), now, false))
        assertEquals(DataFreshness.VERY_STALE, FreshnessClassifier.classify(quote(now - 72 * 60 * 60_000), now, false))
    }

    @Test
    fun `provider timestamp preferred over fetch time`() {
        val q = quote(now - 2 * 60 * 60_000).copy(priceTime = now - 60_000)
        assertEquals(DataFreshness.FRESH, FreshnessClassifier.classify(q, now, true))
    }

    @Test
    fun `clock skew treated as fresh`() {
        assertEquals(DataFreshness.FRESH, FreshnessClassifier.classify(quote(now + 60_000), now, true))
    }
}
