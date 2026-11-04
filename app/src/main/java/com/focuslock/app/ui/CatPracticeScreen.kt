package com.focuslock.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.lock.CatQuestion
import com.focuslock.app.lock.CatQuestions
import com.focuslock.app.stats.CatStats
import com.focuslock.app.ui.components.InsetDivider
import com.focuslock.app.ui.components.LargeTitle
import com.focuslock.app.ui.components.PrimaryButton
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.SegmentedControl
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos

private val SECTIONS = listOf<String?>(null, "Quant", "LRDI", "VARC")
private val LEVELS = listOf<Int?>(null, 1, 2, 3)

/** Free CAT practice with instant explanations, plus accuracy by section and weakest topics. */
@Composable
fun PracticeTab() {
    val ios = LocalIos.current
    val context = LocalContext.current
    val store = remember { CatStats.get(context) }
    val attempts by store.attempts.collectAsStateWithLifecycle()

    var sectionIdx by remember { mutableIntStateOf(0) }
    var levelIdx by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<Pair<CatQuestion, List<Int>>?>(null) }
    var picked by remember { mutableStateOf<Int?>(null) }

    fun next() {
        val s = SECTIONS[sectionIdx]; val l = LEVELS[levelIdx]
        val pool = CatQuestions.all.filter { (s == null || it.section == s) && (l == null || it.level == l) }
            .ifEmpty { CatQuestions.all }
        val q = pool.filter { it != current?.first }.ifEmpty { pool }.random()
        current = q to q.options.indices.shuffled()
        picked = null
    }
    if (current == null) next()
    val (q, order) = current!!

    Column {
        LargeTitle("Practice", "CAT questions with instant explanations")

        Column(Modifier.padding(horizontal = 16.dp)) {
            SegmentedControl(listOf("All", "Quant", "LRDI", "VARC"), sectionIdx, { sectionIdx = it; next() }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SegmentedControl(listOf("Any", "Easy", "Medium", "Hard"), levelIdx, { levelIdx = it; next() }, Modifier.fillMaxWidth())
        }

        // ---- question card
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ios.card),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(q.section, ios.blue)
                    Spacer(Modifier.width(6.dp))
                    Pill(q.topic, ios.secondaryLabel)
                    Spacer(Modifier.width(6.dp))
                    Pill(listOf("", "Easy", "Medium", "Hard")[q.level], listOf(ios.green, ios.green, ios.orange, ios.red)[q.level])
                }
                q.passage?.let {
                    Text(
                        it, style = IosType.subhead, color = ios.secondaryLabel,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).background(ios.background).padding(12.dp),
                    )
                }
                Text(q.text, style = IosType.headline, color = ios.label, modifier = Modifier.padding(top = 12.dp))
            }
            order.forEach { idx ->
                InsetDivider()
                val answered = picked != null
                RowBase(
                    title = q.options[idx],
                    titleColor = when {
                        answered && idx == q.answer -> ios.green
                        answered && idx == picked -> ios.red
                        else -> ios.label
                    },
                    onClick = if (answered) null else ({ picked = idx; store.record(q.section, q.topic, idx == q.answer) }),
                ) {
                    if (answered && idx == q.answer) Icon(Icons.Rounded.CheckCircle, null, tint = ios.green, modifier = Modifier.size(22.dp))
                    else if (answered && idx == picked) Icon(Icons.Rounded.Cancel, null, tint = ios.red, modifier = Modifier.size(22.dp))
                }
            }
        }

        AnimatedVisibility(picked != null) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val right = picked == q.answer
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background((if (right) ios.green else ios.orange).copy(alpha = 0.13f)).padding(14.dp),
                ) {
                    Text(if (right) "Correct" else "Not quite", style = IosType.headline, color = if (right) ios.green else ios.orange)
                    Text(q.explain, style = IosType.subhead, color = ios.label, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Next question", { next() })
            }
        }

        // ---- accuracy
        val all = CatStats.accuracy(attempts)
        Section(
            header = "Your accuracy",
            footer = "Includes questions answered to unlock blocks.",
            rows = listOf<@Composable () -> Unit>({ AccuracyRow("Overall", all.correct, all.total) }) +
                listOf("Quant", "LRDI", "VARC").map { s ->
                    { val a = CatStats.accuracy(attempts.filter { it.section == s }); AccuracyRow(s, a.correct, a.total) }
                },
        )
        val weak = attempts.groupBy { it.topic }.mapValues { CatStats.accuracy(it.value) }
            .filterValues { it.total >= 3 }.entries.sortedBy { it.value.percent }.take(3)
        Section(
            header = "Weakest topics",
            footer = if (weak.isEmpty()) "Answer 3 or more questions in a topic to see it here." else null,
            rows = if (weak.isEmpty()) listOf { RowBase("Not enough answers yet") }
            else weak.map { (topic, a) -> { AccuracyRow(topic, a.correct, a.total) } },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, style = IosType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun AccuracyRow(label: String, correct: Int, total: Int) {
    val ios = LocalIos.current
    val pct = if (total == 0) 0f else correct.toFloat() / total
    val color = when { total == 0 -> ios.fill; pct >= 0.75f -> ios.green; pct >= 0.5f -> ios.orange; else -> ios.red }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Row {
            Text(label, style = IosType.body, color = ios.label, modifier = Modifier.weight(1f))
            Text(if (total == 0) "—" else "${(pct * 100).toInt()}%  ·  $correct/$total", style = IosType.body, color = ios.secondaryLabel)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ios.fill)) {
            Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}
