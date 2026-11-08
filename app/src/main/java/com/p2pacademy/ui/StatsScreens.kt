package com.p2pacademy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.p2pacademy.analytics.StatisticsManager
import com.p2pacademy.domain.*
import com.p2pacademy.trading.FeeConfig
import com.p2pacademy.ui.theme.P2PColors

private val statsMgr = StatisticsManager()

@Composable
fun StatsScreen(s: GameState) {
    val st = statsMgr.compute(s)
    ScreenColumn {
        H1("Статистика")
        Panel {
            KV("Всего сделок", "${st.total}")
            KV("Успешные", "${st.successful} (${st.successPct}%)")
            KV("Отмены", "${st.cancelled}")
            KV("Споры", "${st.disputes}")
            KV("Прибыль", Money.formatRub(st.profit), pnlColor(st.profit))
            KV("Убытки", Money.formatRub(st.losses.negate()), pnlColor(st.losses.negate()))
            KV("Комиссии", Money.formatRub(st.fees))
            KV("Средняя доходность", "${st.avgReturn.toPlainString()}%", pnlColor(st.avgReturn))
            KV("Максимальная прибыль", Money.formatRub(st.maxProfit), pnlColor(st.maxProfit))
            KV("Максимальный убыток", Money.formatRub(st.maxLoss), pnlColor(st.maxLoss))
            KV("Точность распознавания обмана", "${st.fraudAccuracy}%")
            KV("Распознано / пропущено", "${s.fraudCaught} / ${s.fraudMissed}")
            KV("Ложные тревоги", "${s.falseAlarms}")
            KV("Финансовая грамотность", "${st.literacy}/100")
        }
        H2("График капитала")
        LineChart(s.equityHistory)
        H2("История торговых дней")
        if (s.dayHistory.isEmpty()) Muted("Завершённых дней пока нет", 13)
        s.dayHistory.reversed().forEach {
            Panel { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("День ${it.day}")
                Text("${Money.formatRub(it.equity)} (${if (it.pnl.signum() > 0) "+" else ""}${it.pnl.r2().toPlainString()})", color = pnlColor(it.pnl))
            } }
        }
    }
}

@Composable
fun ProfileScreen(s: GameState) {
    ScreenColumn {
        H1("Профиль")
        Panel {
            Text("Уровень ${s.level}")
            Muted("XP: ${s.xp} (до следующего уровня: ${150 - s.xp % 150})")
            LinearProgressIndicator(progress = { (s.xp % 150) / 150f }, modifier = Modifier.fillMaxWidth())
            KV("Сложность", s.difficulty.title)
            KV("Торговый день", "${s.day}")
        }
        H2("Рейтинг навыков")
        Panel {
            Skill.all.forEach { k ->
                KV(k, "${s.skill(k)}/100")
                LinearProgressIndicator(progress = { s.skill(k) / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
        H2("Достижения")
        statsMgr.achievements.forEach { a ->
            val got = a.id in s.achievements
            Panel {
                Text(a.title, color = if (got) P2PColors.Profit else P2PColors.TextMuted)
                Muted(a.desc + if (got) " — получено" else "")
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel, s: GameState) {
    var confirm by remember { mutableStateOf(false) }
    var platform by remember { mutableStateOf(s.fee.platformPercent.toPlainString()) }
    var payment by remember { mutableStateOf(s.fee.paymentPercent.toPlainString()) }
    var network by remember { mutableStateOf(s.fee.networkFeeUsdt.toPlainString()) }
    var slip by remember { mutableStateOf(s.fee.slippagePercent.toPlainString()) }
    ScreenColumn {
        H1("Настройки")
        H2("Сложность")
        Chips(Difficulty.values().map { it.title }, s.difficulty.ordinal) { vm.setDifficulty(Difficulty.values()[it]) }
        Muted("Влияет на волатильность, долю подозрительных сделок, число одновременных заявок и подсказки. Применяется к новым сделкам и новым дням.")

        H2("Режим рынка")
        Chips(MarketMode.values().map { it.title }, s.mode.ordinal) { vm.setMode(MarketMode.values()[it]) }
        if (s.mode == MarketMode.REAL_ANALYSIS) {
            Banner("Источник данных: не подключён. Реальные публичные данные будут подключаться через официальные API в будущих версиях. Сейчас показываются только учебные объявления — это не реальные офферы, и учебные цены не означают, что такая цена доступна на реальном рынке.")
        }

        H2("Комиссии и расходы")
        OutlinedTextField(platform, { platform = it }, label = { Text("Комиссия площадки, %") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(payment, { payment = it }, label = { Text("Платёжные расходы, %") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(network, { network = it }, label = { Text("Сетевая комиссия, USDT") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(slip, { slip = it }, label = { Text("Проскальзывание, %") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SecondaryBtn("Применить расходы") {
            val a = Money.parse(platform); val b = Money.parse(payment); val c = Money.parse(network); val d = Money.parse(slip)
            if (a == null || b == null || c == null || d == null || a.toDouble() > 10 || b.toDouble() > 10 || d.toDouble() > 10) vm.toast("Проверьте значения (проценты не более 10)")
            else { vm.setFee(FeeConfig(a, b, c, d)); vm.toast("Расходы обновлены") }
        }

        H2("Данные")
        SecondaryBtn("Создать резервную копию") { vm.backup() }
        SecondaryBtn("Восстановить из копии") { vm.restoreBackup() }
        SecondaryBtn("Полный сброс прогресса") { confirm = true }
        Banner("Приложение работает офлайн. Все деньги, банки и документы вымышленные.")
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Сбросить весь прогресс?") },
        text = { Text("Баланс, сделки, статистика, уроки и достижения будут удалены. Резервная копия сохранится.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.fullReset() }) { Text("Сбросить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
    )
}
