package com.focuslock.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.rules.ContentRules
import com.focuslock.app.service.UsageTracker
import com.focuslock.app.stats.BlockLog
import com.focuslock.app.ui.components.AppIcon
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.LargeTitle
import com.focuslock.app.ui.components.RowBase
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Blocks per day, by hour of day, and by section. All stored on the phone. */
@Composable
fun StatsTab() {
    val ios = LocalIos.current
    val context = LocalContext.current
    val log = remember { BlockLog.get(context) }
    val events by log.events.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val perDay = days.map { d -> events.count { it.date == d } }
    val prevWeek = events.count { it.date >= today.minusDays(13) && it.date <= today.minusDays(7) }
    val last30 = events.filter { it.date >= today.minusDays(29) }
    val perHour = (0..23).map { h -> last30.count { it.hour == h } }

    Column {
        LargeTitle("Stats", "Every time FocusLock stopped you")

        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Today", perDay.last(), ios.blue, Modifier.weight(1f))
            Tile("7 days", perDay.sum(), ios.indigo, Modifier.weight(1f))
            Tile("30 days", last30.size, ios.purple, Modifier.weight(1f))
        }

        // --- Screen time today (from UsageTracker) ---
        val usage = remember { UsageTracker(context) }
        val screenTime by androidx.compose.runtime.produceState(
            initialValue = emptyList<AppUsage>() to 0,
            events.size,
        ) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val pm = context.packageManager
                val filtered = usage.todayApps()
                    .filterNot { (pkg, _) -> pkg == "com.android.systemui" || pkg.contains("launcher") || pkg.contains("inputmethod") }
                val total = filtered.sumOf { it.second }
                val apps = filtered.take(12).map { (pkg, sec) ->
                    val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    AppUsage(pkg, label, sec, usage.todayOpens(pkg))
                }
                apps to total
            }
        }
        val (appTimes, totalSec) = screenTime
        Section(
            header = "Screen time today",
            footer = if (appTimes.isEmpty()) "Open some apps and your time here fills in through the day." else null,
            rows = buildList<@Composable () -> Unit> {
                add {
                    RowBase("Total today") {
                        Text(fmtDuration(totalSec), style = IosType.body, color = ios.label)
                    }
                }
                appTimes.forEach { u ->
                    add {
                        RowBase(
                            title = u.label,
                            subtitle = if (u.opens > 0) "${u.opens} ${if (u.opens == 1) "open" else "opens"}" else null,
                            leading = { AppIcon(u.pkg) { IconBadge(Icons.Rounded.Block, ios.secondaryLabel) } },
                        ) { Text(fmtDuration(u.sec), style = IosType.body, color = ios.secondaryLabel) }
                    }
                }
            },
        )

        Card("Last 7 days", trend(perDay.sum(), prevWeek)) {
            Bars(perDay, ios.blue, ios.fill, Modifier.fillMaxWidth().height(150.dp), highlightLast = true)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                days.forEachIndexed { i, d ->
                    Column(Modifier.weight(1f)) {
                        Text("${perDay[i]}", style = IosType.caption, color = ios.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(
                            if (i == 6) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = IosType.caption, color = ios.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        val peak = perHour.indices.maxByOrNull { perHour[it] }?.takeIf { perHour[it] > 0 }
        Card("When you reach for it", peak?.let { "Peak: ${hourLabel(it)}–${hourLabel((it + 1) % 24)} · last 30 days" } ?: "Last 30 days") {
            Bars(perHour, ios.orange, ios.fill, Modifier.fillMaxWidth().height(90.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                listOf("12am", "6am", "12pm", "6pm").forEach {
                    Text(it, style = IosType.caption, color = ios.secondaryLabel, modifier = Modifier.weight(1f))
                }
            }
        }

        val bySection = last30.groupingBy { it.ruleId }.eachCount().entries.sortedByDescending { it.value }
        Section(
            header = "By section · 30 days",
            rows = if (bySection.isEmpty()) listOf { RowBase("Nothing blocked yet") }
            else bySection.map { (id, n) ->
                {
                    val rule = ContentRules.byId[id]
                    RowBase(
                        title = rule?.label ?: id,
                        leading = { AppIcon(rule?.pkg ?: "") { IconBadge(Icons.Rounded.Block, ios.indigo) } },
                    ) { Text("$n", style = IosType.body, color = ios.secondaryLabel) }
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun trend(thisWeek: Int, lastWeek: Int): String = when {
    lastWeek == 0 && thisWeek == 0 -> "No attempts. Nice."
    lastWeek == 0 -> "$thisWeek this week"
    thisWeek < lastWeek -> "${(lastWeek - thisWeek) * 100 / lastWeek}% fewer than last week"
    thisWeek > lastWeek -> "${(thisWeek - lastWeek) * 100 / lastWeek}% more than last week"
    else -> "Same as last week"
}

@Composable
private fun Tile(label: String, value: Int, accent: Color, modifier: Modifier) {
    val ios = LocalIos.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(ios.card).padding(14.dp)) {
        Text(label, style = IosType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = accent)
        Text("$value", style = IosType.bigNumber, color = ios.label)
    }
}

@Composable
private fun Card(title: String, subtitle: String, content: @Composable () -> Unit) {
    val ios = LocalIos.current
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(ios.card).padding(16.dp),
    ) {
        Text(title, style = IosType.headline, color = ios.label)
        Text(subtitle, style = IosType.footnote, color = ios.secondaryLabel, modifier = Modifier.padding(bottom = 14.dp))
        content()
    }
}

@Composable
private fun Bars(values: List<Int>, color: Color, empty: Color, modifier: Modifier, highlightLast: Boolean = false) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        val slot = size.width / values.size
        val w = slot * 0.58f
        values.forEachIndexed { i, v ->
            val h = if (v == 0) 6f else (size.height * v / max).coerceAtLeast(6f)
            val c = when {
                v == 0 -> empty
                highlightLast && i != values.lastIndex -> color.copy(alpha = 0.45f)
                else -> color
            }
            drawRoundRect(c, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 3, w / 3))
        }
    }
}

private data class AppUsage(val pkg: String, val label: String, val sec: Int, val opens: Int)

private fun fmtDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "<1m"
    }
}

private fun hourLabel(h: Int) = when {
    h == 0 -> "12am"
    h < 12 -> "${h}am"
    h == 12 -> "12pm"
    else -> "${h - 12}pm"
}
