package com.p2pacademy.education

import com.p2pacademy.domain.*
import com.p2pacademy.market.TraderGenerator
import com.p2pacademy.trading.FeeCalculator
import com.p2pacademy.trading.FeeConfig
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

data class Question(
    val category: String,
    val text: String,
    val options: List<String>,
    val correct: Int,
    val explain: String,
)

enum class PracticeType(val title: String, val skill: String) {
    SPREAD("Прибыль после расходов", Skill.CALC),
    BREAKEVEN("Курс безубыточности", Skill.CALC),
    AD_RISK("Рискованное объявление", Skill.MARKET),
    LIQUIDITY("Ликвидность и резерв", Skill.MARKET),
    DOC_CHECK("Проверка платежа", Skill.VERIFY),
    STRESS("Давление контрагента", Skill.STRESS),
    CAPITAL("Управление капиталом", Skill.CAPITAL),
}

/** Генерирует задания с вычисляемыми ответами (не заготовленные данные). */
class PracticeEngine {
    private val calc = FeeCalculator(FeeConfig())

    private fun shuffled(correct: String, wrong: List<String>, r: Random): Pair<List<String>, Int> {
        val all = (listOf(correct) + wrong.filter { it != correct }.distinct().take(3)).toMutableList()
        while (all.size < 4) all += "Ни один из вариантов"
        val order = all.indices.shuffled(r)
        val opts = order.map { all[it] }
        return opts to opts.indexOf(correct)
    }

    fun generate(type: PracticeType, seed: Long): Question {
        val r = rng(seed, type.ordinal.toLong(), 77L)
        return when (type) {
            PracticeType.SPREAD -> spread(r)
            PracticeType.BREAKEVEN -> breakeven(r)
            PracticeType.AD_RISK -> adRisk(r)
            PracticeType.LIQUIDITY -> liquidity(r)
            PracticeType.DOC_CHECK -> docCheck(r)
            PracticeType.STRESS -> stress(r)
            PracticeType.CAPITAL -> capital(r)
        }
    }

    private fun spread(r: Random): Question {
        val buy = BigDecimal(r.nextInt(8900, 9400)).movePointLeft(2)
        val sell = buy + BigDecimal(r.nextInt(60, 220)).movePointLeft(2)
        val amount = BigDecimal(r.nextInt(3, 20) * 10000)
        val rt = calc.roundTrip(amount, buy, sell)
        val ok = rt.netProfit.toPlainString() + " ₽"
        val wrong = listOf(rt.grossProfit.toPlainString() + " ₽", (rt.netProfit.negate()).toPlainString() + " ₽",
            (rt.netProfit + BigDecimal("150")).r2().toPlainString() + " ₽", (rt.grossProfit * BigDecimal("1.2")).r2().toPlainString() + " ₽")
        val (opts, idx) = shuffled(ok, wrong, r)
        return Question(Skill.CALC, "Вы покупаете USDT на ${amount.toPlainString()} ₽ по ${buy.toPlainString()} и продаёте по ${sell.toPlainString()}. " +
            "Комиссия площадки 0,10% и проскальзывание 0,05% на каждую сторону (по покупке — из USDT, по продаже — из рублей). Чистая прибыль?",
            opts, idx, "Валовая прибыль ${rt.grossProfit.toPlainString()} ₽, расходы ${rt.totalCosts.toPlainString()} ₽, чистая ${rt.netProfit.toPlainString()} ₽. Расходы нужно вычитать всегда.")
    }

    private fun breakeven(r: Random): Question {
        val buy = BigDecimal(r.nextInt(9000, 9400)).movePointLeft(2)
        val amount = BigDecimal(50000)
        val be = calc.breakEvenSellRate(amount, buy)
        val ok = be.toPlainString()
        val wrong = listOf(buy.toPlainString(), (buy + BigDecimal("0.05")).toPlainString(), (be + BigDecimal("1.50")).r2().toPlainString(), (be - BigDecimal("0.30")).r2().toPlainString())
        val (opts, idx) = shuffled(ok, wrong, r)
        return Question(Skill.CALC, "Вы купили USDT на 50 000 ₽ по $buy. При каком минимальном курсе продажа не убыточна (расходы как в предыдущем задании)?",
            opts, idx, "Нужно покрыть расходы обеих сторон: безубыточный курс $be, а не просто цена покупки.")
    }

    private fun adRisk(r: Random): Question {
        val g = TraderGenerator()
        val a = g.generate(1, TraderType.EXP_SELLER, r)
        val b = g.generate(2, TraderType.BIG, r)
        val c = g.generate(3, TraderType.SUSPICIOUS, r).copy(ageDays = r.nextInt(5, 25), trades = r.nextInt(10, 40), successPct = r.nextInt(97, 100), disputes = r.nextInt(5, 9))
        val list = listOf(a to BigDecimal("92.40"), b to BigDecimal("92.55"), c to BigDecimal("90.80"))
        val order = list.indices.shuffled(r)
        val lines = order.map { i ->
            val (t, rate) = list[i]
            "${t.name}: курс $rate, аккаунт ${t.ageDays} дн., сделок ${t.trades}, успешных ${t.successPct}%, споров ${t.disputes}"
        }
        val correct = order.indexOf(2)
        return Question(Skill.MARKET, "Рыночный курс продажи ≈ 92.50. Какое объявление самое рискованное?", lines, correct,
            "Курс заметно ниже рынка, аккаунт новый, мало сделок и много споров. Слишком выгодная цена и красивый процент при малой истории — повод насторожиться.")
    }

    private fun liquidity(r: Random): Question {
        val reserve = BigDecimal(r.nextInt(5, 20) * 100)
        val rate = BigDecimal(r.nextInt(9000, 9400)).movePointLeft(2)
        val maxRub = (reserve * rate).r2()
        val want = (maxRub * BigDecimal("1.4")).setScale(0, RoundingMode.HALF_UP)
        val ok = "Не больше ${maxRub.toPlainString()} ₽"
        val wrong = listOf("Любую сумму в лимитах", "Не больше ${want.toPlainString()} ₽", "Не больше ${(maxRub / BigDecimal(2)).r2().toPlainString()} ₽")
        val (opts, idx) = shuffled(ok, wrong, r)
        return Question(Skill.MARKET, "У продавца резерв $reserve USDT при курсе $rate. Вы хотите купить на ${want.toPlainString()} ₽. Какую максимальную сумму позволит резерв?",
            opts, idx, "Резерв ограничивает объём: $reserve × $rate = ${maxRub.toPlainString()} ₽. Сделку сверх резерва создать нельзя.")
    }

    private fun docCheck(r: Random): Question {
        val cases = listOf(
            Triple("Чек: «Выполнен», сумма 30 000 ₽. История вашего банка: входящих операций за сегодня нет.", "Не отпускать, открыть спор", "Чек не доказывает зачисление. В банке нет входящей операции."),
            Triple("Чек: 30 000 ₽. Банк: «Зачислен» 18 000 ₽ от того же имени. Покупатель просит отпустить: «остаток позже».", "Не отпускать, открыть спор", "Зачислена только часть суммы. Отпускать можно при полной оплате."),
            Triple("Чек: «Выполнен». Банк: «Зачислен» полная сумма, но отправитель — другое лицо, не покупатель.", "Не принимать платёж третьего лица, открыть спор", "Платёж от третьих лиц — риск отзыва платежа и признак схемы."),
            Triple("Чек: «Принят в обработку». Банк: «Ожидает зачисления», прошло 2 минуты.", "Подождать финального статуса", "«Ожидает зачисления» — не окончательный статус."),
            Triple("Банк: «Зачислен» полная сумма, отправитель совпадает с контрагентом, чек соответствует операции.", "Отпустить USDT", "Все проверки пройдены: сумма, имя, статус, ID операции."),
        )
        val (text, ans, why) = cases[r.nextInt(cases.size)]
        val all = listOf("Отпустить USDT", "Подождать финального статуса", "Не отпускать, открыть спор", "Не принимать платёж третьего лица, открыть спор", "Не отпускать, открыть спор")
            .distinct()
        val opts = (listOf(ans) + all.filter { it != ans }).take(4)
        val order = opts.indices.shuffled(r)
        val fin = order.map { opts[it] }
        return Question(Skill.VERIFY, text + " Что делать?", fin, fin.indexOf(ans), why)
    }

    private fun stress(r: Random): Question {
        val msgs = listOf(
            "«Я спешу! Отпускай сейчас, иначе напишу жалобу!»" to "Спокойно сверить банк и не отпускать без зачисления",
            "«Переведи ещё раз, первый платёж потерялся, потом верну.»" to "Не переводить повторно, открыть спор",
            "«Давай в Telegram, тут всё медленно.»" to "Остаться в чате площадки",
        )
        val (m, ok) = msgs[r.nextInt(msgs.size)]
        val wrong = listOf("Сделать как просит, чтобы не терять время", "Отменить заявку после оплаты", "Перейти в другой мессенджер")
        val (opts, idx) = shuffled(ok, wrong, r)
        return Question(Skill.STRESS, "Контрагент пишет: $m Ваши действия?", opts, idx,
            "Давление и спешка — частый приём. Действуйте по проверенным фактам: банк, документы, правила площадки.")
    }

    private fun capital(r: Random): Question {
        val cap = BigDecimal(r.nextInt(8, 20) * 10000)
        val pct = r.nextInt(10, 26)
        val ok = (cap * BigDecimal(pct) / HUNDRED).setScale(0, RoundingMode.HALF_UP)
        val wrong = listOf(cap.toPlainString(), (ok * BigDecimal(2)).toPlainString(), (cap / BigDecimal(2)).toPlainString())
        val (opts, idx) = shuffled(ok.toPlainString() + " ₽", wrong.map { "$it ₽" }, r)
        return Question(Skill.CAPITAL, "Капитал ${cap.toPlainString()} ₽. Правило: в одну сделку не больше $pct% капитала. Максимальный размер сделки?",
            opts, idx, "$cap × $pct% = ${ok.toPlainString()} ₽. Ограничение размера сделки защищает капитал от одной ошибки.")
    }
}
