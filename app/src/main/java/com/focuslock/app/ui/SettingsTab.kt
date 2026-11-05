package com.focuslock.app.ui

import android.content.Intent
import android.net.VpnService
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
import com.focuslock.app.service.FocusVpnService
import com.focuslock.app.admin.UninstallProtection
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.LargeTitle
import com.focuslock.app.ui.components.NavRow
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.ToggleRow
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
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
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material3.Text

@Composable
fun SettingsTab(
    state: LockState,
    serviceOn: Boolean,
    onOpenChallenge: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCooldown: () -> Unit,
    onOpenPause: () -> Unit,
    onOpenUsageLimits: () -> Unit,
    onOpenWebsites: () -> Unit,
    onOpenLearn: () -> Unit,
    onTestingMode: (Boolean) -> Unit,
    onUninstallProtection: (Boolean) -> Unit,
    onSettingsGuarded: (Boolean) -> Unit,
    onBiometricLock: (Boolean) -> Unit,
    onVpnFilter: (Boolean) -> Unit,
    privateDnsStrict: Boolean,
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

        // Usage limits + websites
        Section(
            header = "Limits",
            footer = "Cap your daily time per app or overall, and block specific websites in your browser.",
            rows = listOf(
                {
                    NavRow(
                        "Usage limits",
                        if (state.screenTimeLimit > 0) "${state.screenTimeLimit}min/day"
                        else if (state.appDailyLimits.isNotEmpty()) "${state.appDailyLimits.size} apps"
                        else "Off",
                        subtitle = "Daily time caps per app or overall",
                        leading = { IconBadge(Icons.Rounded.DataUsage, ios.purple) },
                        onClick = onOpenUsageLimits,
                    )
                },
                {
                    NavRow(
                        "Blocked websites",
                        if (state.blockedSites.isNotEmpty()) "${state.blockedSites.size}" else "None",
                        subtitle = "Block any site in your browser",
                        leading = { IconBadge(Icons.Rounded.Language, ios.teal) },
                        onClick = onOpenWebsites,
                    )
                },
            ),
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
                "FocusLock reads the screens of apps you chose to block, on your phone.",
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

        Section(
            header = "Protection",
            footer = "When on, attempts to uninstall FocusLock, disable the accessibility service, or change guarded settings are blocked. Turning these off requires your challenge.",
            rows = listOf(
                {
                    ToggleRow(
                        "Prevent uninstall",
                        state.uninstallProtected && UninstallProtection.isActive(context),
                        onUninstallProtection,
                        leading = { IconBadge(Icons.Rounded.Security, ios.red) },
                    )
                },
                {
                    ToggleRow(
                        "Guard settings",
                        state.settingsGuarded,
                        onSettingsGuarded,
                        subtitle = "Block Force Stop, Private DNS, VPN settings changes",
                        leading = { IconBadge(Icons.Rounded.SettingsApplications, ios.orange) },
                    )
                },
                {
                    ToggleRow(
                        "Lock with biometrics",
                        state.biometricLock,
                        onBiometricLock,
                        subtitle = "Require fingerprint or PIN to open FocusLock",
                        leading = { IconBadge(Icons.Rounded.Fingerprint, ios.blue) },
                    )
                },
            ),
        )

        // VPN filter
        var vpnRunning by remember { mutableStateOf(FocusVpnService.isRunning(context)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            vpnRunning = FocusVpnService.isRunning(context)
            onPauseOrDispose { }
        }
        if (privateDnsStrict) {
            Section(
                header = "DNS Filter",
                footer = "Your Private DNS adult filter is already filtering every app and browser, encrypted and system-wide. The VPN filter does the same job, so it isn't needed — and it can't run at the same time as Private DNS (they both control DNS), which is why it broke your connection.",
                rows = listOf {
                    RowBase("Filtered by Private DNS", leading = { IconBadge(Icons.Rounded.Dns, ios.green) }) {
                        Text("On", style = IosType.body, color = ios.secondaryLabel)
                    }
                },
            )
        } else {
            Section(
                header = "DNS Filter",
                footer = "Routes all DNS through CleanBrowsing (adult filter) via an on-device VPN. Use this only if you're not using the Private DNS filter above — they can't run at the same time.",
                rows = listOf {
                    ToggleRow(
                        "VPN DNS filter",
                        state.vpnFilterEnabled,
                        { on -> onVpnFilter(on) },
                        subtitle = when {
                            state.vpnFilterEnabled && vpnRunning -> "Active · CleanBrowsing"
                            state.vpnFilterEnabled && !vpnRunning -> "Starting…"
                            else -> null
                        },
                        leading = { IconBadge(Icons.Rounded.Dns, if (state.vpnFilterEnabled && vpnRunning) ios.green else ios.secondaryLabel) },
                    )
                },
            )
        }

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
            header = "Learn",
            footer = "Short reads on why feeds are built the way they are, and how to take your attention back.",
            rows = listOf {
                NavRow(
                    "Learn", "${LEARN_ARTICLES.size} reads",
                    leading = { IconBadge(Icons.Rounded.MenuBook, ios.teal) },
                    onClick = onOpenLearn,
                )
            },
        )

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

val COOLDOWN_OPTIONS = listOf(0, 5, 10, 15, 30, 60)
val PAUSE_OPTIONS = listOf(0, 2, 4, 6, 8, 10, 15, 20, 30)
fun cooldownLabel(min: Int) = if (min == 0) "None" else "$min min"
fun pauseLabel(sec: Int) = if (sec == 0) "Off" else "$sec s"

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
    val all = (options + current).distinct().sorted()
    Column {
        com.focuslock.app.ui.components.NavBar(title, "Settings", onBack)
        Section(
            footer = footer,
            rows = all.map { v ->
                {
                    com.focuslock.app.ui.components.CheckRow(
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
        com.focuslock.app.ui.components.NavBar("Challenge", "Settings", onBack)
        Section(
            footer = "Switching to a different challenge needs the current one first, so you can't swap to an easier one on impulse.",
            rows = ChallengeType.entries.map { t ->
                { com.focuslock.app.ui.components.CheckRow(t.label, state.challenge == t, t.detail) { if (state.challenge != t) onPick(t) } }
            },
        )
    }
}

@Composable
fun ViewerModePage(state: LockState, ruleId: String, onBack: () -> Unit, onPick: (com.focuslock.app.lock.ViewerMode) -> Unit) {
    val rule = com.focuslock.app.rules.ContentRules.byId.getValue(ruleId)
    val what = Apps.shortLabel(rule)
    Column {
        com.focuslock.app.ui.components.NavBar(what, "Blocks", onBack)
        Section(
            header = "When one is opened from the feed, a DM or a profile",
            footer = "The $what tab itself is always blocked. Moving towards \"Block\" is instant; moving towards \"Allow\" needs your challenge.",
            rows = listOf(
                { com.focuslock.app.ui.components.CheckRow("Block", state.viewerMode(ruleId) == com.focuslock.app.lock.ViewerMode.BLOCK, "Sent back straight away.") { onPick(com.focuslock.app.lock.ViewerMode.BLOCK) } },
                { com.focuslock.app.ui.components.CheckRow("Only the one I opened", state.viewerMode(ruleId) == com.focuslock.app.lock.ViewerMode.ONE_ONLY, "Watch it after a short pause. Swiping to the next one sends you back.") { onPick(com.focuslock.app.lock.ViewerMode.ONE_ONLY) } },
                { com.focuslock.app.ui.components.CheckRow("Allow", state.viewerMode(ruleId) == com.focuslock.app.lock.ViewerMode.ALLOW, "No restriction outside the tab.") { onPick(com.focuslock.app.lock.ViewerMode.ALLOW) } },
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
        com.focuslock.app.ui.components.NavBar("Block messages", "Settings", onBack, trailing = {
            Text(
                "Save", style = IosType.headline, color = if (changed) ios.blue else ios.tertiaryLabel,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable(enabled = changed) { onSave(text.lines().map { it.trim() }.filter { it.isNotEmpty() }) }
                    .padding(8.dp),
            )
        })
        Section(
            header = "One per line",
            footer = "A random line shows under each \"blocked\" message, with your count for today.",
            rows = listOf {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = IosType.body.copy(color = ios.label),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(ios.blue),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).background(ios.card).padding(16.dp),
                )
            },
        )
    }
}
