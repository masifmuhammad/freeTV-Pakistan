package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.Test;

public class PrayerTimesTest {

    private static final ZoneId KARACHI = ZoneId.of("Asia/Karachi");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);

    private static String clock(List<PrayerTimes.Time> times, PrayerTimes.Prayer prayer) {
        return times.get(prayer.ordinal()).at.toLocalTime().toString();
    }

    @Test
    public void lahoreMatchesAPublishedTimetable() {
        // TrueCalendar, Lahore, 22 Oct 2026 (MWL: Fajr 18 degrees like Karachi): Fajr 04:48, sunrise 06:10,
        // Dhuhr 11:47, standard Asr 14:58, Maghrib 17:24. Within a minute is the formulas' accuracy.
        List<PrayerTimes.Time> standard = PrayerTimes.day(DAY, PrayerTimes.LAHORE_LAT, PrayerTimes.LAHORE_LON,
                false, KARACHI);
        assertEquals("04:49", clock(standard, PrayerTimes.Prayer.FAJR));
        assertEquals("06:10", clock(standard, PrayerTimes.Prayer.SUNRISE));
        assertEquals("11:47", clock(standard, PrayerTimes.Prayer.DHUHR));
        assertEquals("14:58", clock(standard, PrayerTimes.Prayer.ASR));
        assertEquals("17:23", clock(standard, PrayerTimes.Prayer.MAGHRIB));
        assertEquals("18:45", clock(standard, PrayerTimes.Prayer.ISHA));
    }

    @Test
    public void hanafiAsrIsLater() {
        List<PrayerTimes.Time> hanafi = PrayerTimes.day(DAY, PrayerTimes.LAHORE_LAT, PrayerTimes.LAHORE_LON,
                true, KARACHI);
        assertEquals("15:45", clock(hanafi, PrayerTimes.Prayer.ASR));
    }

    @Test
    public void afterIshaTheNextIsTomorrowsFajr() {
        LocalDateTime night = DAY.atTime(21, 0);
        List<PrayerTimes.Time> next = PrayerTimes.upcoming(night, PrayerTimes.LAHORE_LAT, PrayerTimes.LAHORE_LON,
                true, KARACHI);
        assertEquals(PrayerTimes.Prayer.FAJR, next.get(0).prayer);
        assertEquals(DAY.plusDays(1), next.get(0).at.toLocalDate());
        assertEquals("7 h 50 min", PrayerTimes.until(night, next.get(0).at));
    }
}
