package ir.cheghadr.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AlertEvaluatorTest {

    private val now = 1_700_000_000_000L
    private val freshQuoteTime = now - 60_000L

    private fun quote(price: String, pct: String? = null, at: Long = freshQuoteTime) = Quote(
        assetId = "crypto:BTC",
        price = BigDecimal(price),
        changePercent = pct?.let { BigDecimal(it) },
        fetchTime = at,
        providerId = ProviderId.COINGECKO,
    )

    private fun rule(
        type: AlertType,
        threshold: String,
        cooldownMinutes: Int = 60,
        repeat: Boolean = false,
        conditionActive: Boolean = false,
        lastTriggeredAt: Long? = null,
    ) = AlertRule(
        id = 1L,
        assetId = "crypto:BTC",
        type = type,
        threshold = BigDecimal(threshold),
        enabled = true,
        cooldownMinutes = cooldownMinutes,
        repeat = repeat,
        createdAt = now - 86_400_000L,
        lastTriggeredAt = lastTriggeredAt,
        conditionActive = conditionActive,
    )

    @Test
    fun `price above triggers once and re-arms`() {
        val r = rule(AlertType.PRICE_ABOVE, "100", cooldownMinutes = 0)
        // First crossing fires.
        val first = AlertEvaluator.evaluate(r, quote("101"), now)
        assertTrue(first.shouldNotify)
        assertTrue(first.updatedRule.conditionActive)
        assertEquals(now, first.updatedRule.lastTriggeredAt)

        // Price stays above → duplicate suppressed (no repeat monitoring).
        val second = AlertEvaluator.evaluate(first.updatedRule, quote("102"), now + 1_000)
        assertFalse(second.shouldNotify)

        // Price falls back below → re-arm.
        val reArm = AlertEvaluator.evaluate(first.updatedRule, quote("99"), now + 2_000)
        assertFalse(reArm.shouldNotify)
        assertFalse(reArm.updatedRule.conditionActive)

        // Crosses again → fires again.
        val third = AlertEvaluator.evaluate(reArm.updatedRule, quote("105"), now + 3_000)
        assertTrue(third.shouldNotify)
    }

    @Test
    fun `price below triggers`() {
        val r = rule(AlertType.PRICE_BELOW, "50", cooldownMinutes = 0)
        assertTrue(AlertEvaluator.evaluate(r, quote("49.5"), now).shouldNotify)
        assertFalse(AlertEvaluator.evaluate(r, quote("50"), now).shouldNotify)
        assertFalse(AlertEvaluator.evaluate(r, quote("51"), now).shouldNotify)
    }

    @Test
    fun `percent up and down`() {
        assertTrue(AlertEvaluator.evaluate(rule(AlertType.PERCENT_UP, "2", cooldownMinutes = 0), quote("100", "2.5"), now).shouldNotify)
        assertFalse(AlertEvaluator.evaluate(rule(AlertType.PERCENT_UP, "2", cooldownMinutes = 0), quote("100", "1.5"), now).shouldNotify)
        assertTrue(AlertEvaluator.evaluate(rule(AlertType.PERCENT_DOWN, "2", cooldownMinutes = 0), quote("100", "-2.4"), now).shouldNotify)
        assertFalse(AlertEvaluator.evaluate(rule(AlertType.PERCENT_DOWN, "2", cooldownMinutes = 0), quote("100", "-1.1"), now).shouldNotify)
        // Percent alerts do not fire without percentage data.
        assertFalse(AlertEvaluator.evaluate(rule(AlertType.PERCENT_UP, "2", cooldownMinutes = 0), quote("100"), now).shouldNotify)
    }

    @Test
    fun `cooldown suppresses notifications inside the window`() {
        val r = rule(AlertType.PRICE_ABOVE, "100", cooldownMinutes = 60)
        val first = AlertEvaluator.evaluate(r, quote("101"), now)
        assertTrue(first.shouldNotify)
        // Re-arm below, cross again after 30 minutes (< cooldown) → suppressed.
        val reArmed = AlertEvaluator.evaluate(first.updatedRule, quote("99", at = now + 60_000), now + 60_000).updatedRule
        val early = AlertEvaluator.evaluate(reArmed, quote("101", at = now + 30 * 60_000L - 1_000), now + 30 * 60_000L)
        assertFalse(early.shouldNotify)
        // After cooldown → fires.
        val late = AlertEvaluator.evaluate(early.updatedRule, quote("102", at = now + 61 * 60_000L - 1_000), now + 61 * 60_000L)
        assertTrue(late.shouldNotify)
    }

    @Test
    fun `repeat monitoring re-fires while condition holds after cooldown`() {
        val r = rule(AlertType.PRICE_ABOVE, "100", cooldownMinutes = 30, repeat = true)
        val first = AlertEvaluator.evaluate(r, quote("101"), now)
        assertTrue(first.shouldNotify)
        // Condition holds; within cooldown → suppressed.
        val soon = AlertEvaluator.evaluate(first.updatedRule, quote("102", at = now + 10 * 60_000L - 1_000), now + 10 * 60_000L)
        assertFalse(soon.shouldNotify)
        // Condition holds; cooldown elapsed → re-fires.
        val later = AlertEvaluator.evaluate(first.updatedRule, quote("103", at = now + 35 * 60_000L - 1_000), now + 35 * 60_000L)
        assertTrue(later.shouldNotify)
    }

    @Test
    fun `disabled rule never fires`() {
        val r = rule(AlertType.PRICE_ABOVE, "100", cooldownMinutes = 0).copy(enabled = false)
        assertFalse(AlertEvaluator.evaluate(r, quote("101"), now).shouldNotify)
    }

    @Test
    fun `stale quotes do not trigger alerts by default`() {
        val staleQuote = quote("101").copy(fetchTime = now - 3 * 60 * 60_000L)
        val r = rule(AlertType.PRICE_ABOVE, "100", cooldownMinutes = 0)
        assertFalse(AlertEvaluator.evaluate(r, staleQuote, now).shouldNotify)
        // Explicit opt-in allows stale triggering.
        val optedIn = r.copy(allowStaleData = true)
        assertTrue(AlertEvaluator.evaluate(optedIn, staleQuote, now).shouldNotify)
    }

    @Test
    fun `missing quote does not trigger`() {
        val r = rule(AlertType.PRICE_ABOVE, "100", cooldownMinutes = 0)
        assertFalse(AlertEvaluator.evaluate(r, null, now).shouldNotify)
    }

    @Test
    fun `threshold equal to price does not fire above rule`() {
        val r = rule(AlertType.PRICE_ABOVE, "100", cooldownMinutes = 0)
        assertFalse(AlertEvaluator.evaluate(r, quote("100"), now).shouldNotify)
    }
}
