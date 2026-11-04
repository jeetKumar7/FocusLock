package com.focuslock.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focuslock.app.BuildConfig
import com.focuslock.app.lock.ChallengeType
import com.focuslock.app.lock.LockState
import com.focuslock.app.lock.ViewerMode
import com.focuslock.app.rules.ContentRules
import com.focuslock.app.ui.components.CheckRow
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.LargeTitle
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.NavRow
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.ToggleRow
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos

@Composable
fun SettingsTab(
    state: LockState,
    serviceOn: Boolean,
    onOpenChallenge: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCooldown: () -> Unit,
    onOpenPause: () -> Unit,
    onTestingMode: (Boolean) -> Unit,
) {
    val ios = LocalIos.current
    val context = LocalContext.current
    Column {
        LargeTitle("Settings")

        Section(
            header = "Unlocking",
            footer = "Making things stricter is instant. Making them looser needs your challenge first.",
            rows = listOf(
                { NavRow("Challenge", state.challenge.short, leading = { IconBadge(Icons.Rounded.Lock, ios.indigo) }, onClick = onOpenChallenge) },
                {
                    NavRow(
                        "Cooldown", cooldownLabel(state.cooldownMinutes),
                        leading = { IconBadge(Icons.Rounded.Timer, ios.orange) }, onClick = onOpenCooldown,
                    )
                },
                {
                    NavRow(
                        "Pause before Reels", pauseLabel(state.pauseSeconds),
                        leading = { IconBadge(Icons.Rounded.HourglassTop, ios.teal) }, onClick = onOpenPause,
                    )
                },
            ),
        )

        Section(
            header = "Personal",
            rows = listOf {
                NavRow(
                    "Block messages", "${state.messages.size}",
                    leading = { IconBadge(Icons.Rounded.ChatBubble, ios.green) }, onClick = onOpenMessages,
                )
            },
        )

        var unrestricted by remember { mutableStateOf(Background.isUnrestricted(context)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            unrestricted = Background.isUnrestricted(context)
            onPauseOrDispose { }
        }
        Section(
            header = "Permissions",
            footer = "Blocking only works while FocusLock is allowed to run in the background. " +
                (if (Background.isXiaomi) "On Xiaomi, also set MIUI battery saver to \"No restrictions\" and turn on Autostart. " else "") +
                "FocusLock reads only the screens of Instagram, YouTube, Facebook, Snapchat and X, on your phone. It has no internet access.",
            rows = buildList<@Composable () -> Unit> {
                add {
                    NavRow(
                        "Accessibility", if (serviceOn) "On" else "Off",
                        leading = { IconBadge(Icons.Rounded.AccessibilityNew, ios.blue) },
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    )
                }
                add {
                    NavRow(
                        "Battery", if (unrestricted) "Unrestricted" else "Restricted",
                        subtitle = if (unrestricted) null else "Tap to let FocusLock run in the background",
                        leading = { IconBadge(Icons.Rounded.BatteryChargingFull, if (unrestricted) ios.green else ios.red) },
                        onClick = { if (unrestricted) Background.openAppDetails(context) else Background.requestUnrestricted(context) },
                    )
                }
                if (Background.isXiaomi) {
                    add {
                        NavRow(
                            "MIUI battery saver", null,
                            subtitle = "Choose \"No restrictions\"",
                            leading = { IconBadge(Icons.Rounded.BatterySaver, ios.orange) },
                            onClick = { Background.openMiuiBatterySaver(context) },
                        )
                    }
                    add {
                        NavRow(
                            "Autostart", null,
                            subtitle = "Turn on for FocusLock",
                            leading = { IconBadge(Icons.Rounded.RestartAlt, ios.purple) },
                            onClick = { Background.openMiuiAutostart(context) },
                        )
                    }
                }
            },
        )

        if (BuildConfig.DEBUG) {
            Section(
                header = "Developer",
                footer = "Only in debug builds. Loosening skips the challenge and cooldown while this is on.",
                rows = listOf {
                    ToggleRow(
                        "Testing mode", state.testingMode, onTestingMode,
                        leading = { IconBadge(Icons.Rounded.Science, ios.pink) },
                    )
                },
            )
        }

        Section(
            header = "About",
            rows = listOf {
                RowBase("Version", leading = { IconBadge(Icons.Rounded.Info, ios.secondaryLabel) }) {
                    Text("${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""}", style = IosType.body, color = ios.secondaryLabel)
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private val ChallengeType.short get() = when (this) {
    ChallengeType.TYPING -> "Typing"
    ChallengeType.MATHS -> "Maths"
    ChallengeType.CAT -> "CAT"
}

private val ChallengeType.detail get() = when (this) {
    ChallengeType.TYPING -> "Type a long paragraph exactly, letter by letter."
    ChallengeType.MATHS -> "Solve 5 mental-maths problems in a row."
    ChallengeType.CAT -> "Answer 3 medium/hard CAT questions in a row."
}

// ------------------------------------------------------------------ pushed pages

val COOLDOWN_OPTIONS = listOf(0, 5, 10, 15, 30, 60)
val PAUSE_OPTIONS = listOf(0, 2, 4, 6, 8, 10, 15, 20, 30)
fun cooldownLabel(min: Int) = if (min == 0) "None" else "$min min"
fun pauseLabel(sec: Int) = if (sec == 0) "Off" else "$sec s"

/**
 * Pick a value from a list. Larger = stricter: applied instantly. Smaller = looser: one
 * challenge for the whole change, however big the jump.
 */
@Composable
fun ValuePickerPage(
    title: String,
    options: List<Int>,
    current: Int,
    label: (Int) -> String,
    footer: String,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
) {
    // Keep a value set before this list existed (e.g. 12) selectable.
    val all = (options + current).distinct().sorted()
    Column {
        NavBar(title, "Settings", onBack)
        Section(
            footer = footer,
            rows = all.map { v ->
                {
                    CheckRow(
                        label(v), v == current,
                        subtitle = if (v < current) "Needs your challenge" else null,
                    ) { if (v != current) onPick(v) }
                }
            },
        )
    }
}

@Composable
fun ChallengePage(state: LockState, onBack: () -> Unit, onPick: (ChallengeType) -> Unit) {
    Column {
        NavBar("Challenge", "Settings", onBack)
        Section(
            footer = "Switching to a different challenge needs the current one first, so you can't swap to an easier one on impulse.",
            rows = ChallengeType.entries.map { t ->
                { CheckRow(t.label, state.challenge == t, t.detail) { if (state.challenge != t) onPick(t) } }
            },
        )
    }
}

@Composable
fun ViewerModePage(state: LockState, ruleId: String, onBack: () -> Unit, onPick: (ViewerMode) -> Unit) {
    val rule = ContentRules.byId.getValue(ruleId)
    val what = Apps.shortLabel(rule)
    Column {
        NavBar(what, "Blocks", onBack)
        Section(
            header = "When one is opened from the feed, a DM or a profile",
            footer = "The $what tab itself is always blocked. Moving towards \"Block\" is instant; moving towards \"Allow\" needs your challenge.",
            rows = listOf(
                { CheckRow("Block", state.viewerMode(ruleId) == ViewerMode.BLOCK, "Sent back straight away.") { onPick(ViewerMode.BLOCK) } },
                { CheckRow("Only the one I opened", state.viewerMode(ruleId) == ViewerMode.ONE_ONLY, "Watch it after a short pause. Swiping to the next one sends you back.") { onPick(ViewerMode.ONE_ONLY) } },
                { CheckRow("Allow", state.viewerMode(ruleId) == ViewerMode.ALLOW, "No restriction outside the tab.") { onPick(ViewerMode.ALLOW) } },
            ),
        )
    }
}

@Composable
fun MessagesPage(state: LockState, onBack: () -> Unit, onSave: (List<String>) -> Unit) {
    val ios = LocalIos.current
    var text by remember(state.messages) { mutableStateOf(state.messages.joinToString("\n")) }
    val changed = text.trim() != state.messages.joinToString("\n")
    Column {
        NavBar("Block messages", "Settings", onBack, trailing = {
            Text(
                "Save", style = IosType.headline, color = if (changed) ios.blue else ios.tertiaryLabel,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = changed) { onSave(text.lines().map { it.trim() }.filter { it.isNotEmpty() }) }
                    .padding(8.dp),
            )
        })
        Section(
            header = "One per line",
            footer = "A random line shows under each \"blocked\" message, with your count for today.",
            rows = listOf {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = IosType.body.copy(color = ios.label),
                    cursorBrush = SolidColor(ios.blue),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).background(ios.card).padding(16.dp),
                )
            },
        )
    }
}
