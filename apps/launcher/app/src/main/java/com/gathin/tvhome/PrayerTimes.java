package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Prayer times worked out on the TV, with no network, the way Pakistan reckons them: the University of
 * Islamic Sciences, Karachi method (Fajr and Isha with the sun 18 degrees below the horizon) and Hanafi
 * Asr (an object's shadow twice its length) unless the owner picks the standard one. The sun's position
 * follows the Astronomical Almanac's low-precision formulas, good to about a minute; checked against a
 * published Lahore timetable for 22 October 2026.
 */
final class PrayerTimes {

    enum Prayer {
        FAJR("Fajr"), SUNRISE("Sunrise"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha");

        final String title;

        Prayer(String title) {
            this.title = title;
        }
    }

    /** One prayer on one day. */
    static final class Time {
        final Prayer prayer;
        final LocalDateTime at;

        Time(Prayer prayer, LocalDateTime at) {
            this.prayer = prayer;
            this.at = at;
        }
    }

    /** Where the weather widget has no place yet. */
    static final double LAHORE_LAT = 31.5204, LAHORE_LON = 74.3587;
    private static final double TWILIGHT_ANGLE = 18, HORIZON_ANGLE = 0.833;
    private static final double[] FIRST_GUESS_HOURS = {5, 6, 12, 13, 18, 18};

    private PrayerTimes() {
    }

    /** The place the weather widget reports on (its city setting), or Lahore before its first report. */
    static double[] place(Context context) {
        SharedPreferences weather = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        float lat = weather.getFloat("lat", 0f), lon = weather.getFloat("lon", 0f);
        if (lat == 0f && lon == 0f) return new double[]{LAHORE_LAT, LAHORE_LON};
        return new double[]{lat, lon};
    }

    /** The day's six times at that place, in the TV's time zone. */
    static List<Time> day(LocalDate date, double lat, double lon, boolean hanafi, ZoneId zone) {
        double zoneHours = zone.getRules().getOffset(date.atStartOfDay()).getTotalSeconds() / 3600.0;
        double julianDay = julian(date.getYear(), date.getMonthValue(), date.getDayOfMonth()) - lon / (15 * 24);
        double[] guess = new double[FIRST_GUESS_HOURS.length];
        for (int i = 0; i < guess.length; i++) guess[i] = FIRST_GUESS_HOURS[i] / 24;
        double[] hours = {
                angleTime(julianDay, lat, TWILIGHT_ANGLE, guess[0], true),
                angleTime(julianDay, lat, HORIZON_ANGLE, guess[1], true),
                noon(julianDay, guess[2]),
                asrTime(julianDay, lat, hanafi ? 2 : 1, guess[3]),
                angleTime(julianDay, lat, HORIZON_ANGLE, guess[4], false),
                angleTime(julianDay, lat, TWILIGHT_ANGLE, guess[5], false)};
        List<Time> times = new ArrayList<>();
        Prayer[] prayers = Prayer.values();
        for (int i = 0; i < prayers.length; i++) {
            // To the nearest minute, as timetables print them: rounding up would put Fajr (the end of
            // Sehri) late, rounding down would put the other prayers early.
            long minute = Math.round((hours[i] + zoneHours - lon / 15) * 60);
            times.add(new Time(prayers[i], date.atStartOfDay().plusMinutes(minute)));
        }
        return times;
    }

    /** The next times from now on, soonest first: the rest of today, then tomorrow's. */
    static List<Time> upcoming(LocalDateTime now, double lat, double lon, boolean hanafi, ZoneId zone) {
        List<Time> next = new ArrayList<>();
        for (int day = 0; day <= 1; day++) {
            for (Time t : day(now.toLocalDate().plusDays(day), lat, lon, hanafi, zone)) {
                if (t.at.isAfter(now)) next.add(t);
            }
        }
        return next;
    }

    /** Whether Asr is the Hanafi one (Launcher settings, the prayer widget's Settings). */
    static boolean hanafiAsr(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(HANAFI_KEY, true);
    }

    static final String HANAFI_KEY = "prayer_asr_hanafi";

    /** The next times from now at the weather's place, in the TV's time zone. */
    static List<Time> upcoming(Context context, LocalDateTime now) {
        double[] at = place(context);
        return upcoming(now, at[0], at[1], hanafiAsr(context), ZoneId.systemDefault());
    }

    /** A time of day as the TV shows its clock: "15:45", or "3:45 PM" with the 12-hour clock. */
    static String clock(Context context, LocalDateTime at) {
        String pattern = android.text.format.DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a";
        return at.format(java.time.format.DateTimeFormatter.ofPattern(pattern));
    }

    /** The same with AM or PM at markerScale of the time's size, as the header clock shows it. */
    static CharSequence clockText(Context context, LocalDateTime at, float markerScale) {
        String clock = clock(context, at);
        int marker = clock.indexOf(' ');
        android.text.SpannableString text = new android.text.SpannableString(clock);
        if (marker > 0) {
            text.setSpan(new android.text.style.RelativeSizeSpan(markerScale), marker, clock.length(), 0);
        }
        return text;
    }

    /** "1 h 20 min" or "45 min" until then, rounded up to the minute. */
    static String until(LocalDateTime now, LocalDateTime then) {
        long minutes = (ChronoUnit.SECONDS.between(now, then) + 59) / 60;
        if (minutes < 60) return minutes + " min";
        return minutes / 60 + " h " + minutes % 60 + " min";
    }

    // ---- astronomy -------------------------------------------------------------------------------

    private static double julian(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** The sun's declination (degrees) and the equation of time (hours) at a Julian day. */
    private static double[] sun(double julianDay) {
        double d = julianDay - 2451545.0;
        double g = fix(357.529 + 0.98560028 * d, 360);
        double q = fix(280.459 + 0.98564736 * d, 360);
        double longitude = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double obliquity = 23.439 - 0.00000036 * d;
        double rightAscension = Math.toDegrees(Math.atan2(cos(obliquity) * sin(longitude), cos(longitude))) / 15;
        double equation = q / 15 - fix(rightAscension, 24);
        double declination = Math.toDegrees(Math.asin(sin(obliquity) * sin(longitude)));
        return new double[]{declination, equation};
    }

    private static double noon(double julianDay, double dayPortion) {
        return fix(12 - sun(julianDay + dayPortion)[1], 24);
    }

    /** When the sun is angle degrees below the horizon: before noon (morning) or after. */
    private static double angleTime(double julianDay, double lat, double angle, double dayPortion, boolean morning) {
        double declination = sun(julianDay + dayPortion)[0];
        double cosHour = (-sin(angle) - sin(declination) * sin(lat)) / (cos(declination) * cos(lat));
        double hours = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosHour)))) / 15;
        double midday = noon(julianDay, dayPortion);
        return morning ? midday - hours : midday + hours;
    }

    /** Asr: an object's shadow is shadowFactor times its length plus its noon shadow. */
    private static double asrTime(double julianDay, double lat, int shadowFactor, double dayPortion) {
        double declination = sun(julianDay + dayPortion)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (shadowFactor + Math.tan(Math.toRadians(Math.abs(lat - declination))))));
        return angleTime(julianDay, lat, angle, dayPortion, false);
    }

    private static double fix(double value, double range) {
        return value - range * Math.floor(value / range);
    }

    private static double sin(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private static double cos(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }
}
