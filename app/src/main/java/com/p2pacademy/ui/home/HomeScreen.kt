package com.p2pacademy.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p2pacademy.domain.Money
import com.p2pacademy.ui.theme.P2PColors
import java.math.BigDecimal

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenMarket: () -> Unit) {
    val s by vm.state.collectAsState()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("P2P Academy", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = P2PColors.Brand)
            Text("День ${s.tradingDay} · Ур. ${s.level}", color = P2PColors.TextMuted)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Стоимость портфеля", color = P2PColors.TextMuted, fontSize = 13.sp)
                Text(Money.formatRub(s.portfolioValue), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Доходность: ${signed(s.returnPercent)}%",
                    color = pnlColor(s.returnPercent),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("RUB", Money.formatRub(s.rub), Modifier.weight(1f))
            StatCard("USDT", Money.formatUsdt(s.usdt), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Реализованная", Money.formatRub(s.realizedProfit), Modifier.weight(1f), pnlColor(s.realizedProfit))
            StatCard("Нереализованная", Money.formatRub(s.unrealizedProfit), Modifier.weight(1f), pnlColor(s.unrealizedProfit))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Сделок", s.totalTrades.toString(), Modifier.weight(1f))
            StatCard("Успешных", "${s.successPercent}%", Modifier.weight(1f))
        }

        Button(onClick = onOpenMarket, modifier = Modifier.fillMaxWidth()) { Text("Открыть рынок") }
        OutlinedButton(onClick = vm::newTradingDay, modifier = Modifier.fillMaxWidth()) { Text("Новый торговый день") }

        Text("Последние операции", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        if (s.journal.isEmpty()) {
            Text("Пока нет операций", color = P2PColors.TextMuted)
        } else {
            s.journal.forEach { Text(it, color = P2PColors.TextMuted, fontSize = 13.sp) }
        }

        Text(
            "Все операции виртуальные. Приложение не гарантирует прибыль и не заменяет реальный опыт.",
            color = P2PColors.Warning,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier, valueColor: Color = Color.Unspecified) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = P2PColors.TextMuted, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.SemiBold, color = valueColor, fontSize = 15.sp)
        }
    }
}

private fun pnlColor(v: BigDecimal): Color = when {
    v.signum() > 0 -> P2PColors.Profit
    v.signum() < 0 -> P2PColors.Loss
    else -> Color.Unspecified
}

private fun signed(v: BigDecimal): String = (if (v.signum() > 0) "+" else "") + v.toPlainString()
