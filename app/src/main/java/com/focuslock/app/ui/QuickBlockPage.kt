package com.focuslock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focuslock.app.lock.LockState
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.LargeTitle
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.PrimaryButton
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.StepperRow
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import kotlinx.coroutines.delay

val QUICK_BLOCK_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

@Composable
fun QuickBlockPage(
    state: LockState,
    onBack: () -> Unit,
    onStartQuickBlock: (Int) -> Unit,
    onStopQuickBlock: () -> Unit,
    onStartPomodoro: () -> Unit,
    onStartBreak: () -> Unit,
    onStopPomodoro: () -> Unit,
    onSetWorkMin: (Int) -> Unit,
    onSetBreakMin: (Int) -> Unit,
) {
    val ios = LocalIos.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Column {
        NavBar("Focus Sessions", "Blocks", onBack)

        // Quick Block
        val qbActive = state.quickBlockUntil > now
        Section(
            header = "Quick Block",
            footer = "Instantly blocks all apps in your blocked list for the chosen duration. No way to cancel early (unless in testing mode).",
            rows = if (qbActive) {
                val left = (state.quickBlockUntil - now) / 1000
                listOf {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Quick Block active", style = IosType.headline, color = ios.label)
                            Text(formatCountdown(left), style = IosType.title2, color = ios.red)
                        }
                        if (state.testingMode) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(ios.red).clickable { onStopQuickBlock() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Stop, "Stop", tint = Color.White, modifier = Modifier.size(24.dp)) }
                        }
                    }
                }
            } else {
                QUICK_BLOCK_OPTIONS.map { min ->
                    {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).clickable { onStartQuickBlock(min) }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.Timer, ios.blue)
                                Text("  $min minutes", style = IosType.body, color = ios.label)
                            }
                            Icon(Icons.Rounded.PlayArrow, null, tint = ios.blue)
                        }
                    }
                }
            },
        )

        // Pomodoro
        val pomActive = state.pomodoroUntil > now
        Section(
            header = "Pomodoro",
            footer = "Work phase: all blocked apps are locked. Break phase: apps are unlocked. Cycles automatically.",
            rows = if (pomActive) {
                val left = (state.pomodoroUntil - now) / 1000
                val phase = if (state.pomodoroOnBreak) "Break" else "Focus"
                listOf {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("$phase phase", style = IosType.headline, color = ios.label)
                            Text(
                                formatCountdown(left), style = IosType.title2,
                                color = if (state.pomodoroOnBreak) ios.green else ios.indigo,
                            )
                        }
                        Row {
                            if (!state.pomodoroOnBreak) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(ios.green).clickable { onStartBreak() },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Pause, "Break", tint = Color.White, modifier = Modifier.size(24.dp)) }
                            }
                            Box(
                                Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).background(ios.red).clickable { onStopPomodoro() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Stop, "Stop", tint = Color.White, modifier = Modifier.size(24.dp)) }
                        }
                    }
                }
            } else {
                listOf(
                    { StepperRow("Work", state.pomodoroWorkMin, 5, 120, 5, "${state.pomodoroWorkMin} min") { onSetWorkMin(it) } },
                    { StepperRow("Break", state.pomodoroBreakMin, 1, 30, 1, "${state.pomodoroBreakMin} min") { onSetBreakMin(it) } },
                    {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            PrimaryButton("Start Pomodoro", onStartPomodoro)
                        }
                    },
                )
            },
        )

        Spacer(Modifier.height(24.dp))
    }
}

private fun formatCountdown(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
