package com.neardrop.app.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.neardrop.app.core.ThemeMode
import com.neardrop.app.core.Visibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("neardrop_settings")

data class Settings(
    val deviceName: String = Build.MODEL ?: "Android",
    val visibility: Visibility = Visibility.EVERYONE,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notifications: Boolean = true,
    val onboardingDone: Boolean = false,
)

class SettingsStore(private val ctx: Context) {
    private val NAME = stringPreferencesKey("device_name")
    private val VIS = stringPreferencesKey("visibility")
    private val THEME = stringPreferencesKey("theme")
    private val NOTIF = booleanPreferencesKey("notifications")
    private val ONB = booleanPreferencesKey("onboarding_done")

    val flow: Flow<Settings> = ctx.store.data.map { p ->
        Settings(
            deviceName = p[NAME] ?: (Build.MODEL ?: "Android"),
            visibility = p[VIS]?.let { runCatching { Visibility.valueOf(it) }.getOrNull() } ?: Visibility.EVERYONE,
            theme = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            notifications = p[NOTIF] ?: true,
            onboardingDone = p[ONB] ?: false,
        )
    }

    suspend fun setDeviceName(v: String) = ctx.store.edit { it[NAME] = v.trim().take(32).ifBlank { Build.MODEL ?: "Android" } }
    suspend fun setVisibility(v: Visibility) = ctx.store.edit { it[VIS] = v.name }
    suspend fun setTheme(v: ThemeMode) = ctx.store.edit { it[THEME] = v.name }
    suspend fun setNotifications(v: Boolean) = ctx.store.edit { it[NOTIF] = v }
    suspend fun setOnboardingDone(v: Boolean) = ctx.store.edit { it[ONB] = v }
}
