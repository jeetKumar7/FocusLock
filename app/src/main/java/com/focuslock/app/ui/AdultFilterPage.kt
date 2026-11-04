package com.focuslock.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focuslock.app.ui.components.CheckRow
import com.focuslock.app.ui.components.NavBar
import com.focuslock.app.ui.components.PrimaryButton
import com.focuslock.app.ui.components.Section
import com.focuslock.app.ui.components.TintedButton
import com.focuslock.app.ui.theme.IosType
import com.focuslock.app.ui.theme.LocalIos
import com.focuslock.app.web.AdultFilter

@Composable
fun AdultFilterPage(onBack: () -> Unit) {
    val ios = LocalIos.current
    val context = LocalContext.current
    var status by remember { mutableStateOf(AdultFilter.status(context)) }
    LifecycleResumeEffect(Unit) {
        status = AdultFilter.status(context)
        onPauseOrDispose { }
    }
    val currentIdx = (status as? AdultFilter.Status.On)?.let { AdultFilter.levels.indexOf(it.level) } ?: 0
    var chosen by remember(currentIdx) { mutableIntStateOf(currentIdx) }
    val level = AdultFilter.levels[chosen]

    Column {
        NavBar("Adult filter", "Blocks", onBack)

        // ---- status
        val (icon, color, title, detail) = when (val s = status) {
            is AdultFilter.Status.On -> Quad(Icons.Rounded.CheckCircle, ios.green, "Filter is on", "${s.level.name} · ${s.level.provider}. Works in every app and browser.")
            is AdultFilter.Status.OtherDns -> Quad(Icons.Rounded.WarningAmber, ios.orange, "A different DNS is set", "${s.hostname} isn't one of the filters below. Replace it to filter adult content.")
            AdultFilter.Status.Off -> Quad(Icons.Rounded.ErrorOutline, ios.red, "Filter is off", "Adult sites can load in any browser or app. Set it up below in about a minute.")
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.13f)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = IosType.headline, color = ios.label)
                Text(detail, style = IosType.footnote, color = ios.secondaryLabel)
            }
        }

        // ---- level
        Section(
            header = "Filter level",
            rows = AdultFilter.levels.mapIndexed { i, l ->
                { CheckRow(l.name, chosen == i, l.blocks) { chosen = i } }
            },
        )

        // ---- steps
        Section(
            header = "Set it up",
            rows = listOf {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Step(1, "Copy the filter address")
                    Text(
                        level.hostname,
                        style = IosType.subhead.copy(fontFamily = FontFamily.Monospace), color = ios.label,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.background).padding(12.dp),
                    )
                    TintedButton("Copy address", { copy(context, level.hostname) }, Modifier.fillMaxWidth())
                    Step(2, "Open Settings → Connection & sharing → Private DNS")
                    Step(3, "Choose \"Private DNS provider hostname\", paste the address, tap Save")
                    Step(4, "Come back here. This page shows a green tick when it's working")
                    PrimaryButton("Open Settings", { openNetworkSettings(context) })
                }
            },
        )

        Section(
            header = "Make it stick",
            footer = "Private DNS is an Android setting, so FocusLock can't lock it. It can be switched off in Settings; this page will show you if that happens.",
            rows = listOf {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tip("Chrome", "Settings → Privacy and security → Use secure DNS → \"With your current service provider\", so Chrome uses the filter instead of its own DNS.")
                    Tip("Other VPNs", "A VPN app routes around Private DNS. Don't keep one running if you want the filter to hold.")
                    Tip("Wi-Fi and mobile data", "Private DNS applies to both, so the filter works everywhere.")
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun Step(n: Int, text: String) {
    val ios = LocalIos.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(ios.blue), contentAlignment = Alignment.Center) {
            Text("$n", style = IosType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = IosType.subhead, color = ios.label)
    }
}

@Composable
private fun Tip(title: String, text: String) {
    val ios = LocalIos.current
    Column {
        Text(title, style = IosType.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = ios.label)
        Text(text, style = IosType.footnote, color = ios.secondaryLabel)
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Private DNS", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun openNetworkSettings(context: Context) {
    val tries = listOf(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)
    for (action in tries) {
        if (runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
