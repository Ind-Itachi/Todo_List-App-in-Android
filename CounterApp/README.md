# Counter

A minimal tally counter for Android (built for the Samsung Galaxy S24; works on Android 8.0+).

- **Tap anywhere** → +1
- **−** (bottom left) → −1 (never goes below 0)
- **Reset** (bottom right) → back to 0, after a confirmation
- **Volume up / down** → +1 / −1, so you can count without looking at the screen
- **Dot** (bottom centre) → turns the floating counter on/off (filled = on, ring = off)
- Follows the system light/dark mode, gives a light haptic tick on every count, and remembers the count after the app is closed.

## Floating counter (when the app is minimized)

When you leave the app, a clear liquid-glass dot showing the count floats on top of whatever you're doing. It squishes when you press it, wobbles like a drop of water when you let go, sends a light ripple out from your finger, and stretches as you drag it.

- **Tap the dot** → +1
- **Drag** it to move it; it snaps to the nearest side of the screen
- **Hold** it → opens the app (for −1 and Reset)
- **Drag it to the × at the bottom** of the screen (it appears while you drag) → hides the dot. It comes back the next time you open and minimize the app; to turn it off for good, use the dot at the bottom of the app.
- It hides while the app itself is open, and comes back when you minimize it.

It needs the **Appear on top** permission. On first launch the app offers to turn it on and opens the setting: switch on *Allow permission* for Counter and go back. While the dot is on, Android shows a "Floating counter is on" notification; its **Turn off** button (or the dot at the bottom of the app) switches the dot off.

## Glass clock widget

A home-screen clock in the same liquid-glass bubble: the time and date inside a clear glass disc, with your wallpaper showing through. It updates itself every minute (no battery-draining background work) and follows the phone's 12/24-hour setting. Tap it to open Counter.

To add it: long-press an empty spot on the home screen → **Widgets** → **Counter** → **Glass clock**, and drag it onto the home screen. It starts at 2×2; resize it bigger or smaller and the bubble and text scale with it (it stays round).

## Install

1. Copy `Counter.apk` to the phone (or download it from GitHub on the phone).
2. Open it from **My Files** → Downloads and tap **Install**. The first time, Android asks you to allow installs from that app (Settings → *Install unknown apps*).
3. If Play Protect warns that the app is from an unknown developer, choose **Install anyway** (the app is not on the Play Store).
4. If Samsung **Auto Blocker** is on (Settings → Security and privacy → Auto Blocker), turn it off for the install; it blocks all apps from outside the store.

## Build

No Gradle or Android Studio needed — a few Java files built with the stock Android tools. The first build downloads the Android 14 framework jar (~130 MB) from Maven Central:

```sh
sudo apt-get install aapt dalvik-exchange zipalign apksigner default-jdk curl
./build.sh            # -> build/Counter.apk
```

`build.sh` signs with `~/.android/counter.jks` (created on first run). Use the same key for later builds, or uninstall the old app first — Android won't update an app signed with a different key.
