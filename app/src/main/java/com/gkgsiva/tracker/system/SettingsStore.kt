package com.gkgsiva.tracker.system

import android.content.Context
import com.gkgsiva.tracker.domain.Rules
import com.gkgsiva.tracker.domain.Settings
import com.gkgsiva.tracker.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** Settings kept in SharedPreferences, exposed as a flow so screens update when they change. */
object SettingsStore {
    private const val PREFS = "settings"
    @Volatile private var state: MutableStateFlow<Settings>? = null

    fun flow(context: Context): StateFlow<Settings> = state ?: synchronized(this) {
        state ?: MutableStateFlow(load(context)).also { state = it }
    }

    fun get(context: Context): Settings = flow(context).value

    fun save(context: Context, settings: Settings) {
        val r = settings.rules
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("arriveBy", r.arriveBy.toSecondOfDay() / 60)
            .putInt("leaveAfter", r.leaveAfter.toSecondOfDay() / 60)
            .putLong("requiredMinutes", r.requiredInside.toMinutes())
            .putLong("requiredPresentMinutes", r.requiredPresent.toMinutes())
            // Official start and end hours were replaced by the in-office hours in 1.11.
            .remove("officeStart").remove("officeEnd")
            .putLong("halfDayMinutes", r.halfDayHours.toMinutes())
            .putLong("shortLeaveMinutes", r.shortLeave.toMinutes())
            .putStringSet("weeklyOffs", settings.weeklyOffs.map { it.name }.toSet())
            .putBoolean("remindLogin", settings.remindLogin)
            .putBoolean("remindLeave", settings.remindLeave)
            .putBoolean("remindBreak", settings.remindBreak)
            .putBoolean("statusNotification", settings.statusNotification)
            .putString("theme", settings.theme.name)
            .putString("backupFolder", settings.backupFolder)
            .putBoolean("monthSummary", settings.monthSummary)
            .putInt("breakPlan", settings.breakPlanMinutes ?: 0)
            .putInt("breakPlanLead", settings.breakPlanLeadMinutes)
            // Left over from the gate auto-login removed in 1.8.
            .remove("gateLat").remove("gateLng").remove("gateRadius")
            .apply()
        (flow(context) as MutableStateFlow).value = settings
    }

    private fun load(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Settings()
        return Settings(
            rules = Rules(
                arriveBy = LocalTime.ofSecondOfDay(p.getInt("arriveBy", d.rules.arriveBy.toSecondOfDay() / 60) * 60L),
                leaveAfter = LocalTime.ofSecondOfDay(p.getInt("leaveAfter", d.rules.leaveAfter.toSecondOfDay() / 60) * 60L),
                requiredInside = Duration.ofMinutes(p.getLong("requiredMinutes", d.rules.requiredInside.toMinutes())),
                requiredPresent = Duration.ofMinutes(p.getLong("requiredPresentMinutes", d.rules.requiredPresent.toMinutes())),
                halfDayHours = Duration.ofMinutes(p.getLong("halfDayMinutes", d.rules.halfDayHours.toMinutes())),
                shortLeave = Duration.ofMinutes(p.getLong("shortLeaveMinutes", d.rules.shortLeave.toMinutes())),
            ),
            weeklyOffs = p.getStringSet("weeklyOffs", null)
                ?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: d.weeklyOffs,
            remindLogin = p.getBoolean("remindLogin", d.remindLogin),
            remindLeave = p.getBoolean("remindLeave", d.remindLeave),
            remindBreak = p.getBoolean("remindBreak", d.remindBreak),
            statusNotification = p.getBoolean("statusNotification", d.statusNotification),
            theme = ThemeMode.entries.firstOrNull { it.name == p.getString("theme", null) } ?: d.theme,
            backupFolder = p.getString("backupFolder", null),
            monthSummary = p.getBoolean("monthSummary", d.monthSummary),
            breakPlanMinutes = p.getInt("breakPlan", d.breakPlanMinutes ?: 0).takeIf { it > 0 },
            breakPlanLeadMinutes = p.getInt("breakPlanLead", d.breakPlanLeadMinutes),
        )
    }
}
