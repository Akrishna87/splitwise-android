package com.example.splitwise.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitwise.domain.Money

private val palette = listOf(
    Color(0xFF0E9F7E), Color(0xFFF59E0B), Color(0xFF3B82F6), Color(0xFFEF4444), Color(0xFF8B5CF6),
    Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFF84CC16), Color(0xFFF97316), Color(0xFF64748B),
)

@Composable
fun DonutChart(slices: List<Pair<String, Long>>, currency: String) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(130.dp)) {
            val stroke = 30.dp.toPx()
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * s.second / total
                drawArc(
                    color = palette[i % palette.size], startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(palette[i % palette.size], RoundedCornerShape(2.dp)))
                    Text("${s.first}  ${Money.format(s.second, currency)}  (${100 * s.second / total}%)", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun compact(minor: Long, cur: String): String {
    val major = minor / if (Money.digits(cur) == 0) 1 else 100
    return if (major >= 1000) String.format(java.util.Locale.US, "%.1fk", major / 1000.0) else major.toString()
}

@Composable
fun BarChart(data: List<Pair<String, Long>>, currency: String) {
    val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        data.forEach { (label, v) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (v > 0) compact(v, currency) else "", fontSize = 11.sp)
                Box(
                    Modifier.width(28.dp).height((120f * v / max).dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
                Text(label, fontSize = 12.sp)
            }
        }
    }
}
