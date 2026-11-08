package com.p2pacademy.disputes

import com.p2pacademy.banking.BankingEngine
import com.p2pacademy.domain.*
import com.p2pacademy.fraud.FraudDetector

enum class DisputeResult(val title: String) {
    BUYER_WINS("В пользу покупателя"),
    SELLER_WINS("В пользу продавца"),
    ASSETS_RETURNED("Возврат виртуальных активов"),
    CONTINUE("Продолжение проверки"),
    INSUFFICIENT("Недостаточно доказательств"),
}

data class DisputeDecision(val result: DisputeResult, val text: String, val evidence: List<String>)

/** Решение зависит только от доказательств (банк, документы, статус эскроу), не от случайности. */
class DisputeEngine(
    private val banking: BankingEngine = BankingEngine(),
    private val fraud: FraudDetector = FraudDetector(),
) {
    fun decide(s: GameState, t: Trade): DisputeDecision {
        val op = t.opId?.let { id -> s.ops.find { it.id == id } }
        val status = op?.let { banking.statusAt(it, s.clockMin) }
        val ev = mutableListOf<String>()
        ev += "Эскроу: актив заморожен до решения (${t.usdt.r4().toPlainString()} USDT)"
        ev += if (op == null) "Банковская операция: не найдена" else "Банковская операция №${op.id}: «${status?.title}», ${op.amount.r2().toPlainString()} ₽, ${op.counterparty}"
        t.docId?.let { id -> s.docs.find { it.id == id } }?.let { ev += "Документ ${it.code}: статус «${it.status}», ${it.amount.r2().toPlainString()} ₽" }

        if (t.side == Side.SELL) {
            val chk = fraud.sellCheck(s, t)
            return when {
                chk.paidOk -> DisputeDecision(DisputeResult.BUYER_WINS, "Оплата подтверждена банком: сумма и отправитель совпадают. Актив передаётся покупателю.", ev)
                op == null || status == BankStatus.REJECTED || status == BankStatus.RETURNED ->
                    DisputeDecision(DisputeResult.SELLER_WINS, "Банк не подтверждает поступление. Актив возвращается продавцу.", ev)
                status == BankStatus.CREDITED ->
                    DisputeDecision(DisputeResult.ASSETS_RETURNED, "Платёж не соответствует условиям: ${chk.flags.joinToString("; ")}. Платёж возвращается, актив возвращается.", ev)
                else -> DisputeDecision(DisputeResult.CONTINUE, "Банковская операция ещё не завершена. Арбитраж ждёт финального статуса.", ev)
            }
        }
        // Пользователь — покупатель
        return when (status) {
            BankStatus.CREDITED -> DisputeDecision(DisputeResult.BUYER_WINS, "Банк подтверждает зачисление платежа продавцу. Актив передаётся вам.", ev)
            BankStatus.REJECTED, BankStatus.RETURNED -> DisputeDecision(DisputeResult.ASSETS_RETURNED, "Перевод не прошёл, деньги возвращены на счёт. Заявка закрыта.", ev)
            else -> DisputeDecision(DisputeResult.CONTINUE, "Перевод ещё не завершён. Арбитраж ждёт финального статуса.", ev)
        }
    }
}
