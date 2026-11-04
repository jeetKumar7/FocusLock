package com.focuslock.app.lock

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "focuslock")

/** How you prove you really mean it before a block is loosened. */
enum class ChallengeType(val label: String) {
    TYPING("Type a long paragraph"),
    MATHS("Solve hard maths"),
    CAT("Answer CAT questions"),
}

/**
 * What happens when a Reel/Short is opened full-screen OUTSIDE its tab (from the feed, a DM,
 * a profile). Ordered strictest first: moving down the list counts as loosening.
 */
enum class ViewerMode(val label: String) {
    BLOCK("Block them"),
    ONE_ONLY("Only the one I opened (no scrolling)"),
    ALLOW("Allow"),
}

/**
 * Which in-app sections are blocked (see rules/ContentRules.kt for ids).
 *
 * Turning a block ON, or making the cooldown longer, applies instantly. Turning a block OFF
 * needs the challenge, and then only takes effect after the cooldown: we store the time the
 * block switches off rather than a plain "off".
 */
data class LockState(
    val enabled: Set<String> = setOf("ig_reels"),
    /** rule id -> epoch millis when it switches off. */
    val offAt: Map<String, Long> = emptyMap(),
    val cooldownMinutes: Int = 10,
    val challenge: ChallengeType = ChallengeType.TYPING,
    val blockedCount: Int = 0,
    /** rule id -> mode, for rules with viewer modes. Missing = ONE_ONLY. */
    val viewerModes: Map<String, ViewerMode> = emptyMap(),
    /** Lines shown under the block toast, one picked at random. */
    val messages: List<String> = DEFAULT_MESSAGES,
    /** Seconds of "Do you really want this?" before a Reel opened outside its tab plays. 0 = off. */
    val pauseSeconds: Int = 8,
    /** Debug builds only: loosening skips the challenge and cooldown. */
    val testingMode: Boolean = false,
    /** When true, Device Admin is requested and uninstall/disable attempts are blocked. */
    val uninstallProtected: Boolean = false,
) {
    fun viewerMode(id: String) = viewerModes[id] ?: ViewerMode.ONE_ONLY

    companion object {
        val DEFAULT_MESSAGES = listOf(
            "You set this up for a reason.",
            "Ten minutes of CAT practice beats ten minutes of Reels.",
            "Boredom is not an emergency.",
            "Future you says thanks.",
        )
    }

    fun isActive(id: String, now: Long = System.currentTimeMillis()): Boolean =
        id in enabled && (offAt[id]?.let { now < it } ?: true)

    fun isPendingOff(id: String, now: Long = System.currentTimeMillis()): Boolean =
        id in enabled && (offAt[id]?.let { now < it } ?: false)
}

class LockManager(private val context: Context) {

    private object Keys {
        val enabled = stringSetPreferencesKey("enabled_rules")
        val offAt = stringSetPreferencesKey("off_at") // "id=millis"
        val cooldown = intPreferencesKey("cooldown_minutes")
        val challenge = stringPreferencesKey("challenge")
        val blocked = intPreferencesKey("blocked_count")
        val viewerModes = stringSetPreferencesKey("viewer_modes") // "id=MODE"
        val messages = stringPreferencesKey("toast_messages") // newline-separated
        val pause = intPreferencesKey("pause_seconds")
        val testing = androidx.datastore.preferences.core.booleanPreferencesKey("testing_mode")
        val legacyReelsOn = androidx.datastore.preferences.core.booleanPreferencesKey("reels_rule_on")
        val uninstallProtected = androidx.datastore.preferences.core.booleanPreferencesKey("uninstall_protected")
    }

    val state: Flow<LockState> = context.dataStore.data.map { p ->
        val now = System.currentTimeMillis()
        val offAt = p[Keys.offAt].orEmpty().mapNotNull { e ->
            val (id, t) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            t.toLongOrNull()?.let { id to it }
        }.toMap()
        // Blocks whose cooldown has finished are simply off.
        val enabled = (p[Keys.enabled] ?: defaultEnabled(p[Keys.legacyReelsOn]))
            .filterNot { id -> offAt[id]?.let { now >= it } == true }.toSet()
        LockState(
            enabled = enabled,
            offAt = offAt.filterKeys { it in enabled },
            cooldownMinutes = p[Keys.cooldown] ?: 10,
            challenge = p[Keys.challenge]?.let { runCatching { ChallengeType.valueOf(it) }.getOrNull() }
                ?: ChallengeType.TYPING,
            blockedCount = p[Keys.blocked] ?: 0,
            viewerModes = p[Keys.viewerModes].orEmpty().mapNotNull { e ->
                val (id, m) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                runCatching { id to ViewerMode.valueOf(m) }.getOrNull()
            }.toMap(),
            messages = p[Keys.messages]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() } ?: LockState.DEFAULT_MESSAGES,
            pauseSeconds = p[Keys.pause] ?: 8,
            testingMode = com.focuslock.app.BuildConfig.DEBUG && (p[Keys.testing] ?: false),
            uninstallProtected = p[Keys.uninstallProtected] ?: false,
        )
    }

    private fun defaultEnabled(legacyReelsOn: Boolean?) =
        if (legacyReelsOn == false) emptySet() else setOf("ig_reels")

    /** Stricter: no challenge. Also cancels a pending switch-off. */
    suspend fun enable(id: String) {
        context.dataStore.edit { p ->
            p[Keys.enabled] = (p[Keys.enabled] ?: defaultEnabled(p[Keys.legacyReelsOn])) + id
            p[Keys.offAt] = p[Keys.offAt].orEmpty().filterNot { it.startsWith("$id=") }.toSet()
        }
    }

    /** Looser: call ONLY after the challenge has been passed. */
    suspend fun scheduleOff(id: String, cooldownMinutes: Int) {
        val at = System.currentTimeMillis() + cooldownMinutes * 60_000L
        context.dataStore.edit { p ->
            p[Keys.offAt] = p[Keys.offAt].orEmpty().filterNot { it.startsWith("$id=") }.toSet() + "$id=$at"
        }
    }

    /** Stricter (lower ordinal) is free; looser needs the challenge first. */
    suspend fun setViewerMode(id: String, mode: ViewerMode) {
        context.dataStore.edit { p ->
            p[Keys.viewerModes] = p[Keys.viewerModes].orEmpty().filterNot { it.startsWith("$id=") }.toSet() + "$id=${mode.name}"
        }
    }

    /** Cosmetic, so no challenge needed. */
    suspend fun setMessages(lines: List<String>) {
        context.dataStore.edit { it[Keys.messages] = lines.joinToString("\n") }
    }

    /** Longer = stricter (free); shorter needs the challenge. */
    suspend fun setPauseSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.pause] = seconds.coerceIn(0, 60) }
    }

    suspend fun setTestingMode(on: Boolean) {
        context.dataStore.edit { it[Keys.testing] = on && com.focuslock.app.BuildConfig.DEBUG }
    }

    suspend fun setUninstallProtected(on: Boolean) {
        context.dataStore.edit { it[Keys.uninstallProtected] = on }
    }

    suspend fun setCooldown(minutes: Int) {
        context.dataStore.edit { it[Keys.cooldown] = minutes.coerceIn(0, 240) }
    }

    /** Changing the challenge type counts as loosening, so the caller runs the current one first. */
    suspend fun setChallenge(type: ChallengeType) {
        context.dataStore.edit { it[Keys.challenge] = type.name }
    }

    suspend fun incrementBlocked() {
        context.dataStore.edit { it[Keys.blocked] = (it[Keys.blocked] ?: 0) + 1 }
    }
}
