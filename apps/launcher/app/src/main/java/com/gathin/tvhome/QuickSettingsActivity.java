package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {

    /** The open-source AirPlay receiver (jqssun/android-airplay-server), when it is installed. */
    private static final String AIRPLAY_PACKAGE = "io.github.jqssun.airplay";
    /** Android TV's own Accessibility page; the plain settings action lands on a stub on Google TV. */
    private static final String ACCESSIBILITY_TV = "android.settings.ACCESSIBILITY_TV_OEM_LINK";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Palette.loadDarkMode(this);
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        addAction(rows, R.drawable.ic_timer, R.string.sleep_timer, sleepStatus(), status -> {
            Sleep.cycle(this);
            status.setText(sleepStatus());
        });
        addAction(rows, R.drawable.ic_power, R.string.sleep_now, null, status -> {
            finish();
            Sleep.now(getApplicationContext());
        });
        addAction(rows, R.drawable.ic_memory, R.string.free_memory,
                getString(R.string.memory_free, availableMb()), this::freeMemory);
        addRow(rows, R.drawable.ic_w_clear_night, R.string.screensaver_now, R.string.screensaver_now_hint,
                new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui",
                        "com.android.systemui.Somnambulator"));
        addAction(rows, R.drawable.ic_info, R.string.tv_check, getString(checkProblem()), this::fixProblem);
        Intent airplay = getPackageManager().getLeanbackLaunchIntentForPackage(AIRPLAY_PACKAGE);
        if (airplay != null) addRow(rows, R.drawable.ic_airplay, R.string.airplay, R.string.airplay_hint, airplay);
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, TvSettings.main(this));
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, TvSettings.picture(this));
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, new Intent(Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        Config config = Config.load(this);
        M3Skin.apply(findViewById(R.id.panel), Palette.currentOrBaseline(), config.animations, config.reducedMotion);
        rows.getChildAt(0).requestFocus();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        addAction(rows, icon, title, status == 0 ? null : getString(status), hint -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    /** A row that runs action with its status line (hidden when status is null). */
    private void addAction(LinearLayout rows, int icon, int title, String status, Consumer<TextView> action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != null) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> action.accept(hint));
        rows.addView(row);
    }

    /**
     * TV check: what a firmware update may have undone, the first thing to put back, as the row's status. Each
     * one is fixed with the remote, no computer: the remote-button helper (Accessibility), this launcher as Home
     * (the Home app picker). Music access can only be given back from a computer (README).
     */
    private int checkProblem() {
        if (HomeKeyService.bound() == null) return R.string.tv_check_helper;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo owner = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (owner == null || !getPackageName().equals(owner.activityInfo.packageName)) return R.string.tv_check_home;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (!notifications.isNotificationListenerAccessGranted(new ComponentName(this, NowPlayingListener.class))) {
            return R.string.tv_check_music;
        }
        return R.string.tv_check_ok;
    }

    private void fixProblem(TextView status) {
        int problem = checkProblem();
        status.setText(problem);
        Intent fix;
        int help;
        if (problem == R.string.tv_check_helper) {
            fix = new Intent(ACCESSIBILITY_TV);
            help = R.string.tv_check_helper_help;
        } else if (problem == R.string.tv_check_home) {
            fix = new Intent(Settings.ACTION_HOME_SETTINGS);
            help = R.string.home_settings_help;
        } else {
            if (problem == R.string.tv_check_music) {
                Toast.makeText(this, R.string.tv_check_music_help, Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            startActivity(fix.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                return;
            }
        }
        Toast.makeText(getApplicationContext(), help, Toast.LENGTH_LONG).show();
        finish();
    }

    private String sleepStatus() {
        int left = Sleep.minutesLeft(this);
        return left > 0 ? getString(R.string.sleep_timer_left, left) : getString(R.string.sleep_timer_off);
    }

    /** Stops installed apps' cached background processes (never system apps) and says what came back. */
    private void freeMemory(TextView status) {
        ActivityManager activities = getSystemService(ActivityManager.class);
        long before = availableMb();
        for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || app.packageName.equals(getPackageName())) continue;
            activities.killBackgroundProcesses(app.packageName);
        }
        // The processes take a moment to exit before their memory is free.
        status.postDelayed(() -> {
            long after = availableMb();
            status.setText(getString(R.string.memory_free, after));
            Toast.makeText(this, getString(R.string.memory_freed, Math.max(0, after - before), after),
                    Toast.LENGTH_SHORT).show();
        }, 1500);
    }

    private long availableMb() {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(memory);
        return memory.availMem / (1024 * 1024);
    }
}
