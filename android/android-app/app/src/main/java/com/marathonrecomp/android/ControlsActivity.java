package com.marathonrecomp.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** The launcher's Settings screen: touch controls, the FPS counter and the GPU driver. */
public class ControlsActivity extends Activity {
    private static final int PICK_DRIVER = 1;

    private LinearLayout driverSection;
    private boolean importing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        column.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Settings", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button done = button("Done", 0xFF2E7D32);
        done.setOnClickListener(v -> finish());
        header.addView(done);
        column.addView(header);

        TextView help = text("On-screen controls in the Xbox 360 layout: the game sees them as a controller, so a real "
                + "controller still works alongside them. In game, tap with four fingers to change the controls and "
                + "the FPS counter without leaving.", 14, 0xFFB0BEC5);
        help.setPadding(0, dp(10), 0, 0);
        column.addView(help);

        column.addView(ControlsPanel.build(this, ControlsSettings.load(this), null));

        driverSection = new LinearLayout(this);
        driverSection.setOrientation(LinearLayout.VERTICAL);
        column.addView(driverSection);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);
        scroll.addView(column);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDrivers();
    }

    // ------------------------------------------------------------------ GPU driver

    private void renderDrivers() {
        driverSection.removeAllViews();
        driverSection.addView(ControlsPanel.heading(this, "GPU driver"));
        TextView about = text("The game uses the phone's own Vulkan driver unless you pick another one here. You can "
                + "import Turnip (Mesa) driver packages made for AdrenoTools: a .zip with a meta.json inside, as used "
                + "by Android Switch and PC emulators. The S25's GPU is an Adreno 830, so pick a build that supports "
                + "Adreno 8xx. If a driver can't start, the game uses the phone's driver instead and this screen says "
                + "why. The choice applies the next time you tap Start.", 13, 0xFF90A4AE);
        about.setPadding(0, 0, 0, dp(8));
        driverSection.addView(about);

        List<DriverManager.Driver> drivers = DriverManager.list(this);
        DriverManager.Driver active = DriverManager.active(this);

        TextView last = lastStartLine(drivers);
        if (last != null)
            driverSection.addView(last);

        driverSection.addView(driverRow(null, active == null));
        for (DriverManager.Driver d : drivers)
            driverSection.addView(driverRow(d, active != null && active.id.equals(d.id)));

        Button importButton = button(importing ? "Importing…" : "Import driver (.zip)", 0xFF1565C0);
        importButton.setEnabled(!importing);
        importButton.setOnClickListener(v -> pickDriver());
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) importButton.getLayoutParams();
        params.setMarginStart(0);
        params.topMargin = dp(10);
        driverSection.addView(importButton, params);
    }

    private TextView lastStartLine(List<DriverManager.Driver> drivers) {
        DriverManager.LastStart s = DriverManager.lastStart(this);
        if (s == null)
            return null;
        String name = s.driver;
        for (DriverManager.Driver d : drivers)
            if (d.library.getAbsolutePath().equals(s.driver))
                name = label(d);
        if (name.contains("/"))
            name = new File(name).getName();
        String device = s.device.isEmpty() ? "" : " (" + s.device + ")";
        String line;
        int color;
        switch (s.status) {
            case "custom_ok":
                line = "Last start: " + name + device + ".";
                color = 0xFF81C784;
                break;
            case "custom_failed":
                line = "Last start: " + name + " couldn't start (" + s.error + "), so the game used the phone's driver"
                        + device + ".";
                color = 0xFFFFB74D;
                break;
            default:
                line = "Last start: the phone's driver" + device + ".";
                color = 0xFFB0BEC5;
                break;
        }
        TextView view = text(line, 13, color);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private View driverRow(DriverManager.Driver driver, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(12), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? 0xFF1E2D3B : 0xFF1A222C);
        background.setCornerRadius(dp(8));
        if (selected)
            background.setStroke(Math.max(1, dp(1)), 0xFF64B5F6);
        row.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        row.setLayoutParams(params);

        RadioButton radio = new RadioButton(this);
        radio.setChecked(selected);
        radio.setClickable(false);
        row.addView(radio);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(driver == null ? "Phone's driver" : label(driver), 16, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(name);
        String detail;
        if (driver == null) {
            detail = "The Qualcomm driver that came with Android. Known to work with this build.";
        } else {
            StringBuilder b = new StringBuilder();
            if (!driver.author.isEmpty())
                b.append("by ").append(driver.author);
            if (!driver.driverVersion.isEmpty())
                b.append(b.length() > 0 ? " · " : "").append(driver.driverVersion);
            if (!driver.description.isEmpty() && !driver.description.equals(driver.name))
                b.append(b.length() > 0 ? " · " : "").append(driver.description);
            b.append(b.length() > 0 ? " · " : "").append(driver.library.getName());
            detail = b.toString();
        }
        info.addView(text(detail, 13, 0xFF90A4AE));
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (driver != null) {
            Button remove = button("Remove", 0xFF6D4C41);
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Remove " + label(driver) + "?")
                    .setMessage("Deletes the imported driver from the app.")
                    .setPositiveButton("Remove", (d, w) -> {
                        try {
                            DriverManager.remove(this, driver);
                        } catch (IOException ignored) {
                        }
                        renderDrivers();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            row.addView(remove);
        }

        row.setOnClickListener(v -> select(driver));
        return row;
    }

    private void select(DriverManager.Driver driver) {
        try {
            DriverManager.setActive(this, driver);
        } catch (IOException e) {
            new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
        renderDrivers();
    }

    private static String label(DriverManager.Driver d) {
        return d.version.isEmpty() || d.name.contains(d.version) ? d.name : d.name + " " + d.version;
    }

    private void pickDriver() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip", "application/x-zip-compressed", "application/octet-stream" });
        try {
            startActivityForResult(intent, PICK_DRIVER);
        } catch (android.content.ActivityNotFoundException e) {
            new AlertDialog.Builder(this).setMessage("No file picker is available.").setPositiveButton("OK", null).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_DRIVER || resultCode != RESULT_OK || data == null || data.getData() == null)
            return;
        Uri uri = data.getData();
        importing = true;
        renderDrivers();
        new Thread(() -> {
            DriverManager.Driver imported = null;
            String error = null;
            try {
                imported = DriverManager.importZip(getApplicationContext(), uri);
            } catch (IOException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = e.toString();
            }
            DriverManager.Driver d = imported;
            String failure = error;
            runOnUiThread(() -> {
                importing = false;
                if (isDestroyed())
                    return;
                renderDrivers();
                if (d == null) {
                    new AlertDialog.Builder(this)
                            .setTitle("Couldn't import the driver")
                            .setMessage(failure)
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Driver imported")
                        .setMessage(label(d) + (d.author.isEmpty() ? "" : " by " + d.author)
                                + (d.driverVersion.isEmpty() ? "" : "\n" + d.driverVersion)
                                + "\n\nUse it from the next Start? You can switch back to the phone's driver here at any time.")
                        .setPositiveButton("Use it", (dialog, which) -> select(d))
                        .setNegativeButton("Not now", null)
                        .show();
            });
        }).start();
    }

    // ------------------------------------------------------------------ helpers

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, int color) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(8));
        view.setBackground(background);
        view.setPadding(dp(14), dp(6), dp(14), dp(6));
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(8));
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return ControlsPanel.dp(this, value);
    }
}
