package com.p2pacademy.documents

import com.p2pacademy.domain.*
import java.math.BigDecimal
import kotlin.random.Random

class DocumentGenerator {

    private fun code(r: Random, prefix: String) = "$prefix-${r.nextInt(100000, 999999)}-${r.nextInt(10, 99)}"

    /** Чек, который присылает контрагент (может быть с «ловушкой» по сценарию). */
    fun counterpartyReceipt(s: GameState, t: Trade, trader: Trader, op: BankOp?, r: Random): Pair<GameState, Doc> {
        var sender = trader.name
        var amount = t.rub
        var status = "Выполнен"
        var tradeRef = t.id
        var at = s.clockMin
        var kind = DocKind.RECEIPT
        var opRef: Int? = op?.id
        when (t.scenario) {
            Scenario.S_NAME_MISMATCH -> sender = op?.counterparty ?: "Иванов П. К."
            Scenario.S_OLD_RECEIPT -> { tradeRef = t.id - r.nextInt(5, 40); at -= 60 * r.nextInt(5, 30) }
            Scenario.S_WRONG_DATE -> at -= 1440 * r.nextInt(2, 5)
            Scenario.S_PENDING_REJECT -> status = "Принят в обработку"
            Scenario.S_FAKE_NOTIFY -> kind = DocKind.NOTICE
            Scenario.S_CANCELLED -> status = "Выполнен"
            else -> {}
        }
        val doc = Doc(
            id = s.nextId, code = code(r, "УД"), kind = kind, amount = amount, atMin = at,
            sender = sender, receiver = "Вы", bankId = t.bankId, status = status,
            tradeRef = tradeRef, opRef = opRef, genuine = !t.scenario.fraud,
        )
        return s.copy(nextId = s.nextId + 1, docs = s.docs + doc) to doc
    }

    /** Подлинный документ банка по реальной операции. */
    fun bankDoc(s: GameState, op: BankOp, kind: DocKind, status: String, r: Random, you: String = "Вы"): Pair<GameState, Doc> {
        val sender = if (op.incoming) op.counterparty else you
        val receiver = if (op.incoming) you else op.counterparty
        val doc = Doc(
            id = s.nextId, code = code(r, "БК"), kind = kind, amount = op.amount, atMin = op.createdMin,
            sender = sender, receiver = receiver, bankId = op.bankId, status = status,
            tradeRef = op.tradeId, opRef = op.id, genuine = true,
        )
        return s.copy(nextId = s.nextId + 1, docs = s.docs + doc) to doc
    }
}
