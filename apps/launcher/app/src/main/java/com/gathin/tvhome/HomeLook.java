package com.gathin.tvhome;

import android.content.SharedPreferences;

/**
 * Home's look as chosen in Launcher settings: the scheme's seed colour (kept under background_color, over the
 * theme's, see Config) and what is drawn behind the apps. Pushing or resetting a theme clears both.
 */
final class HomeLook {
    private HomeLook() { }

    static final String BACKDROP_KEY = "backdrop";
    /**
     * The M3 Expressive shapes, the time-of-day sky, the scheme's surface alone, or Liquid Glass: a soft
     * colour field under see-through cards ({@link GlassBackdrop}, {@link GlassDrawable}).
     */
    static final String SHAPES = "shapes", SKY = "sky", PLAIN = "plain", GLASS = "glass";
    static final String[] BACKDROPS = {SHAPES, SKY, PLAIN, GLASS};
    /**
     * The sky's opacity when it is chosen here and the theme gives it none. Its gradient is a dark one, so a
     * light scheme gets a tint of it that keeps the dark header text readable.
     */
    private static final int SKY_INTENSITY_DARK = 70, SKY_INTENSITY_LIGHT = 30;

    /** Seeds offered in Launcher settings, in the order of R.array.look_seed_names: M3's baseline violet first. */
    static final int[] SEEDS = {0xFF6750A4, 0xFF0061A4, 0xFF006A6A, 0xFF386A20, 0xFF8B5000, 0xFF984061, 0xFFB3261E};

    static final String MODE_KEY = "appearance";
    /** Light by day and dark at night, always light, or always dark. */
    static final String AUTO = "auto", LIGHT = "light", DARK = "dark";
    static final String[] MODES = {AUTO, LIGHT, DARK};
    // ponytail: fixed hours, not Lahore's sunset (about 5 PM in winter, 7 PM in summer); the weather
    // widget's place could give a real sunset if that ever matters.
    private static final int DARK_FROM_HOUR = 18, DARK_UNTIL_HOUR = 6;

    static String mode(SharedPreferences choices) {
        String chosen = choices.getString(MODE_KEY, AUTO);
        for (String known : MODES) if (known.equals(chosen)) return known;
        return AUTO;
    }

    /** Whether Home is dark at that hour of the day. */
    static boolean dark(SharedPreferences choices, int hour) {
        return dark(mode(choices), hour);
    }

    static boolean dark(String mode, int hour) {
        if (LIGHT.equals(mode)) return false;
        if (DARK.equals(mode)) return true;
        return hour >= DARK_FROM_HOUR || hour < DARK_UNTIL_HOUR;
    }

    static String backdrop(SharedPreferences choices) {
        String chosen = choices.getString(BACKDROP_KEY, SHAPES);
        for (String known : BACKDROPS) if (known.equals(chosen)) return known;
        return SHAPES;
    }

    static boolean glass(SharedPreferences choices) {
        return GLASS.equals(backdrop(choices));
    }

    /** sky_intensity for a backdrop: the theme's for the shapes, a visible sky for the sky, none otherwise. */
    static int skyIntensity(String backdrop, int themeIntensity, boolean dark) {
        if (SKY.equals(backdrop)) {
            if (themeIntensity > 0) return themeIntensity;
            return dark ? SKY_INTENSITY_DARK : SKY_INTENSITY_LIGHT;
        }
        return SHAPES.equals(backdrop) ? themeIntensity : 0;
    }
}
