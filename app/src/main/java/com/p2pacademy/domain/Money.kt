package com.p2pacademy.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Все денежные значения в приложении — только BigDecimal. */
object Money {
    val ZERO: BigDecimal = BigDecimal.ZERO
    val HUNDRED: BigDecimal = BigDecimal(100)

    fun rub(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)
    fun usdt(value: BigDecimal): BigDecimal = value.setScale(4, RoundingMode.HALF_UP)
    fun rate(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    fun parse(text: String): BigDecimal? =
        text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

    fun formatRub(value: BigDecimal): String = "%,.2f ₽".format(java.util.Locale("ru"), rub(value))
    fun formatUsdt(value: BigDecimal): String = "%,.2f USDT".format(java.util.Locale("ru"), usdt(value))
}
