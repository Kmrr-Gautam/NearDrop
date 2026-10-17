package com.neardrop.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    fun required(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES); add(Manifest.permission.POST_NOTIFICATIONS)
        } else add(Manifest.permission.ACCESS_FINE_LOCATION) // required by Wi-Fi Direct / BLE scan before Android 13 / 12
    }

    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun missing(ctx: Context) = required().filterNot { has(ctx, it) }

    fun hasBle(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) has(ctx, Manifest.permission.BLUETOOTH_SCAN) && has(ctx, Manifest.permission.BLUETOOTH_ADVERTISE)
        else has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasWifiDirect(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) has(ctx, Manifest.permission.NEARBY_WIFI_DEVICES)
        else has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
}
