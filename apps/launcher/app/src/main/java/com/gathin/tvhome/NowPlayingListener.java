package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the launcher may read the TV's media sessions for the now playing bar and the screensaver:
 * Android hands active sessions to an app whose notification listener is allowed. It reads no
 * notifications. Allowed over adb with
 * {@code cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener}.
 */
public final class NowPlayingListener extends NotificationListenerService {
}
