/**
 * FocusLock backend — Phase 4a (accountability core).
 *
 * Only the minimum lives server-side: link relationships, small aggregates,
 * and accountability event *types*. No screen content, URLs, or message text.
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as functionsV1 from "firebase-functions/v1";

initializeApp();
const db = getFirestore();

type Role = "buddy" | "parent";

/** Deterministic link id so both directions resolve to a single document. */
function linkId(a: string, b: string): string {
  return a < b ? `${a}_${b}` : `${b}_${a}`;
}

/** Unambiguous code alphabet (no 0/O/1/I). */
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
function randomCode(len = 6): string {
  let s = "";
  for (let i = 0; i < len; i++) s += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
  return s;
}

const CODE_TTL_MS = 15 * 60 * 1000;

/** Create a short-lived linking code the caller shares with their partner. */
export const createLinkCode = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  const role = (req.data?.role as Role) ?? "buddy";
  if (role !== "buddy" && role !== "parent") throw new HttpsError("invalid-argument", "Bad role.");

  const expiresAt = Timestamp.fromMillis(Date.now() + CODE_TTL_MS);
  for (let attempt = 0; attempt < 6; attempt++) {
    const code = randomCode();
    const ref = db.doc(`linkCodes/${code}`);
    try {
      await ref.create({ uid, role, expiresAt });
      return { code, expiresAt: expiresAt.toMillis() };
    } catch {
      // collision — try another code
    }
  }
  throw new HttpsError("resource-exhausted", "Could not allocate a code, try again.");
});

/** Redeem a partner's code, creating an active link between the two accounts. */
export const redeemLinkCode = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
  const code = String(req.data?.code ?? "").trim().toUpperCase();
  if (!code) throw new HttpsError("invalid-argument", "No code.");

  const codeRef = db.doc(`linkCodes/${code}`);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(codeRef);
    if (!snap.exists) throw new HttpsError("not-found", "That code is invalid or already used.");
    const { uid: ownerUid, role, expiresAt } = snap.data() as { uid: string; role: Role; expiresAt: Timestamp };
    if (expiresAt.toMillis() < Date.now()) throw new HttpsError("deadline-exceeded", "That code has expired.");
    if (ownerUid === uid) throw new HttpsError("failed-precondition", "You can't link to yourself.");

    const id = linkId(ownerUid, uid);
    tx.set(db.doc(`links/${id}`), {
      a: ownerUid < uid ? ownerUid : uid,
      b: ownerUid < uid ? uid : ownerUid,
      role: role === "parent" ? "parent_child" : "buddy",
      parentUid: role === "parent" ? ownerUid : null,
      status: "active",
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.delete(codeRef);
    return { linkedTo: ownerUid };
  });
});

const EVENT_COPY: Record<string, { title: string; body: string }> = {
  relapse: { title: "Your partner reset their streak", body: "They logged a relapse in FocusLock." },
  uninstall_attempt: { title: "Uninstall attempt", body: "Your partner tried to remove FocusLock." },
  disable_attempt: { title: "Protection change attempt", body: "Your partner tried to turn off a block." },
  limit_hit: { title: "Limit reached", body: "Your partner hit a usage limit they set." },
};

/** Push an accountability event to the recipients' devices. */
export const onEventCreated = onDocumentCreated("events/{eventId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  const toUids: string[] = Array.isArray(data.toUids) ? data.toUids : [];
  const copy = EVENT_COPY[data.type as string] ?? { title: "FocusLock", body: "An update from your partner." };

  const tokens: string[] = [];
  for (const uid of toUids) {
    const u = await db.doc(`users/${uid}`).get();
    const token = u.get("fcmToken");
    if (typeof token === "string" && token.length > 0) tokens.push(token);
  }
  if (tokens.length === 0) return;

  await getMessaging().sendEachForMulticast({
    tokens,
    notification: copy,
    data: { type: String(data.type ?? ""), fromUid: String(data.fromUid ?? "") },
    android: { priority: "high" },
  });
});

/** Cascade-delete a user's server data when their account is deleted. */
export const onUserDeleted = functionsV1.auth.user().onDelete(async (user) => {
  const uid = user.uid;
  const batch = db.batch();
  batch.delete(db.doc(`users/${uid}`));

  const reports = await db.collection(`reports/${uid}`).listDocuments();
  reports.forEach((d) => batch.delete(d));

  const linksA = await db.collection("links").where("a", "==", uid).get();
  const linksB = await db.collection("links").where("b", "==", uid).get();
  linksA.forEach((d) => batch.delete(d.ref));
  linksB.forEach((d) => batch.delete(d.ref));

  await batch.commit();
});
