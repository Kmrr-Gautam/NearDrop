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
import com.neardrop.app.core.Direction
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
    val activeTransfers by vm.progress.collectAsState()
    val nav = rememberNavController()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var updateInfo by remember { mutableStateOf<com.neardrop.app.update.AppUpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        delay(2500)
        com.neardrop.app.update.UpdateChecker.check(ctx).getOrNull()?.let { info ->
            updateInfo = info
            com.neardrop.app.service.TransferNotifications.notifyUpdate(ctx, info)
        }
    }

    var handledReceiveId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeTransfers) {
        val activeReceive = activeTransfers.firstOrNull { it.direction == Direction.RECEIVE && !it.status.terminal }
        if (activeReceive != null && activeReceive.id != handledReceiveId) {
            handledReceiveId = activeReceive.id
            nav.navigate("progress/${activeReceive.id}") {
                launchSingleTop = true
            }
        }
    }

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
                        if (r != route) {
                            if (r == "home") {
                                val popped = nav.popBackStack("home", inclusive = false)
                                if (!popped) {
                                    nav.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                nav.navigate(r) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                }
            }
        ) { pad ->
            NavHost(nav, start, Modifier.padding(bottom = pad.calculateBottomPadding())) {
                composable("onboarding") { OnboardingScreen(vm) { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
                composable("home") {
                    HomeScreen(
                        vm = vm,
                        onSend = { nav.navigate("send") },
                        onOpen = { nav.navigate("progress/$it") },
                        onSettings = {
                            nav.navigate("settings") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable("transfers") { TransfersScreen(vm) { nav.navigate("progress/$it") } }
                composable("settings") { SettingsScreen(vm, onTrusted = { nav.navigate("trusted") }) }
                composable("trusted") { TrustedScreen(vm) { nav.popBackStack() } }
                composable("send") { SendScreen(vm, onBack = { nav.popBackStack() }, onSent = { id -> nav.navigate("progress/$id") { popUpTo("home") } }) }
                composable("progress/{id}") { ProgressScreen(vm, it.arguments?.getString("id") ?: "") { nav.popBackStack() } }
            }
        }
        incoming?.let { ui ->
            IncomingDialog(
                vm = vm,
                ui = ui,
                onAccepted = {
                    val activeReceive = activeTransfers.firstOrNull { it.direction == Direction.RECEIVE }
                    if (activeReceive != null) {
                        handledReceiveId = activeReceive.id
                        nav.navigate("progress/${activeReceive.id}") {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        updateInfo?.let { info ->
            com.neardrop.app.update.UpdateDialog(info = info, onDismiss = { updateInfo = null })
        }
    }
}
