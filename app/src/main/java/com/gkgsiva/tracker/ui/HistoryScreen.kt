package com.gkgsiva.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.domain.Settings
import com.gkgsiva.tracker.domain.PeriodSummary
import com.gkgsiva.tracker.domain.Periods
import java.time.Duration
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
fun HistoryScreen(
    punchesByDay: Map<LocalDate, List<PunchEntity>>,
    dayTypes: Map<LocalDate, DayType>,
    settings: Settings,
) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val summaries = summarizeAll(punchesByDay, dayTypes, now, settings)
    /** Also covers days with nothing stored, such as weekly offs. */
    fun summaryOf(date: LocalDate) = summaries[date] ?: summarizeDay(date, emptyList(), now, null, settings)
    val target = settings.rules.requiredInside
    var sharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var range by rememberSaveable { mutableIntStateOf(0) }
    val selected = Range.entries[range]

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Range.entries.forEach { r ->
                FilterChip(selected = r == selected, onClick = { range = r.ordinal }, label = { Text(r.label) })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { sharing = true }) {
                Icon(Icons.Filled.Share, contentDescription = "Share report")
            }
        }

        when (selected) {
            Range.DAILY -> DailyView(summaries, ::summaryOf, today, target)
            Range.WEEKLY -> PeriodView(
                target = target,
                periods = Periods.weekly(summaries, today, WEEKS_SHOWN),
                barLabel = { it.start.format(weekLabel) },
                rowLabel = { "Week of ${it.start.format(dayRowFormat)}" },
            )
            Range.MONTHLY -> PeriodView(
                target = target,
                periods = Periods.monthly(summaries, today, MONTHS_SHOWN),
                barLabel = { it.start.format(monthLabel) },
                rowLabel = { it.start.format(monthRowFormat) },
            )
        }

        UpcomingCard(summaries, today)
    }

    if (sharing) {
        fun share(subject: String, text: String) {
            shareText(context, subject, text)
            sharing = false
        }
        AlertDialog(
            onDismissRequest = { sharing = false },
            title = { Text("Share report") },
            text = {
                Column {
                    TextButton(onClick = {
                        share("Office report today", Reports.day(summaryOf(today)))
                    }) { Text("Today") }
                    TextButton(onClick = { share("Office report this week", Reports.week(summaries, today)) }) { Text("This week") }
                    TextButton(onClick = { share("Office report this month", Reports.month(summaries, today)) }) { Text("This month") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { sharing = false }) { Text("Cancel") } },
        )
    }
}

/** Planned holidays and leave, plus how much leave was taken this year. */
@Composable
private fun UpcomingCard(summaries: Map<LocalDate, DaySummary>, today: LocalDate) {
    val upcoming = summaries.values.filter { it.date > today && it.dayOff }.sortedBy { it.date }
    val leaveThisYear = summaries.values.filter { it.type == DayType.LEAVE && it.date.year == today.year }
    val taken = leaveThisYear.count { it.date <= today }
    val planned = leaveThisYear.size - taken
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Leave & holidays", style = MaterialTheme.typography.titleMedium)
            Text(
                "Leave in ${today.year}: $taken taken · $planned planned",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upcoming.isEmpty()) {
                Text(
                    "Nothing planned. On Today, tap the date to pick a future day and mark it Holiday or On leave.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            upcoming.forEach { s ->
                Row {
                    Text(s.date.format(dayRowFormat), Modifier.weight(1f))
                    Text(s.type.label, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DailyView(
    summaries: Map<LocalDate, DaySummary>,
    summaryOf: (LocalDate) -> DaySummary,
    today: LocalDate,
    target: Duration,
) {
    val dates = (DAYS_SHOWN - 1 downTo 0).map { today.minusDays(it.toLong()) }
    ChartCard(
        target = target,
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

    val worked = dates.reversed().map(summaryOf).filter { it.worked || it.dayOff }
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
    target: Duration,
    periods: List<PeriodSummary>,
    barLabel: (PeriodSummary) -> String,
    rowLabel: (PeriodSummary) -> String,
) {
    ChartCard(
        target = target,
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
                        else "${p.daysWorked} days · ${p.wfhDays} WFH · ${p.lopDays} LOP · ${p.holidays} holiday · ${p.leaveDays} leave · ${p.totalInside.formatHm()} inside · avg ${p.avgInside.formatHm()} · avg break ${p.avgBreaks.formatHm()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(target: Duration, title: String, subtitle: String, bars: List<Bar>, showLop: Boolean = false) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BarChart(bars, target = target.hours())
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(MaterialTheme.colorScheme.primary, "Inside")
                Legend(MaterialTheme.colorScheme.tertiary, "Break")
                if (showLop) Legend(MaterialTheme.colorScheme.error, "LOP day")
                Legend(MaterialTheme.colorScheme.onSurfaceVariant, "${target.formatShort()} target")
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
                Total("Late / short", "${p.lateDays} / ${p.shortDays}", Modifier.weight(1f))
            }
            Row {
                Total("WFH", "${p.wfhDays}", Modifier.weight(1f))
                Total("Holidays", "${p.holidays}", Modifier.weight(1f))
                Total("Leave", "${p.leaveDays}", Modifier.weight(1f))
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
    if (s.dayOff) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(s.date.format(dayRowFormat), Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(s.type.label, color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val flags = s.lopReasons.map { it.shortLabel() }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row {
            Text(
                s.date.format(dayRowFormat) + if (s.wfh) "  · WFH" else "",
                Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
            )
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
                if (!s.wfh) append(" · break ${s.breaks.formatHm()}")
                if (flags.isNotEmpty()) append(" · ${flags.joinToString()}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (flags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}
