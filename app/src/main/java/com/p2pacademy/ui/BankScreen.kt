package com.p2pacademy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p2pacademy.banking.BankingEngine
import com.p2pacademy.banking.Banks
import com.p2pacademy.domain.*
import com.p2pacademy.ui.theme.P2PColors

@Composable
fun BankScreen(s: GameState, go: (String) -> Unit) {
    ScreenColumn {
        H1("Банки")
        Muted("Все банки вымышленные. Они различаются скоростью, комиссиями, лимитами и строгостью проверок.")
        Panel { KV("Всего на счетах", Money.formatRub(s.rubTotal)) }
        Banks.all.forEach { b ->
            val pending = s.ops.count { it.bankId == b.id && s.clockMin < it.tFinal }
            Panel(onClick = { go("bank/${b.id}") }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(b.name, fontWeight = FontWeight.Bold, color = Color(b.color))
                    Text(Money.formatRub(s.bankBalances[b.id]), fontWeight = FontWeight.SemiBold)
                }
                Muted(b.tagline)
                Muted("Скорость ~${b.speed} мин · комиссия ${b.feeBp / 100.0}% · лимит ${b.limit.toPlainString()} ₽")
                if (pending > 0) Pill("Операций в обработке: $pending", P2PColors.Warning)
            }
        }
    }
}

@Composable
fun BankDetailScreen(vm: GameViewModel, s: GameState, id: Int) {
    val b = Banks.all.getOrNull(id)
    var tab by remember { mutableIntStateOf(0) }
    var transfer by remember { mutableStateOf(false) }
    ScreenColumn {
        if (b == null) { Muted("Банк не найден"); return@ScreenColumn }
        val color = Color(b.color)
        Column(Modifier.fillMaxWidth().background(color.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(16.dp)) {
            Text(b.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(b.tagline, color = P2PColors.TextMuted, fontSize = 12.sp)
            Text(Money.formatRub(s.bankBalances[id]), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        if (BankingEngine().outageActive(s, id)) Banner("В банке технический сбой: операции идут медленнее.", P2PColors.Loss)
        SecondaryBtn("Перевод между своими банками") { transfer = true }
        Chips(listOf("Операции", "Входящие", "Исходящие", "Документы", "Уведомления", "Лимиты"), tab) { tab = it }
        val ops = s.ops.filter { it.bankId == id }.sortedByDescending { it.createdMin }
        when (tab) {
            0 -> { if (ops.isEmpty()) Muted("Операций пока нет"); ops.forEach { OpCard(it, s) } }
            1 -> { val l = ops.filter { it.incoming }; if (l.isEmpty()) Muted("Входящих нет"); l.forEach { OpCard(it, s) } }
            2 -> { val l = ops.filter { !it.incoming }; if (l.isEmpty()) Muted("Исходящих нет"); l.forEach { OpCard(it, s) } }
            3 -> { val l = s.docs.filter { it.bankId == id && it.genuine }.sortedByDescending { it.atMin }
                if (l.isEmpty()) Muted("Документов нет"); l.forEach { DocCard(it) } }
            4 -> { val l = s.notes.filter { it.bankId == id }
                if (l.isEmpty()) Muted("Уведомлений нет"); l.forEach { Panel { Muted(hhmm(it.min)); Text(it.text) } } }
            else -> {
                Panel {
                    KV("Лимит на операцию", Money.formatRub(b.limit))
                    KV("Комиссия исходящего перевода", "${b.feeBp / 100.0}%")
                    KV("Типичное время зачисления", "~${b.speed} мин")
                    KV("Шанс дополнительной проверки", "~${b.holdPct}%")
                }
                H2("История проверок")
                val checks = ops.filter { it.hold }
                if (checks.isEmpty()) Muted("Проверок не было")
                checks.forEach { Muted("№${it.id}: ${hhmm(it.createdMin)} · дополнительная проверка (${if (s.clockMin >= it.tFinal) "завершена: ${it.finalStatus.title}" else "идёт"})", 13) }
                Muted("Проверка — штатная процедура банка. Правильное поведение: ждать итога и отвечать на запросы банка. Обход проверок недопустим.")
            }
        }
    }
    if (transfer && b != null) TransferDialog(vm, s, id) { transfer = false }
}

@Composable
private fun TransferDialog(vm: GameViewModel, s: GameState, from: Int, onClose: () -> Unit) {
    var to by remember { mutableIntStateOf(if (from == 0) 1 else 0) }
    var amount by remember { mutableStateOf("1000") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Перевод между своими банками") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("Из: ${Banks.all[from].name}")
                Chips(Banks.all.filter { it.id != from }.map { it.name }, Banks.all.filter { it.id != from }.indexOfFirst { it.id == to }) {
                    to = Banks.all.filter { b -> b.id != from }[it].id
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true)
                Muted("Комиссия банка-отправителя: ${Banks.all[from].feeBp / 100.0}%")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Money.parse(amount)
                if (v == null || v.signum() == 0) vm.toast("Введите корректную сумму") else { vm.internalTransfer(from, to, v); onClose() }
            }) { Text("Перевести") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } },
    )
}
