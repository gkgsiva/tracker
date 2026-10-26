package com.gkgsiva.tracker.domain

/** A line in a day's entry list: a login, a whole break (start to end, open while it runs) or a logout. */
sealed interface EntryRow<T> {
    data class Login<T>(val entry: T) : EntryRow<T>
    data class Break<T>(val start: T, val end: T?) : EntryRow<T>
    data class Logout<T>(val entry: T) : EntryRow<T>

    companion object {
        /** Pairs each break start with the check-in after it, so a break takes one line. */
        fun <T> of(entries: List<T>, punch: (T) -> Punch): List<EntryRow<T>> {
            val rows = mutableListOf<EntryRow<T>>()
            for (e in entries.sortedBy { punch(it).time }) {
                val p = punch(e)
                val last = rows.lastOrNull()
                rows += when {
                    p.isIn && last is Break && last.end == null -> { rows.removeAt(rows.lastIndex); Break(last.start, e) }
                    p.isIn -> Login(e)
                    p.isLogout -> Logout(e)
                    else -> Break(e, null)
                }
            }
            return rows
        }
    }
}
