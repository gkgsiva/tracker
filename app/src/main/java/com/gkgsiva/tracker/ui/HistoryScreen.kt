package com.gkgsiva.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.PeriodSummary
import com.gkgsiva.tracker.domain.Periods
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class Range(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly") }

private val dayLabel = DateTimeFormatter.ofPattern("d")
private val dayRowFormat = DateTimeFormatter.ofPattern("EEE, d MMM")
private val weekLabel = DateTimeFormatter.ofPattern("d/M")
private val monthLabel = DateTimeFormatter.ofPattern("MMM")
private val monthRowFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

private const val DAYS_SHOWN = 14
private const val WEEKS_SHOWN = 8
private const val MONTHS_SHOWN = 6

@Composable
fun HistoryScreen(punchesByDay: Map<LocalDate, List<PunchEntity>>) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val summaries = summarizeAll(punchesByDay, now)
    var range by rememberSaveable { mutableIntStateOf(0) }
    val selected = Range.entries[range]

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r ->
                FilterChip(selected = r == selected, onClick = { range = r.ordinal }, label = { Text(r.label) })
            }
        }

        when (selected) {
            Range.DAILY -> DailyView(summaries, today)
            Range.WEEKLY -> PeriodView(
                periods = Periods.weekly(summaries, today, WEEKS_SHOWN),
                barLabel = { it.start.format(weekLabel) },
                rowLabel = { "Week of ${it.start.format(dayRowFormat)}" },
            )
            Range.MONTHLY -> PeriodView(
                periods = Periods.monthly(summaries, today, MONTHS_SHOWN),
                barLabel = { it.start.format(monthLabel) },
                rowLabel = { it.start.format(monthRowFormat) },
            )
        }
    }
}

@Composable
private fun DailyView(summaries: Map<LocalDate, DaySummary>, today: LocalDate) {
    val dates = (DAYS_SHOWN - 1 downTo 0).map { today.minusDays(it.toLong()) }
    ChartCard(
        title = "Last $DAYS_SHOWN days",
        subtitle = "Hours inside office per day",
        showLop = true,
        bars = dates.map { d ->
            val s = summaries[d]
            Bar(d.format(dayLabel), s?.inside?.hours() ?: 0f, s?.breaks?.hours() ?: 0f, lop = s?.lop == true)
        },
    )
    val thisWeek = Periods.summarize(summaries, Periods.weekStart(today), today)
    TotalsCard("This week", thisWeek)

    val worked = dates.reversed().mapNotNull { summaries[it] }.filter { it.worked }
    if (worked.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                worked.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    DayRow(s)
                }
            }
        }
    }
}

@Composable
private fun PeriodView(
    periods: List<PeriodSummary>,
    barLabel: (PeriodSummary) -> String,
    rowLabel: (PeriodSummary) -> String,
) {
    ChartCard(
        title = "Average per working day",
        subtitle = "Hours inside office",
        bars = periods.map { Bar(barLabel(it), it.avgInside.hours(), it.avgBreaks.hours()) },
    )
    periods.lastOrNull()?.let { TotalsCard(rowLabel(it), it) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            periods.reversed().forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(rowLabel(p), fontWeight = FontWeight.Medium)
                    Text(
                        if (p.daysWorked == 0) "No days logged"
                        else "${p.daysWorked} days · ${p.lopDays} LOP · ${p.totalInside.formatHm()} inside · avg ${p.avgInside.formatHm()} · avg break ${p.avgBreaks.formatHm()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, bars: List<Bar>, showLop: Boolean = false) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BarChart(bars, target = 8f)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(MaterialTheme.colorScheme.primary, "Inside")
                Legend(MaterialTheme.colorScheme.tertiary, "Break")
                if (showLop) Legend(MaterialTheme.colorScheme.error, "LOP day")
                Legend(MaterialTheme.colorScheme.onSurfaceVariant, "8h target")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TotalsCard(title: String, p: PeriodSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row {
                Total("Days", "${p.daysWorked}", Modifier.weight(1f))
                Total("Inside", p.totalInside.formatHm(), Modifier.weight(1f))
                Total("Breaks", p.totalBreaks.formatHm(), Modifier.weight(1f))
            }
            Row {
                Total("Avg/day", p.avgInside.formatHm(), Modifier.weight(1f))
                Total("LOP days", "${p.lopDays}", Modifier.weight(1f), warn = p.lopDays > 0)
                Total("Late / under 8h", "${p.lateDays} / ${p.shortDays}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Total(label: String, value: String, modifier: Modifier, warn: Boolean = false) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (warn) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
}

@Composable
private fun DayRow(s: DaySummary) {
    val flags = s.lopReasons.map { it.shortLabel() } + if (s.breakExceeded) listOf("long break") else emptyList()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row {
            Text(s.date.format(dayRowFormat), Modifier.weight(1f), fontWeight = FontWeight.Medium)
            if (s.lop) {
                Text("LOP  ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Text(
                s.inside.formatHm(),
                fontWeight = FontWeight.Medium,
                color = if (s.targetMet && !s.lop) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        }
        Text(
            buildString {
                append("${s.firstIn?.formatClock()} – ${s.lastOut?.formatClock() ?: "…"}")
                append(" · break ${s.breaks.formatHm()}")
                if (flags.isNotEmpty()) append(" · ${flags.joinToString()}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (flags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}
