package com.focuslock.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
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

/** Sends you out of the app sections you chose to block (Reels, Shorts, Stories...). */
class GuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lock: LockManager
    private lateinit var log: BlockLog
    private lateinit var pause: PauseOverlay

    @Volatile private var state = LockState()
    private var lastActionAt = 0L
    /** Loop guard: recent action times. If we keep firing, something is misdetected, so back off. */
    private val recentActions = ArrayDeque<Long>()
    private var pausedUntil = 0L
    /** After pressing Back we wait for the closing animation before ever pressing Back again. */
    private var backSettlingUntil = 0L
    /** Redirect rules that already redirected in this visit to the app. */
    private val redirected = mutableSetOf<String>()
    private val lastEventAt = mutableMapOf<String, Long>()
    private val lastRedirectAt = mutableMapOf<String, Long>()
    /** rule id -> time until which a Reel may be opened without the pause ("Watch it"). */
    private val watchPass = mutableMapOf<String, Long>()
    private lateinit var banner: Banner

    override fun onServiceConnected() {
        lock = LockManager(applicationContext)
        log = BlockLog.get(applicationContext)
        pause = PauseOverlay(this)
        banner = Banner(this)
        lock.state.onEach { state = it }.launchIn(scope)
    }

    /** A Reel/Short opened outside its tab that we are letting you watch (ONE_ONLY mode). */
    private data class ViewerSession(
        val ruleId: String,
        var startedAt: Long,
        var index: Int? = null,
    )
    private var session: ViewerSession? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()
        // Coming back to an app after a while counts as a new visit.
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
                // The tab is always blocked, every time. No back-off here: the tab check is reliable.
                ContentRule.Match.TAB -> {
                    endSession()
                    android.util.Log.w(TAG, "TAB match: ${rule.id}")
                    if (!rule.backIfNoTarget) {
                        android.util.Log.w(TAG, "redirect check ${rule.id}: alreadyRedirected=${rule.id in redirected}")
                        // Redirect-only: redirect once, then leave you alone until you switch
                        // tabs or leave the app. Never presses Back (that would close the app).
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
                    // You switched to another bottom tab: redirect again next time you come back.
                    // (Being on the redirect target also gives null; that must NOT reset.)
                    if (rule.otherTabSelected(root)) redirected -= rule.id
                    // Screen-based rules: not on that screen any more, so redirect again next time
                    // (but never more often than every few seconds, so it can't loop).
                    if (rule.screenLabels.isNotEmpty() && now - (lastRedirectAt[rule.id] ?: 0L) > 8_000) redirected -= rule.id
                }
            }
        }
    }

    /**
     * Lets you watch the Reel/Short you opened, but sends you back as soon as another one fills
     * the screen. Primary signal: the list position of the visible item (works even when the app
     * sends no scroll events, like YouTube). Backup: scroll events from the swipe list.
     * Changes in the first moment are the player settling, not you.
     */
    private fun onePerOpen(rule: ContentRule, event: AccessibilityEvent, root: android.view.accessibility.AccessibilityNodeInfo, now: Long) {
        if (session?.ruleId != rule.id) {
            // Still closing from our own Back (or the pause screen is up): don't act again.
            if (now < backSettlingUntil || pause.isShowing) return
            val hasPass = now < (watchPass[rule.id] ?: 0L)
            if (state.pauseSeconds > 0 && !hasPass) {
                // Close the Reel first so it doesn't keep playing behind the pause screen.
                // "Watch it" gives a short pass: open it again and it plays from the start.
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
            watchPass.remove(rule.id) // one use per pass
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
            pos != null -> {
                if (s.index == null) { s.index = pos; false } else pos != s.index
            }
            isScroll && rule.isPager(event.source) ->
                s.index == null || event.fromIndex < 0 || event.fromIndex != s.index
            else -> false
        }
        if (!moved) return
        endSession()
        android.util.Log.w(TAG, "ONE_ONLY moved: ${rule.id} base=${s.index} pos=$pos")
        act(now, rule, "Only the one you opened: no scrolling") { pressBackOnce(rule) }
    }

    /**
     * The target lives in a bar that hides itself: nudge the list (back first, then forward)
     * until the bar shows, then tap the target. Gives up quietly if it never appears.
     */
    private fun revealThenRedirect(rule: ContentRule, now: Long) {
        lastActionAt = now
        fun attempt(step: Int) {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != rule.pkg) return
            if (rule.goHome(root)) {
                act(System.currentTimeMillis(), rule, rule.tabMessage) {}
                return
            }
            when (step) {
                0 -> rule.reveal(root, forward = false)
                1 -> rule.reveal(root, forward = true)
                else -> { android.util.Log.w(TAG, "reveal failed: ${rule.id}"); return }
            }
            handler.postDelayed({ attempt(step + 1) }, 450)
        }
        attempt(0)
    }

    /** Taps each step of [ContentRule.redirectTaps] in turn, waiting for menus to open in between. */
    private fun tapSequence(rule: ContentRule, step: Int) {
        // Menus open as separate pop-up windows, so look in every window of that app.
        val roots = (windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow))
            .filter { it.packageName?.toString() == rule.pkg }
        if (roots.isEmpty()) return
        android.util.Log.w(TAG, "tap step $step for ${rule.id}: ${roots.size} window(s)")
        if (roots.none { rule.tapStep(it, rule.redirectTaps[step]) }) {
            android.util.Log.w(TAG, "tap step $step not found: ${rule.id}")
            return
        }
        if (step == rule.redirectTaps.lastIndex) {
            act(System.currentTimeMillis(), rule, rule.tabMessage) {}
        } else {
            handler.postDelayed({ tapSequence(rule, step + 1) }, 550)
        }
    }

    /** Opens [uri] inside [pkg] (e.g. YouTube's Subscriptions page). */
    private fun openInApp(pkg: String, uri: String): Boolean = runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                .setPackage(pkg)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /** The pause screen is independent of the session: it stays until you choose. */
    private fun endSession() {
        session = null
    }

    /**
     * Presses Back, then ignores the closing animation (which still shows the viewer for a
     * moment). If the viewer is somehow still open after that, press Back once more.
     * Without this, a second Back lands on the app's home screen and closes the app.
     */
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

    /** True if we have been firing over and over (probably a false match): pause instead of spamming. */
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
    }
}
