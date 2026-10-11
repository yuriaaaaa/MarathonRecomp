package com.marathonrecomp.android;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Touch control and overlay settings, shared by the launcher's Controls screen and the in-game panel.
 * Kept in a plain file rather than SharedPreferences: the game runs in its own process, and
 * SharedPreferences caches values per process.
 */
final class ControlsSettings {
    boolean enabled = true;          // show the on-screen controls
    float opacity = 0.6f;            // 0.15 .. 1.0
    float scale = 1.0f;              // 0.7 .. 1.6
    boolean buttonHaptics = true;    // short vibration on press
    boolean rumble = true;           // the game's controller rumble vibrates the phone
    boolean hideWithController = true; // fade out while a physical controller is in use
    boolean showFps = false;         // frame-rate readout at the top of the screen

    static ControlsSettings load(Context context) {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file(context))) {
            p.load(in);
        } catch (IOException ignored) {
            // 0.4.0 and 0.4.1 kept these in SharedPreferences.
            android.content.SharedPreferences old = context.getSharedPreferences("touch_controls", Context.MODE_PRIVATE);
            for (java.util.Map.Entry<String, ?> entry : old.getAll().entrySet())
                p.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        ControlsSettings s = new ControlsSettings();
        s.enabled = bool(p, "enabled", s.enabled);
        s.opacity = number(p, "opacity", s.opacity);
        s.scale = number(p, "scale", s.scale);
        s.buttonHaptics = bool(p, "buttonHaptics", s.buttonHaptics);
        s.rumble = bool(p, "rumble", s.rumble);
        s.hideWithController = bool(p, "hideWithController", s.hideWithController);
        s.showFps = bool(p, "showFps", s.showFps);
        return s;
    }

    void save(Context context) {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("opacity", String.valueOf(opacity));
        p.setProperty("scale", String.valueOf(scale));
        p.setProperty("buttonHaptics", String.valueOf(buttonHaptics));
        p.setProperty("rumble", String.valueOf(rumble));
        p.setProperty("hideWithController", String.valueOf(hideWithController));
        p.setProperty("showFps", String.valueOf(showFps));
        File target = file(context);
        File temporary = new File(target.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(temporary)) {
            p.store(out, "Touch controls");
        } catch (IOException e) {
            return;
        }
        temporary.renameTo(target);
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "touch_controls.properties");
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static float number(Properties p, String key, float fallback) {
        try {
            String value = p.getProperty(key);
            return value == null ? fallback : Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
