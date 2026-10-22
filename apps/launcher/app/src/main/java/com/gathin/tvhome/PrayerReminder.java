package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Prayer reminders: at each prayer's time, and ten minutes before if the owner wants, a banner at the top
 * of the screen over whatever is playing. It is an accessibility overlay of the Home button helper
 * (HomeKeyService), so it needs no extra permission, takes no focus and leaves the video playing; it fades
 * out by itself. One exact alarm at a time: each shows its banner and sets the next one.
 */
final class PrayerReminder {

    static final String MODE_KEY = "prayer_reminders";
    /** At the prayer's time, also ten minutes before, or never. */
    static final String AT_TIME = "at", EARLY = "early", OFF = "off";
    static final String[] MODES = {AT_TIME, EARLY, OFF};
    static final int EARLY_MINUTES = 10;
    /** A reminder this late (the TV was off when it was due) is dropped rather than shown. */
    private static final long STALE_MS = 15 * 60_000L;
    private static final long BANNER_MS = 30_000L;
    private static final String EXTRA_PRAYER = "prayer", EXTRA_AT = "at", EXTRA_EARLY = "early";

    /** Created on first use, so the reminder logic loads in a plain JVM test. */
    private static Handler main;
    private static View banner;

    /** A reminder that is due: for which prayer, and whether it is the early one. */
    static final class Due {
        final PrayerTimes.Time time;
        final boolean early;

        Due(PrayerTimes.Time time, boolean early) {
            this.time = time;
            this.early = early;
        }

        LocalDateTime when() {
            return early ? time.at.minusMinutes(EARLY_MINUTES) : time.at;
        }
    }

    private PrayerReminder() {
    }

    static String mode(Context context) {
        String chosen = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(MODE_KEY, AT_TIME);
        for (String known : MODES) if (known.equals(chosen)) return known;
        return AT_TIME;
    }

    /** The first reminder after now, sunrise left out (it is no prayer), or null with reminders off. */
    static Due next(List<PrayerTimes.Time> upcoming, LocalDateTime now, String mode) {
        if (OFF.equals(mode)) return null;
        for (PrayerTimes.Time t : upcoming) {
            if (t.prayer == PrayerTimes.Prayer.SUNRISE) continue;
            if (EARLY.equals(mode) && t.at.minusMinutes(EARLY_MINUTES).isAfter(now)) return new Due(t, true);
            if (t.at.isAfter(now)) return new Due(t, false);
        }
        return null;
    }

    /** Sets the alarm for the next reminder, replacing any, or cancels it when reminders are off. */
    static void schedule(Context context) {
        schedule(context, LocalDateTime.now());
    }

    private static void schedule(Context context, LocalDateTime after) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        Due due = next(PrayerTimes.upcoming(context, after), after, mode(context));
        Intent fire = new Intent(context, Alarm.class);
        if (due != null) {
            fire.putExtra(EXTRA_PRAYER, due.time.prayer.name()).putExtra(EXTRA_AT, millis(due.time.at))
                    .putExtra(EXTRA_EARLY, due.early);
        }
        PendingIntent alarm = PendingIntent.getBroadcast(context, 0, fire,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(alarm);
        if (due == null) return;
        long when = millis(due.when());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, when, alarm);
        } else {
            alarms.setWindow(AlarmManager.RTC, when, 60_000L, alarm);
        }
    }

    /** Fires at a reminder's time: shows it unless it comes late, then sets the next one. */
    public static class Alarm extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String name = intent.getStringExtra(EXTRA_PRAYER);
            long at = intent.getLongExtra(EXTRA_AT, 0);
            boolean early = intent.getBooleanExtra(EXTRA_EARLY, false);
            long due = early ? at - EARLY_MINUTES * 60_000L : at;
            if (name != null && Math.abs(System.currentTimeMillis() - due) < STALE_MS) {
                LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault());
                show(context, new Due(new PrayerTimes.Time(PrayerTimes.Prayer.valueOf(name), time), early));
            }
            // A little ahead, so an alarm delivered a moment early cannot set itself again.
            schedule(context, LocalDateTime.now().plusSeconds(30));
        }
    }

    /** The banner for the next prayer now: Settings on the prayer widget, to see what a reminder looks like. */
    static void preview(Context context) {
        LocalDateTime now = LocalDateTime.now();
        Due due = next(PrayerTimes.upcoming(context, now), now, AT_TIME);
        if (due != null) show(context, due);
    }

    private static void show(Context context, Due due) {
        String prayer = due.time.prayer.title;
        CharSequence title = due.early
                ? context.getString(R.string.reminder_early_title, prayer, EARLY_MINUTES)
                : new SpannableStringBuilder(prayer).append("  ")
                        .append(PrayerTimes.clockText(context, due.time.at, 0.55f));
        String message = due.early
                ? context.getString(R.string.reminder_early_message, PrayerTimes.clock(context, due.time.at))
                : context.getString(R.string.reminder_message, prayer);
        AccessibilityService helper = HomeKeyService.bound();
        if (helper == null) {
            // Without the helper there is no overlay: a toast still says it, over any app.
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            return;
        }
        showBanner(helper, title, message);
    }

    /** A glass card at the top centre: non-focusable and non-touchable, so the remote keeps the app. */
    private static void showBanner(AccessibilityService helper, CharSequence title, CharSequence message) {
        hideBanner(helper);
        Palette home = Palette.current();
        Palette p = Palette.dark(home != null ? home : Palette.fromBackground(Config.load(helper).backgroundColor));
        float density = helper.getResources().getDisplayMetrics().density;
        View view = LayoutInflater.from(helper).inflate(R.layout.prayer_reminder, null);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(36 * density);
        glass.setColor((0xF0 << 24) | (p.surfaceContainerHigh & 0x00FFFFFF));
        glass.setStroke(Math.max(1, Math.round(density)), (0x66 << 24) | (p.outlineVariant & 0x00FFFFFF));
        view.setBackground(glass);
        ShapeBadge badge = view.findViewById(R.id.reminderBadge);
        badge.setIcon(helper.getDrawable(R.drawable.ic_w_clear_night));
        badge.setColors(p.primaryContainer, p.onPrimaryContainer);
        TextView titleView = view.findViewById(R.id.reminderTitle);
        titleView.setText(title);
        titleView.setTextColor(p.onSurface);
        TextView messageView = view.findViewById(R.id.reminderMessage);
        messageView.setText(message);
        messageView.setTextColor(p.onSurfaceVariant);

        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layout.y = Math.round(40 * density);
        helper.getSystemService(WindowManager.class).addView(view, layout);
        banner = view;
        view.setAlpha(0f);
        view.setTranslationY(-24 * density);
        view.animate().alpha(1f).translationY(0f).setDuration(450).start();
        main().postDelayed(() -> hideBanner(helper), BANNER_MS);
    }

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static void hideBanner(AccessibilityService helper) {
        main().removeCallbacksAndMessages(null);
        View shown = banner;
        banner = null;
        if (shown == null) return;
        shown.animate().alpha(0f).setDuration(450).withEndAction(() -> {
            if (shown.isAttachedToWindow()) helper.getSystemService(WindowManager.class).removeView(shown);
        }).start();
    }

    private static long millis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
