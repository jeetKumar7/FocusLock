package com.focuslock.app.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Keeping FocusLock alive in the background. Android's battery optimisation can be lifted with
 * one system dialog. Xiaomi (MIUI/HyperOS) adds its own battery saver and Autostart switches,
 * which only have settings pages (no API), so we open those pages directly.
 */
object Background {

    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("xiaomi", true) || Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Android's "Let app always run in background?" dialog. */
    @SuppressLint("BatteryLife")
    fun requestUnrestricted(context: Context) {
        val ok = start(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
        if (!ok) openAppDetails(context)
    }

    /** MIUI battery saver page for FocusLock (choose "No restrictions"). */
    fun openMiuiBatterySaver(context: Context) {
        val miui = Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", "FocusLock")
        if (!start(context, miui)) openAppDetails(context)
    }

    /** MIUI Autostart list (turn FocusLock on). */
    fun openMiuiAutostart(context: Context) {
        val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        if (!start(context, miui)) openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
