package com.focuslock.app.stats

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One block: when, and which rule (see rules/ContentRules.kt). */
data class BlockEvent(val at: Long, val ruleId: String) {
    val date: LocalDate get() = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val hour: Int get() = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).hour
}

/**
 * Every block, stored on the phone in a small text file ("millis,ruleId" per line).
 * Keeps the last [KEEP_DAYS] days. Shared by the service (writes) and the UI/widget (reads).
 */
class BlockLog private constructor(context: Context) {

    private val file = File(context.filesDir, "blocks.csv")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<BlockEvent>> = _events.asStateFlow()

    fun record(ruleId: String, at: Long = System.currentTimeMillis()) {
        val e = BlockEvent(at, ruleId)
        _events.value = _events.value + e
        io.launch { synchronized(file) { file.appendText("${e.at},${e.ruleId}\n") } }
    }

    fun todayCount(): Int {
        val today = LocalDate.now()
        return _events.value.count { it.date == today }
    }

    private fun load(): List<BlockEvent> {
        if (!file.exists()) return emptyList()
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
        val all = file.readLines().mapNotNull { line ->
            val (t, id) = line.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            t.toLongOrNull()?.let { BlockEvent(it, id) }
        }
        val kept = all.filter { it.at >= cutoff }
        if (kept.size != all.size) file.writeText(kept.joinToString("") { "${it.at},${it.ruleId}\n" })
        return kept
    }

    companion object {
        private const val KEEP_DAYS = 60

        @Volatile private var instance: BlockLog? = null
        fun get(context: Context): BlockLog =
            instance ?: synchronized(this) {
                instance ?: BlockLog(context.applicationContext).also { instance = it }
            }
    }
}
