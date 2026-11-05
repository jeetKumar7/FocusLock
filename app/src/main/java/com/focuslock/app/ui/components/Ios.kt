package com.focuslock.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos

// ---------------------------------------------------------------- headers

/** iOS large title, as at the top of Settings. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
        Text(title, style = IosType.largeTitle, color = LocalIos.current.label)
        if (subtitle != null) {
            Text(subtitle, style = IosType.subhead, color = LocalIos.current.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Small centred navigation bar with a "‹ Back" button, for pushed pages. */
@Composable
fun NavBar(title: String, backLabel: String?, onBack: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    val ios = LocalIos.current
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        if (onBack != null) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ArrowBackIosNew, null, tint = ios.blue, modifier = Modifier.size(20.dp))
                if (backLabel != null) Text(backLabel, style = IosType.body, color = ios.blue, modifier = Modifier.padding(start = 2.dp))
            }
        }
        Text(title, style = IosType.headline, color = ios.label, modifier = Modifier.align(Alignment.Center))
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { trailing() }
    }
}

// ---------------------------------------------------------------- grouped lists

/** An iOS inset-grouped section: optional header, rounded card of rows, optional footer. */
@Composable
fun Section(
    header: String? = null,
    footer: String? = null,
    rows: List<@Composable () -> Unit>,
) {
    val ios = LocalIos.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header.uppercase(), style = IosType.sectionHeader, color = ios.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 22.dp),
            )
        } else Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.card)) {
            rows.forEachIndexed { i, row ->
                row()
                if (i < rows.lastIndex) InsetDivider()
            }
        }
        if (footer != null) {
            Text(
                footer, style = IosType.footnote, color = ios.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
fun InsetDivider(start: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(0.5.dp).background(LocalIos.current.separator))
}

/** Base row: optional leading, title + subtitle, trailing; iOS press highlight. */
@Composable
fun RowBase(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = LocalIos.current.label,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val ios = LocalIos.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (pressed && onClick != null) ios.cardPressed else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = IosType.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = IosType.footnote, color = ios.secondaryLabel, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) = RowBase(title, subtitle, leading, onClick = { onCheckedChange(!checked) }) {
    IosSwitch(checked, onCheckedChange)
}

@Composable
fun NavRow(
    title: String,
    value: String? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) = RowBase(title, subtitle, leading, onClick = onClick) {
    val ios = LocalIos.current
    if (value != null) Text(value, style = IosType.body, color = ios.secondaryLabel, maxLines = 1)
    Icon(Icons.Rounded.ChevronRight, null, tint = ios.tertiaryLabel, modifier = Modifier.size(22.dp))
}

@Composable
fun CheckRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) =
    RowBase(title, subtitle, onClick = onClick) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = LocalIos.current.blue, modifier = Modifier.size(22.dp))
    }

@Composable
fun StepperRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) = RowBase(title, subtitle, leading) {
    val ios = LocalIos.current
    Text(value, style = IosType.body, color = ios.secondaryLabel, modifier = Modifier.padding(end = 10.dp))
    Stepper(onMinus, onPlus, minusEnabled)
}

/** Convenience: numeric stepper that calls [onChange] with the new value. */
@Composable
fun StepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    display: String,
    leading: (@Composable () -> Unit)? = null,
    onChange: (Int) -> Unit,
) = RowBase(title, leading = leading) {
    val ios = LocalIos.current
    Text(display, style = IosType.body, color = ios.secondaryLabel, modifier = Modifier.padding(end = 10.dp))
    Stepper(
        onMinus = { onChange((value - step).coerceAtLeast(min)) },
        onPlus = { onChange((value + step).coerceAtMost(max)) },
        minusEnabled = value > min,
    )
}

/** iOS stepper: "− | +" in a grey capsule. */
@Composable
fun Stepper(onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true) {
    val ios = LocalIos.current
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(ios.fill).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(46.dp).height(32.dp)
                .clickable(enabled = minusEnabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMinus() },
            contentAlignment = Alignment.Center,
        ) { Text("−", style = IosType.title3, color = if (minusEnabled) ios.label else ios.tertiaryLabel) }
        Box(Modifier.width(0.5.dp).height(18.dp).background(ios.separator))
        Box(
            Modifier.width(46.dp).height(32.dp)
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPlus() },
            contentAlignment = Alignment.Center,
        ) { Text("+", style = IosType.title3, color = ios.label) }
    }
}

// ---------------------------------------------------------------- controls

/** iOS switch: 51×31, green track, white thumb with a soft shadow, springy. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val ios = LocalIos.current
    val haptic = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) ios.green else ios.fill, label = "track")
    val offset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), label = "thumb",
    )
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            },
    ) {
        Box(
            Modifier
                .offset(x = offset, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** iOS segmented control. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ios = LocalIos.current
    Row(
        modifier.clip(RoundedCornerShape(9.dp)).background(ios.fill).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .then(if (sel) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)) else Modifier)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (sel) (if (ios.isDark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (sel) IosType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else IosType.footnote, color = ios.label, maxLines = 1)
            }
        }
    }
}

/** Large rounded filled button (iOS "borderedProminent"). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = LocalIos.current.blue) {
    val ios = LocalIos.current
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else ios.fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = IosType.headline, color = if (enabled) Color.White else ios.tertiaryLabel) }
}

/** Tinted button (iOS "bordered"). */
@Composable
fun TintedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalIos.current.blue, enabled: Boolean = true) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (enabled) 0.14f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = IosType.headline, color = color.copy(alpha = if (enabled) 1f else 0.4f), textAlign = TextAlign.Center) }
}

// ---------------------------------------------------------------- icons

/** iOS Settings-style coloured rounded-square icon. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(color),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f)) }
}

/** The real launcher icon of an installed app, or [fallback] if it isn't installed. */
@Composable
fun AppIcon(pkg: String, size: Dp = 30.dp, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    // Decode off the main thread; a synchronous getApplicationIcon()/toBitmap() per row
    // freezes the UI while a whole app list renders.
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) {
        Image(b, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f))) { fallback() }
    }
}

// ---------------------------------------------------------------- tab bar

data class TabItem(val label: String, val icon: ImageVector)

/** iOS tab bar: hairline on top, translucent background, blue when selected. */
@Composable
fun TabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    val ios = LocalIos.current
    Column(Modifier.fillMaxWidth().background(ios.card.copy(alpha = 0.94f)).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ios.separator))
        Row(Modifier.fillMaxWidth().height(54.dp)) {
            items.forEachIndexed { i, item ->
                val color = if (i == selected) ios.blue else ios.secondaryLabel.copy(alpha = 0.75f)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(item.icon, null, tint = color, modifier = Modifier.size(26.dp))
                    Text(item.label, style = IosType.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(10.5f, androidx.compose.ui.unit.TextUnitType.Sp)), color = color)
                }
            }
        }
    }
}
