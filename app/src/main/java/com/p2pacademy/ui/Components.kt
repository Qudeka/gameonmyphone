package com.p2pacademy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p2pacademy.domain.GameState
import com.p2pacademy.ui.theme.P2PColors
import java.math.BigDecimal

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = if (onClick != null) m.clickable { onClick() } else m,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun H1(text: String) = Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold)

@Composable
fun H2(text: String) = Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun Muted(text: String, size: Int = 12) = Text(text, color = P2PColors.TextMuted, fontSize = size.sp)

@Composable
fun KV(k: String, v: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = P2PColors.TextMuted, fontSize = 13.sp)
        Text(v, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

fun pnlColor(v: BigDecimal): Color = when {
    v.signum() > 0 -> P2PColors.Profit
    v.signum() < 0 -> P2PColors.Loss
    else -> Color.Unspecified
}

@Composable
fun Banner(text: String, color: Color = P2PColors.Warning) {
    Text(
        text, color = color, fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(10.dp),
    )
}

@Composable
fun PrimaryBtn(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }

@Composable
fun SecondaryBtn(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }

@Composable
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) })
        }
    }
}

/** Простой линейный график. */
@Composable
fun LineChart(values: List<BigDecimal>, modifier: Modifier = Modifier, color: Color = P2PColors.Brand) {
    Canvas(modifier.fillMaxWidth().height(110.dp).background(P2PColors.SurfaceHigh, RoundedCornerShape(10.dp))) {
        if (values.size < 2) return@Canvas
        val d = values.map { it.toDouble() }
        val lo = d.min(); val hi = d.max()
        val span = (hi - lo).let { if (it < 1e-9) 1.0 else it }
        val pad = 10f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val path = Path()
        d.forEachIndexed { i, v ->
            val x = pad + w * i / (d.size - 1)
            val y = pad + h * (1f - ((v - lo) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3f))
        drawLine(P2PColors.TextMuted.copy(alpha = 0.3f), Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad))
    }
}

/** Панель времени: деньги и банки живут во времени, поэтому его надо двигать вручную. */
@Composable
fun TimeBar(s: GameState, onAdvance: (Int) -> Unit) {
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(com.p2pacademy.domain.hhmm(s.clockMin), fontWeight = FontWeight.SemiBold)
            Muted("Прокрутка времени")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 30, 60).forEach { m ->
                OutlinedButton(onClick = { onAdvance(m) }, modifier = Modifier.weight(1f)) { Text(if (m == 60) "+1ч" else "+$m") }
            }
        }
    }
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp).width(1.dp))
