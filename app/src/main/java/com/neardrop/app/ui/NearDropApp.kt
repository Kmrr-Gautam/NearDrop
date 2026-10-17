package com.neardrop.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.neardrop.app.ui.components.*
import com.neardrop.app.ui.screens.*
import kotlinx.coroutines.delay

private val tabs = listOf(
    Triple("home", Icons.Default.Home, "Home"),
    Triple("transfers", Icons.Default.SwapVert, "Transfers"),
    Triple("settings", Icons.Default.Settings, "Settings")
)

@Composable
fun NearDropApp(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val loaded by vm.settingsLoaded.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val nav = rememberNavController()

    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        splashDone = true
    }

    if (!loaded || !splashDone) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FileTransferLoadingAnimation()
            }
        }
        return
    }

    val start = if (settings.onboardingDone) "home" else "onboarding"
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (route in tabs.map { it.first }) {
                    ClayBottomNavigation(tabs, route) { r ->
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        ) { pad ->
            NavHost(nav, start, Modifier.padding(bottom = pad.calculateBottomPadding())) {
                composable("onboarding") { OnboardingScreen(vm) { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
                composable("home") { HomeScreen(vm, onSend = { nav.navigate("send") }, onOpen = { nav.navigate("progress/$it") }, onSettings = { nav.navigate("settings") }) }
                composable("transfers") { TransfersScreen(vm) { nav.navigate("progress/$it") } }
                composable("settings") { SettingsScreen(vm, onTrusted = { nav.navigate("trusted") }) }
                composable("trusted") { TrustedScreen(vm) { nav.popBackStack() } }
                composable("send") { SendScreen(vm, onBack = { nav.popBackStack() }, onSent = { id -> nav.navigate("progress/$id") { popUpTo("home") } }) }
                composable("progress/{id}") { ProgressScreen(vm, it.arguments?.getString("id") ?: "") { nav.popBackStack() } }
            }
        }
        incoming?.let { ui -> IncomingDialog(vm, ui) }
    }
}
