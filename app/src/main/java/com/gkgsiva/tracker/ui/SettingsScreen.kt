package com.gkgsiva.tracker.ui

import android.Manifest
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
import com.gkgsiva.tracker.domain.Settings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
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
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun setReminder(s: Settings) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        update(s)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri, ::toast)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Office rules") {
            SettingRow("Log in by", rules.arriveBy.formatClock(), onClick = { editingTime = TimeSetting.ARRIVE })
            SettingRow("Leave after", rules.leaveAfter.formatClock(), onClick = { editingTime = TimeSetting.LEAVE })
            Stepper(
                "Hours inside",
                rules.requiredInside,
                step = Duration.ofMinutes(30),
                min = Duration.ofHours(1),
                max = Duration.ofHours(12),
            ) { update(settings.copy(rules = rules.copy(requiredInside = it))) }
            Stepper(
                "Break allowance",
                rules.breakAllowance,
                step = Duration.ofMinutes(15),
                min = Duration.ZERO,
                max = Duration.ofHours(3),
            ) { update(settings.copy(rules = rules.copy(breakAllowance = it))) }
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
                "When a break uses up the ${rules.breakAllowance.formatShort()} allowance",
                settings.remindBreak,
            ) { setReminder(settings.copy(remindBreak = it)) }
        }

        Section("Backup") {
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
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Clear all data") }
        }

        Hint("Tip: long-press your home screen → Widgets → Office Tracker for one-tap login and breaks.")
    }

    editingTime?.let { which ->
        TimeDialog(
            title = if (which == TimeSetting.ARRIVE) "Log in by" else "Leave after",
            initial = if (which == TimeSetting.ARRIVE) rules.arriveBy else rules.leaveAfter,
            onDismiss = { editingTime = null },
            onPick = { t ->
                update(settings.copy(rules = if (which == TimeSetting.ARRIVE) rules.copy(arriveBy = t) else rules.copy(leaveAfter = t)))
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
            text = { Text("This permanently deletes every login, break, logout, and WFH, holiday and leave day. Export a backup first if you might need it.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.clearAll(); confirmClear = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private enum class TimeSetting { ARRIVE, LEAVE }

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
