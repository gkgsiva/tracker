package com.gkgsiva.tracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.BuildConfig
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.domain.Rules
import com.gkgsiva.tracker.domain.Settings
import com.gkgsiva.tracker.domain.ThemeMode
import com.gkgsiva.tracker.system.Backup
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(settings: Settings, vm: TrackerViewModel) {
    val context = LocalContext.current
    val rules = settings.rules
    fun update(s: Settings) = vm.updateSettings(s)
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    var editingTime by remember { mutableStateOf<TimeSetting?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun setReminder(s: Settings) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        update(s)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri, ::toast)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
                .onSuccess { vm.setBackupFolder(uri); toast("Weekly backup on. The first one is saved now.") }
                .onFailure { toast("Couldn't use that folder: ${it.message}") }
        }
    }
    // Set after Clear data so it can be undone from this screen.
    var justCleared by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Office rules") {
            Hint("Core hours: logging in after this or leaving before this is LOP.")
            TimeSetting.entries.forEach { SettingRow(it.label, it.get(rules).formatClock(), onClick = { editingTime = it }) }
            Stepper(
                "Work hours (no breaks)",
                rules.requiredInside,
                step = Duration.ofMinutes(30),
                min = Duration.ofHours(1),
                max = Duration.ofHours(12),
            ) { update(settings.copy(rules = rules.copy(requiredInside = it))) }
            Stepper(
                "In office (with breaks)",
                rules.requiredPresent,
                step = Duration.ofMinutes(30),
                min = rules.requiredInside,
                max = Duration.ofHours(14),
            ) { update(settings.copy(rules = rules.copy(requiredPresent = it))) }
            Hint(
                "Each day needs ${rules.requiredInside.formatShort()} of work not counting breaks, and " +
                    "${rules.requiredPresent.formatShort()} from login to logout counting them, so you can take " +
                    "${rules.breakAllowance(null).formatShort()} of break and still leave at login + ${rules.requiredPresent.formatShort()}. " +
                    "Missing either is LOP; a longer break just means staying later."
            )
        }

        Section("Half day and short leave") {
            Stepper(
                "Half day hours",
                rules.halfDayHours,
                step = Duration.ofMinutes(30),
                min = Duration.ofHours(1),
                max = rules.requiredInside,
            ) { update(settings.copy(rules = rules.copy(halfDayHours = it))) }
            Hint("A half day needs this much inside, at any time of day.")
            Stepper(
                "Short leave",
                rules.shortLeave,
                step = Duration.ofMinutes(30),
                min = Duration.ofMinutes(30),
                max = Duration.ofHours(4),
            ) { update(settings.copy(rules = rules.copy(shortLeave = it))) }
            val short = rules.forType(DayType.SHORT_LEAVE)
            Hint(
                "On a short-leave day you need ${short.requiredInside.formatShort()} inside, and can log in by " +
                    "${short.arriveBy.formatClock()} or leave after ${short.leaveAfter.formatClock()}."
            )
        }

        Section("Appearance") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { update(settings.copy(theme = mode)) },
                        modifier = Modifier.weight(1f),
                        label = { Text(mode.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    )
                }
            }
            Hint("System follows your phone's dark mode setting.")
        }

        Section("Weekly off days") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in settings.weeklyOffs
                    FilterChip(
                        selected = on,
                        onClick = {
                            update(settings.copy(weeklyOffs = if (on) settings.weeklyOffs - day else settings.weeklyOffs + day))
                        },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
            Hint("These days need no login unless you log in anyway.")
        }

        Section("Reminders") {
            SwitchRow(
                "Log in reminder",
                "15 minutes before ${rules.arriveBy.formatClock()} on working days",
                settings.remindLogin,
            ) { setReminder(settings.copy(remindLogin = it)) }
            SwitchRow(
                "Time to leave",
                "When you've done ${rules.requiredInside.formatShort()} and it's after ${rules.leaveAfter.formatClock()}",
                settings.remindLeave,
            ) { setReminder(settings.copy(remindLeave = it)) }
            SwitchRow(
                "Long break",
                "5 minutes before your break allowance runs out, and when it does",
                settings.remindBreak,
            ) { setReminder(settings.copy(remindBreak = it)) }
            Stepper(
                "Break limit",
                Duration.ofMinutes(settings.breakPlanMinutes.toLong()),
                step = Duration.ofMinutes(5),
                min = Duration.ofMinutes(5),
                max = Duration.ofMinutes(90),
            ) { setReminder(settings.copy(breakPlanMinutes = it.toMinutes().toInt())) }
            Stepper(
                "Extend by",
                Duration.ofMinutes(settings.breakExtendMinutes.toLong()),
                step = Duration.ofMinutes(1),
                min = Duration.ofMinutes(1),
                max = Duration.ofMinutes(30),
            ) { setReminder(settings.copy(breakExtendMinutes = it.toMinutes().toInt())) }
            Stepper(
                "Alert before limit",
                Duration.ofMinutes(settings.breakPlanLeadMinutes.toLong()),
                step = Duration.ofMinutes(1),
                min = Duration.ofMinutes(1),
                max = Duration.ofMinutes(10),
            ) { setReminder(settings.copy(breakPlanLeadMinutes = it.toMinutes().toInt())) }
            Hint("A break starts with no limit. Tap Set limit during a break to give it the break limit above, and Extend to add more. You're alerted shortly before the limit, and again when it's up.")
            SwitchRow(
                "Month summary",
                "On the 1st: days that met ${rules.requiredInside.formatShort()}, LOP, regularized and total hours for the month just ended",
                settings.monthSummary,
            ) { setReminder(settings.copy(monthSummary = it)) }
            SwitchRow(
                "Status notification",
                "While logged in: countdown to ${rules.requiredInside.formatShort()}, break used, and break / log out buttons on the lock screen",
                settings.statusNotification,
            ) { setReminder(settings.copy(statusNotification = it)) }
        }

        Section("Backup") {
            if (justCleared) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All data deleted.", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.undoClear(::toast); justCleared = false }) { Text("Undo") }
                }
            }
            Hint("Save everything to a CSV file (Drive, Downloads, …) and restore it on a new phone.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("office-tracker-${LocalDate.now()}.csv") },
                    modifier = Modifier.weight(1f),
                ) { Text("Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Import") }
            }
            val folder = settings.backupFolder
            SwitchRow(
                "Weekly backup",
                if (folder == null) "Saves a dated CSV to a folder you choose, once a week"
                else "To ${Uri.parse(folder).lastPathSegment?.substringAfterLast(':') ?: "your folder"}" +
                    Backup.lastAutoBackup(context).takeIf { it > 0 }?.let {
                        " · last on " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(backupDate)
                    }.orEmpty(),
                folder != null,
            ) { on -> if (on) folderLauncher.launch(null) else vm.setBackupFolder(null) }
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Clear all data") }
        }

        Hint("Office Tracker version ${BuildConfig.VERSION_NAME}")
        Hint(
            "Tips: long-press your home screen → Widgets → Office Tracker for one-tap login and breaks. " +
                "Edit your quick settings panel to add the Office Tracker tile for breaks from the lock screen."
        )
    }

    editingTime?.let { which ->
        TimeDialog(
            title = which.label,
            initial = which.get(rules),
            onDismiss = { editingTime = null },
            onPick = { t ->
                update(settings.copy(rules = which.set(rules, t)))
                editingTime = null
            },
        )
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces all current entries and day types with the backup's contents.") },
            confirmButton = {
                TextButton(onClick = { vm.import(uri, ::toast); pendingImport = null }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all data?") },
            text = { Text("This deletes every login, break, logout, and WFH, holiday and leave day. A copy is kept on the phone so you can undo it right after.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.clearAll(); confirmClear = false; justCleared = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private val backupDate = DateTimeFormatter.ofPattern("d MMM")

private enum class TimeSetting(val label: String, val get: (Rules) -> LocalTime, val set: (Rules, LocalTime) -> Rules) {
    ARRIVE("Log in by", { it.arriveBy }, { r, t -> r.copy(arriveBy = t) }),
    LEAVE("Leave after", { it.leaveAfter }, { r, t -> r.copy(leaveAfter = t) }),
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Stepper(label: String, value: Duration, step: Duration, min: Duration, max: Duration, onChange: (Duration) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange(maxOf(min, value - step)) }, enabled = value > min) { Text("−") }
        Text(value.formatShort(), Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange(minOf(max, value + step)) }, enabled = value < max) { Text("+") }
    }
}

@Composable
private fun SwitchRow(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Hint(detail)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
