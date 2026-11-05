# FocusLock backend (Firebase) — Phase 4a

Server side of the accountability features. Everything here is **opt-in** and
holds the minimum: link relationships, small aggregates, and accountability
event *types*. No screen content, URLs, or message text is ever stored.

## What's here
- `firestore.rules` — access control (a partner reads only shared aggregates, only through an active link)
- `firestore.indexes.json` — composite index for the events feed
- `functions/` — Cloud Functions (TypeScript, Node 20):
  - `createLinkCode` / `redeemLinkCode` — pair two accounts with a short code
  - `onEventCreated` — push a partner alert (relapse / uninstall / disable / limit) via FCM
  - `onUserDeleted` — wipe a user's server data on account deletion

## One-time setup (needs your Google account — I can't do these)
1. **Create the project** at <https://console.firebase.google.com> → *Add project* (e.g. `focuslock`).
2. **Add an Android app**: package name `com.focuslock.app`. Download **`google-services.json`** and give it to me (it goes in `app/`). This unblocks the Android integration.
3. **Enable Authentication** → Sign-in methods: turn on **Anonymous** (and Google, if you want recovery/cross-device).
4. **Create Firestore** (production mode, pick a region).
5. **Cloud Messaging** is on by default (used for partner push).
6. **Upgrade to the Blaze plan** — required to deploy Cloud Functions. Functions stay within the free monthly quota at low usage; Blaze just needs a billing account on file.

## Deploy (from this `firebase/` directory)
```bash
npm install -g firebase-tools
firebase login
firebase use --add            # select the project you created
cd functions && npm install && cd ..
firebase deploy --only firestore:rules,firestore:indexes,functions
```

## Notes
- Daily reports (scheduled function + email) land in **Phase 4b** and will need the
  Firebase **Trigger Email** extension plus SendGrid/Mailgun.
- Family/parent controls are **4b/4c**; community + leaderboard are **4d** (rules are
  already stubbed in `firestore.rules`).
- The Android app works fully offline without any of this — the backend is additive.
