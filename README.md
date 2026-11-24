# Aahnik

An Android app for tracking your daily aahnik and seeing *when* you complete them.

## What it does

**Today tab**
- Add each aahnik you follow (name + icon) with the **+** button. They are saved in a local SQLite database.
- Tap an aahnik when you finish it: it fills with liquid, and the exact time is recorded.
- Tap a completed aahnik (or long-press any aahnik) to change today's time, undo today's completion, or edit/delete the aahnik.
- The orb at the top shows today's progress.

**Analysis tab**
- Range: 7 days, 30 days, 90 days, or all time. Filter to one aahnik or see all of them.
- **Completion time trend**: one dot per completion (x = day, y = time of day, earlier times higher up). With one aahnik selected, the chart also shows your average time (dashed) and the fitted trend (dotted).
- **What time you complete**: an hour-by-hour histogram.
- Tiles: average time, consistency (± minutes), trend (minutes earlier/later per week), and current/best streak plus completion rate.
- Per-aahnik table: average time and the number of days completed.
- Tap any dot or bar for details.

## Look & feel

The "liquid glass" UI is built with Jetpack Compose:
- a slowly drifting aurora background
- translucent glass cards with specular rims; when pressed, they squish like a droplet and a bloom follows your finger
- a frosted, real-time blurred tab bar (via [Haze](https://github.com/chrisbanes/haze); falls back to a tint below Android 12) whose selection bubble stretches and settles like liquid
- liquid-fill check marks, springy dialogs, and charts that animate in

## Build

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration, or run:

```
./gradlew :app:assembleDebug
```

Requirements: minSdk 26 (Android 8.0), compileSdk/targetSdk 35, Kotlin 2.0.21, AGP 8.8.1.

Unit tests for the analysis logic: `./gradlew :app:testDebugUnitTest`.

## Code map

| Path | Purpose |
|---|---|
| `data/AahnikDatabase.kt` | SQLite schema (`aahnik`, `completion`) and queries |
| `data/AahnikStats.kt` | Pure analysis: averages, consistency, trend, streaks, hour histogram |
| `ui/AahnikViewModel.kt` | State + actions (mark done, undo, add/edit/delete) |
| `ui/AahnikApp.kt` | App shell, frosted tab bar, add button, dialogs routing |
| `ui/home/HomeScreen.kt` | Today tab |
| `ui/analysis/` | Analysis tab and the Canvas charts |
| `ui/glass/` | Glass material, liquid press, segmented control, liquid check, progress orb |
