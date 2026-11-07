package com.p2pacademy.trading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FeeCalculatorTest {

    private val zeroFees = FeeConfig(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)

    @Test
    fun `spread without fees equals gross profit`() {
        val r = FeeCalculator(zeroFees).roundTrip(BigDecimal("100000"), BigDecimal("90"), BigDecimal("92"))
        // 100000/90 = 1111.1111 USDT; * 92 = 102222.21 (usdt округлён до 4 знаков)
        assertEquals(0, r.netProfit.compareTo(r.grossProfit))
        assertTrue(r.netProfit.signum() > 0)
    }

    @Test
    fun `fees reduce net profit`() {
        val r = FeeCalculator().roundTrip(BigDecimal("100000"), BigDecimal("90"), BigDecimal("90.5"))
        assertTrue(r.totalCosts.signum() > 0)
        assertTrue(r.netProfit < r.grossProfit)
    }

    @Test
    fun `selling at break-even rate is not a loss, below it is`() {
        val calc = FeeCalculator()
        val amount = BigDecimal("100000")
        val buy = BigDecimal("90")
        val be = calc.breakEvenSellRate(amount, buy)
        assertTrue(calc.roundTrip(amount, buy, be).netProfit.signum() >= 0)
        assertTrue(calc.roundTrip(amount, buy, be - BigDecimal("0.10")).netProfit.signum() < 0)
    }

    @Test
    fun `spread percent`() {
        assertEquals(BigDecimal("2.00"), FeeCalculator().spreadPercent(BigDecimal("100"), BigDecimal("102")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero rate rejected`() {
        FeeCalculator().usdtForBuy(BigDecimal("1000"), BigDecimal.ZERO)
    }
}
