package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.Test;

public class PrayerReminderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);

    private static PrayerReminder.Due next(int hour, int minute, String mode) {
        LocalDateTime now = DAY.atTime(hour, minute);
        return PrayerReminder.next(PrayerTimes.upcoming(now, PrayerTimes.LAHORE_LAT, PrayerTimes.LAHORE_LON, true,
                ZoneId.of("Asia/Karachi")), now, mode);
    }

    @Test
    public void remindsAtTheNextPrayerAndSkipsSunrise() {
        PrayerReminder.Due due = next(5, 30, PrayerReminder.AT_TIME);   // after Fajr 04:49, before sunrise
        assertEquals(PrayerTimes.Prayer.DHUHR, due.time.prayer);
        assertFalse(due.early);
    }

    @Test
    public void earlyRemindsTenMinutesBeforeThenAtTheTime() {
        PrayerReminder.Due early = next(15, 30, PrayerReminder.EARLY);   // Asr (Hanafi) is 15:45
        assertEquals(PrayerTimes.Prayer.ASR, early.time.prayer);
        assertTrue(early.early);
        assertEquals(DAY.atTime(15, 35), early.when());
        PrayerReminder.Due onTime = next(15, 40, PrayerReminder.EARLY);
        assertFalse(onTime.early);
        assertEquals(DAY.atTime(15, 45), onTime.when());
    }

    @Test
    public void offRemindsOfNothing() {
        assertNull(next(15, 30, PrayerReminder.OFF));
    }
}
