package com.focuslock.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslock.app.ui.components.PrimaryButton
import com.focuslock.app.ui.components.TintedButton
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos

enum class Goal { FEEDS, APPS, ADULT, TIME }

/**
 * First-run walkthrough. Goal-framed (what the user wants to stop), explains the one
 * permission in plain language, and explains why blocks are hard to turn off — so the
 * home screen is never a cold wall of toggles.
 */
@Composable
fun OnboardingScreen(
    serviceOn: Boolean,
    onFinish: (goals: Set<Goal>) -> Unit,
) {
    val ios = LocalIos.current
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val goals = remember { mutableStateListOf<Goal>() }
    val lastStep = 3

    Column(Modifier.fillMaxSize().background(ios.background)) {
        // progress dots
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(lastStep + 1) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (i == step) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) ios.blue else ios.fill),
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            when (step) {
                0 -> WelcomeStep()
                1 -> GoalStep(goals)
                2 -> HowItWorksStep(serviceOn, context)
                else -> WhyItSticksStep()
            }
        }

        // buttons
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            val canContinue = if (step == 1) goals.isNotEmpty() else true
            PrimaryButton(
                text = if (step == lastStep) "Start using FocusLock" else "Continue",
                onClick = { if (step == lastStep) onFinish(goals.toSet()) else step++ },
                enabled = canContinue,
            )
            if (step > 0) {
                Spacer(Modifier.height(8.dp))
                TintedButton("Back", onClick = { step-- }, modifier = Modifier.fillMaxWidth(), color = ios.secondaryLabel)
            }
        }
    }
}

@Composable
private fun StepHeader(emoji: String, title: String, body: String) {
    val ios = LocalIos.current
    Spacer(Modifier.height(12.dp))
    Text(emoji, style = IosType.largeTitle)
    Spacer(Modifier.height(12.dp))
    Text(title, style = IosType.largeTitle, color = ios.label)
    Spacer(Modifier.height(10.dp))
    Text(body, style = IosType.body, color = ios.secondaryLabel)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun WelcomeStep() {
    val ios = LocalIos.current
    Box(
        Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5E5CE6), Color(0xFF0A84FF))))
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("FocusLock", style = IosType.largeTitle, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Take back your attention.",
                style = IosType.headline, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "FocusLock blocks the things built to pull you in — endless feeds, distracting apps, adult sites — and makes turning a block off hard on purpose, so a moment of impulse can't undo a decision you already made.",
        style = IosType.body, color = ios.secondaryLabel,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Let's set it up in a few taps.",
        style = IosType.body, color = ios.label, fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun GoalStep(goals: MutableList<Goal>) {
    StepHeader(
        "🎯", "What pulls you in?",
        "Pick what you want FocusLock to handle. You can change any of this later.",
    )
    GoalCard(Icons.Rounded.PlayCircle, "Short-form feeds", "Reels, Shorts, Spotlight — the endless scroll", Goal.FEEDS in goals) { toggle(goals, Goal.FEEDS) }
    Spacer(Modifier.height(10.dp))
    GoalCard(Icons.Rounded.Apps, "Whole apps", "Block distracting apps like Instagram or games", Goal.APPS in goals) { toggle(goals, Goal.APPS) }
    Spacer(Modifier.height(10.dp))
    GoalCard(Icons.Rounded.Shield, "Adult content", "Filter porn in every app and browser", Goal.ADULT in goals) { toggle(goals, Goal.ADULT) }
    Spacer(Modifier.height(10.dp))
    GoalCard(Icons.Rounded.Timelapse, "My screen time", "Set daily time limits on apps", Goal.TIME in goals) { toggle(goals, Goal.TIME) }
}

private fun toggle(goals: MutableList<Goal>, g: Goal) { if (g in goals) goals.remove(g) else goals.add(g) }

@Composable
private fun GoalCard(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val ios = LocalIos.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) ios.blue.copy(alpha = 0.12f) else ios.card)
            .border(if (selected) 2.dp else 0.dp, if (selected) ios.blue else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(ios.blue),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = IosType.headline, color = ios.label)
            Text(subtitle, style = IosType.footnote, color = ios.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(ios.blue),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun HowItWorksStep(serviceOn: Boolean, context: android.content.Context) {
    val ios = LocalIos.current
    StepHeader(
        "👀", "How FocusLock works",
        "To block an app or a feed, FocusLock needs to know what's on your screen right now — so it can step in the moment something you blocked opens.",
    )
    InfoPoint(Icons.Rounded.Visibility, "It only reads the screen", "FocusLock sees which app and screen is open. That's it.")
    Spacer(Modifier.height(12.dp))
    InfoPoint(Icons.Rounded.Lock, "Nothing leaves your phone", "No accounts, no servers, no tracking. Everything stays on this device.")
    Spacer(Modifier.height(20.dp))
    if (serviceOn) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.green.copy(alpha = 0.14f)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, null, tint = ios.green, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Accessibility is on — you're ready", style = IosType.subhead, color = ios.label)
        }
    } else {
        PrimaryButton("Turn on Accessibility", onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, color = ios.blue)
        Spacer(Modifier.height(8.dp))
        Text(
            "Opens Android Settings → Accessibility → Downloaded apps → FocusLock. You can also do this later from the home screen.",
            style = IosType.footnote, color = ios.secondaryLabel,
        )
    }
}

@Composable
private fun InfoPoint(icon: ImageVector, title: String, body: String) {
    val ios = LocalIos.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(ios.fill),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ios.blue, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = IosType.subhead, color = ios.label, fontWeight = FontWeight.SemiBold)
            Text(body, style = IosType.footnote, color = ios.secondaryLabel, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun WhyItSticksStep() {
    val ios = LocalIos.current
    StepHeader(
        "🔒", "Why it actually works",
        "Most blockers fail because you can switch them off the second you feel the urge. FocusLock is built to survive that moment.",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(ios.green),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Turning a block ON is instant", style = IosType.headline, color = ios.label)
            Text("Getting stricter never asks questions.", style = IosType.footnote, color = ios.secondaryLabel)
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(ios.orange),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.HourglassTop, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Turning one OFF takes effort", style = IosType.headline, color = ios.label)
            Text("You pass a challenge, then wait out a cooldown — enough time for the urge to pass.", style = IosType.footnote, color = ios.secondaryLabel)
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        "You can make the challenge and cooldown as tough as you like in Settings.",
        style = IosType.footnote, color = ios.secondaryLabel,
    )
}
