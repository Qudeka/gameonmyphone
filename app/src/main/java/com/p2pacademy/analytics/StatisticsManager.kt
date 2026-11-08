package com.p2pacademy.analytics

import com.p2pacademy.domain.*
import java.math.BigDecimal
import java.math.RoundingMode

data class Stats(
    val total: Int,
    val successful: Int,
    val cancelled: Int,
    val disputes: Int,
    val profit: BigDecimal,
    val losses: BigDecimal,
    val fees: BigDecimal,
    val avgReturn: BigDecimal,
    val maxProfit: BigDecimal,
    val maxLoss: BigDecimal,
    val fraudAccuracy: Int,
    val literacy: Int,
    val successPct: Int,
)

data class Achievement(val id: String, val title: String, val desc: String, val test: (GameState) -> Boolean)

class StatisticsManager {

    val achievements: List<Achievement> = listOf(
        Achievement("first", "Первая сделка", "Завершите первую сделку") { it.trades.any { t -> t.success } },
        Achievement("profit", "Первая прибыль", "Получите положительную реализованную прибыль") { it.realized.signum() > 0 },
        Achievement("ten", "Десять сделок", "Завершите 10 успешных сделок") { it.trades.count { t -> t.success } >= 10 },
        Achievement("hunter", "Охотник за обманом", "Распознайте 3 мошеннические сделки") { it.fraudCaught >= 3 },
        Achievement("skeptic", "Скептик", "Распознайте 10 мошеннических сделок") { it.fraudCaught >= 10 },
        Achievement("student", "Студент", "Пройдите 5 уроков") { it.lessonsDone.size >= 5 },
        Achievement("graduate", "Выпускник", "Пройдите все уроки академии") { it.lessonsDone.size >= Lessons15 },
        Achievement("detective", "Детектив", "Решите 5 расследований") { it.casesDone.size >= 5 },
        Achievement("capital", "Капитал растёт", "Достигните капитала 110 000 ₽") { it.equity >= BigDecimal("110000") },
        Achievement("days", "Неделя на рынке", "Проведите 7 торговых дней") { it.day >= 7 },
        Achievement("exam", "Экзамен сдан", "Сдайте итоговый экзамен") { it.examResults.any { r -> r.passed } },
        Achievement("pro", "Подтверждённый уровень", "Сдайте два разных экзамена") { it.examResults.filter { r -> r.passed }.map { r -> r.seed }.distinct().size >= 2 },
    )

    fun compute(s: GameState): Stats {
        val done = s.trades.filter { it.status == TradeStatus.COMPLETED || it.status == TradeStatus.REFUNDED || it.status == TradeStatus.CANCELLED }
        val completed = s.trades.filter { it.status == TradeStatus.COMPLETED }
        val sells = completed.filter { it.side == Side.SELL || it.fraudLoss }
        val profit = sells.filter { it.pnl.signum() > 0 }.fold(ZERO) { a, t -> a + t.pnl }
        val losses = s.trades.filter { it.pnl.signum() < 0 }.fold(ZERO) { a, t -> a + t.pnl.abs() }
        val fees = completed.fold(ZERO) { a, t -> a + t.feeRub + t.bankFee }
        val successful = s.trades.count { it.success }
        val total = s.trades.size
        val avgRet = if (s.initial.signum() == 0) ZERO
        else (s.equity - s.initial).multiply(HUNDRED).divide(s.initial, 2, RoundingMode.HALF_UP)
        val maxP = s.trades.maxOfOrNull { it.pnl } ?: ZERO
        val maxL = s.trades.minOfOrNull { it.pnl } ?: ZERO
        val fraudTotal = s.fraudCaught + s.fraudMissed
        val acc = if (fraudTotal == 0) 100 else s.fraudCaught * 100 / fraudTotal
        val skills = Skill.all.map { s.skill(it) }
        val literacy = skills.sum() / skills.size
        return Stats(
            total, successful, s.trades.count { it.status == TradeStatus.CANCELLED },
            s.trades.count { it.disputed }, profit, losses, fees, avgRet, maxP.max(ZERO), maxL.min(ZERO),
            acc, literacy, if (total == 0) 0 else successful * 100 / total,
        )
    }

    fun unlock(s: GameState): GameState {
        val got = achievements.filter { it.id !in s.achievements && it.test(s) }
        if (got.isEmpty()) return s
        var n = s.copy(achievements = s.achievements + got.map { it.id }).gain(got.size * 40)
        got.forEach { n = n.note("Достижение: ${it.title}") }
        return n
    }

    private val Lessons15 = 15
}
