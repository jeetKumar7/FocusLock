package com.focuslock.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focuslock.app.lock.LockState
import com.focuslock.app.schedule.ScheduleRule
import com.focuslock.app.ui.components.CheckRow
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.PrimaryButton
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.StepperRow
import com.focuslock.app.ui.components.TintedButton
import com.focuslock.app.ui.components.ToggleRow
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import java.time.DayOfWeek

@Composable
fun ScheduleListPage(
    state: LockState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ScheduleRule) -> Unit,
    onToggle: (ScheduleRule, Boolean) -> Unit,
    onDelete: (ScheduleRule) -> Unit,
) {
    val ios = LocalIos.current
    Column {
        NavBar("Schedules", "Blocks", onBack)

        if (state.schedules.isEmpty()) {
            Section(
                footer = "Schedules block apps or feed sections at set times (e.g. 10 PM – 7 AM). Tap + to create one.",
                rows = listOf {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).clickable { onAdd() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(Icons.Rounded.Add, ios.blue)
                        Text("  Add schedule", style = IosType.body, color = ios.blue)
                    }
                },
            )
        } else {
            Section(
                header = "Active schedules",
                rows = state.schedules.map { rule ->
                    {
                        ToggleRow(
                            title = rule.name,
                            subtitle = "${rule.daysLabel} · ${rule.timeLabel}",
                            checked = rule.enabled,
                            onCheckedChange = { onToggle(rule, it) },
                            leading = { IconBadge(Icons.Rounded.Schedule, if (rule.isActiveNow()) ios.green else ios.secondaryLabel) },
                        )
                    }
                },
            )
            Section(
                rows = listOf {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).clickable { onAdd() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(Icons.Rounded.Add, ios.blue)
                        Text("  Add schedule", style = IosType.body, color = ios.blue)
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ScheduleEditPage(
    initial: ScheduleRule?,
    onBack: () -> Unit,
    onSave: (ScheduleRule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val ios = LocalIos.current
    var rule by remember { mutableStateOf(initial ?: ScheduleRule()) }

    Column {
        NavBar(if (initial == null) "New Schedule" else "Edit Schedule", "Schedules", onBack)

        Section(
            header = "Name",
            rows = listOf {
                androidx.compose.foundation.text.BasicTextField(
                    value = rule.name,
                    onValueChange = { rule = rule.copy(name = it) },
                    textStyle = IosType.body.copy(color = ios.label),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(ios.blue),
                    modifier = Modifier.fillMaxWidth()
                        .padding(16.dp),
                    singleLine = true,
                )
            },
        )

        Section(
            header = "Days",
            rows = (1..7).map { dow ->
                {
                    CheckRow(
                        DayOfWeek.of(dow).name.lowercase().replaceFirstChar { it.uppercase() },
                        dow in rule.daysOfWeek,
                    ) {
                        rule = rule.copy(
                            daysOfWeek = if (dow in rule.daysOfWeek) rule.daysOfWeek - dow else rule.daysOfWeek + dow,
                        )
                    }
                }
            },
        )

        Section(
            header = "Time",
            rows = listOf(
                { StepperRow("Start hour", rule.startHour, 0, 23, 1, "%02d:%02d".format(rule.startHour, rule.startMinute)) { rule = rule.copy(startHour = it) } },
                { StepperRow("Start minute", rule.startMinute, 0, 55, 5, "") { rule = rule.copy(startMinute = it) } },
                { StepperRow("End hour", rule.endHour, 0, 23, 1, "%02d:%02d".format(rule.endHour, rule.endMinute)) { rule = rule.copy(endHour = it) } },
                { StepperRow("End minute", rule.endMinute, 0, 55, 5, "") { rule = rule.copy(endMinute = it) } },
            ),
        )

        Section(
            header = "What to block",
            rows = listOf {
                ToggleRow("Block all apps", rule.blockAllApps, onCheckedChange = { on ->
                    rule = rule.copy(blockAllApps = on)
                })
            },
        )

        Section(
            rows = listOf {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    PrimaryButton("Save", onClick = { onSave(rule) })
                }
            },
        )

        if (onDelete != null) {
            Spacer(Modifier.height(16.dp))
            Section(
                rows = listOf {
                    Row(
                        Modifier.fillMaxWidth().clickable { onDelete() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = ios.red)
                        Text("  Delete schedule", style = IosType.body, color = ios.red)
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
