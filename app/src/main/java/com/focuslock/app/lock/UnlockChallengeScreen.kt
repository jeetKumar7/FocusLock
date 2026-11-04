package com.focuslock.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos

/** iOS-style sheet header: "Cancel" on the left, title in the middle. */
@Composable
fun SheetHeader(title: String, onCancel: () -> Unit) {
    val ios = LocalIos.current
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        Text(
            "Cancel", style = IosType.body, color = ios.blue,
            modifier = Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCancel).padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(title, style = IosType.headline, color = ios.label, maxLines = 1, modifier = Modifier.align(Alignment.Center).padding(horizontal = 90.dp))
    }
}

/** Thin progress bar used by all challenges. */
@Composable
fun ChallengeProgress(fraction: Float, label: String) {
    val ios = LocalIos.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ios.fill)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ios.blue))
        }
        Text(label, style = IosType.footnote, color = ios.secondaryLabel, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * Type the paragraph exactly. Rules:
 *  - one character at a time (blocks paste and word suggestions),
 *  - any wrong character wipes everything you typed so far.
 */
@Composable
fun UnlockChallengeScreen(
    title: String,
    onPassed: () -> Unit,
    onCancel: () -> Unit,
) {
    val ios = LocalIos.current
    val target = rememberSaveable { Paragraphs.random() }
    var typed by rememberSaveable { mutableStateOf("") }
    var resets by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(ios.background).imePadding()) {
        SheetHeader(title, onCancel)
        ChallengeProgress(typed.length.toFloat() / target.length, "${typed.length} of ${target.length} characters · restarts: $resets")

        Column(
            Modifier.weight(1f).padding(16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(ios.card)
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ios.green, fontWeight = FontWeight.Medium)) { append(target.substring(0, typed.length)) }
                    withStyle(SpanStyle(color = ios.label)) { append(target.substring(typed.length)) }
                },
                style = IosType.body.copy(lineHeight = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)),
            )
        }

        message?.let {
            Text(it, style = IosType.footnote, color = ios.red, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(6.dp))
        }

        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            BasicTextField(
                value = typed,
                onValueChange = { new ->
                    val added = new.length - typed.length
                    when {
                        added <= 0 -> Unit
                        added > 1 -> message = "Paste and word suggestions don't count. Type each letter."
                        !target.startsWith(new) -> {
                            typed = ""; resets++
                            message = "Wrong character. Start again from the beginning."
                        }
                        else -> {
                            typed = new; message = null
                            if (typed == target) onPassed()
                        }
                    }
                },
                textStyle = IosType.body.copy(color = ios.label),
                cursorBrush = SolidColor(ios.blue),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                    .clip(RoundedCornerShape(14.dp)).background(ios.card).padding(14.dp),
                decorationBox = { inner ->
                    if (typed.isEmpty()) Text("Start typing here…", style = IosType.body, color = ios.tertiaryLabel)
                    inner()
                },
            )
        }
    }
}
