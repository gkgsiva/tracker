package com.gkgsiva.tracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.data.PunchEntity
import com.gkgsiva.tracker.domain.BreakLabel
import com.gkgsiva.tracker.domain.BreakPlan
import com.gkgsiva.tracker.domain.DayShape
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.domain.EntryIssue
import com.gkgsiva.tracker.domain.EntryIssueKind
import com.gkgsiva.tracker.domain.EntryRow
import com.gkgsiva.tracker.domain.Rules
import com.gkgsiva.tracker.domain.Settings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val shortDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM")

/** What a punch means to the user. Stored as isIn + isLogout. */
/** Login and Break end are both check-ins; they differ only in where they sit in the day. */
private enum class EntryKind(val label: String, val isIn: Boolean, val isLogout: Boolean) {
    IN("Login", true, false),
    BREAK("Break start", false, false),
    BREAK_END("Break end", true, false),
    LOGOUT("Logout", false, true),
}

/** The kind of [this] among [day]'s entries: a check-in after an earlier one is a break end. */
private fun PunchEntity.kindIn(day: List<PunchEntity>): EntryKind = when {
    isIn -> if (day.any { it.isIn && it.secondOfDay < secondOfDay }) EntryKind.BREAK_END else EntryKind.IN
    isLogout -> EntryKind.LOGOUT
    else -> EntryKind.BREAK
}

/**
 * Why an entry of [kind] at [time] doesn't fit [others] (the day's other entries), or null.
 * The same checks the day's calculation flags as issues, shown before saving.
 */
private fun entryProblem(kind: EntryKind, time: LocalTime, others: List<PunchEntity>): String? {
    val before = others.filter { it.time < time }.maxByOrNull { it.secondOfDay }
    val after = others.filter { it.time > time }.minByOrNull { it.secondOfDay }
    return when (kind) {
        EntryKind.IN -> if (others.any { it.isIn && it.time < time }) "There's already a login earlier; this would be a break end." else null
        EntryKind.BREAK_END -> when {
            before == null -> "Nothing before this time; use Login for the first entry."
            before.isIn -> "The entry before (${before.time.formatClock()}) isn't a break start."
            after != null && after.isIn -> "The next entry (${after.time.formatClock()}) is also a check-in."
            else -> null
        }
        EntryKind.BREAK, EntryKind.LOGOUT -> when {
            before == null -> "There's no login before this time."
            !before.isIn -> "The entry before (${before.time.formatClock()}) is already a check-out."
            else -> null
        }
    }
}

@Composable
fun TodayScreen(
    punchesByDay: Map<LocalDate, List<PunchEntity>>,
    dayTypes: Map<LocalDate, DayType>,
    regularized: Map<LocalDate, String>,
    settings: Settings,
    vm: TrackerViewModel,
    openDay: LocalDate? = null,
    onOpened: () -> Unit = {},
) {
    val now by rememberNow()
    val today = now.toLocalDate()
    var selectedEpochDay by rememberSaveable { mutableStateOf(openDay?.toEpochDay() ?: today.toEpochDay()) }
    LaunchedEffect(openDay) {
        if (openDay != null) {
            selectedEpochDay = openDay.toEpochDay()
            onOpened()
        }
    }
    val date = LocalDate.ofEpochDay(selectedEpochDay)
    val isToday = date == today
    val punches = punchesByDay[date].orEmpty().sortedBy { it.secondOfDay }
    val context = LocalContext.current
    val isFuture = date > today
    val summary = summarizeDay(date, punches, now, dayTypes[date], settings, regularized[date])
    val type = summary.type
    val wfh = summary.wfh
    val rules = settings.rules
    var pickingDate by remember { mutableStateOf(false) }

    var editing by remember { mutableStateOf<PunchEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    // What a delete removes (a break is its start and end) and how to describe it.
    var deleting by remember { mutableStateOf<Pair<String, List<PunchEntity>>?>(null) }
    var editingBreak by remember { mutableStateOf<EntryRow.Break<PunchEntity>?>(null) }
    var confirmLogout by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editingDay by remember { mutableStateOf(false) }
    val edits by vm.edits.collectAsStateWithLifecycle()
    val breakPlan = if (isToday) vm.breakPlan(summary) else null

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
            // The year only when it isn't this year, so the date fits beside the buttons.
            val shown = date.format(if (date.year == today.year) shortDateFormat else dateFormat)
            Text(
                if (isToday) "Today · $shown" else shown,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                modifier = Modifier.weight(1f).clickable { pickingDate = true }.padding(vertical = 8.dp),
            )
            if (!isToday) {
                TextButton(onClick = { selectedEpochDay = today.toEpochDay() }) { Text("Today") }
            }
            IconButton(onClick = { shareText(context, "Office report ${date.format(dateFormat)}", Reports.day(summary)) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share report")
            }
            IconButton(onClick = { selectedEpochDay++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }

        // Forgot to log out on a recent working day: offer to fill it in.
        if (isToday) {
            val missed = (1L..7L).map { today.minusDays(it) }.firstOrNull { d ->
                summarizeDay(d, punchesByDay[d].orEmpty(), now, dayTypes[d], settings, regularized[d]).status == DayStatus.MISSING_CHECKOUT
            }
            if (missed != null) MissedLogoutCard(missed.format(shortDateFormat)) {
                selectedEpochDay = missed.toEpochDay()
                adding = true
            }
        }

        StatusCard(summary, live = isToday, future = isFuture)

        if (!isFuture) IssuesCard(summary.issues) { issue ->
            val p = punches.firstOrNull { it.time == issue.time }
            when {
                p == null -> Unit
                issue.kind == EntryIssueKind.OPEN_BREAK -> vm.convertToLogout(p)
                else -> editing = p
            }
        }

        if (isToday && type == DayType.WEEKLY_OFF) {
            OutlinedButton(onClick = { vm.punchNow(isIn = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Log in anyway")
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

        val breakStart = punches.lastOrNull()?.takeIf { !it.isIn && !it.isLogout }
        if (isToday && summary.status == DayStatus.ON_BREAK && breakStart != null) {
            BreakCard(
                label = BreakLabel.of(breakStart.label),
                plan = breakPlan,
                now = now.toLocalTime(),
                settings = settings,
                onLabel = { vm.setBreakLabel(breakStart, it) },
                onSetLimit = { vm.setBreakLimit(settings.breakPlanMinutes) },
                onExtend = { vm.extendBreak() },
                onClear = { vm.setBreakLimit(null) },
            )
        }

        if (!summary.dayOff && !isFuture) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Worked", summary.inside.formatHm(), Modifier.weight(1f), sub = "of ${summary.rules.requiredInside.formatShort()}, no breaks")
            if (!wfh) {
                StatTile(
                    "Break",
                    summary.breaks.formatHm(),
                    Modifier.weight(1f),
                    sub = if (summary.breakExceeded) "${summary.breakLeft.negated().formatHm()} over ${summary.breakAllowance.formatShort()}"
                    else "${summary.breakLeft.formatHm()} of ${summary.breakAllowance.formatShort()} left",
                )
            }
            // Until every rule is met: the work hours, the in-office hours and the leave-after time.
            val untilLeave = summary.expectedLeave?.let { Duration.between(now.toLocalTime(), it).coerceAtLeast(Duration.ZERO) }
            StatTile(
                "To go",
                (untilLeave ?: summary.remaining).formatHm(),
                Modifier.weight(1f),
                sub = summary.expectedLeave?.let { "leave at ${it.formatClock()}" } ?: Outlook.hoursOf(summary.rules),
            )
        }

        LopCard(summary, onRegularize = { note -> vm.setRegularized(date, summary.type, note) })

        // A day off with nothing logged stays quiet: no stats, outlook or empty entry list.
        val quiet = summary.dayOff && punches.isEmpty()

        if (isToday && !quiet) {
            fun summaryOf(d: LocalDate) = summarizeDay(d, punchesByDay[d].orEmpty(), now, dayTypes[d], settings, regularized[d])
            val monthStart = today.withDayOfMonth(1)
            OutlookCard(
                forecast = Insights.forecast(summary, now.toLocalTime()),
                month = Insights.outlook(monthStart, monthStart.plusMonths(1).minusDays(1), today, rules, ::summaryOf),
            )
        }

        DayTypeRow(type, rules, onPick = { vm.setDayType(date, it) })

        if (!summary.dayOff && !isFuture) DetailsCard(summary)

        if (quiet && !isFuture) {
            TextButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add an entry for this day") }
        } else if (!isFuture) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (punches.isNotEmpty()) TextButton(onClick = { editingDay = true }) { Text("Edit day") }
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
                val rows = EntryRow.of(punches) { it.toPunch() }
                if (rows.any { it is EntryRow.Break }) EntryHeader()
                rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    when (row) {
                        is EntryRow.Login -> EntryLine("Login", MaterialTheme.colorScheme.primary, row.entry.time) { editing = row.entry }
                        is EntryRow.Logout -> EntryLine("Logout", MaterialTheme.colorScheme.onSurface, row.entry.time) { editing = row.entry }
                        is EntryRow.Break -> {
                            val endAt = row.end?.time ?: if (isToday) now.toLocalTime() else null
                            EntryLine(
                                BreakLabel.textOf(row.start.label),
                                MaterialTheme.colorScheme.tertiary,
                                row.start.time,
                                end = row.end?.time?.formatClock() ?: "–",
                                total = endAt?.let { Duration.between(row.start.time, it).coerceAtLeast(Duration.ZERO).formatBreak() } ?: "–",
                            ) { editingBreak = row }
                        }
                    }
                }
            }
        }

        ChangesButton(edits[date].orEmpty())
    }

    if (editingDay) {
        DayEditorDialog(
            initial = DayShape.of(punches.map { it.toPunch() }),
            defaultLogin = rules.arriveBy,
            defaultLogout = rules.arriveBy.plus(rules.requiredPresent),
            onDismiss = { editingDay = false },
            onSave = { shape, reason -> vm.saveDay(date, shape, reason); editingDay = false },
        )
    }
    if (adding) {
        val last = punches.lastOrNull()
        PunchDialog(
            title = "Add entry",
            initialTime = when {
                isToday -> now.toLocalTime()
                last?.isIn == true -> maxOf(rules.leaveAfter, (summary.firstIn ?: rules.arriveBy).plus(summary.rules.requiredPresent))
                else -> rules.arriveBy
            },
            initialKind = when {
                last == null -> EntryKind.IN
                !last.isIn -> if (wfh) EntryKind.IN else EntryKind.BREAK_END
                isToday && !wfh -> EntryKind.BREAK
                else -> EntryKind.LOGOUT
            },
            others = punches,
            wfh = wfh,
            onDismiss = { adding = false },
            onConfirm = { time, kind, label -> vm.add(date, time, kind.isIn, kind.isLogout, label); adding = false },
        )
    }
    deleting?.let { (what, entries) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(if (entries.size > 1) "Delete break?" else "Delete entry?") },
            text = { Text(what) },
            confirmButton = {
                TextButton(onClick = { vm.deleteBreak(entries[0], entries.getOrNull(1)); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    editing?.let { p ->
        val kind = p.kindIn(punches)
        PunchDialog(
            title = "Edit entry",
            initialTime = p.time,
            initialKind = kind,
            initialLabel = BreakLabel.of(p.label),
            others = punches - p,
            wfh = wfh,
            onDismiss = { editing = null },
            onConfirm = { time, k, label -> vm.update(p, time, k.isIn, k.isLogout, label); editing = null },
            onDelete = { editing = null; deleting = "${kind.label} at ${p.time.formatClock()}" to listOf(p) },
        )
    }
    editingBreak?.let { row ->
        BreakDialog(
            row = row,
            onDismiss = { editingBreak = null },
            onSave = { label, from, to -> vm.saveBreak(row.start, row.end, label, from, to); editingBreak = null },
            onDelete = {
                editingBreak = null
                val span = row.start.time.formatClock() + (row.end?.let { " – " + it.time.formatClock() } ?: "")
                deleting = "${BreakLabel.textOf(row.start.label)} break $span" to listOfNotNull(row.start, row.end)
            },
        )
    }
    if (pickingDate) {
        DateDialog(
            initial = date,
            onDismiss = { pickingDate = false },
            onPick = { selectedEpochDay = it.toEpochDay(); pickingDate = false },
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
                            if (!summary.workMet) add("you've worked ${summary.inside.formatHm()} of ${summary.rules.requiredInside.formatShort()}")
                            if (!summary.presentMet) add("you've been in the office ${summary.present.formatHm()} of ${summary.rules.requiredPresent.formatShort()}")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

/** Equal-width chip with centered text that wraps rather than being cut off. */
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
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        },
    )
}

@Composable
private fun MissedLogoutCard(day: String, onFix: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("No logout on $day", fontWeight = FontWeight.SemiBold)
                Text("Add the time you left so the day counts.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onFix) { Text("Add logout") }
        }
    }
}

/** Entries that don't fit together, each with a one-tap fix or a jump to editing it. */
@Composable
private fun IssuesCard(issues: List<EntryIssue>, onFix: (EntryIssue) -> Unit) {
    if (issues.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Check these entries", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            issues.forEach { issue ->
                val t = issue.time.formatClock()
                val (text, fix) = when (issue.kind) {
                    EntryIssueKind.OPEN_BREAK -> "Break started at $t never ended. Was it your logout?" to "Mark as logout"
                    EntryIssueKind.OUT_BEFORE_LOGIN -> "Check-out at $t comes before the first login" to "Edit"
                    EntryIssueKind.DOUBLE_IN -> "Login at $t follows another login with no break between" to "Edit"
                    EntryIssueKind.DOUBLE_OUT -> "Check-out at $t follows another check-out" to "Edit"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onFix(issue) }) { Text(fix) }
                }
            }
            Text("These entries are skipped until fixed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The day's type as one compact row; tapping it opens the choices with what each means. */
@Composable
private fun DayTypeRow(type: DayType, rules: Rules, onPick: (DayType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Box {
            Row(
                Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Day type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(type.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(type.meaning(rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Change", color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DayType.entries.filter { it.choosable }.forEach { t ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(t.label, fontWeight = if (t == type) FontWeight.Bold else FontWeight.Normal)
                                Text(t.meaning(rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = { open = false; if (t != type) onPick(t) },
                    )
                }
            }
        }
    }
}

private fun DayType.meaning(rules: Rules): String = when (this) {
    DayType.OFFICE -> "Login, breaks and logout are tracked"
    DayType.WFH -> "Only first login and last logout count"
    DayType.HOLIDAY, DayType.LEAVE -> "No login needed, never LOP"
    DayType.HALF_DAY -> "${rules.halfDayHours.formatShort()} inside at any time, no core hours"
    DayType.SHORT_LEAVE -> rules.forType(this).let {
        "${it.requiredInside.formatShort()} inside, log in by ${it.arriveBy.formatClock()}, leave after ${it.leaveAfter.formatClock()}"
    }
    DayType.WEEKLY_OFF -> "Weekly off · choose Office if you're working"
}

/** The break in progress: what it's for, and its limit with buttons to set or extend it. */
@Composable
private fun BreakCard(
    label: BreakLabel?,
    plan: BreakPlan?,
    now: LocalTime,
    settings: Settings,
    onLabel: (BreakLabel) -> Unit,
    onSetLimit: () -> Unit,
    onExtend: () -> Unit,
    onClear: () -> Unit,
) {
    val left = plan?.left(now)
    val over = left?.isNegative == true
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelChips(label, onLabel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plan == null || left == null) {
                    Text("No limit", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onSetLimit) { Text("Set ${settings.breakPlanMinutes}m limit") }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (over) "${left.negated().formatHm()} over your ${plan.length.formatShort()} limit"
                            else "${left.formatHm()} left of ${plan.length.formatShort()}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Back at ${plan.end.formatClock()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onExtend) { Text("Extend ${settings.breakExtendMinutes}m") }
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Remove limit") }
                }
            }
        }
    }
}

/** Breakfast, Tea, Lunch and Other, two to a row so the names fit at any text size. */
@Composable
private fun LabelChips(selected: BreakLabel?, onPick: (BreakLabel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BreakLabel.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { l -> ChoiceChip(l.text, selected = selected == l, onClick = { onPick(l) }, Modifier.weight(1f)) }
            }
        }
    }
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
    val big = Modifier.heightIn(min = 64.dp)
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
private fun StatusCard(s: DaySummary, live: Boolean, future: Boolean) {
    if (future && !s.dayOff) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Upcoming", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("${s.type.label} day · change the day type below to plan leave", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        return
    }
    if (s.dayOff) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(s.type.label, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (future) "Planned day off" else "Day off · no login needed", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val doneColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)
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
                    "${if (s.wfh) "worked" else "inside"} of ${s.rules.requiredInside.formatShort()} · ${(progress * 100).toInt().coerceAtMost(999)}%",
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

/**
 * What made the day LOP. It can be marked regularized (agreed with the manager), with an optional
 * note, which stops it counting as LOP; [onRegularize] gets null to undo that.
 */
@Composable
private fun LopCard(s: DaySummary, onRegularize: (String?) -> Unit) {
    if (s.lopReasons.isEmpty()) return
    var asking by remember { mutableStateOf(false) }
    val regularized = s.isRegularized
    Card(
        Modifier.fillMaxWidth(),
        colors = if (regularized) CardDefaults.cardColors() else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (regularized) "Regularized" else "LOP day",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (regularized) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            s.lopReasons.forEach { Text("• ${it.describe(s)}") }
            if (regularized) {
                s.regularized?.takeIf { it.isNotEmpty() }?.let { Text("Note: $it", fontStyle = FontStyle.Italic) }
                Text("Not counted as LOP.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onRegularize(null) }) { Text("Undo regularize") }
            } else {
                OutlinedButton(onClick = { asking = true }) { Text("Regularize") }
            }
        }
    }
    if (asking) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Regularize this day?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use this when your manager has approved it. The day stops counting as LOP, and the reason stays visible.")
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(120) },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onRegularize(note); asking = false }) { Text("Regularize") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OutlookCard(forecast: Forecast?, month: Outlook) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Outlook", style = MaterialTheme.typography.titleMedium)
            forecast?.let {
                Text(
                    it.message,
                    color = if (it.atRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            DetailLine("LOP days this month", "${month.lopDays}", warn = month.lopDays > 0)
            // Label above the line, so long figures wrap as a whole instead of squeezing the label.
            Text("This month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOfNotNull("${month.done.formatHm()} worked", month.metText()).joinToString(" · "))
            Text(month.paceText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (!s.wfh) DetailLine(
                "In office (with breaks)",
                "${s.present.formatHm()} of ${s.rules.requiredPresent.formatShort()}",
                warn = s.finished && !s.presentMet,
            )
            s.returnBy?.let { DetailLine("Back from break by", it.formatClock()) }
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

/** Column titles over the entry lines, shown once the day has a break. */
@Composable
private fun EntryHeader() {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 52.dp, top = 4.dp)) {
        listOf("", "Start", "End", "Total").forEachIndexed { i, t ->
            Text(
                t,
                Modifier.weight(if (i == 0) 1.2f else 1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (i == 3) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

/** One line of the entry list: a login or logout, or a whole break with its end and length. */
@Composable
private fun EntryLine(
    label: String,
    color: Color,
    start: LocalTime,
    end: String = "",
    total: String = "",
    onEdit: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(label, 1.2f, color)
        if (end.isEmpty() && total.isEmpty()) {
            // A login or logout has only a time, so it gets the rest of the line.
            Cell(start.formatClock(), 3f, bold = true)
        } else {
            Cell(start.formatClock(), 1f, bold = true)
            Cell(end, 1f, bold = true)
            Cell(total, 1f, align = TextAlign.End)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit $label") }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, color: Color = Color.Unspecified, align: TextAlign = TextAlign.Start, bold: Boolean = false) = Text(
    text,
    Modifier.weight(weight),
    color = color,
    textAlign = align,
    fontWeight = if (bold) FontWeight.Medium else null,
    // Wraps at large text sizes rather than cutting a time off.
    maxLines = 2,
)

/** Edits a whole break: what it was for, when it started and (once over) when it ended. */
@Composable
private fun BreakDialog(
    row: EntryRow.Break<PunchEntity>,
    onDismiss: () -> Unit,
    onSave: (BreakLabel, LocalTime, LocalTime?) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember { mutableStateOf(BreakLabel.of(row.start.label) ?: BreakLabel.guess(row.start.time)) }
    var from by remember { mutableStateOf(row.start.time) }
    var to by remember { mutableStateOf(row.end?.time) }
    // Which time is being picked: the start (false) or the end (true).
    var picking by remember { mutableStateOf<Boolean?>(null) }
    val problem = to?.let { if (it <= from) "The break ends before it starts." else null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit break") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabelChips(label) { label = it }
                Spacer(Modifier.height(4.dp))
                TimeLine("Start", from) { picking = false }
                val end = to
                if (end != null) TimeLine("End", end) { picking = true }
                else Text("Still on this break", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (problem != null) Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label, from, to) }, enabled = problem == null) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    picking?.let { endSlot ->
        TimePickDialog(if (endSlot) to ?: from else from, onDismiss = { picking = null }) { t ->
            if (endSlot) to = t else from = t
            picking = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PunchDialog(
    title: String,
    initialTime: LocalTime,
    initialKind: EntryKind,
    others: List<PunchEntity>,
    wfh: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime, EntryKind, BreakLabel?) -> Unit,
    initialLabel: BreakLabel? = null,
    onDelete: (() -> Unit)? = null,
) {
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = false)
    var kind by remember { mutableStateOf(initialKind) }
    // Until one is picked, a break start shows the guess for its time.
    var label by remember { mutableStateOf(initialLabel) }
    val shownLabel = label ?: BreakLabel.guess(LocalTime.of(state.hour, state.minute))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Two per row so the labels have room at any text size.
                EntryKind.entries.filter { !wfh || (it != EntryKind.BREAK && it != EntryKind.BREAK_END) }.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k ->
                            ChoiceChip(
                                k.label,
                                selected = kind == k,
                                onClick = { kind = k },
                                Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (kind == EntryKind.BREAK) {
                    Spacer(Modifier.height(8.dp))
                    LabelChips(shownLabel) { label = it }
                }
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
                entryProblem(kind, LocalTime.of(state.hour, state.minute), others)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute), kind, if (kind == EntryKind.BREAK) shownLabel else null) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
