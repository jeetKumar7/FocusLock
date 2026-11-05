package com.focuslock.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.focuslock.app.admin.UninstallProtection
import com.focuslock.app.lock.LockManager
import com.focuslock.app.lock.LockState
import com.focuslock.app.lock.ViewerMode
import com.focuslock.app.rules.ContentRule
import com.focuslock.app.rules.ContentRules
import com.focuslock.app.stats.BlockLog
import com.focuslock.app.widget.BlockWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class GuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lock: LockManager
    private lateinit var log: BlockLog
    private lateinit var pause: PauseOverlay
    private lateinit var banner: Banner
    private lateinit var usage: UsageTracker

    @Volatile private var state = LockState()
    private var lastActionAt = 0L
    private val recentActions = ArrayDeque<Long>()
    private var pausedUntil = 0L
    private var backSettlingUntil = 0L
    private val redirected = mutableSetOf<String>()
    private val lastEventAt = mutableMapOf<String, Long>()
    private val lastRedirectAt = mutableMapOf<String, Long>()
    private val watchPass = mutableMapOf<String, Long>()
    private var lastForegroundPkg: String? = null
    private var lastAppBlockAt = 0L
    private val lastEmitAt = mutableMapOf<String, Long>()

    /** Sends a partner event at most once a minute per type (no-op if unlinked). */
    private fun emitPartnerEvent(type: String) {
        val now = System.currentTimeMillis()
        if (now - (lastEmitAt[type] ?: 0L) < 60_000L) return
        lastEmitAt[type] = now
        scope.launch { com.focuslock.app.backend.Backend.emitEvent(type) }
    }

    private data class ViewerSession(val ruleId: String, var startedAt: Long, var index: Int? = null)
    private var session: ViewerSession? = null

    override fun onServiceConnected() {
        lock = LockManager(applicationContext)
        log = BlockLog.get(applicationContext)
        pause = PauseOverlay(this)
        banner = Banner(this)
        usage = UsageTracker(applicationContext)
        lock.state.onEach { state = it }.launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()
        val ourPkg = packageName

        // Never block ourselves, launchers, or system UI
        if (pkg == ourPkg || pkg == "com.android.launcher" || pkg.startsWith("com.android.launcher") ||
            pkg == "com.google.android.apps.nexuslauncher" || pkg == "com.android.systemui" ||
            pkg == "com.sec.android.app.launcher"
        ) {
            if (pkg != lastForegroundPkg && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                usage.onAppForeground(pkg)
                lastForegroundPkg = pkg
            }
            return
        }

        // Track foreground app for usage
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != lastForegroundPkg) {
            usage.onAppForeground(pkg)
            if (lastForegroundPkg != pkg) usage.recordOpen(pkg)
            lastForegroundPkg = pkg
        }

        // --- Settings guard: uninstall / disable / settings protection ---
        if (state.uninstallProtected && UninstallProtection.isActive(this)) {
            if (isProtectedSettingsScreen(event, pkg)) {
                lastActionAt = now
                performGlobalAction(GLOBAL_ACTION_HOME)
                banner.show("Access blocked", "Turn off protection in FocusLock first (needs your challenge).", null)
                emitPartnerEvent("uninstall_attempt")
                return
            }
        }

        // --- Enhanced settings guard (#2, #3) ---
        if (state.settingsGuarded) {
            if (isGuardedSettingsScreen(event, pkg)) {
                if (now - lastAppBlockAt > APP_BLOCK_THROTTLE) {
                    lastAppBlockAt = now
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    banner.show("Settings protected", "This setting is guarded by FocusLock.", null)
                }
                emitPartnerEvent("disable_attempt")
                return
            }
        }

        // --- Whole-app blocking (#5) ---
        if (pkg != "com.android.settings" && !pkg.startsWith("com.android.settings")) {
            if (state.isAppBlocked(pkg, now)) {
                if (now - lastAppBlockAt > APP_BLOCK_THROTTLE) {
                    lastAppBlockAt = now
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    val reason = when {
                        state.isPanicActive(now) -> "Panic mode: everything is blocked"
                        state.isQuickBlockActive(now) -> "Quick Block is active"
                        state.isPomodoroWork(now) -> "Focus time (Pomodoro)"
                        state.allowListMode -> "This app isn't in your allowed list"
                        state.activeSchedules().isNotEmpty() -> "Blocked by schedule: ${state.activeSchedules().first().name}"
                        else -> "This app is blocked"
                    }
                    banner.show("App blocked", reason, null)
                }
                return
            }

            // --- Sleep mode: lock everything except chosen essentials + call/clock safelist ---
            if (state.isSleepActive(now) && pkg !in state.sleepEssentials && !isSleepSafe(pkg)) {
                if (now - lastAppBlockAt > APP_BLOCK_THROTTLE) {
                    lastAppBlockAt = now
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    banner.show(
                        "Sleep mode",
                        "Apps are locked until %02d:%02d. Rest up.".format(state.sleepEndHour, state.sleepEndMin),
                        null,
                    )
                }
                return
            }
        }

        // --- Usage limits (#8) ---
        if (state.appDailyLimits.isNotEmpty() || state.screenTimeLimit > 0) {
            val limitMin = state.appDailyLimits[pkg]
            if (limitMin != null && limitMin > 0) {
                val usedSec = usage.todaySeconds(pkg)
                if (usedSec >= limitMin * 60) {
                    if (now - lastAppBlockAt > APP_BLOCK_THROTTLE) {
                        lastAppBlockAt = now
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        banner.show("Daily limit reached", "$pkg: ${limitMin}min used up for today.", null)
                    }
                    return
                }
            }
            if (state.screenTimeLimit > 0) {
                val totalSec = usage.todayTotalSeconds()
                if (totalSec >= state.screenTimeLimit * 60) {
                    if (now - lastAppBlockAt > APP_BLOCK_THROTTLE) {
                        lastAppBlockAt = now
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        banner.show("Screen time limit", "You've used ${state.screenTimeLimit}min of screen time today.", null)
                    }
                    return
                }
            }
        }

        // --- Website blocklist ---
        if (state.blockedSites.isNotEmpty() && pkg in BROWSERS) {
            val url = rootInActiveWindow?.let { findUrl(it) }
            val host = url?.let { com.focuslock.app.lock.normalizeDomain(it) }
            if (host != null && state.blockedSites.any { host == it || host.endsWith(".$it") }) {
                if (now - lastAppBlockAt > APP_BLOCK_THROTTLE) {
                    lastAppBlockAt = now
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    banner.show("Site blocked", "$host is on your blocklist.", null)
                }
                return
            }
        }

        // --- Content rules (feed blocking, existing logic) ---
        if (now - (lastEventAt[pkg] ?: 0L) > NEW_VISIT_MS) redirected.removeAll { ContentRules.byId[it]?.pkg == pkg }
        lastEventAt[pkg] = now
        if (now - lastActionAt < THROTTLE_MS) return

        val rules = ContentRules.all.filter { it.pkg == pkg && state.isActive(it.id, now) }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            android.util.Log.w(TAG, "window $pkg active rules=${rules.map { it.id }}")
        }
        if (rules.isEmpty()) { endSession(); return }
        val root = rootInActiveWindow ?: return
        val dm = resources.displayMetrics

        for (rule in rules) {
            when (rule.match(root, dm.widthPixels, dm.heightPixels)) {
                ContentRule.Match.TAB -> {
                    endSession()
                    android.util.Log.w(TAG, "TAB match: ${rule.id}")
                    if (!rule.backIfNoTarget) {
                        android.util.Log.w(TAG, "redirect check ${rule.id}: alreadyRedirected=${rule.id in redirected}")
                        if (rule.id in redirected) {
                            val sinceLast = now - (lastRedirectAt[rule.id] ?: 0L)
                            if (sinceLast > 1_500 && rule.clearlyOnBlockedTab(root)) redirected -= rule.id
                            else continue
                        }
                        redirected += rule.id
                        lastRedirectAt[rule.id] = now
                        if (rule.redirectTaps.isNotEmpty()) {
                            lastActionAt = now
                            tapSequence(rule, 0)
                            return
                        }
                        val ok = rule.redirectUri?.let { openInApp(rule.pkg, it) } ?: rule.goHome(root)
                        when {
                            ok -> act(now, rule, rule.tabMessage) {}
                            rule.revealIds.isNotEmpty() -> revealThenRedirect(rule, now)
                            else -> android.util.Log.w(TAG, "redirect target not found: ${rule.id}")
                        }
                        return
                    }
                    act(now, rule, rule.tabMessage) {
                        if (!rule.goHome(root)) performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    return
                }
                ContentRule.Match.VIEWER -> {
                    val mode = if (rule.hasViewerModes) state.viewerMode(rule.id) else ViewerMode.BLOCK
                    when (mode) {
                        ViewerMode.ALLOW -> endSession()
                        ViewerMode.BLOCK -> {
                            android.util.Log.w(TAG, "VIEWER block: ${rule.id}")
                            if (now < pausedUntil) return
                            endSession()
                            if (now < backSettlingUntil) return
                            if (loopGuardTripped(now)) return
                            act(now, rule, "${rule.label} blocked") { pressBackOnce(rule) }
                        }
                        ViewerMode.ONE_ONLY -> onePerOpen(rule, event, root, now)
                    }
                    return
                }
                null -> {
                    if (session?.ruleId == rule.id) endSession()
                    if (rule.otherTabSelected(root)) redirected -= rule.id
                    if (rule.screenLabels.isNotEmpty() && now - (lastRedirectAt[rule.id] ?: 0L) > 8_000) redirected -= rule.id
                }
            }
        }
    }

    // --- Settings protection ---

    private fun isProtectedSettingsScreen(event: AccessibilityEvent, pkg: String): Boolean {
        val ourPkg = packageName
        val text = event.text.joinToString(" ").lowercase()
        val className = event.className?.toString()?.lowercase() ?: ""

        if (pkg.contains("packageinstaller") || className.contains("uninstall") || text.contains("uninstall")) {
            if (text.contains(ourPkg) || text.contains("focuslock") || event.text.any { it.toString().contains(ourPkg) }) {
                return true
            }
        }

        if (pkg == "com.android.settings" || pkg.startsWith("com.android.settings")) {
            if (className.contains("deviceadminadd")) return false
            val root = rootInActiveWindow
            val screenText = root?.let { collectText(it) }?.lowercase() ?: text
            val isActivation = screenText.contains("activate") && !screenText.contains("deactivate") && !screenText.contains("de-activate")
            if (isActivation && screenText.contains("focuslock")) return false

            val isOurAppInfo = screenText.contains("focuslock") || screenText.contains(ourPkg)
            val isUninstall = screenText.contains("uninstall") || screenText.contains("remove")
            val isAccessibility = screenText.contains("accessibility") || screenText.contains("focuslock section blocker")
            val isDeviceAdmin = screenText.contains("device admin") || screenText.contains("device administrators") ||
                screenText.contains("deactivate") || screenText.contains("de-activate")

            if (isOurAppInfo && (isUninstall || isAccessibility || isDeviceAdmin)) return true
            if (isAccessibility && (screenText.contains("focuslock") || screenText.contains("section blocker"))) return true
            if (isDeviceAdmin && screenText.contains("focuslock")) return true
        }
        return false
    }

    private fun isGuardedSettingsScreen(event: AccessibilityEvent, pkg: String): Boolean {
        if (pkg != "com.android.settings" && !pkg.startsWith("com.android.settings")) return false
        val root = rootInActiveWindow ?: return false
        val screenText = collectText(root).lowercase()
        val ourPkg = packageName

        // Block FocusLock's App Info page (Force Stop, Clear Data, etc.)
        val isOurAppInfo = (screenText.contains("focuslock") || screenText.contains(ourPkg)) &&
            (screenText.contains("force stop") || screenText.contains("app info") ||
                screenText.contains("storage") || screenText.contains("clear data"))
        if (isOurAppInfo) return true

        // Block Private DNS settings
        if (screenText.contains("private dns") && (screenText.contains("dns provider") || screenText.contains("hostname"))) {
            return true
        }

        // Block VPN settings page
        if ((screenText.contains("vpn") && screenText.contains("settings")) ||
            (screenText.contains("vpn") && (screenText.contains("add") || screenText.contains("delete") || screenText.contains("edit")))
        ) {
            return true
        }

        // Block battery optimization page for FocusLock
        if (screenText.contains("battery") && screenText.contains("optimization") &&
            (screenText.contains("focuslock") || screenText.contains(ourPkg))
        ) {
            return true
        }

        return false
    }

    /** Reads the address-bar URL from a browser window, or null. */
    private fun findUrl(node: android.view.accessibility.AccessibilityNodeInfo): String? {
        val vid = node.viewIdResourceName?.lowercase()
        if (vid != null && (vid.endsWith("url_bar") || vid.contains("url") || vid.contains("location_bar") || vid.contains("address"))) {
            val t = node.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.contains('.') && !t.contains(' ')) return t
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> findUrl(child)?.let { return it } }
        }
        return null
    }

    /** Apps that must stay reachable overnight so the user isn't locked out of calls/alarm. */
    private fun isSleepSafe(pkg: String): Boolean {
        val p = pkg.lowercase()
        return listOf("dialer", "phone", "incall", "contacts", "clock", "deskclock", "alarm", "mms", "messaging", "messages")
            .any { p.contains(it) }
    }

    private fun collectText(node: android.view.accessibility.AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo) {
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(node)
        return sb.toString()
    }

    // --- Feed blocking (existing logic) ---

    private fun onePerOpen(rule: ContentRule, event: AccessibilityEvent, root: android.view.accessibility.AccessibilityNodeInfo, now: Long) {
        if (session?.ruleId != rule.id) {
            if (now < backSettlingUntil || pause.isShowing) return
            val hasPass = now < (watchPass[rule.id] ?: 0L)
            if (state.pauseSeconds > 0 && !hasPass) {
                lastActionAt = now
                pressBackOnce(rule)
                if (!pause.isShowing) {
                    pause.show(
                        title = rule.label,
                        seconds = state.pauseSeconds,
                        onLeave = {
                            log.record(rule.id)
                            BlockWidget.refresh(applicationContext)
                        },
                        onWatch = {
                            watchPass[rule.id] = System.currentTimeMillis() + WATCH_PASS_MS
                            banner.show("Open it again to watch", "You have 60 seconds. No scrolling past it.", null)
                        },
                    )
                }
                return
            }
            watchPass.remove(rule.id)
        }
        val s = session?.takeIf { it.ruleId == rule.id } ?: ViewerSession(rule.id, now).also { session = it }
        val dm = resources.displayMetrics
        val pos = rule.currentIndex(root, dm.widthPixels, dm.heightPixels)
        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (isScroll) {
            val src = event.source
            android.util.Log.d(
                TAG, "scroll ${rule.id}: src=${src?.viewIdResourceName} cls=${event.className} " +
                    "from=${event.fromIndex} to=${event.toIndex} pos=$pos base=${s.index}",
            )
        }
        if (now - s.startedAt < SETTLE_MS) {
            if (pos != null) s.index = pos
            else if (isScroll && rule.isPager(event.source) && event.fromIndex >= 0) s.index = event.fromIndex
            return
        }
        val moved = when {
            pos != null -> { if (s.index == null) { s.index = pos; false } else pos != s.index }
            isScroll && rule.isPager(event.source) ->
                s.index == null || event.fromIndex < 0 || event.fromIndex != s.index
            else -> false
        }
        if (!moved) return
        endSession()
        android.util.Log.w(TAG, "ONE_ONLY moved: ${rule.id} base=${s.index} pos=$pos")
        act(now, rule, "Only the one you opened: no scrolling") { pressBackOnce(rule) }
    }

    private fun revealThenRedirect(rule: ContentRule, now: Long) {
        lastActionAt = now
        fun attempt(step: Int) {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != rule.pkg) return
            if (rule.goHome(root)) { act(System.currentTimeMillis(), rule, rule.tabMessage) {}; return }
            when (step) {
                0 -> rule.reveal(root, forward = false)
                1 -> rule.reveal(root, forward = true)
                else -> { android.util.Log.w(TAG, "reveal failed: ${rule.id}"); return }
            }
            handler.postDelayed({ attempt(step + 1) }, 450)
        }
        attempt(0)
    }

    private fun tapSequence(rule: ContentRule, step: Int) {
        val roots = (windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow))
            .filter { it.packageName?.toString() == rule.pkg }
        if (roots.isEmpty()) return
        android.util.Log.w(TAG, "tap step $step for ${rule.id}: ${roots.size} window(s)")
        if (roots.none { rule.tapStep(it, rule.redirectTaps[step]) }) {
            android.util.Log.w(TAG, "tap step $step not found: ${rule.id}"); return
        }
        if (step == rule.redirectTaps.lastIndex) {
            act(System.currentTimeMillis(), rule, rule.tabMessage) {}
        } else {
            handler.postDelayed({ tapSequence(rule, step + 1) }, 550)
        }
    }

    private fun openInApp(pkg: String, uri: String): Boolean = runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                .setPackage(pkg).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    private fun endSession() { session = null }

    private fun pressBackOnce(rule: ContentRule) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        backSettlingUntil = System.currentTimeMillis() + BACK_SETTLE_MS
        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            if (root.packageName?.toString() != rule.pkg || !state.isActive(rule.id)) return@postDelayed
            val dm = resources.displayMetrics
            if (rule.match(root, dm.widthPixels, dm.heightPixels) == ContentRule.Match.VIEWER &&
                (!rule.hasViewerModes || state.viewerMode(rule.id) == ViewerMode.BLOCK)
            ) {
                android.util.Log.w(TAG, "viewer still open after settle, Back again: ${rule.id}")
                performGlobalAction(GLOBAL_ACTION_BACK)
                backSettlingUntil = System.currentTimeMillis() + BACK_SETTLE_MS
            }
        }, BACK_SETTLE_MS)
    }

    private fun act(now: Long, rule: ContentRule, message: String, action: () -> Unit) {
        lastActionAt = now
        action()
        log.record(rule.id, now)
        banner.show(message, state.messages.randomOrNull(), "Avoided today: ${log.todayCount()}")
        scope.launch { lock.incrementBlocked() }
        BlockWidget.refresh(applicationContext)
    }

    private fun loopGuardTripped(now: Long): Boolean {
        recentActions.addLast(now)
        while (recentActions.isNotEmpty() && now - recentActions.first() > LOOP_WINDOW_MS) recentActions.removeFirst()
        if (recentActions.size <= LOOP_MAX_ACTIONS) return false
        recentActions.clear()
        pausedUntil = now + LOOP_PAUSE_MS
        return true
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::pause.isInitialized) pause.dismiss()
        if (::banner.isInitialized) banner.dismiss()
        if (::usage.isInitialized) usage.flush()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FocusLock"
        const val THROTTLE_MS = 700L
        const val LOOP_WINDOW_MS = 6_000L
        const val LOOP_MAX_ACTIONS = 4
        const val LOOP_PAUSE_MS = 8_000L
        const val SETTLE_MS = 1_200L
        const val BACK_SETTLE_MS = 1_500L
        const val NEW_VISIT_MS = 30_000L
        const val WATCH_PASS_MS = 60_000L
        const val APP_BLOCK_THROTTLE = 1_500L
        val BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
            "org.mozilla.firefox", "com.microsoft.emmx", "com.opera.browser",
            "com.opera.mini.native", "com.brave.browser", "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser", "com.android.browser", "com.UCMobile.intl",
            "com.vivaldi.browser", "com.kiwibrowser.browser",
        )
    }
}
