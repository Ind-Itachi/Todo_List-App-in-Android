# Todo_List-App-in-Android

---

# Satsang Youtube (module `youtube`)

A second app in this project that opens **YouTube as a web page** and keeps the
video playing when the app is minimised or the phone is locked.

## How to run
1. Open the project in Android Studio.
2. In the run-configuration dropdown pick **youtube** (the To-Do app is **app**).
3. Run it on a phone (Android 7.0+). Allow notifications when asked.

## Features
- Full YouTube mobile site (`m.youtube.com`) inside the app; sign in works with cookies kept.
- **Background playback**: press Home or lock the screen and the video keeps playing.
- Media notification + lock-screen controls: play/pause, back 10 s, forward 10 s, seek bar, close.
- Video thumbnail and title on the lock screen.
- Pauses for phone calls and resumes after; pauses when another app starts playing music.
- Full-screen video (rotates to landscape).
- Back button goes back in YouTube; on the first page it minimises the app instead of closing it.
- Share a YouTube link to the app (or open one with it) to play it directly.

## How background playback works
| Piece | What it does |
|---|---|
| `BackgroundWebView` | Always reports itself as visible, so Chromium never suspends the page. |
| `MainActivity` | Never calls `webView.onPause()` / `pauseTimers()`; keeps the renderer at high priority. |
| `assets/background_play.js` | Makes `document.hidden` always `false`, blocks `visibilitychange`, and resumes the video if something pauses it while the app is in the background. Reports state to the app. |
| `PlaybackService` | Foreground service (`mediaPlayback`) with a wake lock + Wi-Fi lock, media session, notification and audio focus. |

## Tips
- Swiping the app away from Recents, or pressing **Close** in the notification, stops playback.
- Some phones (Xiaomi, Oppo, Vivo, Samsung…) kill background apps aggressively. If playback
  stops after a while, set the app's battery usage to **Unrestricted** in *App info → Battery*.
- Google sign-in may be blocked in some WebView versions; the app works fine without signing in.
- Background playback of YouTube outside YouTube Premium is against YouTube's Terms of Service —
  this is intended for personal use.
