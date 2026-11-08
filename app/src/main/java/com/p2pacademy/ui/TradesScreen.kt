package com.p2pacademy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.p2pacademy.banking.BankingEngine
import com.p2pacademy.banking.Banks
import com.p2pacademy.chat.Quick
import com.p2pacademy.domain.*
import com.p2pacademy.fraud.FraudDetector
import com.p2pacademy.ui.theme.P2PColors

private val banking = BankingEngine()

private fun statusColor(t: Trade): Color = when (t.status) {
    TradeStatus.COMPLETED -> if (t.fraudLoss) P2PColors.Loss else P2PColors.Profit
    TradeStatus.CANCELLED, TradeStatus.REFUNDED -> P2PColors.TextMuted
    TradeStatus.DISPUTED -> P2PColors.Warning
    else -> P2PColors.Brand
}

@Composable
fun TradesScreen(vm: GameViewModel, s: GameState, go: (String) -> Unit) {
    ScreenColumn {
        H1("Сделки")
        TimeBar(s) { vm.advance(it) }
        H2("Активные")
        if (s.activeTrades.isEmpty()) Muted("Нет активных заявок. Откройте рынок и создайте заявку.", 13)
        s.activeTrades.reversed().forEach { TradeRow(it, go) }
        H2("История")
        val hist = s.trades.filter { !it.isActive }.reversed()
        if (hist.isEmpty()) Muted("Завершённых сделок пока нет", 13)
        hist.forEach { TradeRow(it, go) }
    }
}

@Composable
private fun TradeRow(t: Trade, go: (String) -> Unit) {
    Panel(onClick = { go("trade/${t.id}") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("№${t.id} · ${if (t.side == Side.BUY) "Покупка" else "Продажа"}", fontWeight = FontWeight.SemiBold)
            Pill(t.status.title, statusColor(t))
        }
        Muted("${Money.formatRub(t.rub)} · ${t.usdt.r4().toPlainString()} USDT · курс ${t.rate.toPlainString()}", 13)
        if (t.pnl.signum() != 0) Text(Money.formatRub(t.pnl), color = pnlColor(t.pnl))
    }
}

@Composable
fun TradeDetailScreen(vm: GameViewModel, s: GameState, id: Int, go: (String) -> Unit) {
    val t = s.trades.find { it.id == id }
    var tab by remember { mutableIntStateOf(0) }
    var docOpen by remember { mutableStateOf<Doc?>(null) }
    ScreenColumn {
        if (t == null) { Muted("Заявка не найдена"); return@ScreenColumn }
        val trader = s.traders.find { it.id == t.traderId }
        val bank = Banks.all[t.bankId]
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            H1("Заявка №${t.id}")
            Pill(t.status.title, statusColor(t))
        }
        Panel {
            KV("Сторона", if (t.side == Side.BUY) "Вы покупаете USDT" else "Вы продаёте USDT")
            KV("Контрагент", trader?.name ?: "—")
            KV("Курс", t.rate.toPlainString())
            KV("Сумма", Money.formatRub(t.rub))
            KV("USDT", t.usdt.r4().toPlainString())
            if (t.side == Side.BUY) KV("Получите USDT (после комиссий)", t.netUsdt.r4().toPlainString())
            KV("Комиссия площадки", Money.formatRub(t.feeRub))
            if (t.side == Side.BUY && t.bankFee.signum() > 0) KV("Комиссия банка", Money.formatRub(t.bankFee))
            KV("Банк", bank.name)
            val escrow = when (t.status) {
                TradeStatus.COMPLETED -> "Эскроу закрыт"
                TradeStatus.CANCELLED, TradeStatus.REFUNDED -> "Эскроу возвращён"
                TradeStatus.DISPUTED -> "Заморожен арбитражем"
                else -> if (t.side == Side.BUY) "Актив продавца заморожен" else "Ваши USDT заморожены"
            }
            KV("Эскроу", escrow)
            if (t.status == TradeStatus.WAITING_FOR_PAYMENT) {
                val left = (t.createdMin + com.p2pacademy.trading.PAY_WINDOW_MIN - s.clockMin).coerceAtLeast(0)
                KV("До конца времени оплаты", "$left мин")
            }
            if (t.result.isNotEmpty()) Text(t.result, color = statusColor(t))
            if (t.pnl.signum() != 0) KV("Результат", Money.formatRub(t.pnl), pnlColor(t.pnl))
        }

        if (s.difficulty.hints && t.isActive) {
            val hint = when {
                t.side == Side.BUY && t.status == TradeStatus.WAITING_FOR_PAYMENT -> "Подсказка: проверьте лимит и баланс банка, затем оплатите. Отправка — ещё не зачисление."
                t.side == Side.BUY -> "Подсказка: дождитесь статуса «Зачислен» в истории банка. Если продавец просит повторить перевод или отменить заявку — это красный флаг."
                t.status == TradeStatus.WAITING_FOR_PAYMENT -> "Подсказка: актив заморожен. Ждём оплату покупателя."
                else -> "Подсказка: сверьте сумму, имя отправителя и статус в истории банка. Чек сам по себе — не доказательство."
            }
            Banner(hint)
        }

        // ---- действия ----
        when {
            t.side == Side.BUY && t.status == TradeStatus.WAITING_FOR_PAYMENT -> {
                PrimaryBtn("Оплатить через ${bank.name} (${Money.formatRub(t.rub + t.bankFee)})") { vm.pay(t.id) }
                SecondaryBtn("Отменить заявку") { vm.cancel(t.id) }
            }
            t.side == Side.BUY && t.status == TradeStatus.PAYMENT_SIMULATED -> {
                PrimaryBtn("Открыть спор") { vm.openDispute(t.id) }
                SecondaryBtn("Оплатить ещё раз") { vm.doublePay(t.id) }
                SecondaryBtn("Отменить заявку") { vm.cancel(t.id) }
            }
            t.side == Side.SELL && t.status == TradeStatus.WAITING_FOR_PAYMENT -> {
                SecondaryBtn("Отменить заявку") { vm.cancel(t.id) }
            }
            t.side == Side.SELL && t.status == TradeStatus.PAYMENT_SIMULATED -> {
                PrimaryBtn("Отпустить USDT покупателю") { vm.release(t.id) }
                SecondaryBtn("Открыть спор") { vm.openDispute(t.id) }
            }
            t.status == TradeStatus.DISPUTED -> Banner("Спор открыт. Арбитраж ждёт финального статуса банковской операции — прокрутите время.")
            else -> {}
        }
        if (t.isActive) TimeBar(s) { vm.advance(it) }

        Chips(listOf("Чат", "Документы", "Банк", "Хронология", "Разбор"), tab) { tab = it }

        when (tab) {
            0 -> {
                t.chat.forEach { m ->
                    val who = when (m.from) { 0 -> "Система"; 1 -> "Вы"; else -> trader?.name ?: "Контрагент" }
                    Panel {
                        Muted("${hhmm(m.min)} · $who")
                        Text(m.text)
                        m.docId?.let { did -> s.docs.find { it.id == did }?.let { d ->
                            AssistChip(onClick = { docOpen = d }, label = { Text("Вложение: ${d.kind.title} ${d.code}") })
                        } }
                    }
                }
                if (t.isActive) {
                    Muted("Быстрые ответы")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Quick.values().forEach { q -> AssistChip(onClick = { vm.quick(t.id, q) }, label = { Text(q.label) }) }
                    }
                }
            }
            1 -> {
                val docs = s.docs.filter { it.tradeRef == t.id || it.id == t.docId || (t.opId != null && it.opRef == t.opId) }
                if (docs.isEmpty()) Muted("Документов пока нет")
                docs.forEach { d -> DocCard(d) }
            }
            2 -> {
                Banner("Сверяйте сделку с историей вашего банка. Это единственный надёжный источник.", P2PColors.Brand)
                val ops = s.ops.filter { it.bankId == t.bankId }.sortedByDescending { it.createdMin }.take(10)
                if (ops.isEmpty()) Muted("В ${bank.name} пока нет операций")
                ops.forEach { OpCard(it, s) }
            }
            3 -> t.timeline.forEach { Muted(it, 13) }
            else -> {
                if (t.report.isEmpty()) Muted("Разбор появится после решения: сделка завершится, будет отпущен актив или открыт спор.")
                t.report.forEach { Text(it) }
            }
        }
    }
    docOpen?.let { d ->
        AlertDialog(onDismissRequest = { docOpen = null }, confirmButton = { TextButton(onClick = { docOpen = null }) { Text("Закрыть") } },
            title = { Text(d.kind.title) }, text = { DocCard(d) })
    }
}

@Composable
fun DocCard(d: Doc) {
    Panel {
        Pill("УЧЕБНЫЙ ДОКУМЕНТ", P2PColors.Warning)
        Text("${d.kind.title} ${d.code}", fontWeight = FontWeight.SemiBold)
        KV("Сумма", Money.formatRub(d.amount))
        KV("Дата", hhmm(d.atMin))
        KV("Отправитель", d.sender)
        KV("Получатель", d.receiver)
        KV("Банк", Banks.all[d.bankId].name)
        KV("Статус", d.status)
        KV("Операция (ID)", "№${d.tradeRef}")
        Muted("Документ вымышленный и непригоден для реальных операций.")
    }
}

@Composable
fun OpCard(op: BankOp, s: GameState) {
    val st = banking.statusAt(op, s.clockMin)
    val color = when (st) {
        BankStatus.CREDITED -> P2PColors.Profit
        BankStatus.REJECTED, BankStatus.RETURNED -> P2PColors.Loss
        BankStatus.REVIEW -> P2PColors.Warning
        else -> P2PColors.Brand
    }
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${if (op.incoming) "Входящий" else "Исходящий"} №${op.id}", fontWeight = FontWeight.SemiBold)
            Pill(st.title, color)
        }
        KV("Сумма", Money.formatRub(op.amount))
        if (op.fee.signum() > 0) KV("Комиссия", Money.formatRub(op.fee))
        KV(if (op.incoming) "От" else "Кому", op.counterparty)
        KV("Создан", hhmm(op.createdMin))
        if (st == BankStatus.REVIEW) Muted("Дополнительная проверка банком: причина — проверка операции. Итог будет известен позже.")
        if (st == BankStatus.SENT) Muted("Отправлено ещё не значит «зачислено».")
    }
}
