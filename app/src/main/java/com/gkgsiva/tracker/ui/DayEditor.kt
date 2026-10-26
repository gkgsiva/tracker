package com.gkgsiva.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gkgsiva.tracker.data.EditEntity
import com.gkgsiva.tracker.domain.BreakLabel
import com.gkgsiva.tracker.domain.BreakSpan
import com.gkgsiva.tracker.domain.DayShape
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Which time in the editor is being picked. */
private sealed interface Slot {
    data object Login : Slot
    data object Logout : Slot
    data class BreakStart(val index: Int) : Slot
    data class BreakEnd(val index: Int) : Slot
}

/**
 * Edits a whole day at once: login, each break and logout, plus why it changed. The change is
 * written to the day's history in words.
 */
@Composable
fun DayEditorDialog(initial: DayShape, defaultLogin: LocalTime, defaultLogout: LocalTime, onDismiss: () -> Unit, onSave: (DayShape, String) -> Unit) {
    var shape by remember { mutableStateOf(initial) }
    var reason by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<Slot?>(null) }
    val problem = shape.problem()

    fun setBreak(i: Int, b: BreakSpan) {
        shape = shape.copy(breaks = shape.breaks.toMutableList().also { it[i] = b })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit day") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TimeLine("Login", shape.login) { picking = Slot.Login }
                shape.breaks.forEachIndexed { i, b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(BreakLabel.textOf(b.label), Modifier.weight(1f))
                        TextButton(onClick = { picking = Slot.BreakStart(i) }) { Text(b.start.formatClock()) }
                        Text("–")
                        TextButton(onClick = { picking = Slot.BreakEnd(i) }) { Text(b.end?.formatClock() ?: "open") }
                        IconButton(onClick = { shape = shape.copy(breaks = shape.breaks.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove break ${i + 1}")
                        }
                    }
                }
                TextButton(
                    onClick = {
                        // A new break goes after everything so far, as a 30-minute placeholder to adjust.
                        val after = shape.breaks.lastOrNull()?.let { it.end ?: it.start } ?: shape.login ?: defaultLogin
                        val start = after.plusMinutes(60)
                        shape = shape.copy(breaks = shape.breaks + BreakSpan(start, start.plusMinutes(30), BreakLabel.guess(start).name))
                    },
                    enabled = shape.login != null,
                ) { Text("Add break") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeLine("Logout", shape.logout, Modifier.weight(1f)) { picking = Slot.Logout }
                    if (shape.logout != null) {
                        IconButton(onClick = { shape = shape.copy(logout = null) }) { Icon(Icons.Filled.Close, contentDescription = "Remove logout") }
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Why (optional)") },
                    placeholder = { Text("e.g. forgot to log the break") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (problem != null) Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(shape, reason) }, enabled = problem == null && (shape != initial || reason.isNotBlank())) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )

    picking?.let { slot ->
        val current = when (slot) {
            Slot.Login -> shape.login ?: defaultLogin
            Slot.Logout -> shape.logout ?: defaultLogout
            is Slot.BreakStart -> shape.breaks[slot.index].start
            is Slot.BreakEnd -> shape.breaks[slot.index].let { it.end ?: it.start.plusMinutes(30) }
        }
        TimePickDialog(current, onDismiss = { picking = null }) { t ->
            picking = null
            when (slot) {
                Slot.Login -> shape = shape.copy(login = t)
                Slot.Logout -> shape = shape.copy(logout = t)
                is Slot.BreakStart -> setBreak(slot.index, shape.breaks[slot.index].copy(start = t))
                is Slot.BreakEnd -> setBreak(slot.index, shape.breaks[slot.index].copy(end = t))
            }
        }
    }
}

@Composable
internal fun TimeLine(label: String, time: LocalTime?, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Row(modifier.clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(time?.formatClock() ?: "Not set", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val editTime = DateTimeFormatter.ofPattern("d MMM, h:mm a")

/** Changes made to this day by hand, newest first. */
@Composable
fun ChangesCard(edits: List<EditEntity>) {
    if (edits.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Changes", style = MaterialTheme.typography.titleMedium)
            edits.asReversed().forEach { e ->
                Column {
                    Text(e.change.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                    val at = Instant.ofEpochMilli(e.atMillis).atZone(ZoneId.systemDefault()).format(editTime)
                    Text(
                        listOfNotNull(at, e.reason).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
