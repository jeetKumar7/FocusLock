package com.focuslock.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WbSunny
import com.focuslock.app.lock.LockState
import com.focuslock.app.ui.components.AppIcon
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.StepperRow
import com.focuslock.app.ui.components.ToggleRow
import com.focuslock.app.ui.theme.LocalIos

@Composable
fun SleepPage(
    state: LockState,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetStart: (Int, Int) -> Unit,
    onSetEnd: (Int, Int) -> Unit,
    onToggleEssential: (String, Boolean) -> Unit,
) {
    val ios = LocalIos.current
    val apps by rememberInstalledApps()

    Column {
        NavBar("Sleep Mode", "Blocks", onBack)

        Section(
            footer = "During the sleep window, every app is locked except the essentials you choose below. Calls, your clock/alarm, and messaging apps always stay reachable. Repeats every night.",
            rows = listOf {
                ToggleRow(
                    "Sleep mode",
                    state.sleepEnabled,
                    onSetEnabled,
                    subtitle = if (state.sleepEnabled) "On · ${state.sleepLabel()}" else "Off",
                    leading = { IconBadge(Icons.Rounded.Bedtime, ios.indigo) },
                )
            },
        )

        Section(
            header = "Bedtime",
            rows = listOf(
                { StepperRow("Lock from (hour)", state.sleepStartHour, 0, 23, 1, "%02d:%02d".format(state.sleepStartHour, state.sleepStartMin)) { onSetStart(it, state.sleepStartMin) } },
                { StepperRow("Minute", state.sleepStartMin, 0, 55, 5, "") { onSetStart(state.sleepStartHour, it) } },
            ),
        )

        Section(
            header = "Wake",
            rows = listOf(
                { StepperRow("Unlock at (hour)", state.sleepEndHour, 0, 23, 1, "%02d:%02d".format(state.sleepEndHour, state.sleepEndMin)) { onSetEnd(it, state.sleepEndMin) } },
                { StepperRow("Minute", state.sleepEndMin, 0, 55, 5, "") { onSetEnd(state.sleepEndHour, it) } },
            ),
        )

        val list = apps
        if (list == null) {
            AppsLoading()
        } else {
            Section(
                header = "Allowed during sleep",
                footer = "These stay usable overnight. Everything else is sent back to the home screen.",
                rows = list.map { app ->
                    {
                        ToggleRow(
                            title = app.label,
                            checked = app.pkg in state.sleepEssentials,
                            onCheckedChange = { on -> onToggleEssential(app.pkg, on) },
                            leading = { AppIcon(app.pkg) {} },
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
