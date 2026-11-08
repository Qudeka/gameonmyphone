package com.p2pacademy.trading

import com.p2pacademy.banking.BankingEngine
import com.p2pacademy.banking.Banks
import com.p2pacademy.chat.ChatEngine
import com.p2pacademy.chat.Quick
import com.p2pacademy.disputes.DisputeEngine
import com.p2pacademy.disputes.DisputeResult
import com.p2pacademy.domain.*
import com.p2pacademy.fraud.FraudDetector
import com.p2pacademy.market.MarketEngine
import com.p2pacademy.payments.PaymentEngine
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

const val PAY_WINDOW_MIN = 30

/** Подбор сценария сделки по типу контрагента и сложности. */
class ScenarioPicker {
    private fun baseFraudPct(type: TraderType) = when (type) {
        TraderType.EXP_SELLER, TraderType.EXP_BUYER -> 2
        TraderType.BIG -> 1
        TraderType.NEWBIE -> 6
        TraderType.SLOW -> 3
        TraderType.UNRELIABLE -> 25
        TraderType.SUSPICIOUS -> 65
        TraderType.AGGRESSIVE -> 20
        TraderType.MANIPULATOR -> 50
    }

    fun pick(trader: Trader, side: Side, d: Difficulty, r: Random): Scenario {
        val pct = (baseFraudPct(trader.type) * d.fraudPct / 100).coerceAtMost(90)
        val fraud = r.nextInt(100) < pct
        return if (side == Side.SELL) {
            if (fraud) {
                val simple = listOf(Scenario.S_FAKE_RECEIPT, Scenario.S_WRONG_AMOUNT, Scenario.S_NAME_MISMATCH)
                val all = Scenario.values().filter { it.side == Side.SELL && it.fraud }
                (if (d == Difficulty.NOVICE) simple else all)[r.nextInt(if (d == Difficulty.NOVICE) simple.size else all.size)]
            } else {
                val honest = mutableListOf(Scenario.S_HONEST, Scenario.S_HONEST, Scenario.S_PENDING_OK)
                if (d != Difficulty.NOVICE) honest += Scenario.S_BANK_HOLD
                if (trader.type == TraderType.UNRELIABLE || trader.type == TraderType.NEWBIE) honest += Scenario.S_NO_SHOW
                honest[r.nextInt(honest.size)]
            }
        } else {
            if (fraud) {
                val list = listOf(Scenario.B_NO_RELEASE, Scenario.B_REPEAT, Scenario.B_ASK_CANCEL, Scenario.B_OFF_PLATFORM)
                list[r.nextInt(if (d == Difficulty.NOVICE) 2 else list.size)]
            } else {
                val honest = mutableListOf(Scenario.B_HONEST, Scenario.B_HONEST)
                if (trader.type == TraderType.SLOW) honest += Scenario.B_SLOW
                if (d != Difficulty.NOVICE) { honest += Scenario.B_BANK_HOLD; honest += Scenario.B_REJECTED }
                honest[r.nextInt(honest.size)]
            }
        }
    }
}

class TradeEngine(
    private val banking: BankingEngine = BankingEngine(),
    private val payments: PaymentEngine = PaymentEngine(),
    private val chat: ChatEngine = ChatEngine(),
    private val fraud: FraudDetector = FraudDetector(),
    private val disputes: DisputeEngine = DisputeEngine(),
    private val picker: ScenarioPicker = ScenarioPicker(),
    private val market: MarketEngine = MarketEngine(),
) {

    // ---------- создание ----------

    fun createTrade(s0: GameState, adId: Int, rub: BigDecimal, bankId: Int): Res {
        val ad = s0.ads.find { it.id == adId } ?: return Res(s0, "Объявление уже недоступно", false)
        val trader = s0.traders.find { it.id == ad.traderId } ?: return Res(s0, "Контрагент не найден", false)
        if (s0.activeTrades.size >= s0.difficulty.maxActive)
            return Res(s0, "Слишком много активных заявок (максимум ${s0.difficulty.maxActive})", false)
        if (rub <= BigDecimal.ZERO) return Res(s0, "Введите сумму", false)
        if (rub < ad.minRub || rub > ad.maxRub)
            return Res(s0, "Сумма вне лимитов: ${ad.minRub.toPlainString()}–${ad.maxRub.toPlainString()} ₽", false)
        if (bankId !in ad.bankIds) return Res(s0, "Контрагент не принимает этот банк", false)
        val rubAmt = rub.r2()
        val gross = rubAmt.divide(ad.rate, 4, RoundingMode.HALF_UP)
        if (gross > ad.reserveUsdt) return Res(s0, "Недостаточно резерва у контрагента (доступно ${ad.reserveUsdt.r4().toPlainString()} USDT)", false)
        val calc = FeeCalculator(s0.fee)
        val r = rng(s0.seed, s0.nextId.toLong(), 3L)
        val scenario = picker.pick(trader, ad.side, s0.difficulty, r)
        val tid = s0.nextId
        var s = s0.copy(nextId = tid + 1)
        val t: Trade
        if (ad.side == Side.BUY) {
            val bank = Banks.all[bankId]
            val net = calc.usdtForBuy(rubAmt, ad.rate)
            val feeRub = (gross - net).multiply(ad.rate).r2()
            val bankFee = banking.feeFor(bank, rubAmt)
            if (rubAmt + bankFee > bank.limit) return Res(s0, "Лимит банка ${bank.name}: ${bank.limit.toPlainString()} ₽", false)
            if (s0.bankBalances[bankId] < rubAmt + bankFee) return Res(s0, "Недостаточно средств в ${bank.name}", false)
            t = Trade(tid, ad.id, trader.id, Side.BUY, rubAmt, gross, net, ad.rate, feeRub, bankFee, rubAmt + bankFee, bankId, scenario,
                TradeStatus.WAITING_FOR_PAYMENT, s.clockMin, s.day)
        } else {
            if (s0.usdt < gross) return Res(s0, "Недостаточно свободных USDT (нужно ${gross.r4().toPlainString()})", false)
            val feeRub = calc.sellFee(rubAmt)
            val payAt = if (scenario == Scenario.S_NO_SHOW) null
            else s.clockMin + (trader.avgMinutes + r.nextInt(-2, 5)).coerceAtLeast(2)
            t = Trade(tid, ad.id, trader.id, Side.SELL, rubAmt, gross, gross, ad.rate, feeRub, BigDecimal.ZERO, BigDecimal.ZERO, bankId, scenario,
                TradeStatus.WAITING_FOR_PAYMENT, s.clockMin, s.day, payAtMin = payAt)
            s = s.copy(usdt = s.usdt - gross)
        }
        val withSched = t.copy(sched = chat.onCreate(t, trader, r))
            .log(s.clockMin, "Заявка создана").log(s.clockMin, "Резерв в эскроу оформлен").log(s.clockMin, "Ожидание оплаты")
            .say(s.clockMin, 0, if (ad.side == Side.BUY) "Заявка создана. Актив продавца заморожен в эскроу. Переведите ${rubAmt.toPlainString()} ₽ в течение $PAY_WINDOW_MIN мин."
            else "Заявка создана. Ваши ${gross.r4().toPlainString()} USDT заморожены в эскроу. Ожидайте оплату от покупателя.")
        val impact = (rubAmt.divide(BigDecimal(10000), 0, RoundingMode.HALF_UP).toInt() * 2).coerceIn(1, 30)
        s = s.copy(
            trades = s.trades + withSched,
            ads = s.ads.map { if (it.id == ad.id) it.copy(reserveUsdt = (it.reserveUsdt - gross).max(BigDecimal.ZERO)) else it },
            pressureBp = s.pressureBp + (if (ad.side == Side.BUY) impact else -impact),
        )
        return Res(s, "Заявка создана", true, tid)
    }

    fun pay(s: GameState, tid: Int): Res = payments.userPay(s, tid)

    // ---------- время ----------

    fun advance(s0: GameState, minutes: Int): GameState {
        var s = s0
        repeat(minutes.coerceIn(1, 180)) {
            s = s.copy(clockMin = s.clockMin + 1)
            s = banking.applyDue(s)
            if (s.clockMin % 15 == 0) s = market.tick(s)
            for (t in s.trades.filter { it.isActive }) s = processTrade(s, t.id)
        }
        return s
    }

    private fun processTrade(s0: GameState, tid: Int): GameState {
        var s = s0
        var t = s.trades.find { it.id == tid } ?: return s
        val clock = s.clockMin
        val due = t.sched.filter { it.atMin <= clock }
        if (due.isNotEmpty()) {
            due.forEach { t = t.say(clock, 2, it.text) }
            t = t.copy(sched = t.sched.filter { it.atMin > clock })
            s = s.withTrade(t)
        }
        t = s.trades.find { it.id == tid } ?: return s
        val trader = s.traders.find { it.id == t.traderId }

        if (t.side == Side.SELL && t.status == TradeStatus.WAITING_FOR_PAYMENT) {
            if (t.payAtMin != null && clock >= t.payAtMin) return payments.simulateIncoming(s, tid)
            if (clock > t.createdMin + PAY_WINDOW_MIN) {
                var n = refundSell(s, t, TradeStatus.CANCELLED, "Время оплаты истекло, актив возвращён")
                trader?.let { tr -> n = n.tweakTrader(tr.id) { it.copy(cancels = it.cancels + 1, reputation = (it.reputation - 2).coerceAtLeast(0)) } }
                return n.note("Заявка №$tid отменена: контрагент не оплатил вовремя", tradeId = tid)
            }
        }
        if (t.side == Side.BUY && t.status == TradeStatus.WAITING_FOR_PAYMENT && clock > t.createdMin + PAY_WINDOW_MIN) {
            val tt = t.copy(status = TradeStatus.CANCELLED, result = "Время оплаты истекло. Заявка отменена автоматически.")
                .log(clock, "Время на оплату истекло")
            return restoreReserve(s.withTrade(tt), t).skillAdd(Skill.CAPITAL, -1).note("Заявка №$tid отменена по таймауту", tradeId = tid)
        }
        if (t.side == Side.BUY && (t.status == TradeStatus.PAYMENT_SIMULATED || t.status == TradeStatus.DISPUTED)) {
            val op = t.opId?.let { id -> s.ops.find { it.id == id } }
            if (op != null && clock >= op.tFinal && (op.finalStatus == BankStatus.REJECTED || op.finalStatus == BankStatus.RETURNED)) {
                val tt = t.copy(status = TradeStatus.REFUNDED, result = "Банк отклонил перевод, деньги вернулись на счёт. Потерь нет.")
                    .log(clock, "Перевод отклонён банком, возврат на счёт")
                return restoreReserve(s.withTrade(tt), t).note("Заявка №$tid: перевод отклонён, деньги вернулись", tradeId = tid).gain(5)
            }
            if (t.status == TradeStatus.PAYMENT_SIMULATED && t.releaseAtMin != null && clock >= t.releaseAtMin) {
                return completeBuy(s, t, "Продавец подтвердил получение и отпустил USDT")
            }
            // Продавец молчит слишком долго: площадка автоматически открывает спор
            if (t.status == TradeStatus.PAYMENT_SIMULATED && t.releaseAtMin == null && op != null && clock >= op.tFinal + 240) {
                return openDispute(s, tid).state
            }
        }
        if (t.status == TradeStatus.DISPUTED) {
            val op = t.opId?.let { id -> s.ops.find { it.id == id } }
            if (op != null && clock >= op.tFinal) return resolveDispute(s, tid).state
        }
        return s
    }

    private fun restoreReserve(s: GameState, t: Trade): GameState =
        s.copy(ads = s.ads.map { if (it.id == t.adId) it.copy(reserveUsdt = it.reserveUsdt + t.usdt) else it })

    // ---------- завершение ----------

    private fun completeBuy(s0: GameState, t: Trade, how: String): GameState {
        var s = s0
        val held = s.usdt + s.lockedUsdt
        val newTotal = held + t.netUsdt
        val cost = held.multiply(s.avgCost) + t.paidRub
        val avg = if (newTotal.signum() == 0) s.avgCost else cost.divide(newTotal, 4, RoundingMode.HALF_UP)
        s = s.copy(usdt = s.usdt + t.netUsdt, avgCost = avg)
        val tt = t.copy(status = TradeStatus.COMPLETED, result = how).log(s.clockMin, "Сделка завершена: +${t.netUsdt.r4().toPlainString()} USDT")
        s = s.withTrade(tt)
        s = s.tweakTrader(t.traderId) { it.copy(trades = it.trades + 1, reputation = (it.reputation + 1).coerceAtMost(100)) }
        return s.gain(25).skillAdd(Skill.CAPITAL, 1).note("Заявка №${t.id} завершена: куплено ${t.netUsdt.r4().toPlainString()} USDT", tradeId = t.id).recordEquity()
    }

    private fun completeSell(s0: GameState, t: Trade, how: String, premature: Boolean): GameState {
        var s = s0
        val cost = t.usdt.multiply(s.avgCost).r2()
        val net = t.rub - t.feeRub
        val pnl = net - cost
        s = s.takeRub(t.bankId, t.feeRub)
        s = s.copy(realized = s.realized + pnl)
        val tt = t.copy(status = TradeStatus.COMPLETED, result = how, pnl = pnl)
            .log(s.clockMin, "Актив передан покупателю, чистый результат ${pnl.toPlainString()} ₽")
        s = s.withTrade(tt)
        s = s.tweakTrader(t.traderId) { it.copy(trades = it.trades + 1, reputation = (it.reputation + 1).coerceAtMost(100)) }
        s = s.gain(if (pnl.signum() > 0) 35 else 20).skillAdd(Skill.VERIFY, if (premature) -2 else 2)
        return s.note("Заявка №${t.id} завершена: результат ${pnl.toPlainString()} ₽", tradeId = t.id).recordEquity()
    }

    private fun refundSell(s: GameState, t: Trade, status: TradeStatus, text: String): GameState {
        val tt = t.copy(status = status, result = text).log(s.clockMin, text)
        return restoreReserve(s.withTrade(tt).copy(usdt = s.usdt + t.usdt), t)
    }

    // ---------- действия пользователя ----------

    fun release(s0: GameState, tid: Int): Res {
        val t = s0.trades.find { it.id == tid } ?: return Res(s0, "Заявка не найдена", false)
        if (t.side != Side.SELL || (t.status != TradeStatus.PAYMENT_SIMULATED && t.status != TradeStatus.DISPUTED))
            return Res(s0, "Отпустить актив сейчас нельзя", false)
        val chk = fraud.sellCheck(s0, t)
        var s = s0
        if (chk.finalOk) {
            val premature = !chk.paidOk
            s = completeSell(s, t, "Вы подтвердили оплату и отпустили USDT", premature)
            val fresh = s.trades.first { it.id == tid }
            val rep = fraud.report(s, fresh, "Отпустить USDT", true) +
                (if (premature) listOf("Предупреждение: вы отпустили актив до статуса «Зачислен». Здесь повезло, но это рискованно.") else emptyList())
            if (t.scenario.fraud) s = s.copy(fraudMissed = s.fraudMissed + 1)
            s = s.withTrade(fresh.copy(report = rep))
            return Res(s, if (premature) "Сделка завершена, но вы отпустили актив преждевременно" else "Сделка завершена", true, tid)
        }
        // небезопасное отпускание = потеря
        val op = t.opId?.let { id -> s.ops.find { it.id == id } }
        var credited = BigDecimal.ZERO
        if (op != null && op.finalStatus == BankStatus.CREDITED && t.scenario != Scenario.S_NAME_MISMATCH) credited = op.amount
        if (op != null && t.scenario == Scenario.S_NAME_MISMATCH) s = banking.reverse(s, op)
        if (op != null && op.finalStatus != BankStatus.CREDITED) s = s.withOp(op.copy(applied = true))
        val cost = t.usdt.multiply(s.avgCost).r2()
        val pnl = credited - cost
        s = s.copy(realized = s.realized + pnl, fraudMissed = s.fraudMissed + 1)
        var tt = t.copy(status = TradeStatus.COMPLETED, fraudLoss = true, pnl = pnl,
            result = "Вы отпустили USDT без подтверждённой оплаты. Актив потерян.").log(s.clockMin, "Актив отпущен без подтверждённой оплаты")
        s = s.withTrade(tt)
        tt = s.trades.first { it.id == tid }
        s = s.withTrade(tt.copy(report = fraud.report(s, tt, "Отпустить USDT", false)))
        s = s.gain(-20).skillAdd(Skill.VERIFY, -6).skillAdd(Skill.SAFETY, -5)
            .tweakTrader(t.traderId) { it.copy(trades = it.trades + 1) }
            .note("Заявка №${t.id}: мошенничество, актив потерян", tradeId = tid).recordEquity()
        return Res(s, "Вы отпустили актив без подтверждённой оплаты — потеря", false, tid)
    }

    fun cancel(s0: GameState, tid: Int): Res {
        val t = s0.trades.find { it.id == tid } ?: return Res(s0, "Заявка не найдена", false)
        var s = s0
        if (t.status == TradeStatus.WAITING_FOR_PAYMENT) {
            if (t.side == Side.SELL) {
                s = refundSell(s, t, TradeStatus.CANCELLED, "Вы отменили заявку, актив возвращён")
            } else {
                s = restoreReserve(s.withTrade(t.copy(status = TradeStatus.CANCELLED, result = "Вы отменили заявку до оплаты").log(s.clockMin, "Отмена до оплаты")), t)
            }
            return Res(s, "Заявка отменена", true, tid)
        }
        if (t.side == Side.BUY && t.status == TradeStatus.PAYMENT_SIMULATED) {
            val op = t.opId?.let { id -> s.ops.find { it.id == id } }
            if (op != null && (op.finalStatus == BankStatus.REJECTED || op.finalStatus == BankStatus.RETURNED))
                return Res(s0, "Банк уже обрабатывает отклонение — деньги вернутся сами. Подождите.", false, tid)
            // Отмена после оплаты: деньги не возвращаются
            val tt = t.copy(status = TradeStatus.CANCELLED, fraudLoss = true, pnl = t.paidRub.negate(),
                result = "Вы отменили заявку после оплаты. Деньги переведены, актив не получен.").log(s.clockMin, "Отмена после оплаты")
            s = s.withTrade(tt).copy(realized = s.realized - t.paidRub, fraudMissed = s.fraudMissed + 1)
            s = s.withTrade(s.trades.first { it.id == tid }.let { it.copy(report = fraud.report(s, it, "Отменить заявку после оплаты", false)) })
            s = s.gain(-15).skillAdd(Skill.SAFETY, -5).note("Заявка №$tid: отмена после оплаты, деньги потеряны", tradeId = tid).recordEquity()
            return Res(s, "Отмена после оплаты приводит к потере денег", false, tid)
        }
        return Res(s0, "Отменить заявку сейчас нельзя. Откройте спор.", false, tid)
    }

    /** Повторный перевод — ошибка: первый платёж уже в пути/зачислен. */
    fun doublePay(s0: GameState, tid: Int): Res {
        val t = s0.trades.find { it.id == tid } ?: return Res(s0, "Заявка не найдена", false)
        if (t.side != Side.BUY || t.status != TradeStatus.PAYMENT_SIMULATED) return Res(s0, "Недоступно", false)
        val bank = Banks.all[t.bankId]
        val total = t.paidRub
        if (s0.bankBalances[t.bankId] < total) return Res(s0, "Недостаточно средств для повторного перевода", false)
        var s = s0.takeRub(t.bankId, total)
        val tt = t.copy(fraudLoss = true, pnl = t.pnl - total).log(s.clockMin, "Повторный перевод ${t.rub.toPlainString()} ₽ — деньги потеряны")
            .say(s.clockMin, 1, "Перевёл повторно.").say(s.clockMin, 2, "Спасибо! Ждите.")
        s = s.withTrade(tt).copy(realized = s.realized - total, fraudMissed = s.fraudMissed + 1)
        s = s.gain(-10).skillAdd(Skill.SAFETY, -4).note("Повторный перевод по заявке №$tid — потеря ${total.toPlainString()} ₽", tradeId = tid)
        return Res(s, "Повторный перевод — потеря ${total.r2().toPlainString()} ₽. Первый платёж уже был зачислен.", false, tid)
    }

    fun quick(s0: GameState, tid: Int, q: Quick): GameState {
        val t = s0.trades.find { it.id == tid } ?: return s0
        val trader = s0.traders.find { it.id == t.traderId } ?: return s0
        val r = rng(s0.seed, tid.toLong(), q.ordinal.toLong(), s0.clockMin.toLong())
        val tt = t.say(s0.clockMin, 1, q.label).say(s0.clockMin, 2, chat.reply(t, trader, q, r))
        return s0.withTrade(tt)
    }

    // ---------- споры ----------

    fun openDispute(s0: GameState, tid: Int): Res {
        val t = s0.trades.find { it.id == tid } ?: return Res(s0, "Заявка не найдена", false)
        if (t.status != TradeStatus.PAYMENT_SIMULATED && t.status != TradeStatus.DISPUTED)
            return Res(s0, "Спор можно открыть только после оплаты", false)
        val s = s0.withTrade(t.copy(status = TradeStatus.DISPUTED, disputed = true).log(s0.clockMin, "Открыт спор, актив в эскроу заморожен"))
        return resolveDispute(s, tid)
    }

    fun resolveDispute(s0: GameState, tid: Int): Res {
        val t = s0.trades.find { it.id == tid } ?: return Res(s0, "Заявка не найдена", false)
        val d = disputes.decide(s0, t)
        var s = s0
        val trader = s.traders.find { it.id == t.traderId }
        val premature = t.releaseAtMin != null && s.clockMin < t.releaseAtMin && !t.scenario.fraud
        when (d.result) {
            DisputeResult.CONTINUE -> {
                s = s.withTrade(t.copy(status = TradeStatus.DISPUTED, disputed = true).log(s.clockMin, "Арбитраж: проверка продолжается"))
                return Res(s, d.text, true, tid)
            }
            DisputeResult.BUYER_WINS -> {
                s = if (t.side == Side.BUY) {
                    completeBuy(s, t.copy(disputed = true), "Арбитраж в вашу пользу: актив передан вам")
                } else {
                    val op = t.opId?.let { id -> s.ops.find { it.id == id } }
                    completeSell(s, t.copy(disputed = true), "Арбитраж в пользу покупателя: оплата подтверждена", false)
                }
                s = s.tweakTrader(t.traderId) { it.copy(disputes = it.disputes + 1, reputation = (it.reputation - (if (t.scenario.fraud) 12 else 1)).coerceAtLeast(0)) }
                if (t.scenario.fraud) s = s.copy(fraudCaught = s.fraudCaught + 1).skillAdd(Skill.SAFETY, 4).gain(40)
                else s = s.copy(falseAlarms = s.falseAlarms + 1).skillAdd(Skill.SAFETY, -1).gain(if (premature) -5 else 0)
            }
            DisputeResult.SELLER_WINS, DisputeResult.ASSETS_RETURNED, DisputeResult.INSUFFICIENT -> {
                if (t.side == Side.SELL) {
                    val op = t.opId?.let { id -> s.ops.find { it.id == id } }
                    if (op != null) s = banking.reverse(s, op)
                    s = refundSell(s, t.copy(disputed = true), TradeStatus.REFUNDED, "Арбитраж: ${d.result.title.lowercase()}. Актив возвращён вам.")
                } else {
                    val tt = t.copy(status = TradeStatus.REFUNDED, disputed = true, result = "Арбитраж: ${d.result.title.lowercase()}. Деньги возвращены.").log(s.clockMin, "Арбитраж завершён")
                    s = restoreReserve(s.withTrade(tt), t)
                }
                s = s.tweakTrader(t.traderId) { it.copy(disputes = it.disputes + 1, cancels = it.cancels + 1, reputation = (it.reputation - 10).coerceAtLeast(0)) }
                if (t.scenario.fraud) s = s.copy(fraudCaught = s.fraudCaught + 1).skillAdd(Skill.SAFETY, 5).skillAdd(Skill.VERIFY, 3).gain(45)
                else s = s.copy(falseAlarms = s.falseAlarms + 1).gain(5)
            }
        }
        val fresh = s.trades.first { it.id == tid }
        val ok = t.scenario.fraud || d.result == DisputeResult.BUYER_WINS
        s = s.withTrade(fresh.copy(report = fraud.report(s, fresh, "Открыть спор → ${d.result.title}", ok || !t.scenario.fraud)))
        return Res(s.recordEquity(), "Арбитраж: ${d.result.title}. ${d.text}", true, tid)
    }

    fun newDay(s: GameState): GameState = market.newDay(s)

    // ---------- проверки для UI/тестов ----------

    fun evidence(s: GameState, t: Trade) = disputes.decide(s, t)
}
