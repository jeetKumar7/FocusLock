package com.focuslock.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.lock.LockState
import com.focuslock.app.lock.ViewerMode
import com.focuslock.app.rules.ContentRule
import com.focuslock.app.rules.ContentRules
import com.focuslock.app.service.GuardAccessibilityService
import com.focuslock.app.stats.BlockLog
import com.focuslock.app.ui.components.AppIcon
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.LargeTitle
import com.focuslock.app.ui.components.NavRow
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.ToggleRow
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import kotlinx.coroutines.delay
import java.time.LocalDate

/** App display names and badge colours for rules (used when an app isn't installed). */
internal object Apps {
    private val names = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.snapchat.android" to "Snapchat",
        "com.twitter.android" to "X",
    )
    fun name(pkg: String) = names[pkg] ?: pkg
    fun shortLabel(rule: ContentRule): String =
        rule.label.removePrefix(name(rule.pkg)).trim().replaceFirstChar { it.uppercase() }.ifEmpty { rule.label }
}

@Composable
fun BlocksTab(
    state: LockState,
    serviceOn: Boolean,
    onEnable: (String) -> Unit,
    onRequestOff: (String) -> Unit,
    onOpenViewerMode: (String) -> Unit,
    onOpenAdultFilter: () -> Unit,
) {
    val ios = LocalIos.current
    val context = LocalContext.current
    val log = remember { BlockLog.get(context) }
    val events by log.events.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val todayCount = events.count { it.date == today }
    val weekCount = events.count { it.date > today.minusDays(7) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.offAt) {
        while (state.offAt.isNotEmpty()) { now = System.currentTimeMillis(); delay(1000) }
    }
    val activeCount = ContentRules.all.count { state.isActive(it.id, now) }

    Column {
        LargeTitle("FocusLock")

        // ---- hero
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (serviceOn) Brush.linearGradient(listOf(Color(0xFF5E5CE6), Color(0xFF0A84FF)))
                    else Brush.linearGradient(listOf(Color(0xFFFF6B5B), Color(0xFFFF375F))),
                )
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (serviceOn) Icons.Rounded.Shield else Icons.Rounded.WarningAmber,
                            null, tint = Color.White, modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (serviceOn) "Protection is on" else "Protection is off",
                            style = IosType.headline, color = Color.White,
                        )
                        Text(
                            if (serviceOn) "$activeCount block${if (activeCount == 1) "" else "s"} active"
                            else "FocusLock needs Accessibility access",
                            style = IosType.footnote, color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (serviceOn) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("$todayCount", style = IosType.bigNumber.copy(fontSize = androidx.compose.ui.unit.TextUnit(44f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Color.White)
                            Text("avoided today", style = IosType.footnote, color = Color.White.copy(alpha = 0.85f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$weekCount", style = IosType.title2, color = Color.White)
                            Text("this week", style = IosType.footnote, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                } else {
                    Text(
                        "It only reads the screens of the apps below, on your phone, and never sends anything anywhere. " +
                            "Open Accessibility → Downloaded apps → FocusLock section blocker.",
                        style = IosType.footnote, color = Color.White.copy(alpha = 0.92f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .clickable { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Turn on in Accessibility", style = IosType.headline, color = Color(0xFFFF375F))
                    }
                }
            }
        }

        if (state.testingMode) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(ios.orange.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Science, null, tint = ios.orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Testing mode: blocks turn off instantly", style = IosType.footnote, color = ios.orange)
            }
        }

        // ---- web
        var filter by remember { androidx.compose.runtime.mutableStateOf(com.focuslock.app.web.AdultFilter.status(context)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            filter = com.focuslock.app.web.AdultFilter.status(context)
            onPauseOrDispose { }
        }
        Section(
            header = "Web",
            rows = listOf {
                NavRow(
                    title = "Adult content filter",
                    value = when (filter) {
                        is com.focuslock.app.web.AdultFilter.Status.On -> "On"
                        else -> "Off"
                    },
                    subtitle = when (val f = filter) {
                        is com.focuslock.app.web.AdultFilter.Status.On -> f.level.name + " · every app and browser"
                        else -> "Blocks porn in every app and browser"
                    },
                    leading = { IconBadge(Icons.Rounded.Shield, if (filter is com.focuslock.app.web.AdultFilter.Status.On) ios.green else ios.red) },
                    onClick = onOpenAdultFilter,
                )
            },
        )

        // ---- per-app sections
        ContentRules.all.groupBy { it.pkg }.forEach { (pkg, rules) ->
            val rows = buildList<@Composable () -> Unit> {
                rules.forEach { rule ->
                    add {
                        val active = state.isActive(rule.id, now)
                        val pending = state.isPendingOff(rule.id, now)
                        ToggleRow(
                            title = Apps.shortLabel(rule),
                            subtitle = when {
                                pending -> "Turns off in ${formatLeft(state.offAt.getValue(rule.id) - now)} · switch on to keep"
                                rule.hasViewerModes && active -> "Tab always blocked"
                                else -> null
                            },
                            checked = active && !pending,
                            onCheckedChange = { on -> if (on) onEnable(rule.id) else if (!pending) onRequestOff(rule.id) },
                            leading = {
                                AppIcon(pkg) { IconBadge(Icons.Rounded.Block, ios.indigo) }
                            },
                        )
                    }
                    if (rule.hasViewerModes && state.isActive(rule.id, now)) add {
                        NavRow(
                            title = "Opened from feed or DMs",
                            value = when (state.viewerMode(rule.id)) {
                                ViewerMode.BLOCK -> "Block"
                                ViewerMode.ONE_ONLY -> "One only"
                                ViewerMode.ALLOW -> "Allow"
                            },
                            leading = { Spacer(Modifier.width(30.dp)) },
                            onClick = { onOpenViewerMode(rule.id) },
                        )
                    }
                }
            }
            Section(header = Apps.name(pkg), rows = rows)
        }
        Section(
            footer = "Turning a block off needs your challenge, then waits out the cooldown. " +
                "Turning one on is always instant.",
            rows = listOf { RowBase("Blocked in total", trailing = { Text("${state.blockedCount}", style = IosType.body, color = ios.secondaryLabel) }) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

internal fun formatLeft(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val me = ComponentName(context, GuardAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}
