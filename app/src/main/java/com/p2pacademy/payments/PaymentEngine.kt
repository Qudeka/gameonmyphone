package com.p2pacademy.payments

import com.p2pacademy.banking.BankingEngine
import com.p2pacademy.banking.Banks
import com.p2pacademy.chat.ChatEngine
import com.p2pacademy.documents.DocumentGenerator
import com.p2pacademy.domain.*
import java.math.BigDecimal

class PaymentEngine(
    private val banking: BankingEngine = BankingEngine(),
    private val docs: DocumentGenerator = DocumentGenerator(),
    private val chat: ChatEngine = ChatEngine(),
) {
    private val strangers = listOf("Иванов П. К.", "Смирнова А. Д.", "Кузнецов Р. В.", "Попова Е. С.", "Орлов Д. М.")

    /** Контрагент (покупатель) «оплачивает» заявку, где пользователь продаёт USDT. */
    fun simulateIncoming(s0: GameState, tid: Int): GameState {
        val t = s0.trades.find { it.id == tid } ?: return s0
        val trader = s0.traders.find { it.id == t.traderId } ?: return s0
        val r = rng(s0.seed, t.id.toLong(), 7L)
        var s = s0
        var op: BankOp? = null
        var amount = t.rub
        var cp = trader.name
        var final = BankStatus.CREDITED
        var hold = false
        var mult = 100
        var makeOp = true
        when (t.scenario) {
            Scenario.S_HONEST -> {}
            Scenario.S_PENDING_OK -> mult = 250
            Scenario.S_BANK_HOLD -> hold = true
            Scenario.S_FAKE_RECEIPT, Scenario.S_FAKE_NOTIFY, Scenario.S_OLD_RECEIPT, Scenario.S_WRONG_DATE -> makeOp = false
            Scenario.S_WRONG_AMOUNT, Scenario.S_SPLIT -> amount = t.rub.multiply(BigDecimal("0.6")).r2()
            Scenario.S_NAME_MISMATCH -> cp = strangers[r.nextInt(strangers.size)]
            Scenario.S_CANCELLED -> final = BankStatus.RETURNED
            Scenario.S_PENDING_REJECT -> { final = BankStatus.REJECTED; mult = 300 }
            else -> {}
        }
        if (makeOp) {
            val (s1, o) = banking.create(s, t.bankId, t.id, true, amount, BigDecimal.ZERO, cp, final, hold, mult, r)
            s = s1; op = o
            val (s2, _) = docs.bankDoc(s, o, DocKind.BANK_MSG, "Входящий перевод", r)
            s = s2
        }
        val (s3, doc) = docs.counterpartyReceipt(s, t, trader, op, r)
        s = s3
        if (t.scenario == Scenario.S_FAKE_NOTIFY) {
            s = s.note("${Banks.all[t.bankId].name}: Зачисление ${t.rub.r2().toPlainString()} ₽ (сообщение получено через чат)", t.bankId, fake = true, tradeId = t.id)
        }
        val msgs = chat.afterBuyerPaid(t, trader, s.clockMin, r)
        val tt = t.copy(
            status = TradeStatus.PAYMENT_SIMULATED,
            opId = op?.id,
            docId = doc.id,
            payAtMin = null,
            sched = t.sched + msgs.drop(1),
        ).log(s.clockMin, "Контрагент сообщил об оплате").say(s.clockMin, 2, msgs.first().text, doc.id)
        return s.withTrade(tt)
    }

    /** Пользователь оплачивает заявку на покупку USDT. */
    fun userPay(s0: GameState, tid: Int): Res {
        val t = s0.trades.find { it.id == tid } ?: return Res(s0, "Заявка не найдена", false)
        if (t.side != Side.BUY || t.status != TradeStatus.WAITING_FOR_PAYMENT)
            return Res(s0, "Оплата сейчас недоступна", false)
        val trader = s0.traders.find { it.id == t.traderId } ?: return Res(s0, "Контрагент не найден", false)
        val bank = Banks.all[t.bankId]
        val fee = banking.feeFor(bank, t.rub)
        val total = t.rub + fee
        if (total > bank.limit) return Res(s0, "Превышен лимит банка ${bank.name}: ${bank.limit.toPlainString()} ₽", false)
        if (s0.bankBalances[t.bankId] < total) return Res(s0, "Недостаточно средств в ${bank.name}", false)
        val r = rng(s0.seed, t.id.toLong(), 9L)
        val final = if (t.scenario == Scenario.B_REJECTED) BankStatus.REJECTED else BankStatus.CREDITED
        val forceHold = t.scenario == Scenario.B_BANK_HOLD
        val mult = if (t.scenario == Scenario.B_SLOW) 180 else 100
        var s = s0.addBank(t.bankId, total.negate())
        val (s1, op) = banking.create(s, t.bankId, t.id, false, t.rub, fee, trader.name, final, forceHold, mult, r)
        s = s1
        val (s2, doc) = docs.bankDoc(s, op, DocKind.CONFIRM, "Перевод создан", r, "Вы")
        s = s2
        val delay = (trader.avgMinutes / 2).coerceAtLeast(1) + r.nextInt(0, trader.avgMinutes.coerceAtLeast(2))
        val releaseAt = when (t.scenario) {
            Scenario.B_HONEST, Scenario.B_BANK_HOLD -> op.tFinal + delay
            Scenario.B_SLOW -> op.tFinal + 30 + delay
            else -> null
        }
        val msgs = chat.afterUserPaid(t, trader, op, r)
        val tt = t.copy(
            status = TradeStatus.PAYMENT_SIMULATED, opId = op.id, docId = doc.id,
            bankFee = fee, paidRub = total, releaseAtMin = releaseAt,
            sched = t.sched + msgs, payAtMin = null,
        ).log(s.clockMin, "Вы отправили ${t.rub.r2().toPlainString()} ₽ через ${bank.name}")
            .say(s.clockMin, 1, "Оплатил(а) через ${bank.name}.")
        return Res(s.withTrade(tt), "Перевод отправлен. Отправка — ещё не зачисление: дождитесь статуса «Зачислен».", true, t.id)
    }
}
