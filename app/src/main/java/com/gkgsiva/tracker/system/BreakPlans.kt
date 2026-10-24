package com.gkgsiva.tracker.system

import android.content.Context
import com.gkgsiva.tracker.domain.BreakPlan
import com.gkgsiva.tracker.domain.DaySummary
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** The plan for the break in progress, kept in preferences until the next break replaces it. */
object BreakPlans {
    private const val PREFS = "breakPlan"

    fun set(context: Context, plan: BreakPlan?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (plan == null) edit.clear() else {
            edit.putLong("day", plan.date.toEpochDay())
                .putInt("start", plan.start.toSecondOfDay())
                .putLong("minutes", plan.length.toMinutes())
        }
        edit.apply()
    }

    fun get(context: Context): BreakPlan? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("day")) return null
        return BreakPlan(
            LocalDate.ofEpochDay(p.getLong("day", 0)),
            LocalTime.ofSecondOfDay(p.getInt("start", 0).toLong()),
            Duration.ofMinutes(p.getLong("minutes", 0)),
        )
    }

    /** The plan for the break [s] is on, if it has one. */
    fun current(context: Context, s: DaySummary): BreakPlan? = get(context)?.takeIf { it.appliesTo(s) }
}
