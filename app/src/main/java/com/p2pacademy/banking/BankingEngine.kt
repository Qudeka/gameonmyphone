package com.p2pacademy.banking

import com.p2pacademy.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/** Вымышленные банки. Все названия и правила — учебные. */
data class BankSpec(
    val id: Int,
    val name: String,
    val tagline: String,
    val speed: Int,          // минут до зачисления
    val feeBp: Int,          // комиссия за исходящий перевод, б.п.
    val limit: BigDecimal,   // лимит на одну операцию
    val holdPct: Int,        // шанс дополнительной проверки, %
    val color: Long,
)

object Banks {
    val all: List<BankSpec> = listOf(
        BankSpec(0, "Нордвик Банк", "Быстро и без лишнего", 4, 0, BigDecimal("300000"), 3, 0xFF2E86DE),
        BankSpec(1, "Вега Кредит", "Мгновенные платежи", 3, 50, BigDecimal("150000"), 6, 0xFF8E44AD),
        BankSpec(2, "Ольха Финанс", "Надёжно как дерево", 8, 0, BigDecimal("500000"), 4, 0xFF27AE60),
        BankSpec(3, "ТандемПей", "Платите вдвоём", 6, 30, BigDecimal("100000"), 10, 0xFFE67E22),
        BankSpec(4, "Горизонт-М", "Дальше видишь — больше можешь", 12, 0, BigDecimal("1000000"), 5, 0xFF16A085),
        BankSpec(5, "Кедр Банк", "Сибирское спокойствие", 15, 0, BigDecimal("700000"), 2, 0xFF7F8C8D),
        BankSpec(6, "Лайма Банк", "Зелёные ставки", 5, 100, BigDecimal("80000"), 14, 0xFFC0392B),
    )
}

class BankingEngine {

    fun feeFor(bank: BankSpec, amount: BigDecimal): BigDecimal =
        amount.multiply(BigDecimal(bank.feeBp)).divide(BigDecimal(10000), 2, RoundingMode.HALF_UP)

    fun statusAt(op: BankOp, clock: Int): BankStatus = when {
        clock < op.createdMin + 1 -> BankStatus.CREATED
        clock < op.tSent -> BankStatus.PROCESSING
        clock < op.tPending -> BankStatus.SENT
        clock < op.tFinal -> if (op.hold) BankStatus.REVIEW else BankStatus.PENDING_CREDIT
        else -> op.finalStatus
    }

    fun isFinal(op: BankOp, clock: Int): Boolean = clock >= op.tFinal

    fun outageActive(s: GameState, bankId: Int): Boolean =
        s.events.any { it.bankId == bankId && s.clockMin < it.untilMin }

    /** Создаёт операцию. speedMult — процент от стандартной скорости банка. */
    fun create(
        s: GameState,
        bankId: Int,
        tradeId: Int,
        incoming: Boolean,
        amount: BigDecimal,
        fee: BigDecimal,
        counterparty: String,
        finalStatus: BankStatus,
        forceHold: Boolean,
        speedMult: Int,
        r: Random,
        note: String = "",
    ): Pair<GameState, BankOp> {
        val bank = Banks.all[bankId]
        val outage = outageActive(s, bankId)
        val speed = (bank.speed * speedMult / 100) * (if (outage) 3 else 1)
        val hold = forceHold || outage || r.nextInt(100) < bank.holdPct
        val c = s.clockMin
        val tFinal = c + speed.coerceAtLeast(3) + (if (hold) r.nextInt(20, 41) else 0)
        val tSent = if (finalStatus == BankStatus.REJECTED) tFinal else c + (speed / 3).coerceAtLeast(2)
        val tPending = if (finalStatus == BankStatus.REJECTED) tFinal else c + (speed * 2 / 3).coerceAtLeast(3)
        val op = BankOp(
            id = s.nextId, bankId = bankId, tradeId = tradeId, incoming = incoming,
            amount = amount, fee = fee, counterparty = counterparty, createdMin = c,
            tSent = tSent, tPending = tPending, tFinal = tFinal, hold = hold,
            finalStatus = finalStatus, note = note,
        )
        return s.copy(nextId = s.nextId + 1, ops = s.ops + op) to op
    }

    /** Применяет финальные результаты операций к балансам. */
    fun applyDue(s0: GameState): GameState {
        var s = s0
        for (op in s0.ops) {
            if (op.applied || s.clockMin < op.tFinal) continue
            val bank = Banks.all[op.bankId]
            var updated = op.copy(applied = true)
            if (op.incoming && op.finalStatus == BankStatus.CREDITED && !op.reversed) {
                s = s.addBank(op.bankId, op.amount)
                s = s.note("${bank.name}: зачислено ${op.amount.r2().toPlainString()} ₽ от ${op.counterparty}", op.bankId)
            } else if (!op.incoming && (op.finalStatus == BankStatus.REJECTED || op.finalStatus == BankStatus.RETURNED)) {
                s = s.addBank(op.bankId, op.amount + op.fee)
                s = s.note("${bank.name}: перевод ${op.amount.r2().toPlainString()} ₽ ${op.finalStatus.title.lowercase()}, деньги возвращены", op.bankId)
            } else if (!op.incoming) {
                s = s.note("${bank.name}: перевод ${op.amount.r2().toPlainString()} ₽ зачислен получателю", op.bankId)
            }
            s = s.withOp(updated)
        }
        return s
    }

    /** Отмена зачисления (возврат платежа банком / арбитраж). */
    fun reverse(s: GameState, op: BankOp): GameState {
        if (op.reversed) return s
        var r = s
        if (op.applied && op.incoming && op.finalStatus == BankStatus.CREDITED) {
            r = r.takeRub(op.bankId, op.amount)
        }
        return r.withOp(op.copy(reversed = true, applied = true))
    }
}
