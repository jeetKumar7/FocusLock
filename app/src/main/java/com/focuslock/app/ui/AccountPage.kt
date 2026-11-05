package com.focuslock.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.backend.Backend
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.TintedButton
import com.focuslock.app.ui.components.ToggleRow
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import kotlinx.coroutines.launch

@Composable
fun AccountPage(onBack: () -> Unit) {
    val ios = LocalIos.current
    val scope = rememberCoroutineScope()
    val state by Backend.state.collectAsStateWithLifecycle()

    var code by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column {
        NavBar("Accountability Partner", "Settings", onBack)

        if (state.link != null) {
            Section(
                header = "Partner",
                footer = "Your partner is alerted if you relapse, or try to uninstall FocusLock or turn off a block. They only ever see what you share below — never your screen, messages, or browsing.",
                rows = listOf(
                    { RowBase("Linked", leading = { IconBadge(Icons.Rounded.Group, ios.green) }) { Text("Active", style = IosType.body, color = ios.secondaryLabel) } },
                    { RowBase("Unlink", titleColor = ios.red, onClick = { scope.launch { Backend.unlink() } }) {} },
                ),
            )
        } else {
            Section(
                header = "Invite a partner",
                footer = "Share this code with someone you trust. It expires in 15 minutes. When they enter it in their FocusLock, you'll be linked.",
                rows = listOf(
                    {
                        RowBase(
                            if (code != null) "Your code" else "Generate a code",
                            onClick = if (code == null) {
                                {
                                    if (!busy) scope.launch {
                                        busy = true; error = null
                                        runCatching { Backend.createLinkCode() }
                                            .onSuccess { code = it }
                                            .onFailure { error = it.message }
                                        busy = false
                                    }
                                }
                            } else null,
                        ) {
                            if (code != null) Text(code!!, style = IosType.title3, color = ios.blue)
                            else if (busy) Text("…", style = IosType.body, color = ios.secondaryLabel)
                        }
                    },
                ),
            )

            Section(
                header = "Enter a partner's code",
                rows = listOf {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (input.isEmpty()) Text("ABC123", style = IosType.body, color = ios.tertiaryLabel)
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it.uppercase() },
                                textStyle = IosType.body.copy(color = ios.label),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(ios.blue),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        TintedButton(
                            "Link",
                            enabled = input.length >= 4 && !busy,
                            onClick = {
                                scope.launch {
                                    busy = true; error = null
                                    runCatching { Backend.redeemLinkCode(input) }
                                        .onSuccess { input = "" }
                                        .onFailure { error = it.message }
                                    busy = false
                                }
                            },
                        )
                    }
                },
            )
        }

        if (error != null) {
            Text(error!!, style = IosType.footnote, color = ios.red, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }

        Section(
            header = "What your partner sees",
            footer = "Only these, and only while you're linked. Everything else stays on your phone.",
            rows = listOf(
                { ToggleRow("Focus streak", state.shareStreak, { on -> scope.launch { Backend.setShare(on, state.shareBlocks) } }) },
                { ToggleRow("Distractions blocked today", state.shareBlocks, { on -> scope.launch { Backend.setShare(state.shareStreak, on) } }) },
            ),
        )
        Spacer(Modifier.height(24.dp))
    }
}
