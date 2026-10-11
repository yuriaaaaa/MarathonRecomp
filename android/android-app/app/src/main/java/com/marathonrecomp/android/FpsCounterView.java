package com.marathonrecomp.android;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import java.util.Locale;

/**
 * A small frame-rate readout at the top of the screen. It counts the frames the game actually
 * presents (GameActivity.nativeGetPresentCount, incremented after each swapchain present in
 * video.cpp), so it shows the game's frame rate rather than the screen's refresh rate.
 * It doesn't take touches, so the controls underneath still work.
 */
final class FpsCounterView extends TextView {
    private static final long POLL_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastCount = -1, lastNanos;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            if (running)
                handler.postDelayed(this, POLL_MS);
        }
    };

    FpsCounterView(Context context) {
        super(context);
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        setTextColor(0xFFF2C14E);
        setGravity(Gravity.CENTER);
        setMinWidth(dp(118));
        setText("… FPS");
        setClickable(false);
        setFocusable(false);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(0xB30A0A0E);
        background.setStroke(Math.max(1, dp(1)), 0x33F2C14E);
        setBackground(background);
        setPadding(dp(9), dp(4), dp(9), dp(4));
    }

    void start() {
        if (running)
            return;
        running = true;
        lastCount = -1;
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void update() {
        long count;
        try {
            count = GameActivity.presentCount();
        } catch (Throwable e) {
            setText("… FPS");
            return;
        }
        long now = System.nanoTime();
        if (lastCount >= 0 && now > lastNanos) {
            double fps = Math.max(0, count - lastCount) / ((now - lastNanos) / 1e9);
            setText(fps >= 0.5
                    ? String.format(Locale.ROOT, "%.0f FPS · %.1f ms", fps, 1000.0 / fps)
                    : "0 FPS");
        }
        lastCount = count;
        lastNanos = now;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
