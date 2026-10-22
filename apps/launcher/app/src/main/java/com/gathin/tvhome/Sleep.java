package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

/**
 * Turn off now and the sleep timer, from the remote's Settings panel (after samdegan2311's Control
 * Center). The standby itself is HomeKeyService's lock-screen action: TVs often lack device admin.
 */
final class Sleep {

    private static final String AT = "sleep_at";
    /** A timer that fires this late (the TV was off, the alarm waited) is dropped instead of sleeping a TV just turned on. */
    private static final long STALE_MS = 2 * 60_000L;
    private static final int[] STEPS = {30, 60, 90};

    private Sleep() {
    }

    /** Fires when the sleep timer runs out. */
    public static class Alarm extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long at = prefs(context).getLong(AT, 0);
            prefs(context).edit().remove(AT).apply();
            if (at > 0 && Math.abs(System.currentTimeMillis() - at) < STALE_MS) now(context);
        }
    }

    static void now(Context context) {
        if (!HomeKeyService.lockScreen()) {
            Toast.makeText(context, R.string.sleep_needs_helper, Toast.LENGTH_LONG).show();
        }
    }

    /** Minutes left on the timer, or 0 when it is off. */
    static int minutesLeft(Context context) {
        long left = prefs(context).getLong(AT, 0) - System.currentTimeMillis();
        return left > 0 ? (int) Math.ceil(left / 60_000.0) : 0;
    }

    /** Off, 30, 60, 90, off: from a running timer, the next step above what is left. */
    static void cycle(Context context) {
        int left = minutesLeft(context);
        int next = 0;
        for (int step : STEPS) {
            if (step > left) {
                next = step;
                break;
            }
        }
        set(context, next);
    }

    private static void set(Context context, int minutes) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent alarm = PendingIntent.getBroadcast(context, 0, new Intent(context, Alarm.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(alarm);
        if (minutes <= 0) {
            prefs(context).edit().remove(AT).apply();
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        prefs(context).edit().putLong(AT, at).apply();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, at, alarm);
        } else {
            alarms.setWindow(AlarmManager.RTC, at, 60_000L, alarm);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
