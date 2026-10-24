package com.gkgsiva.tracker.system

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gkgsiva.tracker.data.AppDatabase
import com.gkgsiva.tracker.data.DayEntity
import com.gkgsiva.tracker.data.PunchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * CSV backup of all entries and day types:
 * `punch,2026-10-23,08:42:00,IN,` / `punch,…,BREAK,` / `punch,…,LOGOUT,` and `day,2026-10-24,,,LEAVE`.
 * A regularized day adds `,REGULARIZED,<note>` (the note may contain commas; it runs to the end of the line).
 */
object Backup {
    private const val HEADER = "kind,date,time,event,dayType"

    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).punchDao()
        val punches = dao.allPunches()
        val days = dao.allDays()
        val lines = buildList {
            add(HEADER)
            punches.forEach { p ->
                val event = when {
                    p.isIn -> "IN"
                    p.isLogout -> "LOGOUT"
                    else -> "BREAK"
                }
                add("punch,${p.date},${p.time},$event,")
            }
            days.forEach { d ->
                val regularized = if (d.regularized) ",REGULARIZED,${d.note.orEmpty().replace('\n', ' ')}" else ""
                add("day,${LocalDate.ofEpochDay(d.epochDay)},,,${d.type}$regularized")
            }
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { w ->
            lines.forEach { w.appendLine(it) }
        }
        punches.size + days.size
    }

    /** Replaces all data with the backup's contents. Throws with the line number on a bad file. */
    suspend fun import(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val punches = mutableListOf<PunchEntity>()
        val days = mutableListOf<DayEntity>()
        context.contentResolver.openInputStream(uri)!!.bufferedReader().useLines { lines ->
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
            dao.insertAll(punches)
            dao.insertDays(days)
        }
        Tracker.onChanged(context)
        punches.size + days.size
    }
}
