package com.marathonrecomp.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Sonic '06 Mod Manager's tweaks and patches, plus Marathon Recompiled's built-in codes.
 * Choices are saved with the mods and applied when the game starts.
 */
public class PatchesActivity extends Activity {
    private final ModManager manager = new ModManager();
    private LinearLayout content;
    private TextView summary;
    private TextView versionLine;
    private Button updateButton;
    private boolean showUnavailable;
    private boolean checking;

    static PatchLibrary.Source assets(AssetManager assets) {
        return new PatchLibrary.Source() {
            @Override
            public List<String> names() throws IOException {
                String[] names = assets.list("patches");
                return names == null ? new ArrayList<>() : Arrays.asList(names);
            }

            @Override
            public InputStream open(String name) throws IOException {
                return assets.open("patches/" + name);
            }
        };
    }

    /** The official patches: a set downloaded with Check for updates, else the one bundled in the app. */
    static PatchLibrary.Source officialSet(Context context) {
        return PatchUpdater.useDownloaded(context)
                ? PatchLibrary.folder(PatchUpdater.folder(context))
                : assets(context.getAssets());
    }

    static void loadLibrary(Activity activity, ModManager manager) {
        manager.library = PatchLibrary.load(officialSet(activity), ModManager.USER_PATCHES);
    }

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
        TextView title = text("Patches", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        updateButton = button("Check for updates", 0xFF1565C0);
        updateButton.setOnClickListener(v -> checkForUpdates());
        header.addView(updateButton);
        Button done = button("Done", 0xFF2E7D32);
        done.setOnClickListener(v -> finish());
        header.addView(done);
        column.addView(header);

        TextView help = text("Tweaks and patches from the Sonic '06 Mod Manager, and the codes built into "
                + "Marathon Recompiled. They're applied when you tap Start, on top of your mods, without "
                + "changing the installed game. Extra .mlua patches can go in MarathonRecomp/patches.", 14, 0xFFB0BEC5);
        help.setPadding(0, dp(10), 0, dp(6));
        column.addView(help);

        versionLine = text("", 13, 0xFF90A4AE);
        versionLine.setPadding(0, 0, 0, dp(6));
        column.addView(versionLine);

        summary = text("", 14, 0xFFE0E0E0);
        summary.setPadding(0, 0, 0, dp(6));
        column.addView(summary);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        column.addView(content);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);
        scroll.addView(column);
        setContentView(scroll);

        manager.load();
        loadLibrary(this, manager);
        render();
        updateVersionLine();
    }

    private void updateVersionLine() {
        PatchUpdater.Version v = PatchUpdater.current(this);
        String text = "Mod Manager patches: the set from " + date(v.date) + " (" + v.sha.substring(0, Math.min(7, v.sha.length()))
                + ")" + (v.bundled ? ", bundled with the app" : ", downloaded from GitHub");
        if (v.checked > 0)
            text += " · last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(v.checked * 1000));
        versionLine.setText(text);
    }

    private static String date(long seconds) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(seconds * 1000));
    }

    /** Like the Mod Manager's "Fetch the latest patches": checks Knuxfan24/Sonic-06-Mod-Manager-Patches. */
    private void checkForUpdates() {
        if (checking)
            return;
        checking = true;
        updateButton.setEnabled(false);
        updateButton.setText("Checking…");
        Set<String> workedBefore = new HashSet<>();
        for (PatchEngine.Patch patch : manager.library)
            if (patch.supported)
                workedBefore.add(patch.id.toLowerCase(Locale.ROOT));
        PatchLibrary.Source currentSet = officialSet(this);
        Context context = getApplicationContext();
        new Thread(() -> {
            PatchUpdater.Result result = null;
            String error = null;
            try {
                result = PatchUpdater.update(context, currentSet, (message, done, total) -> {
                    String label = total > 0 ? message + " " + (done * 100 / total) + "%"
                            : done > 0 ? message + " " + ModBrowserActivity.size(done) : message;
                    runOnUiThread(() -> updateButton.setText(label));
                    return !isDestroyed();
                });
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            PatchUpdater.Result r = result;
            String failure = error;
            runOnUiThread(() -> {
                checking = false;
                if (isDestroyed())
                    return;
                updateButton.setEnabled(true);
                updateButton.setText("Check for updates");
                if (r == null) {
                    new AlertDialog.Builder(this)
                            .setTitle("Couldn't check for patches")
                            .setMessage(failure + "\n\nCheck the connection and try again. Your patches haven't changed.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                loadLibrary(this, manager);
                render();
                updateVersionLine();
                showUpdateResult(r, workedBefore);
            });
        }).start();
    }

    private void showUpdateResult(PatchUpdater.Result r, Set<String> workedBefore) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setPositiveButton("OK", null);
        if (r.upToDate) {
            dialog.setTitle("Patches are up to date")
                    .setMessage("You have the latest Mod Manager patches: the set from " + date(r.version.date) + ".");
        } else {
            Map<String, PatchEngine.Patch> byId = new HashMap<>();
            for (PatchEngine.Patch patch : manager.library)
                byId.put(patch.id.toLowerCase(Locale.ROOT), patch);
            StringBuilder message = new StringBuilder("Updated to the set from " + date(r.version.date) + ".");
            appendList(message, "New", r.added, byId);
            appendList(message, "Changed", r.changed, byId);
            appendList(message, "Removed", r.removed, null);

            List<String> nowWork = new ArrayList<>(), stopped = new ArrayList<>();
            for (PatchEngine.Patch patch : manager.library) {
                String key = patch.id.toLowerCase(Locale.ROOT);
                if (patch.supported && !workedBefore.contains(key))
                    nowWork.add(patch.title);
                else if (!patch.supported && workedBefore.contains(key) && manager.patches.contains(patch.id))
                    stopped.add(patch.title);
            }
            if (!nowWork.isEmpty())
                message.append("\n\nNow available here: ").append(join(nowWork));
            if (!stopped.isEmpty())
                message.append("\n\n⚠ No longer usable (they now change game code), so they'll be skipped: ")
                        .append(join(stopped));
            message.append("\n\nYour on/off choices are kept. Changed patches are reapplied on the next Start.");
            dialog.setTitle("Patches updated").setMessage(message);
        }
        if (!r.version.bundled)
            dialog.setNeutralButton("Use the app's set", (d, which) -> {
                PatchUpdater.reset(this);
                loadLibrary(this, manager);
                render();
                updateVersionLine();
            });
        dialog.show();
    }

    private static void appendList(StringBuilder message, String label, List<String> ids, Map<String, PatchEngine.Patch> byId) {
        if (ids.isEmpty())
            return;
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            PatchEngine.Patch patch = byId != null ? byId.get(id.toLowerCase(Locale.ROOT)) : null;
            names.add(patch != null && !patch.title.isEmpty() ? patch.title : id);
        }
        message.append("\n\n").append(label).append(" (").append(ids.size()).append("): ").append(join(names));
    }

    private static String join(List<String> names) {
        int shown = Math.min(names.size(), 12);
        StringBuilder out = new StringBuilder(String.join(", ", names.subList(0, shown)));
        if (names.size() > shown)
            out.append(" and ").append(names.size() - shown).append(" more");
        return out.toString();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            manager.save();
        } catch (IOException ignored) {
        }
    }

    private void render() {
        content.removeAllViews();

        // Tweaks
        section("Camera and tweaks", "The Mod Manager's Tweaks tab. Camera distance is applied after camera type.");
        content.addView(cameraTypeRow());
        content.addView(stepper("Camera distance", "How far back the camera sits.", () -> manager.tweaks.cameraDistance,
                value -> manager.tweaks.cameraDistance = value, 650, 100, 2000, 25, value -> metres(value)));
        content.addView(stepper("Camera height", "How high the camera sits above the player.", () -> manager.tweaks.cameraHeight,
                value -> manager.tweaks.cameraHeight = value, 70, 0, 400, 5, value -> metres(value)));
        content.addView(stepper("Amy's hammer range", "How far Amy's hammer reaches.", () -> manager.tweaks.hammerRange,
                value -> manager.tweaks.hammerRange = value, 50, 10, 500, 5, value -> metres(value)));
        content.addView(stepper("Starting rings", "Rings every character starts with.", () -> manager.tweaks.startingRings,
                value -> manager.tweaks.startingRings = value, 0, 0, 999, 5, String::valueOf));

        // Built-in codes
        section("Built-in codes", "Part of Marathon Recompiled itself, so they work like the Mod Manager patches of "
                + "the same name, including ones that change the game's code.");
        for (String code : ModManager.CODES) {
            CheckBox box = checkBox(ModsActivity.friendlyName(code), manager.codes.contains(code));
            box.setOnCheckedChangeListener((view, checked) -> {
                if (checked)
                    manager.codes.add(code);
                else
                    manager.codes.remove(code);
                updateSummary();
            });
            content.addView(box);
        }

        // Patches
        List<PatchEngine.Patch> available = new ArrayList<>(), unavailable = new ArrayList<>();
        for (PatchEngine.Patch patch : manager.library)
            (patch.supported ? available : unavailable).add(patch);
        section("Mod Manager patches", available.size() + " of the Mod Manager's patches work in this build. "
                + "They change game data and scripts; patches that change the game's code are listed at the end.");
        for (PatchEngine.Patch patch : available)
            content.addView(patchRow(patch));

        Button toggle = button((showUnavailable ? "Hide" : "Show") + " the " + unavailable.size()
                + " patches that can't be used", 0xFF37474F);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.topMargin = dp(14);
        toggle.setLayoutParams(toggleParams);
        toggle.setOnClickListener(v -> {
            showUnavailable = !showUnavailable;
            render();
        });
        content.addView(toggle);
        if (showUnavailable) {
            for (PatchEngine.Patch patch : unavailable) {
                String note = PatchLibrary.isBuiltIn(patch.id)
                        ? "Available above as a built-in code."
                        : patch.reason;
                TextView line = text(patch.title + " \u2014 " + note, 13,
                        PatchLibrary.isBuiltIn(patch.id) ? 0xFF81C784 : 0xFF90A4AE);
                line.setPadding(0, dp(6), 0, 0);
                content.addView(line);
            }
        }
        updateSummary();
    }

    private void section(String title, String description) {
        TextView heading = text(title, 22, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, dp(22), 0, dp(4));
        content.addView(heading);
        TextView details = text(description, 14, 0xFFB0BEC5);
        details.setPadding(0, 0, 0, dp(8));
        content.addView(details);
    }

    private View cameraTypeRow() {
        LinearLayout box = card();
        TextView name = text("Camera type", 16, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(name);
        box.addView(text("How the camera follows the player: the released game, the Tokyo Game Show 2006 "
                + "demo (closer, lower, snappier), or the E3 2006 demo.", 13, 0xFF90A4AE));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        String[] labels = { "Retail", "Tokyo Game Show", "E3" };
        for (int i = 0; i < labels.length; i++) {
            int type = i;
            boolean selected = manager.tweaks.cameraType == type;
            Button option = button(labels[i], selected ? 0xFF1565C0 : 0xFF37474F);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            option.setLayoutParams(params);
            option.setOnClickListener(v -> {
                manager.tweaks.cameraType = type;
                render();
            });
            row.addView(option);
        }
        box.addView(row);
        return box;
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface Formatter {
        String format(int value);
    }

    private View stepper(String label, String description, IntGetter getter, IntSetter setter, int defaultValue,
                         int min, int max, int step, Formatter formatter) {
        LinearLayout box = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 16, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(name);
        info.addView(text(description + " Default " + formatter.format(defaultValue) + ".", 13, 0xFF90A4AE));
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView value = text(formatter.format(getter.get()), 16, getter.get() == defaultValue ? 0xFFE0E0E0 : 0xFF64B5F6);
        value.setMinWidth(dp(72));
        value.setGravity(Gravity.CENTER);
        Button minus = small("\u2212");
        Button plus = small("+");
        Button reset = small("\u21BA");
        Runnable refresh = () -> {
            value.setText(formatter.format(getter.get()));
            value.setTextColor(getter.get() == defaultValue ? 0xFFE0E0E0 : 0xFF64B5F6);
            updateSummary();
        };
        minus.setOnClickListener(v -> {
            setter.set(Math.max(min, getter.get() - step));
            refresh.run();
        });
        plus.setOnClickListener(v -> {
            setter.set(Math.min(max, getter.get() + step));
            refresh.run();
        });
        reset.setOnClickListener(v -> {
            setter.set(defaultValue);
            refresh.run();
        });
        row.addView(minus);
        row.addView(value);
        row.addView(plus);
        row.addView(reset);
        box.addView(row);
        return box;
    }

    private View patchRow(PatchEngine.Patch patch) {
        LinearLayout box = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox check = new CheckBox(this);
        check.setChecked(manager.patches.contains(patch.id));
        check.setOnCheckedChangeListener((view, checked) -> {
            if (checked)
                manager.patches.add(patch.id);
            else
                manager.patches.remove(patch.id);
            updateSummary();
        });
        row.addView(check);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(patch.title, 16, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(name);
        String details = patch.blurb.isEmpty() ? "" : patch.blurb;
        if (!patch.author.isEmpty())
            details += (details.isEmpty() ? "" : " ") + "By " + patch.author + ".";
        if (!details.isEmpty())
            info.addView(text(details, 13, 0xFF90A4AE));
        info.setOnClickListener(v -> check.toggle());
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        box.addView(row);
        return box;
    }

    private void updateSummary() {
        int patches = manager.enabledPatches().size();
        summary.setText(patches + (patches == 1 ? " patch" : " patches") + " on \u00b7 "
                + manager.codes.size() + (manager.codes.size() == 1 ? " code" : " codes") + " on \u00b7 tweaks "
                + (manager.tweaks.any() ? "changed" : "at defaults"));
    }

    private static String metres(int hundredths) {
        return String.format(Locale.ROOT, "%.2f m", hundredths / 100.0);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(10), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1A222C);
        background.setCornerRadius(dp(8));
        box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        box.setLayoutParams(params);
        return box;
    }

    private CheckBox checkBox(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(0xFFE0E0E0);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.setChecked(checked);
        return box;
    }

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
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(8));
        view.setBackground(background);
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
        return view;
    }

    private Button small(String label) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF37474F);
        background.setCornerRadius(dp(6));
        view.setBackground(background);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(6));
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
