package com.focuslock.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focuslock.app.stats.CatStats
import com.focuslock.app.ui.components.InsetDivider
import com.focuslock.app.ui.components.PrimaryButton
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Shows whichever challenge is configured. */
@Composable
fun ChallengeScreen(
    type: ChallengeType,
    title: String,
    onPassed: () -> Unit,
    onCancel: () -> Unit,
) = when (type) {
    ChallengeType.TYPING -> UnlockChallengeScreen(title, onPassed, onCancel)
    ChallengeType.MATHS -> MathsChallengeScreen(title, onPassed, onCancel)
    ChallengeType.CAT -> CatChallengeScreen(title, onPassed, onCancel)
}

// ---------------- Maths ----------------

private data class MathsProblem(val text: String, val answer: Long)

private fun nextMaths(r: Random = Random): MathsProblem = when (r.nextInt(5)) {
    0 -> { val a = r.nextInt(23, 99); val b = r.nextInt(23, 99); MathsProblem("$a × $b", a.toLong() * b) }
    1 -> { val a = r.nextInt(31, 99); MathsProblem("$a²", a.toLong() * a) }
    2 -> {
        // p is a multiple of 5 and base a multiple of 20, so the answer is always a whole number.
        val p = listOf(15, 25, 35, 45, 55, 65, 75, 85).random(r)
        val base = r.nextInt(3, 60) * 20
        MathsProblem("$p% of $base", p.toLong() * base / 100)
    }
    3 -> { val b = r.nextInt(13, 49); val q = r.nextInt(12, 89); MathsProblem("${b * q} ÷ $b", q.toLong()) }
    else -> {
        val a = r.nextInt(120, 999); val b = r.nextInt(120, 999); val c = r.nextInt(12, 99)
        MathsProblem("$a + $b − $c × 7", a.toLong() + b - c * 7L)
    }
}

/** Solve [needed] problems in a row with no calculator. A wrong answer resets the streak. */
@Composable
private fun MathsChallengeScreen(title: String, onPassed: () -> Unit, onCancel: () -> Unit, needed: Int = 5) {
    val ios = LocalIos.current
    var problem by remember { mutableStateOf(nextMaths()) }
    var input by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val guess = input.trim().toLongOrNull() ?: return
        if (guess == problem.answer) {
            streak++; message = null
            if (streak >= needed) { onPassed(); return }
        } else {
            message = "${problem.text} = ${problem.answer}. Streak reset."
            streak = 0
        }
        problem = nextMaths(); input = ""
    }

    Column(Modifier.fillMaxSize().background(ios.background).imePadding()) {
        SheetHeader(title, onCancel)
        ChallengeProgress(streak.toFloat() / needed, "$streak of $needed in a row · no calculator")

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(problem.text, style = IosType.largeTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(44f, androidx.compose.ui.unit.TextUnitType.Sp)), color = ios.label, textAlign = TextAlign.Center)
            Text("= ?", style = IosType.title2, color = ios.secondaryLabel)
            Spacer(Modifier.height(24.dp))
            BasicTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() || it == '-' } },
                singleLine = true,
                textStyle = IosType.title2.copy(color = ios.label, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(ios.blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ios.card).padding(16.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (input.isEmpty()) Text("Answer", style = IosType.title2, color = ios.tertiaryLabel)
                        inner()
                    }
                },
            )
            message?.let { Text(it, style = IosType.footnote, color = ios.red, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.weight(1f))
            PrimaryButton("Submit", { submit() }, enabled = input.isNotBlank())
        }
    }
}

// ---------------- CAT ----------------

/**
 * Answer [needed] medium/hard CAT questions correctly in a row. A wrong answer shows the
 * explanation, resets the streak and makes you wait [penaltySec] seconds.
 */
@Composable
private fun CatChallengeScreen(
    title: String,
    onPassed: () -> Unit,
    onCancel: () -> Unit,
    needed: Int = 3,
    penaltySec: Int = 30,
) {
    val ios = LocalIos.current
    val stats = CatStats.get(LocalContext.current)
    val pool = remember { CatQuestions.all.filter { it.level >= 2 } }
    val deck = remember { pool.shuffled().toMutableList() }
    fun draw(): Pair<CatQuestion, List<Int>> {
        if (deck.isEmpty()) deck += pool.shuffled()
        val q = deck.removeAt(0)
        return q to q.options.indices.shuffled()
    }

    var current by remember { mutableStateOf(draw()) }
    var streak by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) { now = System.currentTimeMillis(); delay(250) }
        now = System.currentTimeMillis()
    }
    val waiting = now < waitUntil
    val (q, order) = current

    Column(Modifier.fillMaxSize().background(ios.background)) {
        SheetHeader(title, onCancel)
        ChallengeProgress(streak.toFloat() / needed, "$streak of $needed correct in a row · ${q.section} · ${q.topic}")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            feedback?.let {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)).background(ios.orange.copy(alpha = 0.13f)).padding(14.dp),
                ) {
                    Text(if (waiting) "Wrong · next in ${(waitUntil - now) / 1000 + 1}s" else "Previous answer was wrong", style = IosType.headline, color = ios.orange)
                    Text(it, style = IosType.subhead, color = ios.label, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ios.card)) {
                Column(Modifier.padding(16.dp)) {
                    q.passage?.let {
                        Text(
                            it, style = IosType.subhead, color = ios.secondaryLabel,
                            modifier = Modifier.padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(ios.background).padding(12.dp),
                        )
                    }
                    Text(q.text, style = IosType.headline, color = ios.label)
                }
                order.forEach { idx ->
                    InsetDivider()
                    RowBase(
                        title = q.options[idx],
                        titleColor = if (waiting) ios.tertiaryLabel else ios.label,
                        onClick = if (waiting) null else ({
                            stats.record(q.section, q.topic, idx == q.answer)
                            if (idx == q.answer) {
                                streak++; feedback = null
                                if (streak >= needed) onPassed() else current = draw()
                            } else {
                                streak = 0
                                feedback = "Answer: ${q.options[q.answer]}. ${q.explain}"
                                waitUntil = System.currentTimeMillis() + penaltySec * 1000L
                                current = draw()
                            }
                        }),
                    )
                }
            }
        }
    }
}
