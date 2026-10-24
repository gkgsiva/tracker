package com.gkgsiva.tracker.system

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.gkgsiva.tracker.domain.DayShape
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.DayType
import com.gkgsiva.tracker.ui.Insights
import com.gkgsiva.tracker.ui.formatClock
import com.gkgsiva.tracker.ui.formatHm
import com.gkgsiva.tracker.ui.metText
import com.gkgsiva.tracker.ui.shortLabel
import com.gkgsiva.tracker.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** A one-page PDF of a month, one line per day, ready to send to a manager or HR. */
object MonthSheet {
    private val title = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d")

    // A4 in points.
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 36f
    private const val ROW = 21f

    /** Column titles and their left edges. */
    private val columns = listOf(
        "Date" to 0f, "Type" to 62f, "Login" to 132f, "Logout" to 192f, "Breaks" to 252f, "Inside" to 300f, "Status" to 352f,
    )

    suspend fun write(context: Context, month: YearMonth): File = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }.associateWith { Tracker.summary(context, it, now) }
        val dao = AppDatabase.get(context).punchDao()
        val shapes = days.keys.associateWith { d -> DayShape.of(dao.punchesFor(d.toEpochDay()).map { it.toPunch() }) }
        val rules = SettingsStore.get(context).rules
        val outlook = Insights.outlook(month.atDay(1), month.atEndOfMonth(), today, rules) { days.getValue(it) }

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 1).create())
        val c = page.canvas
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val muted = Paint(text).apply { color = 0xFF666666.toInt() }
        val red = Paint(text).apply { color = 0xFFC62828.toInt() }
        val line = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.6f }
        val shade = Paint().apply { color = 0xFFF4F4F4.toInt() }

        var y = MARGIN + 16f
        c.drawText("Office hours · ${month.format(title)}", MARGIN, y, Paint(bold).apply { textSize = 16f })
        y += 16f
        c.drawText(
            "Core hours ${rules.arriveBy.formatClock()}–${rules.leaveAfter.formatClock()} · ${rules.requiredInside.formatHm()} inside a day",
            MARGIN, y, muted,
        )
        y += 22f
        columns.forEach { (name, x) -> c.drawText(name, MARGIN + x, y, bold) }
        y += 6f
        c.drawLine(MARGIN, y, WIDTH - MARGIN, y, line)

        days.values.forEach { s ->
            y += ROW
            if (s.dayOff) c.drawRect(MARGIN, y - ROW + 6f, WIDTH - MARGIN, y + 6f, shade)
            val shape = shapes.getValue(s.date)
            val cells = listOf(
                s.date.format(dayFormat),
                s.type.label,
                shape.login?.formatClock().orEmpty(),
                shape.logout?.formatClock().orEmpty(),
                if (shape.breaks.isEmpty()) "" else s.breaks.formatHm(),
                if (s.worked) s.inside.formatHm() else "",
                status(s, today),
            )
            cells.forEachIndexed { i, v ->
                val paint = when {
                    i == cells.lastIndex && s.lop -> red
                    s.dayOff -> muted
                    else -> text
                }
                c.drawText(v, MARGIN + columns[i].second, y, paint)
            }
        }
        y += 10f
        c.drawLine(MARGIN, y, WIDTH - MARGIN, y, line)
        y += 18f
        val regularized = days.values.count { it.isRegularized }
        val summary = listOfNotNull(
            outlook.metText() ?: "No finished working days",
            "${outlook.lopDays} LOP",
            if (regularized > 0) "$regularized regularized" else null,
            "${days.values.count { it.type == DayType.LEAVE }} leave",
            "${days.values.count { it.type == DayType.HOLIDAY }} holiday",
            "${outlook.done.formatHm()} inside in total",
        ).joinToString(" · ")
        c.drawText(summary, MARGIN, y, bold)
        y += 14f
        c.drawText("Made with Office Tracker on ${today.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", MARGIN, y, muted)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "sheets").apply { mkdirs() }
        val file = File(dir, "office-hours-$month.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        file
    }

    private fun status(s: DaySummary, today: LocalDate): String = when {
        s.dayOff -> ""
        s.date > today -> "planned"
        s.lop -> "LOP: " + s.lopReasons.joinToString { it.shortLabel() }
        s.isRegularized -> "Regularized (" + s.lopReasons.joinToString { it.shortLabel() } + ")"
        s.status == DayStatus.NOT_STARTED -> if (s.date == today) "" else "nothing logged"
        s.finished -> if (s.targetMet) "OK" else "short"
        else -> "in progress"
    }

    /** Opens the share sheet with [file]. */
    fun share(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share month sheet").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
