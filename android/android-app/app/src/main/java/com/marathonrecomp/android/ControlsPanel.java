package com.marathonrecomp.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** Touch controls and the FPS counter, shown on the launcher's Settings screen and in game (four-finger tap). */
final class ControlsPanel {
    interface Listener {
        void onChanged(ControlsSettings settings);
    }

    /** Builds the settings list; every change is saved and reported straight away. */
    static View build(Context context, ControlsSettings s, Listener listener) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        Runnable changed = () -> {
            s.save(context);
            if (listener != null)
                listener.onChanged(s);
        };

        column.addView(heading(context, "Touch controls"));
        column.addView(toggle(context, "Touch controls", "Xbox 360 layout on top of the game.",
                s.enabled, v -> { s.enabled = v; changed.run(); }));
        column.addView(slider(context, "Opacity", 15, 100, Math.round(s.opacity * 100),
                v -> v + "%", v -> { s.opacity = v / 100f; changed.run(); }));
        column.addView(slider(context, "Size", 70, 160, Math.round(s.scale * 100),
                v -> v + "%", v -> { s.scale = v / 100f; changed.run(); }));
        column.addView(toggle(context, "Vibrate on button press", "A short tick when you press a control.",
                s.buttonHaptics, v -> { s.buttonHaptics = v; changed.run(); }));
        column.addView(toggle(context, "Rumble", "The phone vibrates when the game rumbles the touch controller.",
                s.rumble, v -> { s.rumble = v; changed.run(); }));
        column.addView(toggle(context, "Hide while using a controller",
                "The controls fade out when a real controller is used and come back when you touch the screen.",
                s.hideWithController, v -> { s.hideWithController = v; changed.run(); }));
        column.addView(heading(context, "Display"));
        column.addView(toggle(context, "FPS counter",
                "Shows the game's frame rate and frame time at the top of the screen.",
                s.showFps, v -> { s.showFps = v; changed.run(); }));
        return column;
    }

    static AlertDialog show(Activity activity, ControlsSettings s, Runnable onExit, Listener listener) {
        ScrollView scroll = new ScrollView(activity);
        int pad = dp(activity, 20);
        View content = build(activity, s, listener);
        content.setPadding(pad, dp(activity, 8), pad, 0);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Settings")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .setNeutralButton("Exit game", (d, w) -> onExit.run())
                .create();
        dialog.show();
        return dialog;
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface IntFormat {
        String format(int value);
    }

    private static View toggle(Context context, String title, String description, boolean value, BoolSetter setter) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 10), 0, dp(context, 10));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(text(context, title, 16, Color.WHITE, true));
        info.addView(text(context, description, 13, 0xFF90A4AE, false));
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Switch toggle = new Switch(context);
        toggle.setChecked(value);
        toggle.setOnCheckedChangeListener((view, checked) -> setter.set(checked));
        row.addView(toggle);
        info.setOnClickListener(v -> toggle.toggle());
        return row;
    }

    private static View slider(Context context, String title, int min, int max, int value, IntFormat format,
                               IntSetter setter) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(context, 10), 0, dp(context, 6));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(text(context, title, 16, Color.WHITE, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView shown = text(context, format.format(value), 15, 0xFF64B5F6, false);
        header.addView(shown);
        box.addView(header);

        SeekBar bar = new SeekBar(context);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                shown.setText(format.format(progress + min));
                if (fromUser)
                    setter.set(progress + min);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        box.addView(bar);
        return box;
    }

    static TextView heading(Context context, String title) {
        TextView view = text(context, title, 13, 0xFFF2C14E, true);
        view.setAllCaps(true);
        view.setLetterSpacing(0.06f);
        view.setPadding(0, dp(context, 16), 0, dp(context, 2));
        return view;
    }

    private static TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold)
            view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    static String percent(float value) {
        return String.format(Locale.ROOT, "%d%%", Math.round(value * 100));
    }
}
