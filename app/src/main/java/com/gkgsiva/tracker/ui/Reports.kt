package com.gkgsiva.tracker.ui

import com.gkgsiva.tracker.domain.DaySummary
import com.gkgsiva.tracker.domain.PeriodSummary
import com.gkgsiva.tracker.domain.Periods
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Plain-text reports for the share sheet. */
object Reports {
    private val longDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun day(s: DaySummary): String = buildString {
        appendLine("Office Tracker · ${s.date.format(longDate)}${if (s.wfh) " (WFH)" else ""}")
        if (s.dayOff) {
            append(s.type.label)
            return@buildString
        }
        if (!s.worked) {
            appendLine("No entries.")
            return@buildString
        }
        appendLine("Login: ${s.firstIn?.formatClock()}")
        appendLine("Logout: ${s.lastOut?.takeIf { s.finished }?.formatClock() ?: "still working"}")
        appendLine("${if (s.wfh) "Worked" else "Inside office"}: ${s.inside.formatHm()}")
        if (!s.wfh) appendLine("Breaks: ${s.breaks.formatHm()} (${s.breakCount})")
        if (!s.targetMet) appendLine("Still to clock: ${s.remaining.formatHm()}")
        append("Status: ${status(s)}")
    }

    fun week(days: Map<LocalDate, DaySummary>, today: LocalDate): String {
        val start = Periods.weekStart(today)
        return period("Week of ${start.format(longDate)}", days, Periods.summarize(days, start, start.plusDays(6)))
    }

    fun month(days: Map<LocalDate, DaySummary>, today: LocalDate): String {
        val p = Periods.monthly(days, today, 1).single()
        return period(p.start.format(monthFormat), days, p)
    }

    private fun period(title: String, days: Map<LocalDate, DaySummary>, p: PeriodSummary): String = buildString {
        appendLine("Office Tracker · $title")
        appendLine("Days worked: ${p.daysWorked} (${p.wfhDays} WFH) · LOP days: ${p.lopDays}")
        appendLine("Total inside: ${p.totalInside.formatHm()} · Avg/day: ${p.avgInside.formatHm()} · Avg break: ${p.avgBreaks.formatHm()}")
        appendLine("Late logins: ${p.lateDays} · Short-hour days: ${p.shortDays}")
        appendLine("Holidays: ${p.holidays} · Leave days: ${p.leaveDays}")
        val listed = days.values
            .filter { (it.worked || it.dayOff) && it.date >= p.start && it.date <= p.endInclusive }
            .sortedBy { it.date }
        if (listed.isNotEmpty()) appendLine()
        listed.forEach { s ->
            if (s.dayOff) {
                appendLine("${s.date.format(shortDate)}: ${s.type.label}")
                return@forEach
            }
            append(s.date.format(shortDate))
            if (s.wfh) append(" (WFH)")
            append(": ${s.firstIn?.formatClock()}–${s.lastOut?.takeIf { s.finished }?.formatClock() ?: "…"}")
            append(", ${s.inside.formatHm()}")
            if (!s.wfh) append(", break ${s.breaks.formatHm()}")
            appendLine(", ${status(s)}")
        }
    }.trimEnd()

    private fun status(s: DaySummary): String = when {
        s.lop -> "LOP (${s.lopReasons.joinToString { it.shortLabel() }})"
        s.finished -> "OK"
        else -> "in progress"
    }
}
