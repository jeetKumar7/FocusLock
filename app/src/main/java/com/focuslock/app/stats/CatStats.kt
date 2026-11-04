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

/** One answered CAT question, from practice or an unlock challenge. */
data class CatAttempt(val at: Long, val section: String, val topic: String, val correct: Boolean)

data class Accuracy(val correct: Int, val total: Int) {
    val percent get() = if (total == 0) 0 else correct * 100 / total
}

/** Every CAT answer, stored on the phone in a small text file. */
class CatStats private constructor(context: Context) {

    private val file = File(context.filesDir, "cat_attempts.csv")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _attempts = MutableStateFlow(load())
    val attempts: StateFlow<List<CatAttempt>> = _attempts.asStateFlow()

    fun record(section: String, topic: String, correct: Boolean) {
        val a = CatAttempt(System.currentTimeMillis(), section, topic, correct)
        _attempts.value = _attempts.value + a
        val line = listOf(a.at, a.section, a.topic.replace(',', ' '), if (correct) 1 else 0).joinToString(",")
        io.launch { synchronized(file) { file.appendText(line + "\n") } }
    }

    private fun load(): List<CatAttempt> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { l ->
            val p = l.split(',')
            if (p.size != 4) return@mapNotNull null
            CatAttempt(p[0].toLongOrNull() ?: return@mapNotNull null, p[1], p[2], p[3] == "1")
        }
    }

    companion object {
        fun accuracy(list: List<CatAttempt>) = Accuracy(list.count { it.correct }, list.size)

        @Volatile private var instance: CatStats? = null
        fun get(context: Context): CatStats =
            instance ?: synchronized(this) {
                instance ?: CatStats(context.applicationContext).also { instance = it }
            }
    }
}
