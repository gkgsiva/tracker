package com.gkgsiva.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")

/** How a day looks on the calendar. */
private enum class CalendarMark(val label: String) {
    MET("8h, no LOP"),
    LOP("LOP"),
    REGULARIZED("Regularized"),
    OFF("Holiday / leave"),
}

private fun DaySummary.mark(today: LocalDate): CalendarMark? = when {
    type == DayType.HOLIDAY || type == DayType.LEAVE -> CalendarMark.OFF
    lop -> CalendarMark.LOP
    isRegularized -> CalendarMark.REGULARIZED
    finished && date <= today -> CalendarMark.MET
    else -> null
}

@Composable
private fun CalendarMark.color(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (this) {
        CalendarMark.MET -> if (dark) Color(0xFF2E5E33) else Color(0xFFC8E6C9)
        CalendarMark.LOP -> MaterialTheme.colorScheme.errorContainer
        CalendarMark.REGULARIZED -> MaterialTheme.colorScheme.tertiaryContainer
        CalendarMark.OFF -> MaterialTheme.colorScheme.secondaryContainer
    }
}

/** A month grid with each day colored by how it went; tapping a day opens it. */
@Composable
fun CalendarCard(today: LocalDate, summaryOf: (LocalDate) -> DaySummary, onOpenDay: (LocalDate) -> Unit) {
    var monthEpochDay by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    val month = LocalDate.ofEpochDay(monthEpochDay)
    val firstWeekday = DayOfWeek.MONDAY
    val lead = (month.dayOfWeek.value - firstWeekday.value + 7) % 7
    val days = month.lengthOfMonth()
    val cells = List(lead) { null } + (1..days).map { month.withDayOfMonth(it) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthEpochDay = month.minusMonths(1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    month.format(monthTitle),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { monthEpochDay = month.plusMonths(1).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row {
                DayOfWeek.entries.forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f)) { if (date != null) DayCell(summaryOf(date), today, onOpenDay) }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            CalendarMark.entries.chunked(2).forEach { pair ->
                Row(Modifier.padding(top = 4.dp)) {
                    pair.forEach { m ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(m.color(), RoundedCornerShape(3.dp)))
                            Text("  ${m.label}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(s: DaySummary, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val mark = s.mark(today)
    val shape = RoundedCornerShape(8.dp)
    var modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    if (mark != null) modifier = modifier.background(mark.color(), shape)
    if (s.date == today) modifier = modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    if (s.status == DayStatus.MISSING_CHECKOUT) modifier = modifier.border(2.dp, MaterialTheme.colorScheme.error, shape)
    Box(
        modifier.clickable { onOpenDay(s.date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${s.date.dayOfMonth}",
            fontWeight = if (s.date == today) FontWeight.Bold else FontWeight.Normal,
            color = if (s.type == DayType.WEEKLY_OFF) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
