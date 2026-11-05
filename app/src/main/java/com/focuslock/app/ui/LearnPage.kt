package com.focuslock.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focuslock.app.ui.components.IconBadge
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.NavRow
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos

data class Article(val title: String, val blurb: String, val body: String)

val LEARN_ARTICLES = listOf(
    Article(
        "Why short-form video is so hard to put down",
        "The slot-machine loop in your feed",
        "Reels, Shorts and TikTok run on variable rewards — the same mechanism that makes slot machines addictive. You never know whether the next video will be boring or brilliant, so your brain keeps pulling the lever \"just once more.\"\n\nEach swipe is cheap and the payoff is unpredictable, which is the exact recipe for compulsive behaviour. It isn't a lack of willpower. The feed is engineered by large teams to win your attention, and it's very good at it.\n\nThe fix isn't to white-knuckle it. It's to remove the lever. When the feed simply isn't there, the loop has nothing to run on — which is what blocking the feed (not the whole app) does.",
    ),
    Article(
        "Dopamine isn't the enemy — timing is",
        "Resetting your baseline",
        "Dopamine drives motivation, not just pleasure. The problem with endless scrolling is that it delivers a steady drip of easy, low-effort hits. Over time that raises your baseline, so ordinary activities — reading, work, a walk — start to feel flat by comparison.\n\nThe good news: the baseline resets. A day or two with fewer cheap hits and real-world activities start to feel rewarding again. This is why the first few days off the feed feel hardest, then noticeably easier.\n\nProtect the early days with stricter blocks, and let the cooldown do the work when an urge hits.",
    ),
    Article(
        "The 30-second rule",
        "Catch the reach before the spiral",
        "Most doomscrolling doesn't start as a decision. You open an app to check one message, and forty minutes later you surface with no memory of choosing to stay.\n\nThe moment that matters is the first 30 seconds — the reach. If you can add a tiny bit of friction right there (a pause, a reminder, a block that sends you back), the spiral never gets going.\n\nThat's the whole idea behind \"watch the one reel a friend sent, but you can't swipe to the next.\" One is a check-in. The next is the spiral.",
    ),
    Article(
        "Make your phone a tool, not a casino",
        "Design your defaults",
        "Willpower is a bad long-term strategy — you'll run out of it exactly when you're tired or stressed. The durable move is to change your defaults so the easy path is the one you actually want.\n\nBlock the feeds you don't want by default. Put real friction on turning them back on. Keep the useful parts (messages, specific profiles) reachable. Set a Sleep window so the night takes care of itself.\n\nWhen the good choice is the default, you don't have to be disciplined every single time.",
    ),
    Article(
        "What to do with the urge",
        "Ride the wave",
        "An urge is not a command. It's a wave — it rises, peaks, and falls, usually within a few minutes if you don't feed it.\n\nWhen one hits: name it (\"this is an urge\"), and put a small delay between the feeling and the action. The cooldown and challenge exist for exactly this — they hold the door shut for the few minutes it takes the wave to pass.\n\nIf you relapse, don't spiral into all-or-nothing thinking. Reset the streak, note what triggered it, and keep going. Progress is the trend, not a perfect record.",
    ),
)

@Composable
fun LearnPage(onBack: () -> Unit) {
    val ios = LocalIos.current
    var selected by remember { mutableStateOf<Int?>(null) }
    val idx = selected

    if (idx != null) {
        val a = LEARN_ARTICLES[idx]
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NavBar(a.title, "Learn", { selected = null })
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(a.title, style = IosType.title2, color = ios.label)
                Spacer(Modifier.height(4.dp))
                Text(a.blurb, style = IosType.subhead, color = ios.secondaryLabel)
                Spacer(Modifier.height(16.dp))
                Text(a.body, style = IosType.body, color = ios.label)
            }
            Spacer(Modifier.height(32.dp))
        }
    } else {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NavBar("Learn", "Settings", onBack)
            Section(
                header = "Short reads",
                footer = "A few minutes on why the feed is built the way it is — and how to take your attention back. All bundled, nothing loads from the internet.",
                rows = LEARN_ARTICLES.mapIndexed { i, a ->
                    {
                        NavRow(
                            a.title,
                            subtitle = a.blurb,
                            leading = { IconBadge(Icons.Rounded.MenuBook, ios.teal) },
                            onClick = { selected = i },
                        )
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
