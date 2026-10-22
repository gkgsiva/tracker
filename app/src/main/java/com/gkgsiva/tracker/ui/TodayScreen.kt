package com.gkgsiva.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
fun TodayScreen(punchesByDay: Map<LocalDate, List<PunchEntity>>, vm: TrackerViewModel) {
    val now by rememberNow()
    val today = now.toLocalDate()
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(selectedEpochDay)
    val isToday = date == today
    val punches = punchesByDay[date].orEmpty().sortedBy { it.secondOfDay }
    val summary = summarizeDay(date, punches, now)

    var editing by remember { mutableStateOf<PunchEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PunchEntity?>(null) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selectedEpochDay-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            Text(
                if (isToday) "Today · ${date.format(dateFormat)}" else date.format(dateFormat),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (!isToday) {
                TextButton(onClick = { selectedEpochDay = today.toEpochDay() }) { Text("Today") }
            }
            IconButton(onClick = { selectedEpochDay++ }, enabled = !isToday) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }

        StatusCard(summary, live = isToday)
        LopCard(summary)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Inside", summary.inside.formatHm(), Modifier.weight(1f))
            StatTile(
                "Break",
                summary.breaks.formatHm(),
                Modifier.weight(1f),
                sub = if (summary.breakExceeded) "${summary.breakLeft.negated().formatHm()} over" else "${summary.breakLeft.formatHm()} left",
                warn = summary.breakExceeded,
            )
            StatTile("To go", summary.remaining.formatHm(), Modifier.weight(1f), sub = "of 8h")
        }

        DetailsCard(summary)

        if (isToday) {
            val nextIsIn = summary.status != DayStatus.INSIDE
            Button(
                onClick = { vm.punchNow(isIn = nextIsIn) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = if (nextIsIn) ButtonDefaults.buttonColors()
                else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) {
                Text(
                    when {
                        summary.status == DayStatus.NOT_STARTED -> "Log in for the day"
                        nextIsIn -> "Check in"
                        else -> "Check out"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                if (punches.isEmpty()) {
                    Text(
                        "No entries for this day.",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                punches.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    PunchRow(p, isFirst = i == 0, onEdit = { editing = p }, onDelete = { deleting = p })
                }
            }
        }
    }

    if (adding) {
        val lastIsIn = punches.lastOrNull()?.isIn ?: false
        PunchDialog(
            title = "Add entry",
            initialTime = if (isToday) now.toLocalTime() else LocalTime.of(9, 0),
            initialIsIn = !lastIsIn,
            onDismiss = { adding = false },
            onConfirm = { time, isIn -> vm.add(date, time, isIn); adding = false },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = { Text("${if (p.isIn) "Check in" else "Check out"} at ${p.time.formatClock()}") },
            confirmButton = { TextButton(onClick = { vm.delete(p); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    editing?.let { p ->
        PunchDialog(
            title = "Edit entry",
            initialTime = p.time,
            initialIsIn = p.isIn,
            onDismiss = { editing = null },
            onConfirm = { time, isIn -> vm.update(p, time, isIn); editing = null },
        )
    }
}

@Composable
private fun StatusCard(s: DaySummary, live: Boolean) {
    val (label, color) = when (s.status) {
        DayStatus.NOT_STARTED -> (if (live) "Not logged in yet" else "No entries") to MaterialTheme.colorScheme.onSurfaceVariant
        DayStatus.INSIDE -> "Inside office" to MaterialTheme.colorScheme.primary
        DayStatus.ON_BREAK -> "On break" to MaterialTheme.colorScheme.tertiary
        DayStatus.DONE -> (if (s.lop) "Day complete · LOP" else "Day complete") to
            (if (s.lop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        DayStatus.MISSING_CHECKOUT -> "Missing check-out" to MaterialTheme.colorScheme.error
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(
                s.inside.formatHms(),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text("inside office of 8h required", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { (s.inside.seconds / s.rules.requiredInside.seconds.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            if (s.status == DayStatus.ON_BREAK) {
                Text(
                    "Total break so far: ${s.breaks.formatHms()}",
                    color = if (s.breakExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun LopCard(s: DaySummary) {
    if (!s.lop) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LOP day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            s.lopReasons.forEach { Text("• ${it.describe(s)}") }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier, sub: String? = null, warn: Boolean = false) {
    Card(
        modifier,
        colors = if (warn) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailsCard(s: DaySummary) {
    if (!s.worked) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailLine(
                "First login",
                s.firstIn!!.formatClock() + if (s.lateArrival) "  (after ${s.rules.arriveBy.formatClock()})" else "",
                warn = s.lateArrival,
            )
            DetailLine("Breaks taken", "${s.breakCount}")
            s.returnBy?.let { DetailLine("Return from break by", it.formatClock()) }
            s.expectedLeave?.let { DetailLine("You can leave at", it.formatClock()) }
            if (s.status == DayStatus.DONE && s.lastOut != null) {
                DetailLine(
                    "Left at",
                    s.lastOut.formatClock() + if (s.earlyLeave) "  (before ${s.rules.leaveAfter.formatClock()})" else "",
                    warn = s.earlyLeave,
                )
            }
            if (s.status == DayStatus.MISSING_CHECKOUT) {
                Text("Add the check-out time to count this day.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, warn: Boolean = false) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontWeight = FontWeight.Medium,
            color = if (warn) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }
}

@Composable
private fun PunchRow(p: PunchEntity, isFirst: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.time.formatClock(), Modifier.width(96.dp), fontWeight = FontWeight.Medium)
        Text(
            when {
                isFirst && p.isIn -> "Login"
                p.isIn -> "Check in"
                else -> "Check out"
            },
            Modifier.weight(1f),
            color = if (p.isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PunchDialog(
    title: String,
    initialTime: LocalTime,
    initialIsIn: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime, Boolean) -> Unit,
) {
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = false)
    var isIn by remember { mutableStateOf(initialIsIn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isIn, onClick = { isIn = true }, label = { Text("Check in") })
                    FilterChip(selected = !isIn, onClick = { isIn = false }, label = { Text("Check out") })
                }
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute), isIn) }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
