package com.focuslock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.focuslock.app.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class FocusVpnService : VpnService() {

    private var vpnFd: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var proxyThread: Thread? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(); return START_NOT_STICKY }
        startVpn()
        return START_STICKY
    }

    override fun onRevoke() { stop() }

    private fun startVpn() {
        if (vpnFd != null) return
        createChannel()
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("FocusLock DNS Filter")
            .setContentText("Adult content is being filtered")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(NID, n)

        val builder = Builder()
            .setSession("FocusLock Filter")
            .addAddress(VPN_ADDR, 32)
            .addRoute(VPN_DNS, 32)
            .addDnsServer(VPN_DNS)
            .setMtu(MTU)
            .setBlocking(true)
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
        vpnFd = builder.establish() ?: run { stopSelf(); return }
        running = true
        trackUnderlyingNetwork()
        proxyThread = Thread(::runProxy, "dns-proxy").apply { start() }
    }

    // Without this, Android can't validate the split-tunnel VPN (only DNS is routed)
    // and marks the whole network "no internet". Tying the VPN to the real underlying
    // network lets it inherit the physical network's validated state. The request MUST
    // exclude VPN networks (NOT_VPN) or the callback can hand us the VPN itself, which
    // would be a self-reference and break validation entirely.
    private fun trackUnderlyingNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { setUnderlyingNetworks(arrayOf(network)) }
            override fun onLost(network: Network) { setUnderlyingNetworks(null) }
        }
        netCallback = cb
        val req = NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        try { cm.registerNetworkCallback(req, cb) } catch (_: Exception) {}
    }

    private fun stop() {
        running = false
        netCallback?.let {
            try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        netCallback = null
        vpnFd?.close(); vpnFd = null
        proxyThread?.interrupt(); proxyThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runProxy() {
        val fd = vpnFd ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(MTU)
        while (running) {
            val len = try { input.read(buf) } catch (_: Exception) { break; -1 }
            if (len <= 0) continue
            try { handlePacket(buf, len, output) } catch (_: Exception) {}
        }
    }

    private fun handlePacket(buf: ByteArray, len: Int, output: FileOutputStream) {
        if (len < 28) return
        val ver = (buf[0].toInt() ushr 4) and 0xF
        if (ver != 4) return
        val ihl = (buf[0].toInt() and 0xF) * 4
        val proto = buf[9].toInt() and 0xFF
        if (proto != 17) return // UDP only
        if (len < ihl + 8) return
        val dstPort = buf.u16(ihl + 2)
        if (dstPort != 53) return

        val dnsOff = ihl + 8
        val dnsLen = len - dnsOff
        if (dnsLen < 12) return
        val dnsPayload = buf.copyOfRange(dnsOff, len)

        val domain = parseDomain(dnsPayload)
        val blocked = domain != null && isBlocked(domain)
        val response = if (blocked) nxDomain(dnsPayload) else forward(dnsPayload)
        if (response == null) {
            Log.w(TAG, "DNS forward failed for ${domain ?: "?"}")
            return
        }
        Log.d(TAG, "DNS ${if (blocked) "BLOCK" else "OK"} $domain")

        val pkt = buildResponse(buf, ihl, len, response)
        synchronized(output) { output.write(pkt) }
    }

    private fun forward(query: ByteArray): ByteArray? = try {
        val sock = DatagramSocket().also { protect(it) }
        sock.soTimeout = 4000
        val addr = InetAddress.getByName(UPSTREAM)
        sock.send(DatagramPacket(query, query.size, addr, 53))
        val resp = ByteArray(4096)
        val rp = DatagramPacket(resp, resp.size)
        sock.receive(rp)
        sock.close()
        resp.copyOf(rp.length)
    } catch (e: Exception) { Log.w(TAG, "upstream DNS error: ${e.message}"); null }

    private fun nxDomain(query: ByteArray): ByteArray {
        val resp = query.copyOf()
        resp[2] = (resp[2].toInt() or 0x80).toByte() // QR=1
        resp[3] = (resp[3].toInt() and 0xF0 or 0x03).toByte() // RCODE=NXDOMAIN
        // Zero answer/authority/additional counts
        resp[6] = 0; resp[7] = 0; resp[8] = 0; resp[9] = 0; resp[10] = 0; resp[11] = 0
        return resp
    }

    private fun parseDomain(dns: ByteArray): String? {
        if (dns.size < 13) return null
        val sb = StringBuilder()
        var pos = 12 // skip header
        while (pos < dns.size) {
            val labelLen = dns[pos].toInt() and 0xFF
            if (labelLen == 0) break
            if (pos + labelLen >= dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..labelLen) sb.append(dns[pos + i].toInt().toChar())
            pos += labelLen + 1
        }
        return sb.toString().lowercase().takeIf { it.isNotEmpty() }
    }

    private fun isBlocked(domain: String): Boolean {
        val prefs = getSharedPreferences("vpn_blocklist", MODE_PRIVATE)
        val custom = prefs.getStringSet("domains", emptySet()) ?: emptySet()
        return custom.any { domain == it || domain.endsWith(".$it") }
    }

    private fun buildResponse(origBuf: ByteArray, ihl: Int, origLen: Int, dnsResp: ByteArray): ByteArray {
        val udpLen = 8 + dnsResp.size
        val totalLen = ihl + udpLen
        val pkt = ByteArray(totalLen)
        System.arraycopy(origBuf, 0, pkt, 0, ihl.coerceAtMost(origLen))

        // Swap src/dst IP
        System.arraycopy(origBuf, 12, pkt, 16, 4) // orig src -> new dst
        System.arraycopy(origBuf, 16, pkt, 12, 4) // orig dst -> new src

        // Total length
        pkt[2] = (totalLen ushr 8).toByte(); pkt[3] = totalLen.toByte()
        // TTL
        pkt[8] = 64
        // Zero checksum then recalc
        pkt[10] = 0; pkt[11] = 0
        val ipCksum = checksum(pkt, 0, ihl)
        pkt[10] = (ipCksum ushr 8).toByte(); pkt[11] = ipCksum.toByte()

        // UDP header: swap ports
        pkt[ihl] = origBuf[ihl + 2]; pkt[ihl + 1] = origBuf[ihl + 3]
        pkt[ihl + 2] = origBuf[ihl]; pkt[ihl + 3] = origBuf[ihl + 1]
        pkt[ihl + 4] = (udpLen ushr 8).toByte(); pkt[ihl + 5] = udpLen.toByte()
        pkt[ihl + 6] = 0; pkt[ihl + 7] = 0 // zero UDP checksum (valid for IPv4)

        System.arraycopy(dnsResp, 0, pkt, ihl + 8, dnsResp.size)
        return pkt
    }

    private fun checksum(data: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        var remaining = len
        while (remaining > 1) { sum += data.u16(i).toLong(); i += 2; remaining -= 2 }
        if (remaining == 1) sum += (data[i].toInt() and 0xFF shl 8).toLong()
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.toInt().inv()) and 0xFFFF
    }

    private fun ByteArray.u16(off: Int) = ((this[off].toInt() and 0xFF) shl 8) or (this[off + 1].toInt() and 0xFF)

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(NotificationChannel(CHANNEL, "DNS Filter", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        private const val VPN_ADDR = "10.0.0.1"
        private const val VPN_DNS = "10.0.0.2"
        private const val UPSTREAM = "185.228.168.10" // CleanBrowsing adult filter
        private const val MTU = 1500
        private const val TAG = "FocusVpn"
        private const val CHANNEL = "vpn_filter"
        private const val NID = 2
        const val ACTION_STOP = "com.focuslock.STOP_VPN"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, FocusVpnService::class.java))

        fun stop(context: Context) =
            context.startService(Intent(context, FocusVpnService::class.java).setAction(ACTION_STOP))

        fun isRunning(context: Context): Boolean {
            val mgr = context.getSystemService(android.app.ActivityManager::class.java)
            @Suppress("DEPRECATION")
            return mgr.getRunningServices(100).any { it.service.className == FocusVpnService::class.java.name }
        }
    }
}
