package com.focuslock.app.web

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings

/**
 * The adult filter uses Android's built-in Private DNS pointed at a filtering DNS service,
 * so it works system-wide (every app and browser) with nothing running in FocusLock.
 * FocusLock only checks whether it's on and helps you set it.
 */
object AdultFilter {

    data class Level(
        val name: String,
        val hostname: String,
        val provider: String,
        val blocks: String,
    )

    val levels = listOf(
        Level(
            name = "Adult filter",
            hostname = "adult-filter-dns.cleanbrowsing.org",
            provider = "CleanBrowsing",
            blocks = "Porn and adult sites, with SafeSearch forced on Google, Bing and DuckDuckGo. Everything else works normally.",
        ),
        Level(
            name = "Strict (family) filter",
            hostname = "family-filter-dns.cleanbrowsing.org",
            provider = "CleanBrowsing",
            blocks = "Everything in Adult filter, plus mixed-content sites (like Reddit), VPN/proxy sites that could bypass the filter, and YouTube Restricted Mode.",
        ),
        Level(
            name = "Basic filter",
            hostname = "family.cloudflare-dns.com",
            provider = "Cloudflare",
            blocks = "Porn and malware sites. No SafeSearch enforcement, so explicit image results can still appear.",
        ),
    )

    sealed interface Status {
        data class On(val level: Level) : Status
        data class OtherDns(val hostname: String) : Status
        data object Off : Status
    }

    /**
     * True when the user has strict ("hostname") Private DNS configured. In this mode
     * Android forces all DNS over TLS to that server and ignores any DNS a VPN provides,
     * so our VPN DNS filter both duplicates it and blocks the DoT connection (breaking
     * internet). Read from the global setting, not LinkProperties, because a broken VPN
     * can make isPrivateDnsActive read false even while the user has it configured.
     */
    fun privateDnsStrict(context: Context): Boolean =
        Settings.Global.getString(context.contentResolver, "private_dns_mode") == "hostname"

    fun status(context: Context): Status {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Status.Off
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return Status.Off
        val host = lp.privateDnsServerName?.trim()?.lowercase()
        if (host.isNullOrEmpty() || !lp.isPrivateDnsActive) return Status.Off
        return levels.firstOrNull { it.hostname == host }?.let { Status.On(it) } ?: Status.OtherDns(host)
    }
}
