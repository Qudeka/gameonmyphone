package com.p2pacademy.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/** Детерминированный генератор: один и тот же набор ключей даёт одинаковую последовательность. */
fun rng(vararg k: Long): Random {
    var h = 1125899906842597L
    for (x in k) h = 31L * h + x
    return Random(h)
}

fun hhmm(min: Int): String = "д%d %02d:%02d".format(min / 1440 + 1, (min % 1440) / 60, min % 60)

val ZERO: BigDecimal = BigDecimal.ZERO
val HUNDRED: BigDecimal = BigDecimal(100)

fun BigDecimal.r2(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
fun BigDecimal.r4(): BigDecimal = setScale(4, RoundingMode.HALF_UP)
fun bd(i: Int): BigDecimal = BigDecimal(i)

fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]
