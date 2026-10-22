package com.gathin.tvhome;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Hijri date as Pakistan keeps it, and Pakistan's holidays, for the calendar widget. HijrahDate is the
 * Umm al-Qura calendar (Saudi Arabia); Pakistan starts each month on its own moon sighting, usually a day
 * later, so dates are shifted by an offset in days that the owner can change on the widget.
 */
final class Hijri {

    /** Pakistan's usual lag: 1 Jumada al-Awwal 1448 was 13 October 2026 there, 12 October in Umm al-Qura. */
    static final int PAKISTAN_OFFSET = -1;
    /** The owner's offset from Umm al-Qura (the calendar widget's Settings), in the launcher's settings. */
    static final String OFFSET_KEY = "hijri_offset";

    static int offset(android.content.Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE)
                .getInt(OFFSET_KEY, PAKISTAN_OFFSET);
    }

    private static final String[] MONTHS = {"Muharram", "Safar", "Rabi ul Awwal", "Rabi us Sani",
            "Jumada al Awwal", "Jumada us Sani", "Rajab", "Shaban", "Ramazan", "Shawwal", "Zul Qadah",
            "Zul Hijjah"};

    /** A Hijri date: day, month (1 is Muharram), year. */
    static final class Date {
        final int day, month, year;

        Date(int day, int month, int year) {
            this.day = day;
            this.month = month;
            this.year = year;
        }

        String monthName() {
            return MONTHS[month - 1];
        }
    }

    /** The days the widget counts down to: fixed on the Gregorian or on the Hijri calendar. */
    enum Holiday {
        KASHMIR("Kashmir Day", false, 5, 2), PAKISTAN_DAY("Pakistan Day", false, 23, 3),
        LABOUR("Labour Day", false, 1, 5), TAKBEER("Youm-e-Takbeer", false, 28, 5),
        INDEPENDENCE("Independence Day", false, 14, 8), IQBAL("Iqbal Day", false, 9, 11),
        QUAID("Quaid-e-Azam Day", false, 25, 12),
        NEW_YEAR("Islamic New Year", true, 1, 1), ASHURA("Ashura", true, 10, 1),
        MILAD("Eid Milad-un-Nabi", true, 12, 3),
        // Kept on the night that starts on the evening of 14 Shaban.
        SHAB_E_BARAT("Shab-e-Barat", true, 14, 8),
        RAMAZAN("Ramazan", true, 1, 9), EID_UL_FITR("Eid ul Fitr", true, 1, 10),
        EID_UL_ADHA("Eid ul Adha", true, 10, 12);

        final String title;
        final boolean hijri;
        final int day, month;

        Holiday(String title, boolean hijri, int day, int month) {
            this.title = title;
            this.hijri = hijri;
            this.day = day;
            this.month = month;
        }

        boolean eid() {
            return this == EID_UL_FITR || this == EID_UL_ADHA;
        }

        /** Its date in Pakistan on or after from, or null outside the calendar's range. */
        LocalDate onOrAfter(LocalDate from, int offset) {
            if (!hijri) {
                LocalDate d = LocalDate.of(from.getYear(), month, day);
                return d.isBefore(from) ? d.plusYears(1) : d;
            }
            int year = toHijri(from, offset).year;
            for (int y = year; y <= year + 1; y++) {
                LocalDate d = toGregorian(day, month, y, offset);
                if (d != null && !d.isBefore(from)) return d;
            }
            return null;
        }
    }

    /** A holiday and how many days away it is. */
    static final class Upcoming {
        final Holiday holiday;
        final long days;

        Upcoming(Holiday holiday, long days) {
            this.holiday = holiday;
            this.days = days;
        }
    }

    private Hijri() {
    }

    static Date toHijri(LocalDate day, int offset) {
        HijrahDate h = HijrahDate.from(day.plusDays(offset));
        return new Date(h.get(ChronoField.DAY_OF_MONTH), h.get(ChronoField.MONTH_OF_YEAR),
                h.get(ChronoField.YEAR));
    }

    /** The Gregorian day a Hijri date falls on in Pakistan, or null when the calendar has no such day. */
    static LocalDate toGregorian(int day, int month, int year, int offset) {
        try {
            return LocalDate.from(HijrahDate.of(year, month, day)).minusDays(offset);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Every holiday's next date from today, soonest first. */
    static List<Upcoming> upcoming(LocalDate today, int offset) {
        List<Upcoming> all = new ArrayList<>();
        for (Holiday h : Holiday.values()) {
            LocalDate d = h.onOrAfter(today, offset);
            if (d != null) all.add(new Upcoming(h, ChronoUnit.DAYS.between(today, d)));
        }
        Collections.sort(all, (a, b) -> Long.compare(a.days, b.days));
        return all;
    }
}
