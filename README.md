# FocusLock

A personal Android app that blocks the most addictive parts of social apps (Reels, Shorts, Stories, Explore) while leaving the useful parts (DMs, posts, search) working. Turning a block off takes real effort: a typing, maths or CAT-question challenge, then a cooldown.

Built with Kotlin and Jetpack Compose, with an iOS-style interface. Everything runs on the phone; nothing is collected or sent anywhere.

## Features

**Blocks** (each one has its own switch)
- Instagram: Reels tab, Stories, Explore, Following feed by default
- YouTube: Shorts tab, Home feed → Subscriptions
- Facebook Reels, Snapchat Spotlight and Stories, X "For you" → Following
- For Reels and Shorts opened from the feed or DMs, you choose: block them, allow only the one you opened (no scrolling), or allow

**Friction**
- Unlock challenge: type a long paragraph exactly, solve 5 mental-maths problems in a row, or answer 3 CAT questions in a row
- Cooldown before a block actually switches off, which you can cancel
- "Do you really want this?" countdown before a Reel or Short you're allowed to watch
- Stricter changes apply instantly; looser ones need the challenge

**Insight**
- Stats: blocks per day, by hour of day and by section
- Home-screen widget showing blocks avoided today
- CAT practice mode with 64 questions (Quant, LRDI, VARC), levels and accuracy by topic

**Adult content**
- A guided page to set up Android's Private DNS with a filtering service (CleanBrowsing or Cloudflare), with a live status check

## How it works

FocusLock uses Android's Accessibility service to read the screen layout of the supported apps (view IDs, labels, which tab is selected). When a blocked section opens, it presses Back or switches tabs, exactly as you would. It reads only Instagram, YouTube, Facebook, Snapchat and X, and has no internet permission.

Apps rename internal view IDs from time to time. If a block stops working after an app update, find the new IDs with Android Studio's Layout Inspector (or `adb shell uiautomator dump`) and update them in `app/src/main/java/com/focuslock/app/rules/ContentRules.kt`.

## Build and install

1. Install [Android Studio](https://developer.android.com/studio), then open this folder.
2. Turn on USB debugging on your phone and connect it.
3. Run the app from Android Studio, or from the command line:
   ```bash
   ./gradlew assembleRelease
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
4. Open FocusLock → Settings → Accessibility, and turn on **FocusLock section blocker**.
5. On Xiaomi and similar phones, also allow background running: Settings → Permissions → Battery, MIUI battery saver and Autostart.

Debug builds include a **Testing mode** that skips challenges; release builds don't.

## Code map

| Path | What it does |
|---|---|
| `rules/ContentRules.kt` | Which screens are blocked and how they're detected |
| `service/GuardAccessibilityService.kt` | Watches the supported apps and acts on matches |
| `service/PauseOverlay.kt`, `service/Banner.kt` | Pause screen and block banner |
| `lock/` | Saved settings, challenges, CAT question bank |
| `stats/` | Block log and CAT accuracy, stored on the phone |
| `ui/` | Compose screens and iOS-style components |
| `web/AdultFilter.kt` | Private DNS status check |
| `widget/BlockWidget.kt` | Home-screen widget |
