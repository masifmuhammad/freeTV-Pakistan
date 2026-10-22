package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

public class HijriTest {

    private static final int PK = Hijri.PAKISTAN_OFFSET;

    private static void assertHijri(LocalDate day, int hijriDay, int month, int year) {
        Hijri.Date d = Hijri.toHijri(day, PK);
        assertEquals(day + " day", hijriDay, d.day);
        assertEquals(day + " month", month, d.month);
        assertEquals(day + " year", year, d.year);
    }

    @Test
    public void followsPakistansMoonSighting() {
        // Ruet-e-Hilal: 1 Jumada al-Awwal 1448 was Tuesday 13 October 2026.
        assertHijri(LocalDate.of(2026, 10, 13), 1, 5, 1448);
        assertHijri(LocalDate.of(2026, 10, 22), 10, 5, 1448);
        assertEquals("Jumada al Awwal", Hijri.toHijri(LocalDate.of(2026, 10, 22), PK).monthName());
    }

    @Test
    public void hijriHolidaysLandOnPakistansDay() {
        // Eid Milad-un-Nabi 1448 was announced for 26 August 2026; Eid ul Fitr 1446 was 31 March 2025.
        assertEquals(LocalDate.of(2026, 8, 26), Hijri.Holiday.MILAD.onOrAfter(LocalDate.of(2026, 8, 1), PK));
        assertEquals(LocalDate.of(2025, 3, 31),
                Hijri.Holiday.EID_UL_FITR.onOrAfter(LocalDate.of(2025, 3, 1), PK));
    }

    @Test
    public void countsDownToTheNearestHolidayFirst() {
        List<Hijri.Upcoming> upcoming = Hijri.upcoming(LocalDate.of(2026, 10, 22), PK);
        assertEquals(Hijri.Holiday.values().length, upcoming.size());
        assertEquals(Hijri.Holiday.IQBAL, upcoming.get(0).holiday);
        assertEquals(18, upcoming.get(0).days);
        assertEquals(0, Hijri.upcoming(LocalDate.of(2026, 12, 25), PK).get(0).days);
    }
}
