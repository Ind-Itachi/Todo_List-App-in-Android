# Counter

A minimal tally counter for Android (built for the Samsung Galaxy S24; works on Android 8.0+).

- **Tap anywhere** → +1
- **−** (bottom left) → −1 (never goes below 0)
- **Reset** (bottom right) → back to 0, after a confirmation
- **Volume up / down** → +1 / −1, so you can count without looking at the screen
- Follows the system light/dark mode, gives a light haptic tick on every count, and remembers the count after the app is closed.

## Install

1. Copy `Counter.apk` to the phone (or download it from GitHub on the phone).
2. Open it from **My Files** → Downloads and tap **Install**. The first time, Android asks you to allow installs from that app (Settings → *Install unknown apps*).
3. If Play Protect warns that the app is from an unknown developer, choose **Install anyway** (the app is not on the Play Store).
4. If Samsung **Auto Blocker** is on (Settings → Security and privacy → Auto Blocker), turn it off for the install; it blocks all apps from outside the store.

## Build

No Gradle or Android Studio needed — one Java file, built with the stock Android tools:

```sh
sudo apt-get install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23 default-jdk
./build.sh            # -> build/Counter.apk
```

`build.sh` signs with `~/.android/counter.jks` (created on first run). Use the same key for later builds, or uninstall the old app first — Android won't update an app signed with a different key.
