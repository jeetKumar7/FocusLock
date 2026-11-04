package com.focuslock.app.rules

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Blocks one part of an app (Reels, Shorts, Stories...) while leaving the rest usable.
 *
 * Detection is data-driven so an app update that renames things is a one-line fix:
 *  - [viewerIds]: view IDs that, when visible, mean the blocked screen is open.
 *  - [tabIds] / [tabLabels]: bottom-bar tabs that are blocked when selected.
 * Find new IDs with Android Studio → Tools → Layout Inspector, or
 * `adb shell uiautomator dump` while the screen is open.
 */
data class ContentRule(
    val id: String,
    val label: String,
    val pkg: String,
    val viewerIds: List<String> = emptyList(),
    val tabIds: List<String> = emptyList(),
    val tabLabels: List<String> = emptyList(),
    /** The app's home/feed tab (view ID or content description), clicked when a blocked tab is selected. */
    val homeIds: List<String> = emptyList(),
    val homeLabels: List<String> = emptyList(),
    /**
     * The vertical list you swipe through (e.g. Instagram's clips_viewer_view_pager). When set,
     * the rule supports [ViewerMode]: Reels opened outside the tab can be allowed one at a time.
     */
    val pagerIds: List<String> = emptyList(),
    /** Shown on the toast when a blocked tab is redirected. */
    val tabMessage: String = "$label: tab blocked",
    /**
     * Redirect-only rules (e.g. YouTube Home → Subscriptions) must never press Back when the
     * target can't be found: Back on an app's start screen closes the app.
     */
    val backIfNoTarget: Boolean = true,
    /** Redirect by opening this link in the app instead of tapping a tab (e.g. YouTube Subscriptions). */
    val redirectUri: String? = null,
    /** Lists to nudge-scroll when the redirect target is in a bar that hides itself (YouTube's top tabs). */
    val revealIds: List<String> = emptyList(),
    /**
     * Redirect by tapping a short sequence of things (e.g. Instagram logo, then "Following").
     * Each step lists alternatives: a view ID, or an exact content description / text.
     */
    val redirectTaps: List<List<String>> = emptyList(),
    /**
     * Content descriptions that identify the screen to redirect away from, when tabs aren't
     * reliable (e.g. Instagram's logo is labelled "Instagram Home feed" only on the For-You feed).
     */
    val screenLabels: List<String> = emptyList(),
) {
    fun onScreen(root: AccessibilityNodeInfo) = screenLabels.any { label ->
        labelNodes(root, label).any { it.isVisibleToUser }
    }

    /** Taps the first visible match for one step. Returns false if nothing matched. */
    fun tapStep(root: AccessibilityNodeInfo, alternatives: List<String>): Boolean {
        for (alt in alternatives) {
            val byId = root.findAccessibilityNodeInfosByViewId(qualify(alt))
            val nodes = byId.ifEmpty { labelNodes(root, alt) }
            val target = nodes.firstOrNull { it.isVisibleToUser } ?: continue
            var n: AccessibilityNodeInfo? = target
            while (n != null && !n.isClickable) n = n.parent
            if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
    }

    /** Nudge-scrolls a [revealIds] list to bring a hidden bar into view. */
    fun reveal(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return revealIds.flatMap { root.findAccessibilityNodeInfosByViewId(qualify(it)) }
            .any { it.isVisibleToUser && it.performAction(action) }
    }

    /** TAB = the blocked tab is selected. VIEWER = the full-screen player opened from elsewhere. */
    enum class Match { VIEWER, TAB }

    val hasViewerModes get() = pagerIds.isNotEmpty()

    /**
     * Position (in the swipe list) of the Reel/Short currently filling the screen, read from the
     * list item's collection info. null if the app doesn't expose it.
     */
    fun currentIndex(root: AccessibilityNodeInfo, screenW: Int, screenH: Int): Int? {
        val pager = pagerIds.flatMap { root.findAccessibilityNodeInfosByViewId(qualify(it)) }
            .firstOrNull { it.coversScreen(screenW, screenH) } ?: return null
        var best: AccessibilityNodeInfo? = null
        var bestH = 0
        val r = android.graphics.Rect()
        for (i in 0 until pager.childCount) {
            val c = pager.getChild(i) ?: continue
            c.getBoundsInScreen(r)
            if (!r.intersect(0, 0, screenW, screenH)) continue
            if (r.height() > bestH) { bestH = r.height(); best = c }
        }
        val info = best?.collectionItemInfo ?: return null
        return maxOf(info.rowIndex, info.columnIndex).takeIf { it >= 0 }
    }

    fun isPager(node: AccessibilityNodeInfo?): Boolean {
        val id = node?.viewIdResourceName ?: return false
        return pagerIds.any { qualify(it) == id }
    }

    /**
     * [screenW]/[screenH] are used to ignore pages an app keeps loaded off-screen (Instagram
     * keeps Reels next to Home in a swipeable pager): a viewer only counts when it really
     * covers most of the screen.
     */
    fun match(root: AccessibilityNodeInfo, screenW: Int, screenH: Int): Match? {
        // On the app's home tab nothing here can be "the blocked tab". This also guarantees
        // we never tap Home while already on Home (which refreshes the feed in a loop).
        if (screenLabels.isNotEmpty()) return if (onScreen(root)) Match.TAB else null
        val onHome = homeNodes(root).any { it.selfOrParentSelected() }
        if (!onHome && tabSelected(root)) return Match.TAB
        val viewerHit = viewerIds.any { id ->
            root.findAccessibilityNodeInfosByViewId(qualify(id)).any { it.coversScreen(screenW, screenH) }
        }
        return if (viewerHit) Match.VIEWER else null
    }

    /** True only when the tab bar is visible and a DIFFERENT tab is selected (you really switched tabs). */
    fun otherTabSelected(root: AccessibilityNodeInfo): Boolean {
        val byId = tabIds.flatMap { root.findAccessibilityNodeInfosByViewId(qualify(it)) }.filter { it.isVisibleToUser }
        if (byId.isNotEmpty()) return byId.none { it.selfOrParentSelected() }
        val byLabel = tabLabels.flatMap { labelNodes(root, it) }.filter { it.isVisibleToUser }
        return byLabel.isNotEmpty() && byLabel.none { it.selfOrParentSelected() }
    }

    /** True if the blocked tab is selected at all (bar visible or not). */
    fun isTabSelected(root: AccessibilityNodeInfo) = tabSelected(root)

    /**
     * True only when we can actually SEE the redirect target and it is not selected while the
     * blocked tab is: i.e. you really went back to Home. When the bar is hidden we can't tell.
     */
    fun clearlyOnBlockedTab(root: AccessibilityNodeInfo): Boolean {
        val targets = homeNodes(root).filter { it.isVisibleToUser }
        return targets.isNotEmpty() && targets.none { it.selfOrParentSelected() } && tabSelected(root)
    }

    /** Uses the real tab IDs when the tab bar is on screen; labels only as a fallback. */
    private fun tabSelected(root: AccessibilityNodeInfo): Boolean {
        val byId = tabIds.flatMap { root.findAccessibilityNodeInfosByViewId(qualify(it)) }
        if (byId.isNotEmpty()) return byId.any { it.isVisibleToUser && it.selfOrParentSelected() }
        return tabLabels.any { label ->
            labelNodes(root, label).any { it.isVisibleToUser && it.selfOrParentSelected() }
        }
    }

    /** Nodes whose content description OR text is exactly [label] (tabs use either). */
    private fun labelNodes(root: AccessibilityNodeInfo, label: String) =
        root.findAccessibilityNodeInfosByText(label).filter {
            it.contentDescription?.toString()?.trim().equals(label, ignoreCase = true) ||
                it.text?.toString()?.trim().equals(label, ignoreCase = true)
        }

    /** Some apps (e.g. X) mark the tab container as selected, not the label inside it. */
    /**
     * Apps mark the current tab in different places: on the tab itself, on its container (X),
     * or on the icon inside it (newer Instagram). Check all three.
     */
    private fun AccessibilityNodeInfo.selfOrParentSelected(): Boolean {
        if (isSelected || isChecked || parent?.isSelected == true || parent?.isChecked == true) return true
        for (i in 0 until childCount) {
            val c = getChild(i) ?: continue
            if (c.isSelected || c.isChecked) return true
        }
        return false
    }

    private fun homeNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val byId = homeIds.flatMap { root.findAccessibilityNodeInfosByViewId(qualify(it)) }
        if (byId.isNotEmpty()) return byId
        return homeLabels.flatMap { labelNodes(root, it) }
    }

    /** Clicks the app's home/feed tab. Returns false if it could not find one. Never clicks if already on Home. */
    fun goHome(root: AccessibilityNodeInfo): Boolean {
        val nodes = homeNodes(root)
        if (nodes.any { it.selfOrParentSelected() }) return true
        var n: AccessibilityNodeInfo? = nodes.firstOrNull() ?: return false
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun AccessibilityNodeInfo.coversScreen(w: Int, h: Int): Boolean {
        if (!isVisibleToUser) return false
        val r = android.graphics.Rect().also { getBoundsInScreen(it) }
        if (!r.intersect(0, 0, w, h)) return false
        return r.width() >= w * 0.8 && r.height() >= h * 0.5
    }

    private fun qualify(id: String) = if (':' in id) id else "$pkg:id/$id"
}

object ContentRules {
    private const val IG = "com.instagram.android"
    private const val YT = "com.google.android.youtube"
    private const val FB = "com.facebook.katana"
    private const val SNAP = "com.snapchat.android"
    private const val X = "com.twitter.android"

    val all = listOf(
        ContentRule(
            id = "ig_reels", label = "Instagram Reels", pkg = IG,
            // Only the full-screen player. (clips_video_container etc. also appear on Reels
            // playing inside the home feed, which must not count.)
            viewerIds = listOf("clips_viewer_view_pager", "clips_viewer_container"),
            tabIds = listOf("clips_tab"), tabLabels = listOf("Reels"),
            homeIds = listOf("feed_tab"), homeLabels = listOf("Home"),
            pagerIds = listOf("clips_viewer_view_pager"),
        ),
        ContentRule(
            // Home → Following feed: tap the Instagram logo, then "Following" in its menu.
            id = "ig_following", label = "Instagram Following feed by default", pkg = IG,
            screenLabels = listOf("Instagram Home feed"),
            backIfNoTarget = false,
            redirectTaps = listOf(
                listOf("action_bar_title_logo", "title_logo", "action_bar_logo", "Instagram"),
                listOf("Following"),
            ),
            tabMessage = "Instagram → Following feed",
        ),
        ContentRule(
            // Instagram calls Stories "reels" internally (reel_viewer); Reels are "clips".
            id = "ig_stories", label = "Instagram Stories", pkg = IG,
            viewerIds = listOf("reel_viewer_root", "reel_viewer_media_container", "reel_viewer_texture_view"),
            homeIds = listOf("feed_tab"), homeLabels = listOf("Home"),
        ),
        ContentRule(
            id = "ig_explore", label = "Instagram Explore", pkg = IG,
            tabIds = listOf("search_tab"), tabLabels = listOf("Search and explore"),
            homeIds = listOf("feed_tab"), homeLabels = listOf("Home"),
        ),
        ContentRule(
            id = "yt_shorts", label = "YouTube Shorts", pkg = YT,
            viewerIds = listOf(
                "reel_watch_fragment_root", "reel_recycler", "reel_player_page_container",
                "reel_watch_player",
            ),
            tabLabels = listOf("Shorts"),
            homeLabels = listOf("Home"),
            pagerIds = listOf("reel_recycler"),
        ),
        ContentRule(
            // "YouTube without the algorithm": the Home tab sends you to Subscriptions.
            // Subscriptions is the "Subs" tab in YouTube's top bar, which hides itself until
            // you scroll. We nudge the feed to reveal it, then tap it.
            id = "yt_home", label = "YouTube Home feed", pkg = YT,
            tabLabels = listOf("Home"),
            homeLabels = listOf("Subs", "Subscriptions"),
            revealIds = listOf("results"),
            tabMessage = "YouTube Home → Subscriptions",
            backIfNoTarget = false,
        ),
        ContentRule(
            id = "x_for_you", label = "X \"For you\" feed", pkg = X,
            tabLabels = listOf("For you"),
            homeLabels = listOf("Following"),
            tabMessage = "X: For you → Following",
            backIfNoTarget = false,
        ),
        ContentRule(
            id = "fb_reels", label = "Facebook Reels", pkg = FB,
            tabLabels = listOf("Reels", "Video"),
            viewerIds = listOf("reels_viewer", "video_home_reels"),
            homeLabels = listOf("Home"),
        ),
        ContentRule(
            id = "snap_spotlight", label = "Snapchat Spotlight", pkg = SNAP,
            tabLabels = listOf("Spotlight"),
            homeLabels = listOf("Camera", "Chat"),
        ),
        ContentRule(
            id = "snap_stories", label = "Snapchat Stories", pkg = SNAP,
            tabLabels = listOf("Stories"),
            homeLabels = listOf("Camera", "Chat"),
        ),
    )

    val byId = all.associateBy { it.id }
    val packages = all.map { it.pkg }.toSet()
}
