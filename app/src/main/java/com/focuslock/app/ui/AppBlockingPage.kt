package com.focuslock.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focuslock.app.lock.LockState
import com.focuslock.app.ui.components.AppIcon
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.ToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String)

fun installedApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }
        .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}

/** Loads the installed-app list off the main thread. null while still loading. */
@Composable
fun rememberInstalledApps(): State<List<AppEntry>?> {
    val context = LocalContext.current
    return produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { installedApps(context) }
    }
}

@Composable
fun AppsLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun AppBlockingPage(
    state: LockState,
    onBack: () -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onToggleAllowListMode: (Boolean) -> Unit,
) {
    val apps by rememberInstalledApps()

    Column {
        NavBar("App Blocking", "Blocks", onBack)

        Section(
            header = "Mode",
            footer = if (state.allowListMode)
                "Only apps you allow below can be opened. Everything else is blocked."
            else
                "Apps you block below will be sent home when opened.",
            rows = listOf {
                ToggleRow("Allow-list mode", state.allowListMode, onToggleAllowListMode)
            },
        )

        val list = apps
        if (list == null) {
            AppsLoading()
        } else {
            Section(
                header = if (state.allowListMode) "Allowed apps" else "Blocked apps",
                footer = "${list.size} third-party apps installed",
                rows = list.map { app ->
                    {
                        val active = if (state.allowListMode) app.pkg in state.allowedApps else app.pkg in state.blockedApps
                        ToggleRow(
                            title = app.label,
                            checked = active,
                            onCheckedChange = { on -> onToggleApp(app.pkg, on) },
                            leading = { AppIcon(app.pkg) {} },
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
