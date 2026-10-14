package ir.cheghadr.core.model

import java.math.BigDecimal

/** Type of a price-alert threshold. */
enum class AlertType {
    /** Price rises above threshold. */
    PRICE_ABOVE,

    /** Price falls below threshold. */
    PRICE_BELOW,

    /** Percentage change (vs. provider's daily reference) rises above threshold. */
    PERCENT_UP,

    /** Percentage change falls below (more negative than) threshold. */
    PERCENT_DOWN,
}

/**
 * A persisted alert rule with its runtime trigger state.
 *
 * Rules are evaluated locally by a periodic background worker. This is
 * device-side monitoring: delivery can be delayed by Android scheduling,
 * battery optimization, Doze, or network unavailability — the UI states this
 * explicitly instead of promising real-time alerts.
 *
 * Notification semantics:
 *  - A **new crossing** of the threshold fires one notification.
 *  - While the condition stays active, further notifications only occur if
 *    [repeat] is enabled and the [cooldownMinutes] window has elapsed.
 *  - After the condition becomes false again the rule **re-arms**, so the next
 *    crossing fires again — subject to the cooldown window.
 *  - Quotes older than the staleness window never trigger notifications
 *    unless the user explicitly opts in via [allowStaleData].
 */
data class AlertRule(
    val id: Long = 0L,
    val assetId: String,
    val type: AlertType,
    val threshold: BigDecimal,
    val enabled: Boolean = true,
    /** Minimum minutes between two notifications of the same rule. */
    val cooldownMinutes: Int = 60,
    /** Keep notifying while the condition stays active (recurring monitoring). */
    val repeat: Boolean = false,
    /** Explicit user opt-in to triggering from stale data. */
    val allowStaleData: Boolean = false,
    val createdAt: Long,
    /** Epoch millis of the last notification for this rule. */
    val lastTriggeredAt: Long? = null,
    /** Price observed when the rule last fired (for re-arming logic). */
    val lastTriggerValue: BigDecimal? = null,
    /** True while the condition is currently met (duplicate guard / re-arm). */
    val conditionActive: Boolean = false,
)

/**
 * Pure, deterministic alert evaluation — exhaustively unit tested.
 */
object AlertEvaluator {

    data class Outcome(
        val shouldNotify: Boolean,
        val updatedRule: AlertRule,
    )

    /**
     * Evaluates [rule] against the freshest available [quote] at time [now].
     */
    fun evaluate(
        rule: AlertRule,
        quote: Quote?,
        now: Long,
        staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
    ): Outcome {
        if (!rule.enabled) return Outcome(false, rule)

        val q = quote ?: return Outcome(false, rule)

        val effectiveTime = q.priceTime ?: q.fetchTime
        if (!rule.allowStaleData && now - effectiveTime > staleAfterMillis) {
            return Outcome(false, rule)
        }

        val conditionMet = when (rule.type) {
            AlertType.PRICE_ABOVE -> q.price > rule.threshold
            AlertType.PRICE_BELOW -> q.price < rule.threshold
            AlertType.PERCENT_UP -> q.changePercent != null && q.changePercent >= rule.threshold
            AlertType.PERCENT_DOWN -> q.changePercent != null && q.changePercent <= -rule.threshold.abs()
        }

        if (!conditionMet) {
            // Condition no longer holds → re-arm so a later crossing fires again.
            return Outcome(false, rule.copy(conditionActive = false))
        }

        val relevantValue = when (rule.type) {
            AlertType.PERCENT_UP, AlertType.PERCENT_DOWN -> q.changePercent ?: rule.lastTriggerValue
            else -> q.price
        }

        // While the condition stays active, only repeat-monitoring may re-fire.
        if (rule.conditionActive && !rule.repeat) {
            return Outcome(false, rule)
        }

        // Absolute cooldown between any two notifications of this rule.
        // A crossing observed inside the cooldown window is not consumed: the
        // rule stays unarmed so a later evaluation can still notify once the
        // window has elapsed.
        val cooldownMillis = rule.cooldownMinutes * 60_000L
        val cooldownOver = rule.lastTriggeredAt == null ||
            now - rule.lastTriggeredAt >= cooldownMillis
        if (!cooldownOver) {
            return Outcome(false, rule)
        }

        val updated = rule.copy(
            lastTriggeredAt = now,
            lastTriggerValue = relevantValue,
            conditionActive = true,
        )
        return Outcome(true, updated)
    }

    const val DEFAULT_STALE_AFTER_MILLIS = 30 * 60_000L
}
