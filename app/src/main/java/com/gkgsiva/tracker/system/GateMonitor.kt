package com.gkgsiva.tracker.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gkgsiva.tracker.R
import com.gkgsiva.tracker.domain.DayStatus
import com.gkgsiva.tracker.system.TrackerWidget.Companion.Action
import com.gkgsiva.tracker.ui.MainActivity
import com.gkgsiva.tracker.ui.formatClock
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Optional gate auto-login. A small geofence around the saved office gate; crossing it only offers
 * the punch (at the moment you crossed) in a notification, it never writes one by itself.
 * The system watches the geofence, so the app does nothing in the background.
 */
object GateMonitor {
    private const val CHANNEL = "gate"
    private const val ID = 20
    private const val FENCE = "gate"

    fun hasForeground(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackground(context: Context) = Build.VERSION.SDK_INT < 29 ||
        granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** Adds the gate geofence when a gate is set and location is allowed, otherwise removes it. */
    @SuppressLint("MissingPermission")
    fun register(context: Context) {
        val client = LocationServices.getGeofencingClient(context)
        val gate = SettingsStore.get(context).gate
        val pending = pending(context)
        client.removeGeofences(pending)
        if (gate == null || !hasForeground(context) || !hasBackground(context)) return
        val fence = Geofence.Builder()
            .setRequestId(FENCE)
            .setCircularRegion(gate.latitude, gate.longitude, gate.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(fence)
            .build()
        runCatching { client.addGeofences(request, pending) }
    }

    fun cancelPrompt(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GateReceiver::class.java),
        // Play services fills in the geofence event, so this one has to be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    /** Offers the punch that fits crossing the gate, timed to when you crossed it. */
    internal suspend fun onTransition(context: Context, entered: Boolean, atMillis: Long) {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
        val s = Tracker.summary(context, at.toLocalDate())
        if (s.dayOff || s.wfh) return
        val time = at.toLocalTime().formatClock()
        val (title, actions) = if (entered) {
            when (s.status) {
                DayStatus.NOT_STARTED, DayStatus.DONE, DayStatus.MISSING_CHECKOUT ->
                    "At the gate · log in at $time?" to listOf(Action.LOGIN)
                DayStatus.ON_BREAK -> "Back at the gate · end break at $time?" to listOf(Action.BREAK_END)
                else -> return
            }
        } else {
            if (s.status != DayStatus.INSIDE) return
            "Left the gate at $time" to listOf(Action.BREAK_START, Action.LOGOUT)
        }
        prompt(context, title, actions, atMillis)
    }

    private fun prompt(context: Context, title: String, actions: List<Action>, atMillis: Long) {
        if (Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Gate prompts", NotificationManager.IMPORTANCE_HIGH))
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Tap to record it at the time you crossed the gate.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
        actions.forEach { builder.addAction(0, it.label, TrackerWidget.punchIntent(context, it, atMillis)) }
        nm.notify(ID, builder.build())
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    class GateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = GeofencingEvent.fromIntent(intent) ?: return
            if (event.hasError()) return
            val entered = when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> true
                Geofence.GEOFENCE_TRANSITION_EXIT -> false
                else -> return
            }
            val atMillis = event.triggeringLocation?.time ?: System.currentTimeMillis()
            goAsyncIo { onTransition(context, entered, atMillis) }
        }
    }
}
