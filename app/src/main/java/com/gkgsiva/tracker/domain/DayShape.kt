package com.gkgsiva.tracker.domain

import java.time.LocalTime

/** A break from [start] until [end]; [end] is null for a break you never came back from (yet). */
data class BreakSpan(val start: LocalTime, val end: LocalTime?)

/**
 * A day's entries as login, breaks and logout, the way you'd describe them, so a whole day can
 * be edited at once and the change written down in words.
 */
data class DayShape(val login: LocalTime?, val breaks: List<BreakSpan>, val logout: LocalTime?) {

    fun toPunches(): List<Punch> = buildList {
        if (login == null) return@buildList
        add(Punch(login, isIn = true))
        breaks.forEach { b ->
            add(Punch(b.start, isIn = false))
            if (b.end != null) add(Punch(b.end, isIn = true))
        }
        if (logout != null) add(Punch(logout, isIn = false, isLogout = true))
    }

    /** Why this day can't be saved as it is, or null when the times line up. */
    fun problem(): String? {
        if (login == null) return if (breaks.isNotEmpty() || logout != null) "Add the login time first." else null
        var last = login
        breaks.forEachIndexed { i, b ->
            if (b.start <= last) return "Break ${i + 1} starts before the time before it."
            if (b.end == null) {
                if (i != breaks.lastIndex || logout != null) return "Only a last break with no logout can be left open."
            } else {
                if (b.end <= b.start) return "Break ${i + 1} ends before it starts."
                last = b.end
            }
        }
        if (logout != null && logout <= last) return "Logout must come after login and every break."
        return null
    }

    /** What changed from [before] to this, e.g. "login 9:05 AM → 8:55 AM; break 1:00 PM–1:30 PM added". */
    fun describeChange(before: DayShape, fmt: (LocalTime) -> String): String {
        fun span(b: BreakSpan) = fmt(b.start) + "–" + (b.end?.let(fmt) ?: "open")
        fun time(label: String, old: LocalTime?, new: LocalTime?): String? = when {
            old == new -> null
            old == null -> "$label ${fmt(new!!)} added"
            new == null -> "$label ${fmt(old)} removed"
            else -> "$label ${fmt(old)} → ${fmt(new)}"
        }
        val parts = mutableListOf<String>()
        time("login", before.login, login)?.let(parts::add)
        val removed = before.breaks - breaks.toSet()
        val added = breaks - before.breaks.toSet()
        if (removed.size == added.size) {
            removed.zip(added).forEach { (o, n) -> parts += "break ${span(o)} → ${span(n)}" }
        } else {
            removed.forEach { parts += "break ${span(it)} removed" }
            added.forEach { parts += "break ${span(it)} added" }
        }
        time("logout", before.logout, logout)?.let(parts::add)
        return parts.joinToString("; ").ifEmpty { "no change" }
    }

    companion object {
        /**
         * Reads punches back into a shape. Entries that don't fit (see [EntryIssueKind]) are
         * dropped, the same way the day's calculation skips them.
         */
        fun of(punches: List<Punch>): DayShape {
            var login: LocalTime? = null
            val breaks = mutableListOf<BreakSpan>()
            var openOut: LocalTime? = null
            var outWasLogout = false
            var inside = false
            for (p in punches.sortedBy { it.time }) {
                if (p.isIn) {
                    if (inside) continue
                    // Logging in again after a logout makes that logout a break, as in the day's calculation.
                    if (login == null) login = p.time
                    else openOut?.let { breaks += BreakSpan(it, p.time) }
                    openOut = null
                    inside = true
                } else {
                    if (!inside) continue
                    inside = false
                    openOut = p.time
                    outWasLogout = p.isLogout
                }
            }
            val logout = openOut?.takeIf { outWasLogout }
            if (openOut != null && !outWasLogout) breaks += BreakSpan(openOut, null)
            return DayShape(login, breaks, logout)
        }
    }
}
