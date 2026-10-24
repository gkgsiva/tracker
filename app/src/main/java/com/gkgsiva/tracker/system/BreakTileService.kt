package com.gkgsiva.tracker.system

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.system.TrackerWidget.Companion.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Quick settings tile: one tap to log in, start or end a break, also from the lock screen. */
class BreakTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var action: Action? = null

    override fun onStartListening() {
        scope.launch { refresh() }
    }

    override fun onClick() {
        val next = action ?: return
        scope.launch {
            withContext(Dispatchers.IO) { Tracker.punchNow(applicationContext, next.isIn, next.isLogout) }
            refresh()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refresh() {
        val s = withContext(Dispatchers.IO) { Tracker.summary(applicationContext, LocalDate.now()) }
        val tile = qsTile ?: return
        action = when (s.status) {
            DayStatus.NOT_STARTED -> Action.LOGIN
            DayStatus.INSIDE -> if (s.wfh) null else Action.BREAK_START
            DayStatus.ON_BREAK -> Action.BREAK_END
            DayStatus.DONE, DayStatus.MISSING_CHECKOUT, DayStatus.DAY_OFF -> null
        }
        tile.label = action?.label ?: "Office Tracker"
        tile.state = when {
            action == null -> Tile.STATE_UNAVAILABLE
            s.status == DayStatus.ON_BREAK -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (s.status) {
                DayStatus.ON_BREAK -> "On break"
                DayStatus.INSIDE -> "Inside"
                DayStatus.DAY_OFF -> s.type.label
                DayStatus.DONE -> "Logged out"
                else -> null
            }
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to refresh the tile after a change elsewhere. */
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, BreakTileService::class.java)) }
        }
    }
}
