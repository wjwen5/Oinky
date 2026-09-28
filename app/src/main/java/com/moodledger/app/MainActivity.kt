package com.moodledger.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moodledger.app.ui.calendar.CalendarScreen
import com.moodledger.app.ui.day.DayScreen
import com.moodledger.app.ui.insights.InsightsScreen
import com.moodledger.app.ui.recurring.RecurringScreen
import com.moodledger.app.ui.settings.SettingsScreen
import com.moodledger.app.ui.theme.MoodLedgerTheme
import com.moodledger.app.work.Notifications
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Destination requested by a notification tap; consumed by the nav host. */
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDestination.value = intent.getStringExtra(Notifications.EXTRA_DESTINATION)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MoodLedgerTheme {
                AppNav(pendingDestination.value) { pendingDestination.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDestination.value = intent.getStringExtra(Notifications.EXTRA_DESTINATION)
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("calendar", "Diary", Icons.Filled.CalendarMonth),
    Tab("recurring", "Recurring", Icons.Filled.Repeat),
    Tab("insights", "Insights", Icons.Filled.Insights),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

fun NavHostController.openDay(date: LocalDate) = navigate("day/${date.toEpochDay()}")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppNav(destination: String?, onDestinationHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(destination) {
        when (destination) {
            "today" -> nav.openDay(LocalDate.now())
            "recurring" -> nav.navigate("recurring")
        }
        if (destination != null) onDestinationHandled()
    }

    // Inner screens own their insets; this Scaffold only reserves room for the bottom bar.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "calendar", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("calendar") { CalendarScreen(onOpenDay = nav::openDay) }
            composable(
                "day/{epochDay}",
                arguments = listOf(navArgument("epochDay") { type = NavType.LongType }),
            ) { entry ->
                DayScreen(
                    epochDay = entry.arguments!!.getLong("epochDay"),
                    onBack = { nav.popBackStack() },
                    onOpenDay = { date ->
                        nav.navigate("day/${date.toEpochDay()}") { popUpTo("calendar") }
                    },
                )
            }
            composable("recurring") { RecurringScreen() }
            composable("insights") { InsightsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
