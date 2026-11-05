package com.focuslock.app.backend

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

data class PartnerLink(val partnerUid: String, val role: String)

data class BackendState(
    val uid: String? = null,
    val link: PartnerLink? = null,
    val shareStreak: Boolean = true,
    val shareBlocks: Boolean = true,
    val ready: Boolean = false,
)

/**
 * Phase 4a client: anonymous auth, the user doc, FCM token, partner linking,
 * shared aggregates, and accountability events. Everything is opt-in and
 * additive — the app works fully without ever calling in here.
 */
object Backend {
    private const val TAG = "Backend"
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance()

    private val _state = MutableStateFlow(BackendState())
    val state: StateFlow<BackendState> = _state

    /** Signs in anonymously, ensures the user doc, registers the push token. */
    suspend fun init() {
        try {
            val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: return
            val ref = db.document("users/${user.uid}")
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(mapOf("createdAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
            }
            val shareStreak = snap.get("share.streak") as? Boolean ?: true
            val shareBlocks = snap.get("share.blocksToday") as? Boolean ?: true
            _state.update { it.copy(uid = user.uid, shareStreak = shareStreak, shareBlocks = shareBlocks, ready = true) }
            registerToken(user.uid)
            refreshLink(user.uid)
        } catch (e: Exception) {
            Log.w(TAG, "init failed: ${e.message}")
        }
    }

    suspend fun registerToken(uid: String? = auth.currentUser?.uid) {
        val u = uid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            db.document("users/$u").set(mapOf("fcmToken" to token), SetOptions.merge()).await()
        } catch (e: Exception) { Log.w(TAG, "token register failed: ${e.message}") }
    }

    suspend fun createLinkCode(role: String = "buddy"): String {
        val res = functions.getHttpsCallable("createLinkCode").call(mapOf("role" to role)).await()
        @Suppress("UNCHECKED_CAST")
        val data = res.getData() as Map<String, Any?>
        return data["code"] as String
    }

    suspend fun redeemLinkCode(code: String) {
        functions.getHttpsCallable("redeemLinkCode").call(mapOf("code" to code.trim().uppercase())).await()
        auth.currentUser?.uid?.let { refreshLink(it) }
    }

    suspend fun unlink() {
        val uid = auth.currentUser?.uid ?: return
        val link = _state.value.link ?: return
        val id = if (uid < link.partnerUid) "${uid}_${link.partnerUid}" else "${link.partnerUid}_$uid"
        try { db.document("links/$id").delete().await() } catch (e: Exception) { Log.w(TAG, "unlink: ${e.message}") }
        _state.update { it.copy(link = null) }
    }

    private suspend fun refreshLink(uid: String) {
        try {
            val a = db.collection("links").whereEqualTo("a", uid).get().await()
            val b = db.collection("links").whereEqualTo("b", uid).get().await()
            val doc = (a.documents + b.documents).firstOrNull { it.getString("status") == "active" }
            val link = doc?.let {
                val other = if (it.getString("a") == uid) it.getString("b") else it.getString("a")
                other?.let { o -> PartnerLink(o, it.getString("role") ?: "buddy") }
            }
            _state.update { it.copy(link = link) }
        } catch (e: Exception) { Log.w(TAG, "refreshLink: ${e.message}") }
    }

    suspend fun setShare(streak: Boolean, blocks: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        _state.update { it.copy(shareStreak = streak, shareBlocks = blocks) }
        try {
            db.document("users/$uid").set(mapOf("share" to mapOf("streak" to streak, "blocksToday" to blocks)), SetOptions.merge()).await()
        } catch (e: Exception) { Log.w(TAG, "setShare: ${e.message}") }
    }

    /** Pushes only the aggregates the user agreed to share. */
    suspend fun pushAggregates(streakDays: Int, blocksToday: Int) {
        val uid = auth.currentUser?.uid ?: return
        val s = _state.value
        if (s.link == null) return
        val agg = buildMap<String, Any> {
            if (s.shareStreak) put("streakDays", streakDays)
            if (s.shareBlocks) put("blocksToday", blocksToday)
            put("lastSyncAt", FieldValue.serverTimestamp())
        }
        try { db.document("users/$uid").set(mapOf("agg" to agg), SetOptions.merge()).await() }
        catch (e: Exception) { Log.w(TAG, "pushAggregates: ${e.message}") }
    }

    /** Sends an accountability event to the linked partner (no-op if unlinked). */
    suspend fun emitEvent(type: String) {
        val uid = auth.currentUser?.uid ?: return
        val link = _state.value.link ?: return
        try {
            db.collection("events").add(
                mapOf(
                    "fromUid" to uid,
                    "toUids" to listOf(link.partnerUid),
                    "type" to type,
                    "at" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: Exception) { Log.w(TAG, "emitEvent: ${e.message}") }
    }
}
