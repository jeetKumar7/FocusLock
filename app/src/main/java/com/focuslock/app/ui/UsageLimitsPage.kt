package com.focuslock.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focuslock.app.lock.LockState
import com.focuslock.app.ui.components.AppIcon
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.StepperRow

val LIMIT_OPTIONS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

@Composable
fun UsageLimitsPage(
    state: LockState,
    onBack: () -> Unit,
    onSetScreenTimeLimit: (Int) -> Unit,
    onSetAppLimit: (String, Int) -> Unit,
) {
    val apps by rememberInstalledApps()

    Column {
        NavBar("Usage Limits", "Settings", onBack)

        Section(
            header = "Total screen time",
            footer = "When you hit the limit, all apps are blocked for the rest of the day (except essentials). 0 = off.",
            rows = listOf {
                StepperRow(
                    "Daily limit", state.screenTimeLimit, 0, 480, 15,
                    if (state.screenTimeLimit == 0) "Off" else "${state.screenTimeLimit} min",
                ) { onSetScreenTimeLimit(it) }
            },
        )

        val list = apps
        if (list == null) {
            AppsLoading()
        } else {
            Section(
                header = "Per-app daily limits",
                footer = "When an app's daily time is used up, it's blocked until midnight. 0 = no limit.",
                rows = list.map { app ->
                    {
                        val curLimit = state.appDailyLimits[app.pkg] ?: 0
                        StepperRow(
                            app.label, curLimit, 0, 480, 15,
                            if (curLimit == 0) "No limit" else "$curLimit min",
                            leading = { AppIcon(app.pkg) {} },
                        ) { onSetAppLimit(app.pkg, it) }
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
