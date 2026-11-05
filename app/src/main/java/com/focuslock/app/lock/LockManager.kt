package com.focuslock.app.lock

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focuslock.app.schedule.ScheduleRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "focuslock")
private val json = Json { ignoreUnknownKeys = true }

/** "https://www.Example.com/x" -> "example.com". Returns null if it isn't a usable host. */
fun normalizeDomain(raw: String): String? {
    var d = raw.trim().lowercase()
    if (d.isEmpty()) return null
    d = d.removePrefix("https://").removePrefix("http://")
    d = d.substringBefore('/').substringBefore('?').substringBefore(':')
    d = d.removePrefix("www.")
    return d.takeIf { it.contains('.') && it.length >= 3 }
}

enum class ChallengeType(val label: String) {
    TYPING("Type a long paragraph"),
    MATHS("Solve hard maths"),
    CAT("Answer CAT questions"),
}

enum class ViewerMode(val label: String) {
    BLOCK("Block them"),
    ONE_ONLY("Only the one I opened (no scrolling)"),
    ALLOW("Allow"),
}

data class LockState(
    // --- feed blocking ---
    val enabled: Set<String> = setOf("ig_reels"),
    val offAt: Map<String, Long> = emptyMap(),
    val cooldownMinutes: Int = 10,
    val challenge: ChallengeType = ChallengeType.TYPING,
    val blockedCount: Int = 0,
    val viewerModes: Map<String, ViewerMode> = emptyMap(),
    val messages: List<String> = DEFAULT_MESSAGES,
    val pauseSeconds: Int = 8,
    val testingMode: Boolean = false,
    val uninstallProtected: Boolean = false,

    // --- app blocking (#5) ---
    val blockedApps: Set<String> = emptySet(),
    val allowListMode: Boolean = false,
    val allowedApps: Set<String> = emptySet(),

    // --- settings guard (#2, #3) ---
    val settingsGuarded: Boolean = true,

    // --- Quick Block (#7) ---
    val quickBlockUntil: Long = 0L,

    // --- Pomodoro (#7) ---
    val pomodoroWorkMin: Int = 25,
    val pomodoroBreakMin: Int = 5,
    val pomodoroUntil: Long = 0L,
    val pomodoroOnBreak: Boolean = false,

    // --- Usage limits (#8) ---
    val appDailyLimits: Map<String, Int> = emptyMap(),
    val screenTimeLimit: Int = 0,

    // --- Schedules (#6) ---
    val schedules: List<ScheduleRule> = emptyList(),

    // --- VPN filter (#4) ---
    val vpnFilterEnabled: Boolean = false,

    // --- onboarding ---
    val onboardingComplete: Boolean = false,

    // --- panic (block everything now) ---
    val panicUntil: Long = 0L,

    // --- streak (days focused since last relapse) ---
    val streakStartAt: Long = 0L, // 0 = not started yet
    val bestStreakMs: Long = 0L,

    // --- sleep mode ---
    val sleepEnabled: Boolean = false,
    val sleepStartHour: Int = 22,
    val sleepStartMin: Int = 0,
    val sleepEndHour: Int = 7,
    val sleepEndMin: Int = 0,
    val sleepEssentials: Set<String> = emptySet(),

    // --- website blocklist ---
    val blockedSites: Set<String> = emptySet(),

    // --- biometric app lock ---
    val biometricLock: Boolean = false,
) {
    fun viewerMode(id: String) = viewerModes[id] ?: ViewerMode.ONE_ONLY

    fun isActive(id: String, now: Long = System.currentTimeMillis()): Boolean =
        id in enabled && (offAt[id]?.let { now < it } ?: true)

    fun isPendingOff(id: String, now: Long = System.currentTimeMillis()): Boolean =
        id in enabled && (offAt[id]?.let { now < it } ?: false)

    fun isQuickBlockActive(now: Long = System.currentTimeMillis()) = quickBlockUntil > now

    fun isPanicActive(now: Long = System.currentTimeMillis()) = panicUntil > now

    /** Current focus streak length in ms (0 if never started). */
    fun currentStreakMs(now: Long = System.currentTimeMillis()) =
        if (streakStartAt == 0L) 0L else (now - streakStartAt).coerceAtLeast(0L)

    fun isPomodoroWork(now: Long = System.currentTimeMillis()) = pomodoroUntil > now && !pomodoroOnBreak
    fun isPomodoroBreak(now: Long = System.currentTimeMillis()) = pomodoroUntil > now && pomodoroOnBreak

    fun isAppBlocked(pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        if (isPanicActive(now)) return true // panic blocks every app
        if (isQuickBlockActive(now) && pkg in blockedApps) return true
        if (allowListMode && pkg !in allowedApps) return true
        if (pkg in blockedApps) return true
        if (schedules.any { it.isActiveNow() && (it.blockAllApps || pkg in it.blockedApps) }) return true
        return false
    }

    fun activeSchedules(): List<ScheduleRule> = schedules.filter { it.isActiveNow() }

    /** Whether the sleep window is currently open (handles overnight wrap). */
    fun isSleepActive(now: Long = System.currentTimeMillis()): Boolean {
        if (!sleepEnabled) return false
        val t = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val cur = t.hour * 60 + t.minute
        val start = sleepStartHour * 60 + sleepStartMin
        val end = sleepEndHour * 60 + sleepEndMin
        return if (start <= end) cur in start until end else (cur >= start || cur < end)
    }

    fun sleepLabel(): String = "%02d:%02d – %02d:%02d".format(sleepStartHour, sleepStartMin, sleepEndHour, sleepEndMin)

    companion object {
        val DEFAULT_MESSAGES = listOf(
            "You set this up for a reason.",
            "Ten minutes of CAT practice beats ten minutes of Reels.",
            "Boredom is not an emergency.",
            "Future you says thanks.",
        )
    }
}

class LockManager(private val context: Context) {

    private object Keys {
        val enabled = stringSetPreferencesKey("enabled_rules")
        val offAt = stringSetPreferencesKey("off_at")
        val cooldown = intPreferencesKey("cooldown_minutes")
        val challenge = stringPreferencesKey("challenge")
        val blocked = intPreferencesKey("blocked_count")
        val viewerModes = stringSetPreferencesKey("viewer_modes")
        val messages = stringPreferencesKey("toast_messages")
        val pause = intPreferencesKey("pause_seconds")
        val testing = booleanPreferencesKey("testing_mode")
        val legacyReelsOn = booleanPreferencesKey("reels_rule_on")
        val uninstallProtected = booleanPreferencesKey("uninstall_protected")

        // App blocking
        val blockedApps = stringSetPreferencesKey("blocked_apps")
        val allowListMode = booleanPreferencesKey("allow_list_mode")
        val allowedApps = stringSetPreferencesKey("allowed_apps")

        // Settings guard
        val settingsGuarded = booleanPreferencesKey("settings_guarded")

        // Quick Block
        val quickBlockUntil = longPreferencesKey("quick_block_until")

        // Pomodoro
        val pomodoroWorkMin = intPreferencesKey("pomodoro_work_min")
        val pomodoroBreakMin = intPreferencesKey("pomodoro_break_min")
        val pomodoroUntil = longPreferencesKey("pomodoro_until")
        val pomodoroOnBreak = booleanPreferencesKey("pomodoro_on_break")

        // Usage limits
        val appDailyLimits = stringSetPreferencesKey("app_daily_limits")
        val screenTimeLimit = intPreferencesKey("screen_time_limit")

        // Schedules
        val schedulesJson = stringPreferencesKey("schedules_json")

        // VPN
        val vpnFilterEnabled = booleanPreferencesKey("vpn_filter_enabled")

        // Onboarding
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")

        // Panic
        val panicUntil = longPreferencesKey("panic_until")

        // Streak
        val streakStartAt = longPreferencesKey("streak_start_at")
        val bestStreakMs = longPreferencesKey("best_streak_ms")

        // Sleep mode
        val sleepEnabled = booleanPreferencesKey("sleep_enabled")
        val sleepStartHour = intPreferencesKey("sleep_start_hour")
        val sleepStartMin = intPreferencesKey("sleep_start_min")
        val sleepEndHour = intPreferencesKey("sleep_end_hour")
        val sleepEndMin = intPreferencesKey("sleep_end_min")
        val sleepEssentials = stringSetPreferencesKey("sleep_essentials")

        // Website blocklist
        val blockedSites = stringSetPreferencesKey("blocked_sites")

        // Biometric lock
        val biometricLock = booleanPreferencesKey("biometric_lock")
    }

    val state: Flow<LockState> = context.dataStore.data.map { p ->
        val now = System.currentTimeMillis()
        val offAt = p[Keys.offAt].orEmpty().mapNotNull { e ->
            val (id, t) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            t.toLongOrNull()?.let { id to it }
        }.toMap()
        val enabled = (p[Keys.enabled] ?: defaultEnabled(p[Keys.legacyReelsOn]))
            .filterNot { id -> offAt[id]?.let { now >= it } == true }.toSet()

        val schedules = try {
            p[Keys.schedulesJson]?.let { json.decodeFromString<List<ScheduleRule>>(it) }
        } catch (_: Exception) { null } ?: emptyList()

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
            blockedApps = p[Keys.blockedApps] ?: emptySet(),
            allowListMode = p[Keys.allowListMode] ?: false,
            allowedApps = p[Keys.allowedApps] ?: emptySet(),
            settingsGuarded = p[Keys.settingsGuarded] ?: true,
            quickBlockUntil = p[Keys.quickBlockUntil] ?: 0L,
            pomodoroWorkMin = p[Keys.pomodoroWorkMin] ?: 25,
            pomodoroBreakMin = p[Keys.pomodoroBreakMin] ?: 5,
            pomodoroUntil = p[Keys.pomodoroUntil] ?: 0L,
            pomodoroOnBreak = p[Keys.pomodoroOnBreak] ?: false,
            appDailyLimits = p[Keys.appDailyLimits].orEmpty().mapNotNull { e ->
                val (pkg, min) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                min.toIntOrNull()?.let { pkg to it }
            }.toMap(),
            screenTimeLimit = p[Keys.screenTimeLimit] ?: 0,
            schedules = schedules,
            vpnFilterEnabled = p[Keys.vpnFilterEnabled] ?: false,
            onboardingComplete = p[Keys.onboardingComplete] ?: false,
            panicUntil = p[Keys.panicUntil] ?: 0L,
            streakStartAt = p[Keys.streakStartAt] ?: 0L,
            bestStreakMs = p[Keys.bestStreakMs] ?: 0L,
            sleepEnabled = p[Keys.sleepEnabled] ?: false,
            sleepStartHour = p[Keys.sleepStartHour] ?: 22,
            sleepStartMin = p[Keys.sleepStartMin] ?: 0,
            sleepEndHour = p[Keys.sleepEndHour] ?: 7,
            sleepEndMin = p[Keys.sleepEndMin] ?: 0,
            sleepEssentials = p[Keys.sleepEssentials] ?: emptySet(),
            blockedSites = p[Keys.blockedSites] ?: emptySet(),
            biometricLock = p[Keys.biometricLock] ?: false,
        )
    }

    private fun defaultEnabled(legacyReelsOn: Boolean?) =
        if (legacyReelsOn == false) emptySet() else setOf("ig_reels")

    // --- Feed blocking ---

    suspend fun enable(id: String) {
        context.dataStore.edit { p ->
            p[Keys.enabled] = (p[Keys.enabled] ?: defaultEnabled(p[Keys.legacyReelsOn])) + id
            p[Keys.offAt] = p[Keys.offAt].orEmpty().filterNot { it.startsWith("$id=") }.toSet()
        }
    }

    suspend fun scheduleOff(id: String, cooldownMinutes: Int) {
        val at = System.currentTimeMillis() + cooldownMinutes * 60_000L
        context.dataStore.edit { p ->
            p[Keys.offAt] = p[Keys.offAt].orEmpty().filterNot { it.startsWith("$id=") }.toSet() + "$id=$at"
        }
    }

    suspend fun setViewerMode(id: String, mode: ViewerMode) {
        context.dataStore.edit { p ->
            p[Keys.viewerModes] = p[Keys.viewerModes].orEmpty().filterNot { it.startsWith("$id=") }.toSet() + "$id=${mode.name}"
        }
    }

    suspend fun setMessages(lines: List<String>) {
        context.dataStore.edit { it[Keys.messages] = lines.joinToString("\n") }
    }

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

    suspend fun setChallenge(type: ChallengeType) {
        context.dataStore.edit { it[Keys.challenge] = type.name }
    }

    suspend fun incrementBlocked() {
        context.dataStore.edit { it[Keys.blocked] = (it[Keys.blocked] ?: 0) + 1 }
    }

    // --- App blocking ---

    suspend fun setBlockedApps(apps: Set<String>) {
        context.dataStore.edit { it[Keys.blockedApps] = apps }
    }

    suspend fun addBlockedApp(pkg: String) {
        context.dataStore.edit { it[Keys.blockedApps] = (it[Keys.blockedApps] ?: emptySet()) + pkg }
    }

    suspend fun removeBlockedApp(pkg: String) {
        context.dataStore.edit { it[Keys.blockedApps] = (it[Keys.blockedApps] ?: emptySet()) - pkg }
    }

    suspend fun setAllowListMode(on: Boolean) {
        context.dataStore.edit { it[Keys.allowListMode] = on }
    }

    suspend fun setAllowedApps(apps: Set<String>) {
        context.dataStore.edit { it[Keys.allowedApps] = apps }
    }

    // --- Settings guard ---

    suspend fun setSettingsGuarded(on: Boolean) {
        context.dataStore.edit { it[Keys.settingsGuarded] = on }
    }

    // --- Quick Block ---

    suspend fun startQuickBlock(minutes: Int) {
        context.dataStore.edit { it[Keys.quickBlockUntil] = System.currentTimeMillis() + minutes * 60_000L }
    }

    suspend fun stopQuickBlock() {
        context.dataStore.edit { it[Keys.quickBlockUntil] = 0L }
    }

    // --- Pomodoro ---

    suspend fun setPomodoroWork(minutes: Int) {
        context.dataStore.edit { it[Keys.pomodoroWorkMin] = minutes.coerceIn(5, 120) }
    }

    suspend fun setPomodoroBreak(minutes: Int) {
        context.dataStore.edit { it[Keys.pomodoroBreakMin] = minutes.coerceIn(1, 30) }
    }

    suspend fun startPomodoro() {
        context.dataStore.edit { p ->
            val work = p[Keys.pomodoroWorkMin] ?: 25
            p[Keys.pomodoroUntil] = System.currentTimeMillis() + work * 60_000L
            p[Keys.pomodoroOnBreak] = false
        }
    }

    suspend fun startPomodoroBreak() {
        context.dataStore.edit { p ->
            val brk = p[Keys.pomodoroBreakMin] ?: 5
            p[Keys.pomodoroUntil] = System.currentTimeMillis() + brk * 60_000L
            p[Keys.pomodoroOnBreak] = true
        }
    }

    suspend fun stopPomodoro() {
        context.dataStore.edit { p ->
            p[Keys.pomodoroUntil] = 0L
            p[Keys.pomodoroOnBreak] = false
        }
    }

    // --- Usage limits ---

    suspend fun setAppDailyLimit(pkg: String, minutes: Int) {
        context.dataStore.edit { p ->
            val cur = p[Keys.appDailyLimits].orEmpty().filterNot { it.startsWith("$pkg=") }.toSet()
            p[Keys.appDailyLimits] = if (minutes > 0) cur + "$pkg=$minutes" else cur
        }
    }

    suspend fun setScreenTimeLimit(minutes: Int) {
        context.dataStore.edit { it[Keys.screenTimeLimit] = minutes.coerceAtLeast(0) }
    }

    // --- Schedules ---

    suspend fun setSchedules(rules: List<ScheduleRule>) {
        context.dataStore.edit { it[Keys.schedulesJson] = json.encodeToString(rules) }
    }

    suspend fun addSchedule(rule: ScheduleRule) {
        context.dataStore.edit { p ->
            val cur = try {
                p[Keys.schedulesJson]?.let { json.decodeFromString<List<ScheduleRule>>(it) }
            } catch (_: Exception) { null } ?: emptyList()
            p[Keys.schedulesJson] = json.encodeToString(cur + rule)
        }
    }

    suspend fun removeSchedule(id: String) {
        context.dataStore.edit { p ->
            val cur = try {
                p[Keys.schedulesJson]?.let { json.decodeFromString<List<ScheduleRule>>(it) }
            } catch (_: Exception) { null } ?: emptyList()
            p[Keys.schedulesJson] = json.encodeToString(cur.filter { it.id != id })
        }
    }

    suspend fun updateSchedule(rule: ScheduleRule) {
        context.dataStore.edit { p ->
            val cur = try {
                p[Keys.schedulesJson]?.let { json.decodeFromString<List<ScheduleRule>>(it) }
            } catch (_: Exception) { null } ?: emptyList()
            p[Keys.schedulesJson] = json.encodeToString(cur.map { if (it.id == rule.id) rule else it })
        }
    }

    // --- VPN filter ---

    suspend fun setVpnFilterEnabled(on: Boolean) {
        context.dataStore.edit { it[Keys.vpnFilterEnabled] = on }
    }

    // --- Onboarding ---

    suspend fun setOnboardingComplete(on: Boolean) {
        context.dataStore.edit { it[Keys.onboardingComplete] = on }
    }

    /** Turns on the blocks chosen during onboarding, in one write. */
    suspend fun applyOnboarding(feeds: Boolean, apps: Boolean) {
        context.dataStore.edit { p ->
            if (feeds) {
                val feedRules = setOf("ig_reels", "yt_shorts", "fb_reels", "snap_spotlight")
                p[Keys.enabled] = (p[Keys.enabled] ?: defaultEnabled(p[Keys.legacyReelsOn])) + feedRules
                // Clear any stale off-timers so the freshly enabled feeds are actually active.
                p[Keys.offAt] = p[Keys.offAt].orEmpty()
                    .filterNot { e -> feedRules.any { e.startsWith("$it=") } }.toSet()
            }
            // App blocking is configured later by the user from the App Blocking page;
            // we only record intent here by leaving the blocked set as-is.
            p[Keys.onboardingComplete] = true
            if ((p[Keys.streakStartAt] ?: 0L) == 0L) p[Keys.streakStartAt] = System.currentTimeMillis()
        }
    }

    // --- Panic ---

    suspend fun startPanic(minutes: Int) {
        context.dataStore.edit { it[Keys.panicUntil] = System.currentTimeMillis() + minutes * 60_000L }
    }

    suspend fun stopPanic() {
        context.dataStore.edit { it[Keys.panicUntil] = 0L }
    }

    // --- Streak ---

    /** Starts the streak clock if it has never been started. */
    suspend fun ensureStreakStarted() {
        context.dataStore.edit { p ->
            if ((p[Keys.streakStartAt] ?: 0L) == 0L) p[Keys.streakStartAt] = System.currentTimeMillis()
        }
    }

    // --- Sleep mode ---

    suspend fun setSleepEnabled(on: Boolean) {
        context.dataStore.edit { it[Keys.sleepEnabled] = on }
    }

    suspend fun setSleepStart(hour: Int, min: Int) {
        context.dataStore.edit { it[Keys.sleepStartHour] = hour.coerceIn(0, 23); it[Keys.sleepStartMin] = min.coerceIn(0, 59) }
    }

    suspend fun setSleepEnd(hour: Int, min: Int) {
        context.dataStore.edit { it[Keys.sleepEndHour] = hour.coerceIn(0, 23); it[Keys.sleepEndMin] = min.coerceIn(0, 59) }
    }

    suspend fun setSleepEssentials(apps: Set<String>) {
        context.dataStore.edit { it[Keys.sleepEssentials] = apps }
    }

    // --- Website blocklist ---

    suspend fun addBlockedSite(domain: String) {
        val clean = normalizeDomain(domain) ?: return
        context.dataStore.edit { it[Keys.blockedSites] = (it[Keys.blockedSites] ?: emptySet()) + clean }
    }

    suspend fun removeBlockedSite(domain: String) {
        context.dataStore.edit { it[Keys.blockedSites] = (it[Keys.blockedSites] ?: emptySet()) - domain }
    }

    // --- Biometric lock ---

    suspend fun setBiometricLock(on: Boolean) {
        context.dataStore.edit { it[Keys.biometricLock] = on }
    }

    /** Records a relapse: banks the best streak and restarts the clock from now. */
    suspend fun relapse() {
        context.dataStore.edit { p ->
            val start = p[Keys.streakStartAt] ?: 0L
            val now = System.currentTimeMillis()
            if (start != 0L) {
                val current = (now - start).coerceAtLeast(0L)
                if (current > (p[Keys.bestStreakMs] ?: 0L)) p[Keys.bestStreakMs] = current
            }
            p[Keys.streakStartAt] = now
        }
    }
}
