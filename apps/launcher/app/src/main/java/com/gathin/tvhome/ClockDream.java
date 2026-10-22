package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Screensaver, made to be read from across the room: the clock and the dates at the top centre, and two cards
 * at the bottom, the next prayer and the air quality, in the style chosen in Launcher settings: over Home's
 * M3E shapes, over a slow ambient glow, dim, or with a larger clock. Every minute the page shifts a few dp and
 * the glow changes colour, so nothing burns in while the clock stays in the middle. No network, no images:
 * the air quality is the one Home last fetched.
 */
public class ClockDream extends DreamService {

    private static final long TICK_MS = 60_000L;
    /** The shapes at part strength on black, the dim style's page: nothing bright stays put. */
    private static final float SHAPES_ALPHA = 0.3f, MINIMAL_ALPHA = 0.5f;
    private static final float LARGE_SCALE = 1.5f;
    /** How far the page may wander from the middle, in dp: invisible from the sofa, enough for the panel. */
    private static final float SHIFT_DP = 10f;
    /** Home fetches the air quality every half hour while it shows; older than this, the card hides. */
    private static final long AIR_FRESH_MS = 3 * 60 * 60_000L;
    /** The cards' glass: the scheme's container colour at this opacity over black. */
    private static final int CARD_ALPHA = 0xE6;
    /** Glow colours from the scheme; the glow is drawn at partial alpha on black. */
    private int[] palette;
    private Palette scheme;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View page, content;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    private String style;
    private ExpressiveBackdrop shapes;
    /** Music playing in the background: its line under the dates. */
    private NowPlaying music;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showInfo();
            shiftPage();
            shiftGlow();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        page = findViewById(R.id.dreamPage);
        content = findViewById(R.id.dreamContent);
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 0.22f);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        // Always the dark scheme: the screensaver sits on black so nothing burns in. Seeded like Home's when
        // Home has not run in this process yet.
        Config config = Config.load(this);
        Palette home = Palette.current();
        scheme = Palette.dark(home != null ? home : Palette.fromBackground(config.backgroundColor));
        palette = new int[]{scheme.primary, scheme.tertiary, scheme.secondary};
        M3Skin.apply(content, scheme, true, false);
        colorClock(content, scheme);
        styleCards();
        colorIndex = random.nextInt(palette.length);
        setGlow(palette[colorIndex]);

        style = Screensaver.style(getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE));
        if (Screensaver.SHAPES.equals(style)) {
            shapes = new ExpressiveBackdrop(this, config.animations, config.reducedMotion,
                    config.advancedEffects);
            shapes.setPalette(scheme);
            shapes.setBackgroundColor(0xFF000000);
            shapes.setAlpha(SHAPES_ALPHA);
            ((ViewGroup) findViewById(R.id.dreamRoot)).addView(shapes, 0);
            ambient.setVisibility(View.GONE);
        } else if (Screensaver.MINIMAL.equals(style)) {
            ambient.setVisibility(View.GONE);
        } else if (Screensaver.LARGE.equals(style)) {
            scaleText(content, LARGE_SCALE);
        }
        showInfo();
        ImageView art = findViewById(R.id.dreamNowArt);
        float density = getResources().getDisplayMetrics().density;
        art.setClipToOutline(true);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8 * density);
            }
        });
        music = new NowPlaying(this, this::showTrack);
    }

    /** The track playing in the background, or nothing; the line fades in and out with it. */
    private void showTrack(NowPlaying.Track track) {
        View line = findViewById(R.id.dreamNow);
        if (track == null || !track.playing) {
            if (line.getVisibility() == View.VISIBLE) {
                line.animate().alpha(0f).setDuration(600).withEndAction(() -> line.setVisibility(View.GONE)).start();
            }
            return;
        }
        ImageView art = findViewById(R.id.dreamNowArt);
        if (track.art != null) {
            art.setImageTintList(null);
            art.setBackgroundColor(0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageBitmap(track.art);
        } else {
            art.setImageResource(R.drawable.ic_music);
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            art.setImageTintList(ColorStateList.valueOf(scheme.onSecondaryContainer));
            art.setBackgroundColor(scheme.secondaryContainer);
        }
        SpannableStringBuilder text = new SpannableStringBuilder(track.title);
        text.setSpan(new ForegroundColorSpan(scheme.onSurface), 0, text.length(), 0);
        if (!track.artist.isEmpty()) {
            int start = text.length();
            text.append("  ·  ").append(track.artist);
            text.setSpan(new ForegroundColorSpan(scheme.onSurfaceVariant), start, text.length(), 0);
        }
        ((TextView) findViewById(R.id.dreamNowText)).setText(text);
        if (line.getVisibility() != View.VISIBLE) {
            line.setAlpha(0f);
            line.setVisibility(View.VISIBLE);
            line.animate().alpha(1f).setDuration(600).start();
        }
    }

    /** Every text in the clock {@code factor} times as large, so the layout keeps its proportions. */
    private static void scaleText(View v, float factor) {
        if (v instanceof TextView) {
            TextView text = (TextView) v;
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.getTextSize() * factor);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) scaleText(g.getChildAt(i), factor);
        }
    }

    /** The page's resting opacity: dimmer in the minimal style. */
    private float pageAlpha() {
        return Screensaver.MINIMAL.equals(style) ? MINIMAL_ALPHA : 1f;
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        page.setAlpha(0f);
        page.animate().alpha(pageAlpha()).setDuration(1200).start();
        if (shapes != null) shapes.start();
        music.start();
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        if (shapes != null) shapes.stop();
        music.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (shapes != null) shapes.stop();
        if (music != null) music.stop();
        super.onDetachedFromWindow();
    }

    /** The big time (the largest text) in primary, as on the home screen. */
    private static void colorClock(View v, Palette p) {
        if (v instanceof TextClock && ((TextView) v).getTextSize() > 150) {
            ((TextView) v).setTextColor(p.primary);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) colorClock(g.getChildAt(i), p);
        }
    }

    /** Glass cards in the scheme's container colour, labels and details in onSurfaceVariant. */
    private void styleCards() {
        float density = getResources().getDisplayMetrics().density;
        for (int id : new int[]{R.id.dreamPrayerCard, R.id.dreamAirCard}) {
            GradientDrawable glass = new GradientDrawable();
            glass.setCornerRadius(36 * density);
            glass.setColor((CARD_ALPHA << 24) | (scheme.surfaceContainerHigh & 0x00FFFFFF));
            glass.setStroke(Math.max(1, Math.round(density)), (0x40 << 24) | (scheme.outlineVariant & 0x00FFFFFF));
            findViewById(id).setBackground(glass);
        }
        tint(findViewById(R.id.dreamCards), scheme);
        ShapeBadge prayerBadge = findViewById(R.id.dreamPrayerBadge);
        prayerBadge.setIcon(getDrawable(R.drawable.ic_w_clear_night));
        prayerBadge.setColors(scheme.primaryContainer, scheme.onPrimaryContainer);
        ((ShapeBadge) findViewById(R.id.dreamAirBadge)).setIcon(getDrawable(R.drawable.ic_air));
    }

    /** Values in onSurface, the rest in onSurfaceVariant, by text size: the values are the large ones. */
    private static void tint(View v, Palette p) {
        if (v instanceof TextView) {
            TextView text = (TextView) v;
            text.setTextColor(text.getTextSize() > 60 ? p.onSurface : p.onSurfaceVariant);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tint(g.getChildAt(i), p);
        }
    }

    /** The Hijri date, the next prayer and the air quality; a card with nothing to say hides. */
    private void showInfo() {
        LocalDateTime now = LocalDateTime.now();
        Hijri.Date hijri = Hijri.toHijri(LocalDate.now(), Hijri.offset(this));
        ((TextView) findViewById(R.id.dreamHijri)).setText(getString(R.string.dream_hijri, hijri.day,
                hijri.monthName(), hijri.year));

        List<PrayerTimes.Time> next = PrayerTimes.upcoming(this, now);
        View prayerCard = findViewById(R.id.dreamPrayerCard);
        prayerCard.setVisibility(next.isEmpty() ? View.GONE : View.VISIBLE);
        if (!next.isEmpty()) {
            PrayerTimes.Time t = next.get(0);
            ((TextView) findViewById(R.id.dreamPrayerValue)).setText(t.prayer.title);
            ((TextView) findViewById(R.id.dreamPrayerDetail)).setText(getString(R.string.dream_prayer_detail,
                    PrayerTimes.clock(this, t.at), PrayerTimes.until(now, t.at)));
        }

        AirQuality.Report air = AirQuality.cached(this);
        boolean fresh = air != null && System.currentTimeMillis() - air.fetchedAt < AIR_FRESH_MS;
        findViewById(R.id.dreamAirCard).setVisibility(fresh ? View.VISIBLE : View.GONE);
        if (fresh) {
            AirQuality.Level level = AirQuality.level(air.aqi);
            ((TextView) findViewById(R.id.dreamAirValue)).setText(String.valueOf(air.aqi));
            ((TextView) findViewById(R.id.dreamAirDetail)).setText(getString(R.string.dream_air_detail,
                    getString(AirQuality.label(level)), air.pm25));
            int[] colors = WidgetShelf.airAlertColors(air.aqi);
            ((ShapeBadge) findViewById(R.id.dreamAirBadge)).setColors(colors[0], colors[1]);
        }
    }

    /** A few dp off the middle, each minute somewhere else, on a slow ease: the clock never leaves the top. */
    private void shiftPage() {
        float reach = SHIFT_DP * getResources().getDisplayMetrics().density;
        page.animate().translationX((random.nextFloat() * 2 - 1) * reach)
                .translationY((random.nextFloat() * 2 - 1) * reach).setDuration(3000).start();
    }

    private void shiftGlow() {
        if (!Screensaver.GLOW.equals(style) && !Screensaver.LARGE.equals(style)) return;   // no glow on screen
        int from = glowColor;
        colorIndex = (colorIndex + 1) % palette.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, palette[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
