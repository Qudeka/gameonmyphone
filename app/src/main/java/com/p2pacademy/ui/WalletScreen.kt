package com.p2pacademy.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.p2pacademy.analytics.StatisticsManager
import com.p2pacademy.banking.Banks
import com.p2pacademy.domain.*
import com.p2pacademy.trading.FeeCalculator
import java.math.BigDecimal

@Composable
fun WalletScreen(s: GameState) {
    val st = StatisticsManager().compute(s)
    ScreenColumn {
        H1("Кошелёк")
        Panel {
            H2("Рубли")
            KV("Всего RUB", Money.formatRub(s.rubTotal))
            KV("Доступно", Money.formatRub(s.rubTotal))
            KV("Заблокировано (платежи в пути)", Money.formatRub(s.lockedRub))
            Banks.all.forEach { b -> if (s.bankBalances[b.id].signum() > 0) KV(b.name, Money.formatRub(s.bankBalances[b.id])) }
        }
        Panel {
            H2("USDT")
            KV("Доступно", Money.formatUsdt(s.usdt))
            KV("Заблокировано в эскроу", Money.formatUsdt(s.lockedUsdt))
            KV("Средняя цена покупки", s.avgCost.toPlainString())
            KV("Себестоимость", Money.formatRub(s.usdtTotal.multiply(s.avgCost)))
            KV("Рыночная оценка", Money.formatRub(s.usdtTotal.multiply(s.mid)))
        }
        Panel {
            H2("Результат")
            KV("Реализованная прибыль", Money.formatRub(s.realized), pnlColor(s.realized))
            KV("Нереализованная", Money.formatRub(s.unrealized), pnlColor(s.unrealized))
            KV("Общая доходность", "${st.avgReturn.toPlainString()}%", pnlColor(st.avgReturn))
            KV("Стоимость портфеля", Money.formatRub(s.equity))
            KV("Начальный капитал", Money.formatRub(s.initial))
        }
        Calculator(s)
    }
}

@Composable
fun Calculator(s: GameState) {
    var amount by remember { mutableStateOf("50000") }
    var buy by remember { mutableStateOf(s.ads.filter { it.side == Side.BUY }.minOfOrNull { it.rate }?.toPlainString() ?: s.mid.toPlainString()) }
    var sell by remember { mutableStateOf(s.ads.filter { it.side == Side.SELL }.maxOfOrNull { it.rate }?.toPlainString() ?: s.mid.toPlainString()) }
    val a = Money.parse(amount); val b = Money.parse(buy); val c = Money.parse(sell)
    Panel {
        H2("Калькулятор спреда и безубыточности")
        OutlinedTextField(amount, { amount = it }, label = { Text("Вложение, ₽") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(buy, { buy = it }, label = { Text("Курс покупки") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sell, { sell = it }, label = { Text("Курс продажи") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (a != null && b != null && c != null && b.signum() > 0 && a.signum() > 0) {
            val calc = FeeCalculator(s.fee)
            val rt = calc.roundTrip(a, b, c)
            KV("Спред", "${calc.spreadPercent(b, c).toPlainString()}%")
            KV("Вложено", Money.formatRub(rt.invested))
            KV("Получено USDT", rt.usdtBought.toPlainString())
            KV("Курс входа", b.toPlainString())
            KV("Курс выхода", c.toPlainString())
            KV("Валовая прибыль", Money.formatRub(rt.grossProfit), pnlColor(rt.grossProfit))
            KV("Все расходы", Money.formatRub(rt.totalCosts))
            KV("Чистая прибыль", Money.formatRub(rt.netProfit), pnlColor(rt.netProfit))
            KV("ROI", "${rt.roiPercent.toPlainString()}%", pnlColor(rt.roiPercent))
            val be = runCatching { calc.breakEvenSellRate(a, b) }.getOrNull()
            if (be != null) {
                KV("Безубыточный курс продажи", be.toPlainString())
                if (c < be) Banner("При курсе ${c.toPlainString()} продажа убыточна: ниже безубыточного ${be.toPlainString()}.", com.p2pacademy.ui.theme.P2PColors.Loss)
            }
        } else Muted("Введите положительные значения")
    }
}
