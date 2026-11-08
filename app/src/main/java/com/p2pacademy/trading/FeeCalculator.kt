@file:UseSerializers(BigDecimalSerializer::class)

package com.p2pacademy.trading

import com.p2pacademy.domain.BigDecimalSerializer
import com.p2pacademy.domain.Money
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.math.RoundingMode

/** Настраиваемые параметры расходов (проценты, например 0.10 = 0.10%). */
@Serializable
data class FeeConfig(
    val platformPercent: BigDecimal = BigDecimal("0.10"),
    val paymentPercent: BigDecimal = BigDecimal("0.00"),
    val networkFeeUsdt: BigDecimal = BigDecimal("0.00"),
    val slippagePercent: BigDecimal = BigDecimal("0.05"),
)

data class RoundTrip(
    val invested: BigDecimal,
    val usdtBought: BigDecimal,
    val proceeds: BigDecimal,
    val grossProfit: BigDecimal,
    val totalCosts: BigDecimal,
    val netProfit: BigDecimal,
    val roiPercent: BigDecimal,
)

class FeeCalculator(private val config: FeeConfig = FeeConfig()) {

    private fun pct(base: BigDecimal, percent: BigDecimal): BigDecimal =
        base.multiply(percent).divide(Money.HUNDRED, 10, RoundingMode.HALF_UP)

    /** Суммарный процент расходов на сторону сделки. */
    fun sellCostPercent(): BigDecimal = config.platformPercent + config.slippagePercent + config.paymentPercent

    /** Сколько USDT получит покупатель за rubAmount по курсу buyRate (после комиссии площадки и проскальзывания). */
    fun usdtForBuy(rubAmount: BigDecimal, buyRate: BigDecimal): BigDecimal {
        require(buyRate.signum() > 0) { "Курс должен быть положительным" }
        val gross = rubAmount.divide(buyRate, 10, RoundingMode.HALF_UP)
        val cost = pct(gross, config.platformPercent) + pct(gross, config.slippagePercent)
        return Money.usdt((gross - cost).max(BigDecimal.ZERO))
    }

    /** Расходы при продаже на сумму sellGross (в рублях). */
    fun sellFee(sellGross: BigDecimal): BigDecimal =
        pct(sellGross, sellCostPercent()).setScale(2, RoundingMode.HALF_UP)

    /** Полный цикл: купить USDT по buyRate, продать по sellRate. */
    fun roundTrip(rubAmount: BigDecimal, buyRate: BigDecimal, sellRate: BigDecimal): RoundTrip {
        val bought = usdtForBuy(rubAmount, buyRate)
        val sellable = (bought - config.networkFeeUsdt).max(BigDecimal.ZERO)
        val sellGross = sellable.multiply(sellRate)
        val proceeds = Money.rub(sellGross - pct(sellGross, sellCostPercent()))
        val invested = Money.rub(rubAmount)
        val gross = Money.rub(sellable.multiply(sellRate) - rubAmount)
        val net = Money.rub(proceeds - invested)
        val costs = Money.rub(gross - net)
        val roi = if (invested.signum() == 0) BigDecimal.ZERO
        else net.multiply(Money.HUNDRED).divide(invested, 2, RoundingMode.HALF_UP)
        return RoundTrip(invested, bought, proceeds, gross, costs, net, roi)
    }

    /**
     * Минимальный курс продажи, при котором цикл не убыточен
     * (после покупки по buyRate на rubAmount). Ниже него продажа даёт убыток.
     */
    fun breakEvenSellRate(rubAmount: BigDecimal, buyRate: BigDecimal): BigDecimal {
        val bought = usdtForBuy(rubAmount, buyRate)
        val sellable = (bought - config.networkFeeUsdt)
        require(sellable.signum() > 0) { "Сетевая комиссия поглощает всю сумму" }
        val keep = BigDecimal.ONE - sellCostPercent().divide(Money.HUNDRED, 10, RoundingMode.HALF_UP)
        return rubAmount.divide(sellable.multiply(keep), 2, RoundingMode.UP)
    }

    /** Спред в процентах между курсом продажи и покупки (до расходов). */
    fun spreadPercent(buyRate: BigDecimal, sellRate: BigDecimal): BigDecimal =
        (sellRate - buyRate).multiply(Money.HUNDRED).divide(buyRate, 2, RoundingMode.HALF_UP)
}
