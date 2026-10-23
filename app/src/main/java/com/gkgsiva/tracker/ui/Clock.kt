package com.gkgsiva.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * Current time, ticking every second so live counters update. Ticks only while the app is on
 * screen, so nothing runs in the background.
 */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now()
                delay(1_000 - System.currentTimeMillis() % 1_000)
            }
        }
    }
}
