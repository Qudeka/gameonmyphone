package com.p2pacademy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.p2pacademy.banking.Banks
import com.p2pacademy.domain.*
import com.p2pacademy.fraud.Risk
import com.p2pacademy.fraud.RiskEngine
import com.p2pacademy.trading.FeeCalculator
import com.p2pacademy.ui.theme.P2PColors
import java.math.BigDecimal

private val sortNames = listOf("Лучший курс", "Размер лимита", "Успешные", "Скорость", "Репутация")
private val risk = RiskEngine()

@Composable
fun MarketScreen(vm: GameViewModel, s: GameState, go: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }       // 0 BUY, 1 SELL
    var sort by remember { mutableIntStateOf(0) }
    var bankFilter by remember { mutableIntStateOf(-1) }
    var onlySafe by remember { mutableStateOf(false) }
    var onlyPro by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Ad?>(null) }
    val side = if (tab == 0) Side.BUY else Side.SELL

    val tradersById = s.traders.associateBy { it.id }
    var list = s.ads.filter { it.side == side && tradersById[it.traderId] != null }
    if (bankFilter >= 0) list = list.filter { bankFilter in it.bankIds }
    if (onlySafe) list = list.filter { risk.adRisk(tradersById.getValue(it.traderId), it, s.mid).first == Risk.LOW }
    if (onlyPro) list = list.filter { tradersById.getValue(it.traderId).successPct >= 96 }
    list = when (sort) {
        1 -> list.sortedByDescending { it.maxRub }
        2 -> list.sortedByDescending { tradersById.getValue(it.traderId).successPct }
        3 -> list.sortedBy { tradersById.getValue(it.traderId).avgMinutes }
        4 -> list.sortedByDescending { tradersById.getValue(it.traderId).reputation }
        else -> if (side == Side.BUY) list.sortedBy { it.rate } else list.sortedByDescending { it.rate }
    }

    ScreenColumn {
        H1("Рынок P2P")
        Muted(if (s.mode == MarketMode.TRAINING) "Режим: учебный рынок (все объявления сгенерированы)" else "Режим: анализ реального рынка — источник данных не подключён, показаны только учебные объявления", 12)
        Panel {
            KV("Средний курс", s.mid.toPlainString())
            val bb = s.ads.filter { it.side == Side.BUY }.minByOrNull { it.rate }
            val bs = s.ads.filter { it.side == Side.SELL }.maxByOrNull { it.rate }
            if (bb != null && bs != null) {
                val calc = FeeCalculator(s.fee)
                val rt = calc.roundTrip(BigDecimal("50000"), bb.rate, bs.rate)
                KV("Спред лучшей связки", "${calc.spreadPercent(bb.rate, bs.rate).toPlainString()}%")
                KV("Чистый результат на 50 000 ₽", Money.formatRub(rt.netProfit), pnlColor(rt.netProfit))
                if (rt.netProfit.signum() <= 0) Muted("Сейчас связка убыточна — спред не покрывает расходы.", 12)
            }
            LineChart(s.priceHistory)
        }
        TimeBar(s) { vm.advance(it) }

        Chips(listOf("BUY — купить USDT", "SELL — продать USDT"), tab) { tab = it }
        Muted("Сортировка")
        Chips(sortNames, sort) { sort = it }
        Muted("Банк")
        Chips(listOf("Все") + Banks.all.map { it.name }, bankFilter + 1) { bankFilter = it - 1 }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(onlySafe, { onlySafe = !onlySafe }, { Text("Низкий риск") })
            androidx.compose.material3.FilterChip(onlyPro, { onlyPro = !onlyPro }, { Text("Успешных ≥96%") })
        }
        if (side == Side.SELL) Muted("Свободно USDT: ${Money.formatUsdt(s.usdt)}", 13)

        if (list.isEmpty()) Muted("Нет объявлений по фильтрам. Новые участники приходят со временем.", 13)
        list.forEach { ad ->
            val tr = tradersById.getValue(ad.traderId)
            val (rk, _) = risk.adRisk(tr, ad, s.mid)
            Panel(onClick = { selected = ad }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr.name, fontWeight = FontWeight.SemiBold)
                    Text(ad.rate.toPlainString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P2PColors.Brand)
                }
                Muted("${tr.type.title} · репутация ${tr.reputation}")
                Muted("Лимиты: ${ad.minRub.toPlainString()}–${ad.maxRub.toPlainString()} ₽ · резерв ${ad.reserveUsdt.toPlainString()} USDT")
                Muted("Сделок ${tr.trades} · успешных ${tr.successPct}% · аккаунт ${tr.ageDays} дн. · ~${tr.avgMinutes} мин · отмен ${tr.cancels} · споров ${tr.disputes}")
                Muted(ad.bankIds.joinToString(", ") { Banks.all[it].name })
                Pill("Риск: ${rk.title}", when (rk) { Risk.LOW -> P2PColors.Profit; Risk.MEDIUM -> P2PColors.Warning; Risk.HIGH -> P2PColors.Loss })
            }
        }
    }

    selected?.let { ad ->
        AdDialog(vm, s, ad, onDismiss = { selected = null }, go = go)
    }
}

@Composable
private fun AdDialog(vm: GameViewModel, s: GameState, ad: Ad, onDismiss: () -> Unit, go: (String) -> Unit) {
    val tr = s.traders.find { it.id == ad.traderId }
    if (tr == null) { onDismiss(); return }
    var amount by remember { mutableStateOf(ad.minRub.toPlainString()) }
    var bank by remember { mutableIntStateOf(ad.bankIds.first()) }
    val rub = Money.parse(amount)
    val calc = FeeCalculator(s.fee)
    val (rk, why) = risk.adRisk(tr, ad, s.mid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr.name) },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Курс ${ad.rate.toPlainString()} · ${if (ad.side == Side.BUY) "вы покупаете" else "вы продаёте"} USDT")
                Muted("Лимиты ${ad.minRub.toPlainString()}–${ad.maxRub.toPlainString()} ₽, резерв ${ad.reserveUsdt.toPlainString()} USDT")
                Pill("Риск: ${rk.title}", when (rk) { Risk.LOW -> P2PColors.Profit; Risk.MEDIUM -> P2PColors.Warning; Risk.HIGH -> P2PColors.Loss })
                if (s.difficulty.hints) why.forEach { Muted("Подсказка: $it", 12) }
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Muted("Банк для оплаты")
                Chips(ad.bankIds.map { Banks.all[it].name }, ad.bankIds.indexOf(bank)) { bank = ad.bankIds[it] }
                if (rub != null && rub.signum() > 0) {
                    val gross = rub.divide(ad.rate, 4, java.math.RoundingMode.HALF_UP)
                    if (ad.side == Side.BUY) {
                        val net = calc.usdtForBuy(rub, ad.rate)
                        KV("Получите USDT", net.r4().toPlainString())
                        KV("Расходы (USDT)", (gross - net).r4().toPlainString())
                        val bf = com.p2pacademy.banking.BankingEngine().feeFor(Banks.all[bank], rub)
                        KV("Комиссия банка", Money.formatRub(bf))
                        KV("Баланс банка", Money.formatRub(s.bankBalances[bank]))
                    } else {
                        KV("Продадите USDT", gross.r4().toPlainString())
                        KV("Комиссия площадки", Money.formatRub(calc.sellFee(rub)))
                        KV("Доступно USDT", Money.formatUsdt(s.usdt))
                        KV("Безубыточный курс", if (s.avgCost.signum() > 0) runCatching { calc.breakEvenSellRate(s.avgCost.multiply(gross), s.avgCost).toPlainString() }.getOrDefault("—") else "—")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = rub
                if (v == null) vm.toast("Введите корректную сумму")
                else {
                    val res = vm.createTrade(ad.id, v, bank)
                    if (res != null && res.ok && res.tradeId != null) { onDismiss(); go("trade/${res.tradeId}") }
                }
            }) { Text("Создать заявку") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDismiss(); go("trader/${tr.id}") }) { Text("Профиль") }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
    )
}

@Composable
fun TraderScreen(s: GameState, id: Int) {
    val tr = s.traders.find { it.id == id }
    ScreenColumn {
        if (tr == null) { Muted("Контрагент больше не на рынке."); return@ScreenColumn }
        H1(tr.name)
        Pill(tr.type.title, P2PColors.Warning)
        Panel {
            KV("Репутация", "${tr.reputation}/100")
            KV("Возраст аккаунта", "${tr.ageDays} дн.")
            KV("Сделок", "${tr.trades}")
            KV("Успешных", "${tr.successPct}%")
            KV("Отмен", "${tr.cancels}")
            KV("Споров", "${tr.disputes}")
            KV("Среднее время исполнения", "${tr.avgMinutes} мин")
        }
        H2("Объявления")
        s.ads.filter { it.traderId == tr.id }.forEach { ad ->
            Panel {
                Text("${if (ad.side == Side.BUY) "Вы покупаете" else "Вы продаёте"} по ${ad.rate.toPlainString()}")
                Muted("Лимиты ${ad.minRub.toPlainString()}–${ad.maxRub.toPlainString()} ₽ · резерв ${ad.reserveUsdt.toPlainString()} USDT")
                Muted(ad.bankIds.joinToString(", ") { Banks.all[it].name })
            }
        }
        H2("История с вами")
        val mine = s.trades.filter { it.traderId == tr.id }
        if (mine.isEmpty()) Muted("Сделок не было")
        mine.forEach { Muted("№${it.id} · ${it.status.title} · ${Money.formatRub(it.rub)}") }
        Muted("Репутация меняется по итогам сделок: споры и отмены снижают её, успешные сделки повышают.")
    }
}
