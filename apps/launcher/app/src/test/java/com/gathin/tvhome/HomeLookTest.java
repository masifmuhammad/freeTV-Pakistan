package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeLookTest {

    @Test
    public void autoIsDarkFromSixInTheEveningToSixInTheMorning() {
        assertFalse(HomeLook.dark(HomeLook.AUTO, 17));
        assertTrue(HomeLook.dark(HomeLook.AUTO, 18));
        assertTrue(HomeLook.dark(HomeLook.AUTO, 5));
        assertFalse(HomeLook.dark(HomeLook.AUTO, 6));
    }

    @Test
    public void lightAndDarkIgnoreTheHour() {
        assertFalse(HomeLook.dark(HomeLook.LIGHT, 23));
        assertTrue(HomeLook.dark(HomeLook.DARK, 12));
    }

    @Test
    public void onlyTheShapesAndTheSkyShowTheSky() {
        assertEquals(40, HomeLook.skyIntensity(HomeLook.SHAPES, 40, true));
        assertEquals(40, HomeLook.skyIntensity(HomeLook.SKY, 40, true));
        assertEquals(0, HomeLook.skyIntensity(HomeLook.PLAIN, 40, true));
        assertEquals(0, HomeLook.skyIntensity(HomeLook.GLASS, 40, true));
    }
}
