package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Home's now playing bar, between the widget shelf and the dock: music playing in the background (Spotify,
 * YouTube Music) slides it up, with play or pause and a cross that stops the music and slides it away. OK on
 * the track opens the player. Up goes to the widgets and Down to the dock; the dock's Up comes here first.
 */
final class NowPlayingBar {

    /** Where focus goes from the bar: the widget shelf above, the dock below. */
    interface Neighbours {
        void up();

        void down();
    }

    private static final long SLIDE_MS = 380;
    private final View frame, bar, info, toggle, close;
    private final ImageView art, toggleIcon;
    private final TextView title, subtitle;
    private final NowPlaying player;
    private final Neighbours neighbours;
    private final float density;
    private final boolean animations;
    private Palette palette = Palette.currentOrBaseline();
    private boolean shown;
    private float scrollOffset;

    NowPlayingBar(ViewGroup parent, boolean animations, Neighbours neighbours) {
        this.animations = animations;
        this.neighbours = neighbours;
        density = parent.getResources().getDisplayMetrics().density;
        frame = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.now_playing_bar, parent, false);
        parent.addView(frame);
        bar = frame.findViewById(R.id.nowBar);
        info = frame.findViewById(R.id.nowInfo);
        toggle = frame.findViewById(R.id.nowToggle);
        close = frame.findViewById(R.id.nowClose);
        art = frame.findViewById(R.id.nowArt);
        toggleIcon = (ImageView) toggle;
        title = frame.findViewById(R.id.nowTitle);
        subtitle = frame.findViewById(R.id.nowSubtitle);
        art.setClipToOutline(true);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 10 * density);
            }
        });
        player = new NowPlaying(parent.getContext(), this::show);

        info.setOnClickListener(v -> player.open());
        toggle.setOnClickListener(v -> player.togglePlayPause());
        close.setOnClickListener(v -> {
            player.close();
            neighbours.down();
        });
        View.OnKeyListener keys = (v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                neighbours.up();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                neighbours.down();
                return true;
            }
            // The bar's ends: focus stays on it rather than jumping to a tile.
            return code == KeyEvent.KEYCODE_DPAD_LEFT && v == info || code == KeyEvent.KEYCODE_DPAD_RIGHT && v == close;
        };
        for (View v : new View[]{info, toggle, close}) {
            v.setOnKeyListener(keys);
            v.setOnFocusChangeListener((view, focused) -> paintFocus());
        }
        setPalette(palette);
    }

    void start() {
        player.start();
    }

    void stop() {
        player.stop();
    }

    /** Puts the bar in the gap between the shelf and the dock (in the frame both share). */
    void place(int gapTop, int gapBottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) frame.getLayoutParams();
        int top = gapTop + Math.max(0, (gapBottom - gapTop - lp.height) / 2);
        if (lp.topMargin != top) {
            lp.topMargin = top;
            frame.setLayoutParams(lp);
        }
    }

    /** Scrolls away with the grid, as the shelf does. */
    void onGridScrolled(int offset) {
        scrollOffset = offset;
        frame.setTranslationY(-offset);
    }

    boolean shown() {
        return shown && scrollOffset == 0;
    }

    /** The dock's Up: onto play or pause, when the bar is up. */
    boolean focus() {
        if (!shown()) return false;
        return toggle.requestFocus();
    }

    void setPalette(Palette p) {
        palette = p;
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(32 * density);
        glass.setColor(p.surfaceContainerHigh);
        glass.setStroke(Math.max(1, Math.round(density / 2)), p.outlineVariant);
        bar.setBackground(glass);
        bar.setElevation(6 * density);
        title.setTextColor(p.onSurface);
        subtitle.setTextColor(p.onSurfaceVariant);
        paintFocus();
    }

    /** Focus as on the header pills: the focused control filled with primary. */
    private void paintFocus() {
        for (View v : new View[]{info, toggle, close}) {
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(26 * density);
            shape.setColor(v.isFocused() ? palette.primary : 0);
            v.setBackground(shape);
        }
        int infoText = info.isFocused() ? palette.onPrimary : palette.onSurface;
        title.setTextColor(infoText);
        subtitle.setTextColor(info.isFocused() ? palette.onPrimary : palette.onSurfaceVariant);
        toggleIcon.setImageTintList(ColorStateList.valueOf(toggle.isFocused() ? palette.onPrimary : palette.onSurface));
        ((ImageView) close).setImageTintList(ColorStateList.valueOf(close.isFocused() ? palette.onPrimary
                : palette.onSurfaceVariant));
    }

    private void show(NowPlaying.Track track) {
        if (track == null) {
            hide();
            return;
        }
        title.setText(track.title);
        subtitle.setText(track.artist.isEmpty() ? track.app : track.artist + " · " + track.app);
        toggleIcon.setImageResource(track.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (track.art != null) {
            art.setImageTintList(null);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setBackgroundColor(0);
            art.setImageBitmap(track.art);
        } else {
            art.setImageResource(R.drawable.ic_music);
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            art.setImageTintList(ColorStateList.valueOf(palette.onSecondaryContainer));
            art.setBackgroundColor(palette.secondaryContainer);
        }
        if (shown) return;
        shown = true;
        frame.setVisibility(View.VISIBLE);
        if (!animations) return;
        // Up from below the dock's edge, decelerating, as a sheet arrives.
        bar.setAlpha(0f);
        bar.setTranslationY(28 * density);
        bar.animate().alpha(1f).translationY(0f).setDuration(SLIDE_MS)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private void hide() {
        if (!shown) return;
        shown = false;
        // The music stopped under the remote: the dock takes focus before the bar goes.
        if (frame.hasFocus()) neighbours.down();
        if (!animations) {
            frame.setVisibility(View.GONE);
            return;
        }
        bar.animate().alpha(0f).translationY(28 * density).setDuration(SLIDE_MS)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    if (!shown) frame.setVisibility(View.GONE);
                }).start();
    }
}
