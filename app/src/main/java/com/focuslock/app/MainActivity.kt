package com.focuslock.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.admin.UninstallProtection
import com.focuslock.app.lock.ChallengeScreen
import com.focuslock.app.lock.ChallengeType
import com.focuslock.app.lock.LockManager
import com.focuslock.app.lock.LockState
import com.focuslock.app.lock.ViewerMode
import com.focuslock.app.rules.ContentRules
import com.focuslock.app.schedule.ScheduleRule
import com.focuslock.app.service.FocusVpnService
import com.focuslock.app.web.AdultFilter
import com.focuslock.app.ui.AdultFilterPage
import com.focuslock.app.ui.AppBlockingPage
import com.focuslock.app.ui.Apps
import com.focuslock.app.ui.BlocksTab
import com.focuslock.app.ui.ChallengePage
import com.focuslock.app.ui.Goal
import com.focuslock.app.ui.LearnPage
import com.focuslock.app.ui.WebsiteBlockPage
import com.focuslock.app.ui.MessagesPage
import com.focuslock.app.ui.OnboardingScreen
import com.focuslock.app.ui.PracticeTab
import com.focuslock.app.ui.QuickBlockPage
import com.focuslock.app.ui.ScheduleEditPage
import com.focuslock.app.ui.ScheduleListPage
import com.focuslock.app.ui.SettingsTab
import com.focuslock.app.ui.StatsTab
import com.focuslock.app.ui.SleepPage
import com.focuslock.app.ui.StreaksPage
import com.focuslock.app.ui.UsageLimitsPage
import com.focuslock.app.ui.COOLDOWN_OPTIONS
import com.focuslock.app.ui.PAUSE_OPTIONS
import com.focuslock.app.ui.ValuePickerPage
import com.focuslock.app.ui.ViewerModePage
import com.focuslock.app.ui.cooldownLabel
import com.focuslock.app.ui.pauseLabel
import com.focuslock.app.ui.components.TabBar
import com.focuslock.app.ui.components.TabItem
import com.focuslock.app.ui.isServiceEnabled
import com.focuslock.app.ui.theme.FocusTheme
import com.focuslock.app.ui.theme.LocalIos
import kotlinx.coroutines.launch

private sealed interface Route {
    data class Viewer(val ruleId: String) : Route
    data object Challenge : Route
    data object Messages : Route
    data object AdultFilter : Route
    data object Cooldown : Route
    data object Pause : Route
    data object AppBlocking : Route
    data object QuickBlock : Route
    data object ScheduleList : Route
    data class ScheduleEdit(val rule: ScheduleRule?) : Route
    data object UsageLimits : Route
    data object Streak : Route
    data object Sleep : Route
    data object Websites : Route
    data object Learn : Route
}

private sealed interface Pending {
    val title: String
    data class TurnOff(val ruleId: String) : Pending {
        override val title get() = "Turn off ${ContentRules.byId[ruleId]?.let { Apps.shortLabel(it) } ?: ruleId}"
    }
    data class SetCooldown(val minutes: Int) : Pending { override val title = "Shorter cooldown" }
    data class SetViewerMode(val ruleId: String, val mode: ViewerMode) : Pending {
        override val title get() = "Loosen ${ContentRules.byId[ruleId]?.let { Apps.shortLabel(it) } ?: ruleId}"
    }
    data class SetPause(val seconds: Int) : Pending { override val title = "Shorter pause" }
    data class SwitchChallenge(val to: ChallengeType) : Pending { override val title = "Change challenge" }
    data object DisableUninstallProtection : Pending { override val title = "Turn off uninstall protection" }
    data object DisableSettingsGuard : Pending { override val title = "Turn off settings guard" }
    data object DisableVpnFilter : Pending { override val title = "Turn off VPN filter" }
    data object DisableBiometricLock : Pending { override val title = "Turn off biometric lock" }
}

private val TABS = listOf(
    TabItem("Blocks", Icons.Rounded.Shield),
    TabItem("Stats", Icons.Rounded.BarChart),
    TabItem("Practice", Icons.Rounded.School),
    TabItem("Settings", Icons.Rounded.Settings),
)

class MainActivity : FragmentActivity() {
    private var vpnPending = false

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && vpnPending) {
            FocusVpnService.start(this)
        }
        vpnPending = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lock = LockManager(applicationContext)
        setContent { FocusTheme { App(lock, ::startVpn, ::stopVpn, ::authenticate) } }
    }

    /** Device biometric / PIN. Fails open (onSuccess) if no authenticator is enrolled, to avoid lockout. */
    fun authenticate(onSuccess: () -> Unit) {
        val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess(); return
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock FocusLock")
                .setSubtitle("Confirm it's you to open FocusLock")
                .setAllowedAuthenticators(allowed)
                .build(),
        )
    }

    fun startVpn() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            vpnPending = true
            vpnLauncher.launch(prepare)
        } else {
            FocusVpnService.start(this)
        }
    }

    fun stopVpn() {
        FocusVpnService.stop(this)
    }
}

@Composable
private fun App(lock: LockManager, startVpn: () -> Unit, stopVpn: () -> Unit, authenticate: (() -> Unit) -> Unit) {
    val ios = LocalIos.current
    val context = LocalContext.current
    val state by lock.state.collectAsStateWithLifecycle(initialValue = LockState())
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var serviceOn by remember { mutableStateOf(isServiceEnabled(context)) }
    var privateDnsStrict by remember { mutableStateOf(AdultFilter.privateDnsStrict(context)) }
    LifecycleResumeEffect(Unit) {
        serviceOn = isServiceEnabled(context)
        privateDnsStrict = AdultFilter.privateDnsStrict(context)
        onPauseOrDispose { }
    }
    // Private DNS and the VPN filter both control DNS and can't coexist; strict Private DNS
    // breaks the VPN's connectivity. If both end up on, defer to Private DNS and shut the VPN
    // off (this makes filtering stricter/safer, so it needs no challenge).
    LaunchedEffect(privateDnsStrict, state.vpnFilterEnabled) {
        if (privateDnsStrict && state.vpnFilterEnabled) {
            stopVpn()
            lock.setVpnFilterEnabled(false)
        }
    }
    LaunchedEffect(Unit) { lock.ensureStreakStarted() }

    // Biometric / PIN gate. Re-locks whenever the app leaves the foreground.
    var unlocked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(state.biometricLock) {
        if (state.biometricLock && !unlocked) authenticate { unlocked = true }
        onPauseOrDispose { if (state.biometricLock) unlocked = false }
    }
    if (state.biometricLock && !unlocked) {
        BiometricLockScreen(onUnlock = { authenticate { unlocked = true } })
        return
    }
    val scrolls = remember { List(TABS.size) { androidx.compose.foundation.ScrollState(0) } }

    fun apply(p: Pending, cooldown: Int) = scope.launch {
        when (p) {
            is Pending.TurnOff -> lock.scheduleOff(p.ruleId, cooldown)
            is Pending.SetCooldown -> lock.setCooldown(p.minutes)
            is Pending.SetPause -> lock.setPauseSeconds(p.seconds)
            is Pending.SwitchChallenge -> lock.setChallenge(p.to)
            is Pending.SetViewerMode -> lock.setViewerMode(p.ruleId, p.mode)
            is Pending.DisableUninstallProtection -> {
                UninstallProtection.deactivate(context)
                lock.setUninstallProtected(false)
            }
            is Pending.DisableSettingsGuard -> lock.setSettingsGuarded(false)
            is Pending.DisableVpnFilter -> {
                stopVpn()
                lock.setVpnFilterEnabled(false)
            }
            is Pending.DisableBiometricLock -> lock.setBiometricLock(false)
        }
    }
    fun loosen(p: Pending) { if (state.testingMode) apply(p, 0) else pending = p }

    fun pickViewerMode(id: String, mode: ViewerMode) {
        val cur = state.viewerMode(id)
        when {
            mode == cur -> Unit
            mode.ordinal < cur.ordinal -> scope.launch { lock.setViewerMode(id, mode) }
            else -> loosen(Pending.SetViewerMode(id, mode))
        }
    }

    if (!state.onboardingComplete) {
        OnboardingScreen(
            serviceOn = serviceOn,
            onFinish = { goals ->
                scope.launch {
                    lock.applyOnboarding(feeds = Goal.FEEDS in goals, apps = Goal.APPS in goals)
                }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(ios.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab to stack.toList(),
                    transitionSpec = {
                        val (fromTab, fromStack) = initialState
                        val (toTab, toStack) = targetState
                        when {
                            fromTab != toTab -> fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                            toStack.size > fromStack.size ->
                                (slideInHorizontally(tween(320)) { it } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(260)))
                            else ->
                                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(220)))
                        }
                    },
                    label = "nav",
                ) { (t, s) ->
                    val top = s.lastOrNull()
                    Box(Modifier.fillMaxSize().background(ios.background).statusBarsPadding()) {
                        if (top == null) {
                            Column(Modifier.fillMaxSize().verticalScroll(scrolls[t])) {
                                when (t) {
                                    0 -> BlocksTab(
                                        state, serviceOn,
                                        onEnable = { id -> scope.launch { lock.enable(id) } },
                                        onRequestOff = { id -> loosen(Pending.TurnOff(id)) },
                                        onOpenViewerMode = { id -> stack += Route.Viewer(id) },
                                        onOpenAdultFilter = { stack += Route.AdultFilter },
                                        onOpenAppBlocking = { stack += Route.AppBlocking },
                                        onOpenQuickBlock = { stack += Route.QuickBlock },
                                        onOpenSchedules = { stack += Route.ScheduleList },
                                        onPanic = { scope.launch { lock.startPanic(30) } },
                                        onStopPanic = { scope.launch { lock.stopPanic() } },
                                        onOpenStreak = { stack += Route.Streak },
                                        onOpenSleep = { stack += Route.Sleep },
                                    )
                                    1 -> StatsTab()
                                    2 -> PracticeTab()
                                    else -> SettingsTab(
                                        state, serviceOn,
                                        onOpenChallenge = { stack += Route.Challenge },
                                        onOpenMessages = { stack += Route.Messages },
                                        onOpenCooldown = { stack += Route.Cooldown },
                                        onOpenPause = { stack += Route.Pause },
                                        onOpenUsageLimits = { stack += Route.UsageLimits },
                                        onOpenWebsites = { stack += Route.Websites },
                                        onOpenLearn = { stack += Route.Learn },
                                        onTestingMode = { on -> scope.launch { lock.setTestingMode(on) } },
                                        onUninstallProtection = { on ->
                                            if (on) {
                                                scope.launch { lock.setUninstallProtected(true) }
                                                UninstallProtection.requestActivation(context)
                                            } else {
                                                loosen(Pending.DisableUninstallProtection)
                                            }
                                        },
                                        onSettingsGuarded = { on ->
                                            if (on) scope.launch { lock.setSettingsGuarded(true) }
                                            else loosen(Pending.DisableSettingsGuard)
                                        },
                                        onBiometricLock = { on ->
                                            if (on) scope.launch { lock.setBiometricLock(true) }
                                            else loosen(Pending.DisableBiometricLock)
                                        },
                                        onVpnFilter = { on ->
                                            if (on) {
                                                if (!privateDnsStrict) {
                                                    scope.launch { lock.setVpnFilterEnabled(true) }
                                                    startVpn()
                                                }
                                            } else {
                                                loosen(Pending.DisableVpnFilter)
                                            }
                                        },
                                        privateDnsStrict = privateDnsStrict,
                                    )
                                }
                            }
                        } else {
                            val back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                when (top) {
                                    is Route.Viewer -> ViewerModePage(state, top.ruleId, { back() }) { pickViewerMode(top.ruleId, it) }
                                    Route.Challenge -> ChallengePage(state, { back() }) { loosen(Pending.SwitchChallenge(it)) }
                                    Route.AdultFilter -> AdultFilterPage { back() }
                                    Route.Cooldown -> ValuePickerPage(
                                        "Cooldown", COOLDOWN_OPTIONS, state.cooldownMinutes, ::cooldownLabel,
                                        "After you pass the challenge, a block stays on this long before it switches off. Longer is instant; shorter needs one challenge.",
                                        { back() },
                                    ) { v ->
                                        if (v > state.cooldownMinutes) scope.launch { lock.setCooldown(v) }
                                        else loosen(Pending.SetCooldown(v))
                                    }
                                    Route.Pause -> ValuePickerPage(
                                        "Pause before Reels", PAUSE_OPTIONS, state.pauseSeconds, ::pauseLabel,
                                        "The \"Do you really want this?\" countdown before a Reel or Short you're allowed to watch. Longer is instant; shorter needs one challenge.",
                                        { back() },
                                    ) { v ->
                                        if (v > state.pauseSeconds) scope.launch { lock.setPauseSeconds(v) }
                                        else loosen(Pending.SetPause(v))
                                    }
                                    Route.Messages -> MessagesPage(state, { back() }) { lines ->
                                        scope.launch { lock.setMessages(lines) }
                                        back()
                                    }
                                    Route.AppBlocking -> AppBlockingPage(
                                        state, { back() },
                                        onToggleApp = { pkg, on ->
                                            scope.launch {
                                                if (state.allowListMode) {
                                                    lock.setAllowedApps(
                                                        if (on) state.allowedApps + pkg else state.allowedApps - pkg,
                                                    )
                                                } else {
                                                    if (on) lock.addBlockedApp(pkg) else lock.removeBlockedApp(pkg)
                                                }
                                            }
                                        },
                                        onToggleAllowListMode = { on ->
                                            scope.launch { lock.setAllowListMode(on) }
                                        },
                                    )
                                    Route.QuickBlock -> QuickBlockPage(
                                        state, { back() },
                                        onStartQuickBlock = { min -> scope.launch { lock.startQuickBlock(min) } },
                                        onStopQuickBlock = { scope.launch { lock.stopQuickBlock() } },
                                        onStartPomodoro = { scope.launch { lock.startPomodoro() } },
                                        onStartBreak = { scope.launch { lock.startPomodoroBreak() } },
                                        onStopPomodoro = { scope.launch { lock.stopPomodoro() } },
                                        onSetWorkMin = { scope.launch { lock.setPomodoroWork(it) } },
                                        onSetBreakMin = { scope.launch { lock.setPomodoroBreak(it) } },
                                    )
                                    Route.ScheduleList -> ScheduleListPage(
                                        state, { back() },
                                        onAdd = { stack += Route.ScheduleEdit(null) },
                                        onEdit = { stack += Route.ScheduleEdit(it) },
                                        onToggle = { rule, on -> scope.launch { lock.updateSchedule(rule.copy(enabled = on)) } },
                                        onDelete = { rule -> scope.launch { lock.removeSchedule(rule.id) } },
                                    )
                                    is Route.ScheduleEdit -> ScheduleEditPage(
                                        initial = top.rule,
                                        onBack = { back() },
                                        onSave = { rule ->
                                            scope.launch {
                                                if (top.rule == null) lock.addSchedule(rule) else lock.updateSchedule(rule)
                                            }
                                            back()
                                        },
                                        onDelete = top.rule?.let { rule ->
                                            {
                                                scope.launch { lock.removeSchedule(rule.id) }
                                                // Pop both the edit page and go back to schedule list
                                                if (stack.size >= 2) {
                                                    stack.removeAt(stack.lastIndex)
                                                }
                                                back()
                                            }
                                        },
                                    )
                                    Route.UsageLimits -> UsageLimitsPage(
                                        state, { back() },
                                        onSetScreenTimeLimit = { scope.launch { lock.setScreenTimeLimit(it) } },
                                        onSetAppLimit = { pkg, min -> scope.launch { lock.setAppDailyLimit(pkg, min) } },
                                    )
                                    Route.Streak -> StreaksPage(
                                        state, { back() },
                                        onRelapse = { scope.launch { lock.relapse() } },
                                    )
                                    Route.Websites -> WebsiteBlockPage(
                                        state, { back() },
                                        onAdd = { domain -> scope.launch { lock.addBlockedSite(domain) } },
                                        onRemove = { domain -> scope.launch { lock.removeBlockedSite(domain) } },
                                    )
                                    Route.Learn -> LearnPage { back() }
                                    Route.Sleep -> SleepPage(
                                        state, { back() },
                                        onSetEnabled = { on -> scope.launch { lock.setSleepEnabled(on) } },
                                        onSetStart = { h, m -> scope.launch { lock.setSleepStart(h, m) } },
                                        onSetEnd = { h, m -> scope.launch { lock.setSleepEnd(h, m) } },
                                        onToggleEssential = { pkg, on ->
                                            scope.launch {
                                                lock.setSleepEssentials(
                                                    if (on) state.sleepEssentials + pkg else state.sleepEssentials - pkg,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            TabBar(TABS, tab) { i ->
                if (i == tab) stack.clear() else { tab = i; stack.clear() }
            }
        }

        // ---- challenge sheet slides up over everything
        AnimatedVisibility(
            visible = pending != null,
            enter = slideInVertically(tween(340)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200)),
        ) {
            val p = remember { pending } ?: pending
            if (p != null) {
                Box(Modifier.fillMaxSize().background(ios.background).statusBarsPadding()) {
                    ChallengeScreen(
                        type = state.challenge,
                        title = p.title,
                        onCancel = { pending = null },
                        onPassed = {
                            apply(p, state.cooldownMinutes)
                            pending = null
                        },
                    )
                }
            }
        }
    }

    BackHandler(enabled = pending != null || stack.isNotEmpty()) {
        if (pending != null) pending = null else stack.removeAt(stack.lastIndex)
    }
}

@Composable
private fun BiometricLockScreen(onUnlock: () -> Unit) {
    val ios = LocalIos.current
    Box(Modifier.fillMaxSize().background(ios.background), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ios.blue),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Lock, null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Text("FocusLock is locked", style = com.focuslock.app.ui.theme.IosType.title2, color = ios.label)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                "Confirm it's you to continue.",
                style = com.focuslock.app.ui.theme.IosType.body, color = ios.secondaryLabel,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
            com.focuslock.app.ui.components.PrimaryButton("Unlock", onClick = onUnlock, modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}
