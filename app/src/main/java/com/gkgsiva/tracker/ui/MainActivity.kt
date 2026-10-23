package com.gkgsiva.tracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gkgsiva.tracker.ui.theme.TrackerTheme

class MainActivity : ComponentActivity() {
    private val vm: TrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackerTheme {
                val punchesByDay by vm.punchesByDay.collectAsStateWithLifecycle()
                val dayTypes by vm.dayTypes.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }

                // Reminders are on by default, so ask for notification permission once.
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    val prefs = getSharedPreferences("app", MODE_PRIVATE)
                    if (Build.VERSION.SDK_INT >= 33 &&
                        !prefs.getBoolean("askedNotifications", false) &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        prefs.edit().putBoolean("askedNotifications", true).apply()
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                label = { Text("Today") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                                label = { Text("History") },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                label = { Text("Settings") },
                            )
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            0 -> TodayScreen(punchesByDay, dayTypes, settings, vm)
                            1 -> HistoryScreen(punchesByDay, dayTypes, settings)
                            else -> SettingsScreen(settings, vm)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
