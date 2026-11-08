package com.p2pacademy.fraud

import com.p2pacademy.banking.BankingEngine
import com.p2pacademy.domain.*

data class SellCheck(
    val paidOk: Boolean,       // можно ли сейчас безопасно отпускать
    val finalOk: Boolean,      // чем закончится в итоге (для расчёта последствий)
    val status: BankStatus?,
    val flags: List<String>,
)

data class ScenarioInfo(val flags: List<String>, val correct: String, val consequence: String, val explanation: String)

enum class Risk(val title: String) { LOW("Низкий"), MEDIUM("Средний"), HIGH("Высокий") }

class RiskEngine {
    /** Оценка риска объявления: баллы и причины. */
    fun adRisk(trader: Trader, ad: Ad, mid: java.math.BigDecimal): Pair<Risk, List<String>> {
        var score = 0
        val why = mutableListOf<String>()
        if (trader.ageDays < 30) { score += 25; why += "Аккаунт младше 30 дней" }
        if (trader.trades < 50) { score += 15; why += "Мало сделок (${trader.trades})" }
        if (trader.successPct < 90) { score += 20; why += "Успешных сделок только ${trader.successPct}%" }
        if (trader.disputes > 5) { score += 15; why += "Много споров (${trader.disputes})" }
        if (trader.reputation < 50) { score += 15; why += "Низкая репутация (${trader.reputation})" }
        val tooGood = if (ad.side == Side.BUY) ad.rate < mid.multiply(java.math.BigDecimal("0.994"))
        else ad.rate > mid.multiply(java.math.BigDecimal("1.006"))
        if (tooGood) { score += 25; why += "Курс заметно выгоднее рынка — повод насторожиться" }
        val lvl = if (score >= 55) Risk.HIGH else if (score >= 30) Risk.MEDIUM else Risk.LOW
        return lvl to why
    }
}

class FraudDetector(private val banking: BankingEngine = BankingEngine()) {

    fun sellCheck(s: GameState, t: Trade): SellCheck {
        val trader = s.traders.find { it.id == t.traderId }
        val op = t.opId?.let { id -> s.ops.find { it.id == id } }
        val doc = t.docId?.let { id -> s.docs.find { it.id == id } }
        val status = op?.let { banking.statusAt(it, s.clockMin) }
        val flags = mutableListOf<String>()
        if (op == null) flags += "В истории банка нет входящей операции по этой сделке"
        else {
            if (status != BankStatus.CREDITED) flags += "Статус операции в банке: «${status?.title}» — деньги ещё не зачислены окончательно"
            if (op.amount < t.rub) flags += "Зачислено ${op.amount.r2().toPlainString()} ₽, а ожидалось ${t.rub.r2().toPlainString()} ₽"
            if (trader != null && op.counterparty != trader.name) flags += "Отправитель в банке («${op.counterparty}») не совпадает с контрагентом («${trader.name}»)"
        }
        if (doc != null) {
            if (doc.tradeRef != t.id) flags += "На чеке указана другая операция (№${doc.tradeRef})"
            if (doc.atMin / 1440 != t.createdMin / 1440 || doc.atMin < t.createdMin - 10) flags += "Дата чека (${hhmm(doc.atMin)}) не совпадает со временем сделки"
            if (doc.status != "Выполнен") flags += "Статус на чеке «${doc.status}» не является окончательным"
            if (doc.kind == DocKind.NOTICE) flags += "Это «уведомление» из чата, а не данные банка"
        }
        val paidOk = op != null && status == BankStatus.CREDITED && op.amount >= t.rub &&
            trader != null && op.counterparty == trader.name
        val finalOk = op != null && op.finalStatus == BankStatus.CREDITED && op.amount >= t.rub &&
            trader != null && op.counterparty == trader.name
        return SellCheck(paidOk, finalOk, status, flags)
    }

    fun info(sc: Scenario): ScenarioInfo = when (sc) {
        Scenario.S_HONEST, Scenario.S_PENDING_OK, Scenario.S_BANK_HOLD -> ScenarioInfo(
            emptyList(),
            "Дождаться статуса «Зачислен» в банке и сверить сумму и имя отправителя, затем отпустить USDT.",
            "При проверке и терпении сделка завершается без потерь.",
            "Платёж был настоящим, но банк мог обрабатывать его дольше. Главное — опираться на историю банка, а не на слова.",
        )
        Scenario.S_NO_SHOW -> ScenarioInfo(emptyList(), "Дождаться окончания времени и не отпускать актив.", "Заявка отменяется, актив возвращается.", "Если оплаты нет, ничего отпускать не нужно.")
        Scenario.S_FAKE_RECEIPT -> ScenarioInfo(
            listOf("Чек есть, но в истории банка нет входящей операции", "Давление: «отпускайте скорее»"),
            "Не отпускать актив и открыть спор.", "Потеря всего актива сделки.",
            "Чек — картинка. Деньги подтверждает только история вашего банка.",
        )
        Scenario.S_FAKE_NOTIFY -> ScenarioInfo(
            listOf("Уведомление пришло из чата, а не от банка", "В истории банка нет зачисления"),
            "Игнорировать «уведомление», проверить историю банка, открыть спор.", "Потеря актива.",
            "Сообщения из чата и скриншоты не доказывают зачисление.",
        )
        Scenario.S_WRONG_AMOUNT -> ScenarioInfo(
            listOf("Сумма зачисления меньше суммы сделки", "Объяснение про «комиссию банка» неубедительно"),
            "Не отпускать актив, открыть спор: возврат частичной суммы и активов.", "Потеря разницы между актив и пришедшей суммой.",
            "Всегда сверяйте сумму в банке с суммой сделки до копейки.",
        )
        Scenario.S_SPLIT -> ScenarioInfo(
            listOf("Платёж пришёл частью суммы", "«Остаток позже» — классический приём"),
            "Не отпускать актив до полной суммы, открыть спор.", "Потеря актива при частичной оплате.",
            "Частичная оплата — не оплата. Отпускать можно только при полном зачислении.",
        )
        Scenario.S_NAME_MISMATCH -> ScenarioInfo(
            listOf("Имя отправителя не совпадает с контрагентом", "Платёж от третьего лица"),
            "Не принимать платёж от третьих лиц, открыть спор для возврата.", "Банк может отозвать платёж, актив потерян.",
            "Платежи от третьих лиц — частый признак мошеннических схем и источник блокировок.",
        )
        Scenario.S_OLD_RECEIPT -> ScenarioInfo(
            listOf("Номер операции на чеке не относится к вашей сделке", "Дата чека раньше сделки"),
            "Не отпускать актив, открыть спор.", "Потеря актива.",
            "Чек от другой операции — подмена. Сверяйте ID и дату.",
        )
        Scenario.S_WRONG_DATE -> ScenarioInfo(
            listOf("Дата на чеке не совпадает со временем сделки", "В банке нет поступления"),
            "Не отпускать актив, открыть спор.", "Потеря актива.",
            "Старый чек выдают за новый. Дата и время должны совпадать.",
        )
        Scenario.S_CANCELLED -> ScenarioInfo(
            listOf("Операция в банке закончилась статусом «Возвращён»", "Чек остался «Выполнен»"),
            "Дождаться финального статуса; при возврате — не отпускать, открыть спор.", "Потеря актива при преждевременном отпускании.",
            "Перевод можно отменить после отправки. Нужен финальный статус «Зачислен».",
        )
        Scenario.S_PENDING_REJECT -> ScenarioInfo(
            listOf("На чеке «Принят в обработку» — статус не окончательный", "В банке платёж долго не зачисляется и в итоге отклонён"),
            "Ждать окончательного статуса в банке; не отпускать до «Зачислен».", "Потеря актива при преждевременном отпускании.",
            "«В обработке» — не «зачислено». Финальный результат может быть отказом.",
        )
        Scenario.B_HONEST, Scenario.B_SLOW, Scenario.B_BANK_HOLD -> ScenarioInfo(
            emptyList(), "Отправить перевод, дождаться «Зачислен» и подождать подтверждения продавца.", "Сделка завершается без потерь.",
            "Продавец проверяет поступление в своём банке — это занимает время.",
        )
        Scenario.B_REJECTED -> ScenarioInfo(emptyList(), "Дождаться результата банка: деньги вернутся.", "Потерь нет.", "Банк отклонил перевод — деньги вернулись на счёт, заявка закрыта.")
        Scenario.B_NO_RELEASE -> ScenarioInfo(
            listOf("Банк показывает, что платёж зачислен получателю", "Продавец утверждает, что денег нет, и не отпускает актив"),
            "Открыть спор и приложить банковские подтверждения.", "Если не защищаться — потеря денег.",
            "У вас есть доказательства зачисления. Арбитраж решает по ним.",
        )
        Scenario.B_REPEAT -> ScenarioInfo(
            listOf("Просьба повторить перевод, пока первый зачислен", "Давление временем"),
            "Не переводить повторно, открыть спор.", "Двойная потеря денег при повторном переводе.",
            "Повторный платёж — классическая ловушка. Первый перевод уже зачислен.",
        )
        Scenario.B_ASK_CANCEL -> ScenarioInfo(
            listOf("Просьба отменить заявку после оплаты", "«Верну деньги сам» вне системы"),
            "Не отменять заявку, открыть спор.", "Отмена после оплаты закрывает заявку, деньги теряются.",
            "После оплаты возврат идёт только через арбитраж.",
        )
        Scenario.B_OFF_PLATFORM -> ScenarioInfo(
            listOf("Просьба перейти в Telegram", "Продавец не отпускает актив на площадке"),
            "Оставаться в чате площадки, открыть спор.", "Вне площадки арбитраж не поможет.",
            "Уход из чата лишает вас доказательств и защиты.",
        )
    }

    fun report(s: GameState, t: Trade, decision: String, ok: Boolean): List<String> {
        val inf = info(t.scenario)
        val out = mutableListOf("Ваше решение: $decision")
        if (inf.flags.isNotEmpty()) {
            out += "Красные флаги:"
            inf.flags.forEach { out += "• $it" }
            if (!ok) {
                val extra = if (t.side == Side.SELL) sellCheck(s, t).flags else emptyList()
                if (extra.isNotEmpty()) { out += "Пропущенные признаки:"; extra.forEach { out += "• $it" } }
            }
        } else {
            out += "Красных флагов в этой сделке не было."
        }
        out += "Правильное решение: ${inf.correct}"
        out += "Возможные последствия: ${inf.consequence}"
        out += "Объяснение: ${inf.explanation}"
        return out
    }
}
