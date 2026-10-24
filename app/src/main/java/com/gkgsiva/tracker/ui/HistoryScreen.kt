package com.gkgsiva.tracker.ui

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.domain.Settings
import com.gkgsiva.tracker.domain.PeriodSummary
import com.gkgsiva.tracker.domain.Periods
import com.gkgsiva.tracker.system.MonthSheet
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class Range(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Year") }

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
    regularized: Map<LocalDate, String>,
    regularizedOn: Map<LocalDate, LocalDate?>,
    settings: Settings,
    onOpenDay: (LocalDate) -> Unit,
    onSetType: (LocalDate, DayType) -> Unit,
) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val summaries = summarizeAll(punchesByDay, dayTypes, now, settings, regularized)
    /** Also covers days with nothing stored, such as weekly offs. */
    fun summaryOf(date: LocalDate) = summaries[date] ?: summarizeDay(date, emptyList(), now, null, settings)
    val target = settings.rules.requiredInside
    var sharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun sharePdf(month: YearMonth) {
        sharing = false
        scope.launch {
            runCatching { MonthSheet.write(context, month) }
                .onSuccess { MonthSheet.share(context, it, "Office hours ${month.format(monthRowFormat)}") }
                .onFailure { Toast.makeText(context, "Couldn't make the PDF: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    var range by rememberSaveable { mutableIntStateOf(0) }
    val selected = Range.entries[range]

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CalendarCard(today, ::summaryOf, onOpenDay, onSetType)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Range.entries.forEach { r ->
                FilterChip(selected = r == selected, onClick = { range = r.ordinal }, label = { Text(r.label, maxLines = 1) })
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
            Range.YEARLY -> YearView(summaries, today)
        }

        TargetsCard(
            week = Insights.outlook(Periods.weekStart(today), Periods.weekStart(today).plusDays(6), today, settings.rules, ::summaryOf),
            month = Insights.outlook(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1).minusDays(1), today, settings.rules, ::summaryOf),
            required = settings.rules.requiredInside,
        )
        UpcomingCard(summaries, today)
        RegularizedCard(summaries.values.filter { it.isRegularized }.sortedByDescending { it.date }, regularizedOn) { text ->
            shareText(context, "Regularized days", text)
        }
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
                    HorizontalDivider()
                    TextButton(onClick = { sharePdf(YearMonth.from(today)) }) { Text("This month as PDF") }
                    TextButton(onClick = { sharePdf(YearMonth.from(today).minusMonths(1)) }) { Text("Last month as PDF") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { sharing = false }) { Text("Cancel") } },
        )
    }
}

/** This week and month: days that met the required hours, and working days left. */
@Composable
private fun TargetsCard(week: Outlook, month: Outlook, required: Duration) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week and month", style = MaterialTheme.typography.titleMedium)
            listOf("This week" to week, "This month" to month).forEach { (label, o) ->
                Column {
                    Row {
                        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(listOfNotNull("${o.done.formatHm()} clocked", o.metText()).joinToString(" · "))
                    }
                    Text(
                        o.paceText() + if (o.lopDays > 0) " · ${o.lopDays} LOP" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (o.daysShort > 0 || o.lopDays > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Every working day needs ${required.formatShort()} inside on its own; extra hours on one day don't make up " +
                    "for another. Planned leave, holidays and weekly offs aren't counted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Planned holidays and leave, plus how much leave was taken this year. */
@Composable
private fun UpcomingCard(summaries: Map<LocalDate, DaySummary>, today: LocalDate) {
    val upcoming = summaries.values.filter { it.date > today && (it.dayOff || it.type.isPartDay) }.sortedBy { it.date }
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
                    "Nothing planned. Long-press a day on the calendar above to mark it Holiday, On leave, Half day or Short leave.",
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
            } else if (s.isRegularized) {
                Text("Regularized  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                if (!s.regularized.isNullOrEmpty()) append(" · ${s.regularized}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (s.lop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val regularizedOnFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Every regularized day with what happened, the note and when it was agreed, for HR if they ask. */
@Composable
private fun RegularizedCard(days: List<DaySummary>, regularizedOn: Map<LocalDate, LocalDate?>, onShare: (String) -> Unit) {
    if (days.isEmpty()) return
    fun line(s: DaySummary) = listOfNotNull(
        s.date.format(dayRowFormat) + if (s.date.year != LocalDate.now().year) " ${s.date.year}" else "",
        s.lopReasons.joinToString { it.shortLabel() },
        s.regularized?.takeIf { it.isNotEmpty() }?.let { "\"$it\"" },
        regularizedOn[s.date]?.let { "regularized on ${it.format(regularizedOnFormat)}" },
    ).joinToString(" · ")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Regularized (${days.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onShare("Regularized days\n" + days.joinToString("\n") { "• " + line(it) }) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share regularized days")
                }
            }
            days.forEach { s ->
                Column {
                    Row {
                        Text(s.date.format(dayRowFormat), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        regularizedOn[s.date]?.let {
                            Text("on ${it.format(dayRowFormat)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(s.lopReasons.joinToString { it.describe(s) }, style = MaterialTheme.typography.bodySmall)
                    s.regularized?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One row per month of a year: days that met the hours, LOP, regularized, leave, holidays, half and short days. */
@Composable
private fun YearView(summaries: Map<LocalDate, DaySummary>, today: LocalDate) {
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    val months = Periods.year(summaries, year)
    val columns = listOf("Met", "LOP", "Reg.", "Leave", "Hol.", "½ / SL")
    fun cells(p: PeriodSummary) = listOf(p.metDays, p.lopDays, p.regularizedDays, p.leaveDays, p.holidays, p.halfDays + p.shortLeaveDays)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year") }
                Text("$year", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }, enabled = year < today.year + 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year")
                }
            }
            YearRow("", columns, bold = true)
            HorizontalDivider()
            months.forEach { p ->
                val future = p.start > today
                YearRow(p.start.format(monthLabel), if (future && cells(p).all { it == 0 }) columns.map { "" } else cells(p).map { "$it" }, lop = true)
            }
            HorizontalDivider()
            YearRow("Total", months.map(::cells).reduce { a, b -> a.zip(b) { x, y -> x + y } }.map { "$it" }, bold = true, lop = true)
            Text(
                "Met: working days that reached the required hours. Reg.: regularized. ½ / SL: half days and short leave. " +
                    "Total inside this year: ${months.fold(Duration.ZERO) { acc, p -> acc + p.totalInside }.formatHm()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YearRow(label: String, values: List<String>, bold: Boolean = false, lop: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.3f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
        values.forEachIndexed { i, v ->
            Text(
                v,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                // The LOP column.
                color = if (lop && i == 1 && v != "0") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
