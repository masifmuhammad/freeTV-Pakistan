package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the TV is playing in the background (Spotify, YouTube Music, any player), from the active media
 * sessions, for Home's now playing bar and the screensaver. Android hands the sessions to an app whose
 * notification listener is allowed ({@link NowPlayingListener}); without that this reports nothing.
 *
 * A session counts once it has played while this was watching, so music that keeps going behind Home
 * shows, paused or not, while a video app that paused when Home came up (YouTube, Netflix) does not.
 */
final class NowPlaying {

    /** One track as the bar and the screensaver show it. */
    static final class Track {
        final String app, title, artist;
        final Bitmap art;
        final boolean playing;

        Track(String app, String title, String artist, Bitmap art, boolean playing) {
            this.app = app;
            this.title = title;
            this.artist = artist;
            this.art = art;
            this.playing = playing;
        }
    }

    interface Listener {
        /** The track to show, or null for nothing. */
        void onTrack(Track track);
    }

    private final Context context;
    private final Listener listener;
    private final MediaSessionManager sessions;
    private final ComponentName access;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaController controller;
    /** Sessions seen playing while this watched: they play in the background, so they are music. */
    private final Set<MediaSession.Token> background = new HashSet<>();

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::pick;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            report();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            report();
        }

        @Override
        public void onSessionDestroyed() {
            pick(active());
        }
    };

    NowPlaying(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        sessions = context.getSystemService(MediaSessionManager.class);
        access = new ComponentName(context, NowPlayingListener.class);
    }

    void start() {
        try {
            sessions.addOnActiveSessionsChangedListener(changed, access, main);
            pick(sessions.getActiveSessions(access));
        } catch (SecurityException notAllowed) {
            listener.onTrack(null);
        }
    }

    void stop() {
        try {
            sessions.removeOnActiveSessionsChangedListener(changed);
        } catch (RuntimeException ignored) {
        }
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
    }

    void togglePlayPause() {
        if (controller == null) return;
        if (playing(controller)) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    /** The bar's cross: the music stops and the bar goes until something plays in the background again. */
    void close() {
        if (controller == null) return;
        background.remove(controller.getSessionToken());
        controller.getTransportControls().pause();
        controller.getTransportControls().stop();
        listener.onTrack(null);
    }

    /** Back into the player: its own screen if the session names one, else the app. */
    void open() {
        if (controller == null) return;
        PendingIntent screen = controller.getSessionActivity();
        try {
            if (screen != null) {
                screen.send();
                return;
            }
        } catch (PendingIntent.CanceledException ignored) {
        }
        PackageManager pm = context.getPackageManager();
        Intent app = pm.getLeanbackLaunchIntentForPackage(controller.getPackageName());
        if (app == null) app = pm.getLaunchIntentForPackage(controller.getPackageName());
        if (app == null) return;
        try {
            context.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private List<MediaController> active() {
        try {
            return sessions.getActiveSessions(access);
        } catch (SecurityException notAllowed) {
            return null;
        }
    }

    /** The playing session if there is one, else the last one that played in the background. */
    private void pick(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                if (playing(c)) {
                    pick = c;
                    break;
                }
            }
            if (pick == null) {
                for (MediaController c : list) {
                    if (background.contains(c.getSessionToken())) {
                        pick = c;
                        break;
                    }
                }
            }
        }
        boolean same = controller != null && pick != null && controller.getSessionToken().equals(pick.getSessionToken());
        if (!same) {
            if (controller != null) controller.unregisterCallback(callback);
            controller = pick;
            if (controller != null) controller.registerCallback(callback, main);
        }
        report();
    }

    private void report() {
        listener.onTrack(track());
    }

    private Track track() {
        if (controller == null) return null;
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return null;
        boolean playing = playing(controller);
        if (playing) background.add(controller.getSessionToken());
        PlaybackState state = controller.getPlaybackState();
        boolean paused = state != null && state.getState() == PlaybackState.STATE_PAUSED;
        if (!playing && !(paused && background.contains(controller.getSessionToken()))) return null;
        String title = first(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = first(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return new Track(appLabel(controller.getPackageName()), title, artist, art, playing);
    }

    private static boolean playing(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING
                || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private String appLabel(String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private static String first(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        return b == null ? "" : b;
    }
}
