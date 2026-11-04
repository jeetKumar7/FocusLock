package com.focuslock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
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
import com.focuslock.app.lock.ChallengeScreen
import com.focuslock.app.lock.ChallengeType
import com.focuslock.app.lock.LockManager
import com.focuslock.app.lock.LockState
import com.focuslock.app.lock.ViewerMode
import com.focuslock.app.rules.ContentRules
import com.focuslock.app.ui.AdultFilterPage
import com.focuslock.app.ui.Apps
import com.focuslock.app.ui.BlocksTab
import com.focuslock.app.ui.ChallengePage
import com.focuslock.app.ui.MessagesPage
import com.focuslock.app.ui.PracticeTab
import com.focuslock.app.ui.SettingsTab
import com.focuslock.app.ui.StatsTab
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

/** Pages pushed on top of a tab (slide in from the right). */
private sealed interface Route {
    data class Viewer(val ruleId: String) : Route
    data object Challenge : Route
    data object Messages : Route
    data object AdultFilter : Route
    data object Cooldown : Route
    data object Pause : Route
}

/** An action that loosens protection and therefore needs the challenge first. */
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
}

private val TABS = listOf(
    TabItem("Blocks", Icons.Rounded.Shield),
    TabItem("Stats", Icons.Rounded.BarChart),
    TabItem("Practice", Icons.Rounded.School),
    TabItem("Settings", Icons.Rounded.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lock = LockManager(applicationContext)
        setContent { FocusTheme { App(lock) } }
    }
}

@Composable
private fun App(lock: LockManager) {
    val ios = LocalIos.current
    val context = LocalContext.current
    val state by lock.state.collectAsStateWithLifecycle(initialValue = LockState())
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var serviceOn by remember { mutableStateOf(isServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceOn = isServiceEnabled(context)
        onPauseOrDispose { }
    }
    val scrolls = remember { List(TABS.size) { androidx.compose.foundation.ScrollState(0) } }

    /** Runs a loosening action: instantly in testing mode, otherwise via the challenge. */
    fun apply(p: Pending, cooldown: Int) = scope.launch {
        when (p) {
            is Pending.TurnOff -> lock.scheduleOff(p.ruleId, cooldown)
            is Pending.SetCooldown -> lock.setCooldown(p.minutes)
            is Pending.SetPause -> lock.setPauseSeconds(p.seconds)
            is Pending.SwitchChallenge -> lock.setChallenge(p.to)
            is Pending.SetViewerMode -> lock.setViewerMode(p.ruleId, p.mode)
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
                                    )
                                    1 -> StatsTab()
                                    2 -> PracticeTab()
                                    else -> SettingsTab(
                                        state, serviceOn,
                                        onOpenChallenge = { stack += Route.Challenge },
                                        onOpenMessages = { stack += Route.Messages },
                                        onOpenCooldown = { stack += Route.Cooldown },
                                        onOpenPause = { stack += Route.Pause },
                                        onTestingMode = { on -> scope.launch { lock.setTestingMode(on) } },
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
