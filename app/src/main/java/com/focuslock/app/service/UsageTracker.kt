package com.focuslock.app.service

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

class UsageTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("usage_tracker", Context.MODE_PRIVATE)

    @Volatile private var currentPkg: String? = null
    @Volatile private var fgSince: Long = 0L
    private var storedDate: String = ""

    init { storedDate = todayKey() }

    private fun todayKey() = LocalDate.now().toString()

    fun onAppForeground(pkg: String) {
        val now = System.currentTimeMillis()
        flush(now)
        currentPkg = pkg
        fgSince = now
    }

    fun flush(now: Long = System.currentTimeMillis()) {
        val pkg = currentPkg ?: return
        // Cap a single gap: with the screen off the service stops getting events, so an
        // un-capped gap would dump hours of idle time onto whatever was last in front.
        val elapsed = ((now - fgSince) / 1000).toInt().coerceIn(0, MAX_GAP_SEC)
        if (elapsed <= 0) return
        val today = todayKey()
        if (today != storedDate) { clearOld(); storedDate = today }
        val key = "t_${today}_$pkg"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + elapsed).apply()
        fgSince = now
    }

    fun todaySeconds(pkg: String): Int {
        val today = todayKey()
        if (today != storedDate) { clearOld(); storedDate = today }
        flush()
        return prefs.getInt("t_${today}_$pkg", 0)
    }

    fun todayTotalSeconds(): Int {
        val today = todayKey()
        if (today != storedDate) { clearOld(); storedDate = today }
        flush()
        return prefs.all.entries
            .filter { it.key.startsWith("t_${today}_") }
            .sumOf { (it.value as? Int) ?: 0 }
    }

    fun todayOpens(pkg: String): Int = prefs.getInt("o_${todayKey()}_$pkg", 0)

    /** (pkg, seconds) for every app used today, busiest first. */
    fun todayApps(): List<Pair<String, Int>> {
        val today = todayKey()
        if (today != storedDate) { clearOld(); storedDate = today }
        flush()
        val prefix = "t_${today}_"
        return prefs.all.entries
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { e -> ((e.value as? Int) ?: 0).let { s -> if (s > 0) e.key.removePrefix(prefix) to s else null } }
            .sortedByDescending { it.second }
    }

    fun recordOpen(pkg: String) {
        val today = todayKey()
        if (today != storedDate) { clearOld(); storedDate = today }
        val key = "o_${today}_$pkg"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    companion object {
        private const val MAX_GAP_SEC = 180 // 3 min: longest gap we credit between events
    }

    private fun clearOld() {
        val today = todayKey()
        val ed = prefs.edit()
        prefs.all.keys.filter { k ->
            (k.startsWith("t_") || k.startsWith("o_")) && !k.contains(today)
        }.forEach { ed.remove(it) }
        ed.apply()
    }
}
