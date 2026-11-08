package com.p2pacademy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p2pacademy.analytics.StatisticsManager
import com.p2pacademy.domain.*
import com.p2pacademy.education.EducationManager
import com.p2pacademy.ui.theme.P2PColors

@Composable
fun HomeScreen(vm: GameViewModel, s: GameState, go: (String) -> Unit) {
    val st = StatisticsManager().compute(s)
    val next = EducationManager().nextLesson(s)
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("P2P Academy", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P2PColors.Brand)
            Muted("День ${s.day} · Ур. ${s.level} · ${s.difficulty.title}", 13)
        }
        Panel {
            Muted("Стоимость портфеля", 13)
            Text(Money.formatRub(s.equity), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            val ret = st.avgReturn
            Text("Доходность: ${if (ret.signum() > 0) "+" else ""}${ret.toPlainString()}%", color = pnlColor(ret))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.weight(1f)) { Muted("RUB"); Text(Money.formatRub(s.rubTotal), fontWeight = FontWeight.SemiBold) }
            Panel(Modifier.weight(1f)) { Muted("USDT"); Text(Money.formatUsdt(s.usdtTotal), fontWeight = FontWeight.SemiBold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.weight(1f)) { Muted("Реализованная"); Text(Money.formatRub(s.realized), color = pnlColor(s.realized)) }
            Panel(Modifier.weight(1f)) { Muted("Нереализованная"); Text(Money.formatRub(s.unrealized), color = pnlColor(s.unrealized)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.weight(1f)) { Muted("Сделок"); Text("${st.total}") }
            Panel(Modifier.weight(1f)) { Muted("Успешных"); Text("${st.successPct}%") }
        }

        PrimaryBtn("Открыть рынок") { go("market") }
        SecondaryBtn("Новый торговый день") { vm.newDay() }
        SecondaryBtn(if (next != null) "Продолжить обучение: ${next.title}" else "Обучение пройдено — к экзамену") {
            go(if (next != null) "lesson/${next.id}" else "exam")
        }

        H2("Текущий рынок")
        Panel {
            KV("Курс USDT/RUB (средний)", s.mid.toPlainString())
            val buys = s.ads.filter { it.side == Side.BUY }.minOfOrNull { it.rate }
            val sells = s.ads.filter { it.side == Side.SELL }.maxOfOrNull { it.rate }
            KV("Лучшая покупка", buys?.toPlainString() ?: "—")
            KV("Лучшая продажа", sells?.toPlainString() ?: "—")
            LineChart(s.priceHistory)
        }

        H2("Активные заявки")
        if (s.activeTrades.isEmpty()) Muted("Нет активных заявок", 13)
        s.activeTrades.forEach { t ->
            Panel(onClick = { go("trade/${t.id}") }) {
                Text("№${t.id} · ${if (t.side == Side.BUY) "Покупка" else "Продажа"} · ${Money.formatRub(t.rub)}")
                Muted(t.status.title, 13)
            }
        }

        H2("Рыночные события")
        if (s.events.isEmpty()) Muted("Событий пока нет", 13)
        s.events.takeLast(3).reversed().forEach { Muted("${hhmm(it.atMin)} · ${it.title}", 13) }

        H2("Последние уведомления")
        s.notes.take(5).forEach { Muted("${hhmm(it.min)} · ${it.text}", 13) }

        H2("Последние операции")
        val last = s.trades.filter { !it.isActive }.takeLast(5).reversed()
        if (last.isEmpty()) Muted("Операций пока нет", 13)
        last.forEach { t ->
            Panel(onClick = { go("trade/${t.id}") }) {
                Text("№${t.id} · ${if (t.side == Side.BUY) "Покупка" else "Продажа"} · ${t.status.title}")
                if (t.pnl.signum() != 0) Text(Money.formatRub(t.pnl), color = pnlColor(t.pnl))
            }
        }

        H2("Разделы")
        listOf(
            "Обучение" to "learn", "Практика" to "practice", "Расследования" to "cases", "Экзамен" to "exam",
            "Статистика" to "stats", "Профиль" to "profile", "Настройки" to "settings",
        ).forEach { (t, r) -> SecondaryBtn(t) { go(r) } }

        Banner("Все деньги и банки в приложении вымышленные. Приложение не обещает прибыли и не заменяет реальный опыт.")
        Gap(12)
    }
}
