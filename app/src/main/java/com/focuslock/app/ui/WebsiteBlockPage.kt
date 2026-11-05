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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.focuslock.app.lock.LockState
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.TintedButton
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos

@Composable
fun WebsiteBlockPage(
    state: LockState,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val ios = LocalIos.current
    var input by remember { mutableStateOf("") }
    val submit = { if (input.isNotBlank()) { onAdd(input); input = "" } }

    Column {
        NavBar("Blocked Websites", "Settings", onBack)

        Section(
            header = "Add a website",
            footer = "Type a domain like youtube.com or reddit.com. When you open it in a browser, FocusLock sends you straight back out. Works in Chrome and most browsers.",
            rows = listOf {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) Text("example.com", style = IosType.body, color = ios.tertiaryLabel)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = IosType.body.copy(color = ios.label),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(ios.blue),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    TintedButton("Add", onClick = submit, enabled = input.isNotBlank())
                }
            },
        )

        if (state.blockedSites.isNotEmpty()) {
            Section(
                header = "Blocked",
                rows = state.blockedSites.sorted().map { domain ->
                    {
                        RowBase(
                            title = domain,
                            leading = { IconBadge(Icons.Rounded.Language, ios.red) },
                            onClick = { onRemove(domain) },
                        ) {
                            Icon(Icons.Rounded.Close, "Remove", tint = ios.secondaryLabel)
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
