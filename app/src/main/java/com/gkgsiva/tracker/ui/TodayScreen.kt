package com.gkgsiva.tracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/** What a punch means to the user. Stored as isIn + isLogout. */
private enum class EntryKind(val label: String, val isIn: Boolean, val isLogout: Boolean) {
    IN("Login / break end", true, false),
    BREAK("Break start", false, false),
    LOGOUT("Logout", false, true),
}

private val PunchEntity.kind: EntryKind
    get() = when {
        isIn -> EntryKind.IN
        isLogout -> EntryKind.LOGOUT
        else -> EntryKind.BREAK
    }

private fun PunchEntity.label(isFirst: Boolean): String = when (kind) {
    EntryKind.IN -> if (isFirst) "Login" else "Break end"
    EntryKind.BREAK -> "Break start"
    EntryKind.LOGOUT -> "Logout"
}

@Composable
fun TodayScreen(punchesByDay: Map<LocalDate, List<PunchEntity>>, dayTypes: Map<LocalDate, DayType>, vm: TrackerViewModel) {
    val now by rememberNow()
    val today = now.toLocalDate()
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(selectedEpochDay)
    val isToday = date == today
    val punches = punchesByDay[date].orEmpty().sortedBy { it.secondOfDay }
    val context = LocalContext.current
    val type = dayTypes[date] ?: DayType.OFFICE
    val wfh = type == DayType.WFH
    val summary = summarizeDay(date, punches, now, type)

    var editing by remember { mutableStateOf<PunchEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PunchEntity?>(null) }
    var confirmLogout by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** Logs out, asking first when doing it now would make the day LOP. */
    fun logout(action: () -> Unit) {
        val t = now.toLocalTime()
        if (t < summary.rules.leaveAfter || !summary.targetMet) confirmLogout = action else action()
    }

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
            IconButton(onClick = { shareText(context, "Office report ${date.format(dateFormat)}", Reports.day(summary)) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share report")
            }
            IconButton(onClick = { selectedEpochDay++ }, enabled = !isToday) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }

        StatusCard(summary, live = isToday)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayType.entries.forEach { t ->
                        ChoiceChip(t.label, selected = type == t, onClick = { vm.setDayType(date, t) }, Modifier.weight(1f))
                    }
                }
                Text(
                    when (type) {
                        DayType.OFFICE -> "Login, breaks and logout are tracked"
                        DayType.WFH -> "Only first login and last logout count"
                        DayType.HOLIDAY, DayType.LEAVE -> "No login needed, never LOP"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isToday && !summary.dayOff) {
            ActionButtons(
                status = summary.status,
                wfh = wfh,
                onLogin = { vm.punchNow(isIn = true) },
                onBreakStart = { vm.punchNow(isIn = false) },
                onBreakEnd = { vm.punchNow(isIn = true) },
                onLogout = { logout { vm.punchNow(isIn = false, isLogout = true) } },
                onLeftDuringBreak = { punches.lastOrNull()?.let { last -> logout { vm.convertToLogout(last) } } },
            )
        }

        LopCard(summary)

        if (!summary.dayOff) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(if (wfh) "Worked" else "Inside", summary.inside.formatHm(), Modifier.weight(1f))
            if (!wfh) {
                StatTile(
                    "Break",
                    summary.breaks.formatHm(),
                    Modifier.weight(1f),
                    sub = if (summary.breakExceeded) "${summary.breakLeft.negated().formatHm()} over 1h" else "${summary.breakLeft.formatHm()} of 1h left",
                )
            }
            StatTile("To go", summary.remaining.formatHm(), Modifier.weight(1f), sub = "of 8h")
        }

        if (!summary.dayOff) DetailsCard(summary)

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
        val last = punches.lastOrNull()
        PunchDialog(
            title = "Add entry",
            initialTime = if (isToday) now.toLocalTime() else LocalTime.of(9, 0),
            initialKind = when {
                last == null || !last.isIn -> EntryKind.IN
                isToday && !wfh -> EntryKind.BREAK
                else -> EntryKind.LOGOUT
            },
            wfh = wfh,
            onDismiss = { adding = false },
            onConfirm = { time, kind -> vm.add(date, time, kind.isIn, kind.isLogout); adding = false },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = { Text("${p.label(isFirst = p == punches.firstOrNull())} at ${p.time.formatClock()}") },
            confirmButton = { TextButton(onClick = { vm.delete(p); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    editing?.let { p ->
        PunchDialog(
            title = "Edit entry",
            initialTime = p.time,
            initialKind = p.kind,
            wfh = wfh,
            onDismiss = { editing = null },
            onConfirm = { time, kind -> vm.update(p, time, kind.isIn, kind.isLogout); editing = null },
        )
    }
    confirmLogout?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmLogout = null },
            title = { Text("Log out for the day?") },
            text = {
                Text(
                    buildString {
                        append("Logging out now will make today LOP")
                        val reasons = buildList {
                            if (now.toLocalTime() < summary.rules.leaveAfter) add("it's before ${summary.rules.leaveAfter.formatClock()}")
                            if (!summary.targetMet) add("you still need ${summary.remaining.formatHm()} inside")
                        }
                        append(": ").append(reasons.joinToString(" and ")).append('.')
                    }
                )
            },
            confirmButton = { TextButton(onClick = { action(); confirmLogout = null }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = null }) { Text("Cancel") } },
        )
    }
}

/** Equal-width chip with single-line, centered text, for a row of choices. */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        },
    )
}

@Composable
private fun ActionButtons(
    status: DayStatus,
    wfh: Boolean,
    onLogin: () -> Unit,
    onBreakStart: () -> Unit,
    onBreakEnd: () -> Unit,
    onLogout: () -> Unit,
    onLeftDuringBreak: () -> Unit,
) {
    val big = Modifier.height(64.dp)
    val tertiary = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
    )
    when (status) {
        DayStatus.NOT_STARTED -> Button(onClick = onLogin, modifier = big.fillMaxWidth()) {
            Text("Log in", style = MaterialTheme.typography.titleLarge)
        }
        DayStatus.INSIDE -> if (wfh) {
            OutlinedButton(onClick = onLogout, modifier = big.fillMaxWidth()) {
                Text("Log out", style = MaterialTheme.typography.titleLarge)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBreakStart, modifier = big.weight(1f), colors = tertiary) {
                Text("Start break", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onLogout, modifier = big.weight(1f)) {
                Text("Log out", style = MaterialTheme.typography.titleMedium)
            }
        }
        DayStatus.ON_BREAK -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onBreakEnd, modifier = big.fillMaxWidth()) {
                Text("End break", style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = onLeftDuringBreak) { Text("I've left for the day") }
        }
        DayStatus.DONE, DayStatus.MISSING_CHECKOUT -> OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Log in again")
        }
        DayStatus.DAY_OFF -> Unit
    }
}

@Composable
private fun StatusCard(s: DaySummary, live: Boolean) {
    if (s.dayOff) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(s.type.label, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text("Day off · no login needed", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val (label, target) = when (s.status) {
        DayStatus.NOT_STARTED -> (if (live) "Not logged in yet" else "No entries") to MaterialTheme.colorScheme.onSurfaceVariant
        DayStatus.INSIDE -> (if (s.wfh) "Working from home" else "Inside office") to MaterialTheme.colorScheme.primary
        DayStatus.ON_BREAK -> "On break" to MaterialTheme.colorScheme.tertiary
        DayStatus.DONE -> (if (s.lop) "Logged out · LOP" else "Day complete") to
            (if (s.lop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        DayStatus.MISSING_CHECKOUT -> "Missing logout" to MaterialTheme.colorScheme.error
        DayStatus.DAY_OFF -> s.type.label to MaterialTheme.colorScheme.primary
    }
    val color by animateColorAsState(target, tween(600), label = "status")
    val progress = s.inside.seconds / s.rules.requiredInside.seconds.toFloat()

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (live && (s.status == DayStatus.INSIDE || s.status == DayStatus.ON_BREAK)) PulsingDot(color)
                Text(label, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
            }
            val doneColor = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
            val fill by animateColorAsState(
                when {
                    s.finished && s.lop -> MaterialTheme.colorScheme.error
                    progress >= 1f -> doneColor
                    s.status == DayStatus.ON_BREAK -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                tween(800),
                label = "fill",
            )
            ClockDial(
                progress = progress,
                fillColor = fill,
                faceColor = MaterialTheme.colorScheme.surfaceVariant,
                handColor = MaterialTheme.colorScheme.onSurface,
                tickColor = MaterialTheme.colorScheme.surface,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(
                    s.inside.formatHms(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    "${if (s.wfh) "worked" else "inside"} of 8h · ${(progress * 100).toInt().coerceAtMost(999)}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.status == DayStatus.ON_BREAK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total break  ", color = MaterialTheme.colorScheme.tertiary)
                    RollingText(
                        s.breaks.formatHms(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
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
private fun StatTile(title: String, value: String, modifier: Modifier, sub: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RollingText(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
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
            if (!s.wfh) DetailLine("Breaks taken", "${s.breakCount}")
            s.returnBy?.let { DetailLine("Back by (to stay within 1h)", it.formatClock()) }
            s.expectedLeave?.let { DetailLine("You can leave at", it.formatClock()) }
            if (s.status == DayStatus.DONE && s.lastOut != null) {
                DetailLine(
                    "Logged out at",
                    s.lastOut.formatClock() + if (s.earlyLeave) "  (before ${s.rules.leaveAfter.formatClock()})" else "",
                    warn = s.earlyLeave,
                )
            }
            if (s.status == DayStatus.MISSING_CHECKOUT) {
                Text("Add the logout time to count this day.", color = MaterialTheme.colorScheme.error)
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
            p.label(isFirst),
            Modifier.weight(1f),
            color = when (p.kind) {
                EntryKind.IN -> MaterialTheme.colorScheme.primary
                EntryKind.BREAK -> MaterialTheme.colorScheme.tertiary
                EntryKind.LOGOUT -> MaterialTheme.colorScheme.onSurface
            },
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
    initialKind: EntryKind,
    wfh: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime, EntryKind) -> Unit,
) {
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = false)
    var kind by remember { mutableStateOf(initialKind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EntryKind.entries.filter { !wfh || it != EntryKind.BREAK }.forEach { k ->
                        ChoiceChip(
                            if (k == EntryKind.IN) "In" else k.label,
                            selected = kind == k,
                            onClick = { kind = k },
                            Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute), kind) }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
