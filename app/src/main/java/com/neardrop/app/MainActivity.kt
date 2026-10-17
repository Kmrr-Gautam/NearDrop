package com.neardrop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neardrop.app.core.ThemeMode
import com.neardrop.app.ui.MainViewModel
import com.neardrop.app.ui.NearDropApp
import com.neardrop.app.ui.theme.NearDropTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val s by vm.settings.collectAsState()
            val dark = when (s.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
            val owner = LocalLifecycleOwner.current
            // Battery-conscious: scan only while the UI is visible.
            DisposableEffect(owner) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_START) vm.startDiscovery()
                    if (e == Lifecycle.Event.ON_STOP) vm.stopDiscovery()
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }
            NearDropTheme(dark) { NearDropApp(vm) }
        }
    }
}
