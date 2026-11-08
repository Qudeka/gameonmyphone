package com.p2pacademy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.p2pacademy.education.Question
import com.p2pacademy.ui.theme.P2PColors

/** Вопрос с вариантами. Если reveal = true, показывает верный ответ и разбор. */
@Composable
fun QuestionBlock(index: Int, q: Question, selected: Int?, reveal: Boolean, onSelect: (Int) -> Unit) {
    Panel {
        Muted("Вопрос ${index + 1} · ${q.category}")
        Text(q.text)
        q.options.forEachIndexed { i, opt ->
            val color = when {
                !reveal -> Color.Unspecified
                i == q.correct -> P2PColors.Profit
                i == selected -> P2PColors.Loss
                else -> Color.Unspecified
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !reveal) { onSelect(i) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RadioButton(selected = selected == i, onClick = { if (!reveal) onSelect(i) }, enabled = !reveal)
                Text(opt, color = color)
            }
        }
        if (reveal) {
            val ok = selected == q.correct
            Text(if (ok) "Верно" else "Неверно", color = if (ok) P2PColors.Profit else P2PColors.Loss)
            Muted("Разбор: ${q.explain}", 13)
        }
    }
}
