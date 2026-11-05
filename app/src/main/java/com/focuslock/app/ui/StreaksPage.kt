package com.focuslock.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslock.app.lock.LockState
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import kotlinx.coroutines.delay

private val MILESTONE_DAYS = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365)

@Composable
fun StreaksPage(
    state: LockState,
    onBack: () -> Unit,
    onRelapse: () -> Unit,
) {
    val ios = LocalIos.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    var confirmRelapse by remember { mutableStateOf(false) }

    val cur = state.currentStreakMs(now)
    val best = maxOf(state.bestStreakMs, cur)
    val days = (cur / 86_400_000L).toInt()

    Column {
        NavBar("Streak", "Blocks", onBack)

        // hero
        Box(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF9500), Color(0xFFFF375F))))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("$days", style = IosType.largeTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Color.White)
                Text(if (days == 1) "day focused" else "days focused", style = IosType.headline, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(4.dp))
                Text(streakClock(cur), style = IosType.footnote, color = Color.White.copy(alpha = 0.85f))
            }
        }

        Section(
            rows = listOf(
                { com.focuslock.app.ui.components.RowBase("Best streak") { Text(bestLabel(best), style = IosType.body, color = ios.secondaryLabel) } },
                { com.focuslock.app.ui.components.RowBase("Started") { Text(if (state.streakStartAt == 0L) "—" else relativeStart(cur), style = IosType.body, color = ios.secondaryLabel) } },
            ),
        )

        Section(
            header = "Milestones",
            footer = "Each milestone is a day count kept clean. Hitting a relapse restarts the clock — your best streak is still saved.",
            rows = MILESTONE_DAYS.map { m ->
                {
                    val reached = days >= m
                    com.focuslock.app.ui.components.RowBase(
                        title = "$m ${if (m == 1) "day" else "days"}",
                        titleColor = if (reached) ios.label else ios.secondaryLabel,
                    ) {
                        if (reached) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(ios.green), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Text("${(m - days).coerceAtLeast(0)}d to go", style = IosType.footnote, color = ios.tertiaryLabel)
                        }
                    }
                }
            },
        )

        Section(
            rows = listOf {
                com.focuslock.app.ui.components.RowBase(
                    title = if (confirmRelapse) "Tap again to confirm" else "I relapsed",
                    titleColor = ios.red,
                    onClick = {
                        if (confirmRelapse) { onRelapse(); confirmRelapse = false } else confirmRelapse = true
                    },
                ) {}
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun streakClock(ms: Long): String {
    val totalSec = ms / 1000
    val d = totalSec / 86_400
    val h = (totalSec % 86_400) / 3600
    val m = (totalSec % 3600) / 60
    return "%dd %02dh %02dm".format(d, h, m)
}

private fun bestLabel(ms: Long): String {
    val days = ms / 86_400_000L
    val hours = (ms % 86_400_000L) / 3_600_000L
    return if (ms == 0L) "—" else if (days > 0) "${days}d ${hours}h" else "${hours}h"
}

private fun relativeStart(curMs: Long): String {
    val days = curMs / 86_400_000L
    return if (days <= 0) "today" else "$days ${if (days == 1L) "day" else "days"} ago"
}
