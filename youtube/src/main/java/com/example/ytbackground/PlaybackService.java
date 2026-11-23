package com.example.ytbackground;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that keeps the app process alive while a video plays in
 * the background, and provides the media notification + lock-screen controls.
 *
 * The video itself plays inside MainActivity's WebView; this service only
 * keeps the process, CPU and Wi-Fi awake and forwards control buttons to the
 * page through {@link PlayerController}.
 *
 * Note: this service must NOT request audio focus. The WebView takes audio
 * focus itself when a video plays; requesting it here would make the WebView
 * lose focus and pause the video immediately.
 */
public class PlaybackService extends Service {

    private static final String TAG = "PlaybackService";
    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 1;

    private static final String ACTION_START = "com.example.ytbackground.START";
    private static final String ACTION_PLAY = "com.example.ytbackground.PLAY";
    private static final String ACTION_PAUSE = "com.example.ytbackground.PAUSE";
    private static final String ACTION_REWIND = "com.example.ytbackground.REWIND";
    private static final String ACTION_FORWARD = "com.example.ytbackground.FORWARD";
    private static final String ACTION_CLOSE = "com.example.ytbackground.CLOSE";

    private static final String CUSTOM_REWIND = "rewind";
    private static final String CUSTOM_FORWARD = "forward";
    private static final int SEEK_STEP_SECONDS = 10;

    // ---- State shared with MainActivity (main thread only) ----
    @Nullable private static PlaybackService instance;
    private static WeakReference<PlayerController> controllerRef = new WeakReference<>(null);
    private static boolean playing;
    private static long positionMs;
    private static long durationMs;
    private static long updatedAt;
    private static String title = "";
    private static String videoId = "";

    public static void setController(@Nullable PlayerController controller) {
        controllerRef = new WeakReference<>(controller);
    }

    /** Called by MainActivity whenever the page reports a playback change. */
    public static void onPlayerState(Context context, boolean isPlaying, long posMs, long durMs,
                                     String newTitle, String newVideoId) {
        playing = isPlaying;
        positionMs = posMs;
        durationMs = durMs;
        updatedAt = SystemClock.elapsedRealtime();
        title = newTitle;
        videoId = newVideoId;

        if (instance != null) {
            instance.refresh();
        } else if (isPlaying) {
            try {
                ContextCompat.startForegroundService(context,
                        new Intent(context, PlaybackService.class).setAction(ACTION_START));
            } catch (RuntimeException e) {
                // e.g. ForegroundServiceStartNotAllowedException if we're already in background.
                Log.w(TAG, "Could not start playback service", e);
            }
        }
    }

    /** Stops the service (e.g. when the activity is destroyed). */
    public static void stop() {
        if (instance != null) instance.close(false);
    }

    // ---- Instance ----
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private MediaSessionCompat session;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private boolean isForeground;
    @Nullable private Bitmap artwork;
    private String artworkFor = "";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();

        session = new MediaSessionCompat(this, "YTBackground");
        session.setSessionActivity(openAppIntent());
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { control(ACTION_PLAY); }
            @Override public void onPause() { control(ACTION_PAUSE); }
            @Override public void onStop() { close(true); }
            @Override public void onFastForward() { control(ACTION_FORWARD); }
            @Override public void onRewind() { control(ACTION_REWIND); }
            @Override public void onSkipToNext() { control(ACTION_FORWARD); }
            @Override public void onSkipToPrevious() { control(ACTION_REWIND); }

            @Override
            public void onSeekTo(long pos) {
                PlayerController c = controllerRef.get();
                if (c != null) c.seekTo(pos);
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                if (CUSTOM_REWIND.equals(action)) control(ACTION_REWIND);
                else if (CUSTOM_FORWARD.equals(action)) control(ACTION_FORWARD);
            }
        });
        session.setActive(true);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YTBackground:playback");
        wakeLock.setReferenceCounted(false);

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        //noinspection deprecation
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "YTBackground:playback");
        wifiLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // Must enter the foreground quickly after startForegroundService().
        refresh();
        if (instance == null) return START_NOT_STICKY; // refresh() failed and stopped us

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_CLOSE.equals(action)) {
            close(true);
        } else if (action != null && !ACTION_START.equals(action)) {
            control(action);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // App swiped away from recents: the WebView is gone, so stop.
        close(true);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        releaseLocks();
        session.setActive(false);
        session.release();
        artworkExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- controls

    private void control(String action) {
        PlayerController c = controllerRef.get();
        if (c == null) {
            close(false);
            return;
        }
        switch (action) {
            case ACTION_PLAY:
                c.play();
                break;
            case ACTION_PAUSE:
                c.pause();
                break;
            case ACTION_REWIND:
                c.seekBy(-SEEK_STEP_SECONDS);
                break;
            case ACTION_FORWARD:
                c.seekBy(SEEK_STEP_SECONDS);
                break;
            default:
                break;
        }
    }

    private void close(boolean pauseVideo) {
        if (pauseVideo) {
            PlayerController c = controllerRef.get();
            if (c != null) c.pause();
        }
        playing = false;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        isForeground = false;
        stopSelf();
        instance = null;
        releaseLocks();
    }

    private void releaseLocks() {
        if (wakeLock.isHeld()) wakeLock.release();
        if (wifiLock.isHeld()) wifiLock.release();
    }

    // ---------------------------------------------------------------- state / notification

    /** Pushes the current state to the media session, notification and locks. */
    private void refresh() {
        if (playing) {
            if (!wakeLock.isHeld()) wakeLock.acquire();
            if (!wifiLock.isHeld()) wifiLock.acquire();
        } else {
            releaseLocks();
        }

        loadArtworkIfNeeded();
        updateSession();
        Notification notification = buildNotification();

        if (!isForeground) {
            try {
                int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
                isForeground = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "startForeground failed", e);
                close(false);
            }
        } else if (canPostNotifications()) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private void updateSession() {
        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, displayTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "YouTube")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs > 0 ? durationMs : -1);
        if (artwork != null) meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
        session.setMetadata(meta.build());

        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_REWIND
                | PlaybackStateCompat.ACTION_FAST_FORWARD;
        if (durationMs > 0) actions |= PlaybackStateCompat.ACTION_SEEK_TO;

        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        positionMs, playing ? 1f : 0f, updatedAt)
                // Shown as buttons on the Android 13+ lock screen / quick settings player.
                .addCustomAction(CUSTOM_REWIND, getString(R.string.action_rewind), R.drawable.ic_rewind)
                .addCustomAction(CUSTOM_FORWARD, getString(R.string.action_forward), R.drawable.ic_forward)
                .build());
    }

    private Notification buildNotification() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(displayTitle())
                .setContentText(getString(playing ? R.string.status_playing : R.string.status_paused))
                .setContentIntent(openAppIntent())
                .setDeleteIntent(serviceIntent(ACTION_CLOSE, 5))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(playing)
                .addAction(R.drawable.ic_rewind, getString(R.string.action_rewind),
                        serviceIntent(ACTION_REWIND, 1))
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        getString(playing ? R.string.action_pause : R.string.action_play),
                        serviceIntent(playing ? ACTION_PAUSE : ACTION_PLAY, 2))
                .addAction(R.drawable.ic_forward, getString(R.string.action_forward),
                        serviceIntent(ACTION_FORWARD, 3))
                .addAction(R.drawable.ic_close, getString(R.string.action_close),
                        serviceIntent(ACTION_CLOSE, 4))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        if (artwork != null) b.setLargeIcon(artwork);
        return b.build();
    }

    private String displayTitle() {
        return title.isEmpty() ? getString(R.string.app_name) : title;
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.channel_description));
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    /** Fetches the video thumbnail for the notification / lock screen. */
    private void loadArtworkIfNeeded() {
        final String id = videoId;
        if (id.equals(artworkFor)) return;
        artworkFor = id;
        artwork = null;
        if (id.isEmpty() || !id.matches("[\\w-]+")) return;
        artworkExecutor.execute(() -> {
            Bitmap bmp = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL("https://i.ytimg.com/vi/" + id + "/hqdefault.jpg")
                        .openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                try (InputStream in = conn.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(in);
                }
            } catch (Exception e) {
                Log.d(TAG, "Thumbnail download failed", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            final Bitmap result = bmp;
            mainHandler.post(() -> {
                if (instance == PlaybackService.this && id.equals(artworkFor) && result != null) {
                    artwork = result;
                    refresh();
                }
            });
        });
    }
}
