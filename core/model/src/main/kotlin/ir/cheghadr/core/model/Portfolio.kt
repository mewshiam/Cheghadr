package ir.cheghadr.core.model

import java.math.BigDecimal

/** Side of a portfolio transaction. */
enum class TxSide { BUY, SELL }

/**
 * A local-only portfolio transaction. Never synchronized anywhere —
 * portfolio data stays on the device by design.
 */
data class Transaction(
    val id: Long = 0L,
    val assetId: String,
    val side: TxSide,
    val quantity: BigDecimal,
    /** Price per unit in the asset's quote currency. */
    val pricePerUnit: BigDecimal,
    /** Fees in the quote currency, charged on this transaction. */
    val fee: BigDecimal = BigDecimal.ZERO,
    val timestamp: Long,
    val note: String? = null,
)

/**
 * Deterministic portfolio accounting (average-cost method) — pure and
 * exhaustively unit tested. Results are informational, never investment advice.
 */
object PortfolioCalculator {

    data class Position(
        val assetId: String,
        val quantity: BigDecimal,
        /** Average acquisition cost per unit (fees included in cost basis). */
        val avgCostPerUnit: BigDecimal?,
        /** Total cost basis of currently held units. */
        val invested: BigDecimal,
        /** Realized profit/loss from closed (sold) units. */
        val realized: BigDecimal,
        /** Accumulated fees. */
        val fees: BigDecimal,
    )

    /** Aggregates the full transaction list into open positions. */
    fun positions(transactions: List<Transaction>): Map<String, Position> {
        data class Acc(
            var qty: BigDecimal = BigDecimal.ZERO,
            var basis: BigDecimal = BigDecimal.ZERO, // total cost of held units
            var realized: BigDecimal = BigDecimal.ZERO,
            var fees: BigDecimal = BigDecimal.ZERO,
        )

        val acc = mutableMapOf<String, Acc>()
        for (t in transactions.sortedBy { it.timestamp }) {
            val a = acc.getOrPut(t.assetId) { Acc() }
            a.fees += t.fee
            when (t.side) {
                TxSide.BUY -> {
                    a.basis += t.quantity.multiply(t.pricePerUnit) + t.fee
                    a.qty += t.quantity
                }
                TxSide.SELL -> {
                    val sellQty = t.quantity.min(a.qty)
                    val avgCost = if (a.qty.signum() > 0) {
                        a.basis.divide(a.qty, 12, java.math.RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }
                    // Remove sold units at average cost.
                    a.basis -= avgCost.multiply(sellQty)
                    a.qty -= sellQty
                    if (a.qty.signum() <= 0) {
                        a.qty = BigDecimal.ZERO
                        a.basis = BigDecimal.ZERO
                    }
                    // Realized result of this sale.
                    a.realized += sellQty.multiply(t.pricePerUnit - avgCost) - t.fee
                }
            }
        }
        return acc.map { (assetId, a) ->
            val avg = if (a.qty.signum() > 0) {
                a.basis.divide(a.qty, 12, java.math.RoundingMode.HALF_UP)
            } else {
                null
            }
            assetId to Position(
                assetId = assetId,
                quantity = a.qty,
                avgCostPerUnit = avg,
                invested = a.basis,
                realized = a.realized,
                fees = a.fees,
            )
        }.toMap()
    }

    /** Unrealized profit/loss for a position at [currentPrice]; null when no price. */
    fun unrealized(position: Position, currentPrice: BigDecimal?): BigDecimal? {
        if (currentPrice == null || position.quantity.signum() <= 0) return null
        return currentPrice.multiply(position.quantity).subtract(position.invested)
    }

    /** Current estimated value of a position; null when no price. */
    fun currentValue(position: Position, currentPrice: BigDecimal?): BigDecimal? {
        if (currentPrice == null) return null
        return currentPrice.multiply(position.quantity)
    }
}

/** Home-screen widget kinds. */
enum class WidgetKind { SINGLE_ASSET, ASSET_LIST }

/** Persisted configuration of one widget instance (separate from UI state). */
data class WidgetConfig(
    val appWidgetId: Int,
    val kind: WidgetKind,
    /** One asset for [WidgetKind.SINGLE_ASSET]; several for [WidgetKind.ASSET_LIST]. */
    val assetIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Supported historical ranges in the details chart. */
enum class HistoryRange(val days: Int, val key: String) {
    DAY_1(1, "1d"),
    DAY_7(7, "7d"),
    DAY_30(30, "30d"),
    DAY_90(90, "90d"),
    DAY_180(180, "180d"),
    YEAR_1(365, "1y"),
    YEAR_5(365 * 5, "5y"),
    MAX(0, "max");

    companion object {
        fun fromKey(key: String): HistoryRange = entries.firstOrNull { it.key == key } ?: DAY_30
    }
}
