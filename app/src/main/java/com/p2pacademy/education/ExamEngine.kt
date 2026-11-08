package com.p2pacademy.education

import com.p2pacademy.domain.*

/** Итоговый экзамен без подсказок: 14 заданий по всем навыкам, каждое генерируется по seed. */
class ExamEngine(private val practice: PracticeEngine = PracticeEngine()) {

    private val plan = listOf(
        PracticeType.SPREAD, PracticeType.SPREAD, PracticeType.BREAKEVEN,
        PracticeType.AD_RISK, PracticeType.LIQUIDITY,
        PracticeType.DOC_CHECK, PracticeType.DOC_CHECK, PracticeType.DOC_CHECK,
        PracticeType.STRESS, PracticeType.STRESS,
        PracticeType.CAPITAL, PracticeType.CAPITAL,
        PracticeType.DOC_CHECK, PracticeType.AD_RISK,
    )

    fun generate(seed: Long): List<Question> =
        plan.mapIndexed { i, t -> practice.generate(t, seed * 31 + i * 1013) }

    data class Outcome(val result: ExamResult, val weak: List<String>, val level: String)

    fun grade(seed: Long, questions: List<Question>, answers: List<Int>, prior: List<ExamResult>): Outcome {
        val byCat = mutableMapOf<String, Pair<Int, Int>>()
        var right = 0
        questions.forEachIndexed { i, q ->
            val ok = answers.getOrNull(i) == q.correct
            if (ok) right++
            val (a, b) = byCat[q.category] ?: (0 to 0)
            byCat[q.category] = (a + if (ok) 1 else 0) to (b + 1)
        }
        val pct = right * 100 / questions.size.coerceAtLeast(1)
        val cats = byCat.mapValues { (_, v) -> v.first * 100 / v.second.coerceAtLeast(1) }
        val passed = pct >= 80 && cats.values.all { it >= 60 }
        val res = ExamResult(seed, pct, cats, passed)
        val weak = cats.filter { it.value < 70 }.keys.toList()
        val allPassed = (prior + res).filter { it.passed }.map { it.seed }.distinct()
        val level = when {
            allPassed.size >= 2 -> "Подтверждённый уровень: устойчивые навыки показаны в двух разных экзаменах"
            passed -> "Экзамен сдан. Для подтверждённого уровня нужен ещё один успешный экзамен на другом сценарии"
            else -> "Экзамен не сдан. Повторите слабые темы и попробуйте снова"
        }
        return Outcome(res, weak, level)
    }

    fun apply(s: GameState, o: Outcome): GameState {
        var n = s.copy(examResults = s.examResults + o.result)
        for ((cat, v) in o.result.byCategory) n = n.skillAdd(cat, if (v >= 70) 3 else -2)
        return n.gain(if (o.result.passed) 120 else 20)
    }
}
