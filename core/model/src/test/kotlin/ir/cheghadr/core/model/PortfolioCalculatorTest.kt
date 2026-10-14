package ir.cheghadr.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PortfolioCalculatorTest {

    private fun buy(qty: String, price: String, fee: String = "0", ts: Long = 1) = Transaction(
        assetId = "crypto:BTC",
        side = TxSide.BUY,
        quantity = BigDecimal(qty),
        pricePerUnit = BigDecimal(price),
        fee = BigDecimal(fee),
        timestamp = ts,
    )

    private fun sell(qty: String, price: String, fee: String = "0", ts: Long = 2) = Transaction(
        assetId = "crypto:BTC",
        side = TxSide.SELL,
        quantity = BigDecimal(qty),
        pricePerUnit = BigDecimal(price),
        fee = BigDecimal(fee),
        timestamp = ts,
    )

    @Test
    fun `single buy position`() {
        val positions = PortfolioCalculator.positions(listOf(buy("0.5", "60000", "10")))
        val p = positions.getValue("crypto:BTC")
        assertEquals(0, BigDecimal("0.5").compareTo(p.quantity))
        assertEquals(0, BigDecimal("30010").compareTo(p.invested))
        assertEquals(0, BigDecimal("60020").compareTo(p.avgCostPerUnit))
        assertEquals(0, BigDecimal.ZERO.compareTo(p.realized))
    }

    @Test
    fun `multiple buys use weighted average cost`() {
        val positions = PortfolioCalculator.positions(
            listOf(
                buy("1", "100"),
                buy("1", "200", ts = 3),
            ),
        )
        val p = positions.getValue("crypto:BTC")
        assertEquals(0, BigDecimal("2").compareTo(p.quantity))
        assertEquals(0, BigDecimal("300").compareTo(p.invested))
        assertEquals(0, BigDecimal("150").compareTo(p.avgCostPerUnit))
    }

    @Test
    fun `partial sell realizes profit and keeps average cost`() {
        val positions = PortfolioCalculator.positions(
            listOf(
                buy("2", "100"),
                sell("1", "150", fee = "5"),
            ),
        )
        val p = positions.getValue("crypto:BTC")
        // Remaining 1 unit at avg cost 100.
        assertEquals(0, BigDecimal("1").compareTo(p.quantity))
        assertEquals(0, BigDecimal("100").compareTo(p.invested))
        // Realized: (150-100)*1 - 5 = 45.
        assertEquals(0, BigDecimal("45").compareTo(p.realized))
    }

    @Test
    fun `full sell closes position with realized result`() {
        val positions = PortfolioCalculator.positions(
            listOf(
                buy("1", "100"),
                sell("1", "80"),
            ),
        )
        val p = positions.getValue("crypto:BTC")
        assertEquals(0, BigDecimal.ZERO.compareTo(p.quantity))
        assertNull(p.avgCostPerUnit)
        assertEquals(0, BigDecimal("-20").compareTo(p.realized))
        // Position with zero quantity yields no unrealized P/L.
        assertNull(PortfolioCalculator.unrealized(p, BigDecimal("500")))
    }

    @Test
    fun `selling more than held is clamped`() {
        val positions = PortfolioCalculator.positions(
            listOf(
                buy("1", "100"),
                sell("5", "200"), // only 1 unit actually held
            ),
        )
        val p = positions.getValue("crypto:BTC")
        assertEquals(0, BigDecimal.ZERO.compareTo(p.quantity))
        // Realized only from the single held unit: (200-100)*1.
        assertEquals(0, BigDecimal("100").compareTo(p.realized))
    }

    @Test
    fun `multiple assets tracked separately`() {
        val positions = PortfolioCalculator.positions(
            listOf(
                buy("1", "100"),
                Transaction(
                    assetId = "crypto:ETH",
                    side = TxSide.BUY,
                    quantity = BigDecimal("10"),
                    pricePerUnit = BigDecimal("2000"),
                    timestamp = 1L,
                ),
            ),
        )
        assertEquals(2, positions.size)
        assertEquals(0, BigDecimal("20000").compareTo(positions.getValue("crypto:ETH").invested))
    }

    @Test
    fun `unrealized and current value`() {
        val positions = PortfolioCalculator.positions(listOf(buy("2", "100")))
        val p = positions.getValue("crypto:BTC")
        // Current price 130 → value 260, unrealized +60.
        assertEquals(0, BigDecimal("260").compareTo(PortfolioCalculator.currentValue(p, BigDecimal("130"))))
        assertEquals(0, BigDecimal("60").compareTo(PortfolioCalculator.unrealized(p, BigDecimal("130"))))
        assertNull(PortfolioCalculator.currentValue(p, null))
    }

    @Test
    fun `transactions sorted by timestamp before processing`() {
        val positions = PortfolioCalculator.positions(
            listOf(
                sell("1", "150", ts = 3),
                buy("1", "100", ts = 1),
                buy("1", "200", ts = 2),
            ),
        )
        val p = positions.getValue("crypto:BTC")
        // Buys first (avg 150), then sell 1 @150 → realized 0, remaining 1 @150.
        assertEquals(0, BigDecimal.ZERO.compareTo(p.realized))
        assertEquals(0, BigDecimal("1").compareTo(p.quantity))
        assertEquals(0, BigDecimal("150").compareTo(p.avgCostPerUnit))
    }

    @Test
    fun `empty transaction list yields no positions`() {
        assertTrue(PortfolioCalculator.positions(emptyList()).isEmpty())
    }
}
