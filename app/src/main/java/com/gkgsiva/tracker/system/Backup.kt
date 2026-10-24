package com.gkgsiva.tracker.system

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.gkgsiva.tracker.data.AppDatabase
import com.gkgsiva.tracker.data.DayEntity
import com.gkgsiva.tracker.data.EditEntity
import com.gkgsiva.tracker.data.PunchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * CSV backup of all entries and day types:
 * `punch,2026-10-23,08:42:00,IN,` / `punch,…,BREAK,` / `punch,…,LOGOUT,` and `day,2026-10-24,,,LEAVE`.
 * A regularized day adds `,REGULARIZED,<note>` (the note may contain commas; it runs to the end of the line)
 * and puts the date it was regularized in the time column. Hand edits are
 * `edit,<date>,<when, ISO>,<reason without commas>,<change to the end of the line>`.
 */
object Backup {
    private const val HEADER = "kind,date,time,event,dayType"

    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { write(context, it) }
    }

    /** Replaces all data with the backup's contents. Throws with the line number on a bad file. */
    suspend fun import(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)!!.use { restore(context, it) }
    }

    /** Copy kept inside the app just before Clear data, so it can be undone. */
    private fun clearedCopy(context: Context) = File(context.filesDir, "before-clear.csv")

    suspend fun saveBeforeClear(context: Context): Int = withContext(Dispatchers.IO) {
        clearedCopy(context).outputStream().use { write(context, it) }
    }

    suspend fun undoClear(context: Context): Int = withContext(Dispatchers.IO) {
        clearedCopy(context).inputStream().use { restore(context, it) }
    }

    private const val AUTO_PREFS = "backup"
    private const val LAST_AUTO = "lastAuto"
    private val AUTO_EVERY: Duration = Duration.ofDays(7)

    /**
     * Writes a dated backup into the folder chosen in Settings once a week. Called whenever data
     * changes, so it needs no background job of its own. Failures (folder removed, permission
     * revoked) are left for the next attempt.
     */
    suspend fun autoBackupIfDue(context: Context) = withContext(Dispatchers.IO) {
        val folder = SettingsStore.get(context).backupFolder ?: return@withContext
        val prefs = context.getSharedPreferences(AUTO_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_AUTO, 0) < AUTO_EVERY.toMillis()) return@withContext
        runCatching {
            val tree = Uri.parse(folder)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val file = DocumentsContract.createDocument(
                context.contentResolver, parent, "text/csv", "office-tracker-${LocalDate.now()}.csv",
            ) ?: return@runCatching
            context.contentResolver.openOutputStream(file, "wt")!!.use { write(context, it) }
            prefs.edit().putLong(LAST_AUTO, now).apply()
        }
    }

    /** Makes the next [autoBackupIfDue] write right away, e.g. after choosing a new folder. */
    fun resetAutoBackup(context: Context) {
        context.getSharedPreferences(AUTO_PREFS, Context.MODE_PRIVATE).edit().remove(LAST_AUTO).apply()
    }

    /** When the last automatic backup was written, in epoch millis; 0 if never. */
    fun lastAutoBackup(context: Context): Long =
        context.getSharedPreferences(AUTO_PREFS, Context.MODE_PRIVATE).getLong(LAST_AUTO, 0)

    private suspend fun write(context: Context, out: OutputStream): Int {
        val dao = AppDatabase.get(context).punchDao()
        val punches = dao.allPunches()
        val days = dao.allDays()
        val edits = dao.allEdits()
        out.bufferedWriter().apply {
            appendLine(HEADER)
            punches.forEach { p ->
                val event = when {
                    p.isIn -> "IN"
                    p.isLogout -> "LOGOUT"
                    else -> "BREAK"
                }
                appendLine("punch,${p.date},${p.time},$event,")
            }
            days.forEach { d ->
                val regularized = if (d.regularized) ",REGULARIZED,${d.note.orEmpty().replace('\n', ' ')}" else ""
                val on = d.regularizedOn?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()
                appendLine("day,${LocalDate.ofEpochDay(d.epochDay)},$on,,${d.type}$regularized")
            }
            edits.forEach { e ->
                val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.atMillis), ZoneId.systemDefault()).withNano(0)
                val reason = e.reason.orEmpty().replace(',', ';').replace('\n', ' ')
                appendLine("edit,${e.date},$at,$reason,${e.change.replace('\n', ' ')}")
            }
            flush()
        }
        return punches.size + days.size + edits.size
    }

    private suspend fun restore(context: Context, input: InputStream): Int {
        val punches = mutableListOf<PunchEntity>()
        val days = mutableListOf<DayEntity>()
        val edits = mutableListOf<EditEntity>()
        input.bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line == HEADER) return@forEachIndexed
                val f = line.split(',')
                try {
                    val date = LocalDate.parse(f[1])
                    when (f[0]) {
                        "punch" -> punches += PunchEntity(
                            epochDay = date.toEpochDay(),
                            secondOfDay = LocalTime.parse(f[2]).toSecondOfDay(),
                            isIn = f[3] == "IN",
                            isLogout = f[3] == "LOGOUT",
                        )
                        "day" -> days += DayEntity(
                            epochDay = date.toEpochDay(),
                            type = requireNotNull(Tracker.parseType(f[4])).name,
                            regularized = f.getOrNull(5) == "REGULARIZED",
                            note = f.drop(6).joinToString(",").takeIf { it.isNotEmpty() },
                            regularizedOn = f.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).toEpochDay() },
                        )
                        "edit" -> edits += EditEntity(
                            epochDay = date.toEpochDay(),
                            atMillis = LocalDateTime.parse(f[2]).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            reason = f[3].takeIf { it.isNotEmpty() },
                            change = f.drop(4).joinToString(","),
                        )
                        else -> error("unknown row")
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException("Line ${i + 1} isn't a valid backup row: $line", e)
                }
            }
        }
        val db = AppDatabase.get(context)
        db.withTransaction {
            val dao = db.punchDao()
            dao.deleteAllPunches()
            dao.deleteAllDays()
            dao.deleteAllEdits()
            dao.insertAll(punches)
            dao.insertDays(days)
            dao.insertEdits(edits)
        }
        Tracker.onChanged(context)
        return punches.size + days.size + edits.size
    }
}
