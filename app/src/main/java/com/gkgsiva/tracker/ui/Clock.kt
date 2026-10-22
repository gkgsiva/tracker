package com.gkgsiva.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Current time, ticking every second so live counters update. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        delay(1_000 - System.currentTimeMillis() % 1_000)
        value = LocalDateTime.now()
    }
}
