package com.marathonrecomp.android;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Custom Vulkan drivers in the AdrenoTools format (Turnip and others): a .zip with a meta.json
 * and the driver's .so. Imported drivers live in the app's private files; the selected one is
 * loaded by the game through libadrenotools (os/android/gpu_android.cpp), falling back to the
 * system driver if it can't start.
 */
final class DriverManager {
    static final class Driver {
        String id = "", name = "", description = "", author = "", version = "", vendor = "", driverVersion = "";
        File library;
    }

    /** What happened the last time the game started (written by the game). */
    static final class LastStart {
        String status = "";   // system, custom_ok or custom_failed
        String driver = "";
        String error = "";
        String device = "";   // GPU name the driver reported
    }

    static final class ImportException extends IOException {
        ImportException(String message) {
            super(message);
        }
    }

    static File folder(Context context) {
        return new File(context.getFilesDir(), "drivers");
    }

    static File lastStartFile(Context context) {
        return new File(folder(context), "last_start.txt");
    }

    private static File activeFile(Context context) {
        return new File(folder(context), "active.txt");
    }

    static List<Driver> list(Context context) {
        List<Driver> out = new ArrayList<>();
        File[] dirs = folder(context).listFiles(File::isDirectory);
        if (dirs == null)
            return out;
        for (File dir : dirs) {
            Driver d = read(dir);
            if (d != null)
                out.add(d);
        }
        out.sort((a, b) -> b.id.compareTo(a.id));
        return out;
    }

    private static Driver read(File dir) {
        try {
            JSONObject json = new JSONObject(readText(new File(dir, "driver.json")));
            Driver d = new Driver();
            d.id = dir.getName();
            d.name = json.optString("name", "");
            d.description = json.optString("description", "");
            d.author = json.optString("author", "");
            d.version = json.optString("version", "");
            d.vendor = json.optString("vendor", "");
            d.driverVersion = json.optString("driverVersion", "");
            d.library = new File(dir, json.optString("library", ""));
            return d.library.isFile() ? d : null;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** The selected driver, or null for the phone's own driver. */
    static Driver active(Context context) {
        String id;
        try {
            id = readText(activeFile(context)).trim();
        } catch (IOException e) {
            return null;
        }
        if (id.isEmpty() || id.contains("/") || id.startsWith("."))
            return null;
        return read(new File(folder(context), id));
    }

    static void setActive(Context context, Driver driver) throws IOException {
        if (driver == null) {
            activeFile(context).delete();
            return;
        }
        folder(context).mkdirs();
        File temporary = new File(folder(context), "active.txt.tmp");
        writeText(temporary, driver.id);
        if (!temporary.renameTo(activeFile(context)))
            throw new IOException("Couldn't save the driver choice");
    }

    static void remove(Context context, Driver driver) throws IOException {
        Driver active = active(context);
        if (active != null && active.id.equals(driver.id))
            setActive(context, null);
        ModInstaller.deleteRecursively(new File(folder(context), driver.id));
    }

    static LastStart lastStart(Context context) {
        try {
            LastStart s = new LastStart();
            for (String line : readText(lastStartFile(context)).split("\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0)
                    continue;
                String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
                switch (key) {
                    case "status": s.status = value; break;
                    case "driver": s.driver = value; break;
                    case "error": s.error = value; break;
                    case "device": s.device = value; break;
                }
            }
            return s.status.isEmpty() ? null : s;
        } catch (IOException e) {
            return null;
        }
    }

    /** Imports an AdrenoTools .zip picked by the user. */
    static Driver importZip(Context context, Uri uri) throws IOException {
        // Pass 1: meta.json and the list of files.
        String metaText = null;
        List<String> libraries = new ArrayList<>();
        int entries = 0;
        try (ZipInputStream zip = open(context, uri)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entry.isDirectory())
                    continue;
                String name = entry.getName();
                String base = baseName(name).toLowerCase(Locale.ROOT);
                if (base.equals("meta.json") && metaText == null)
                    metaText = new String(readAll(zip), StandardCharsets.UTF_8);
                else if (base.endsWith(".so"))
                    libraries.add(name);
            }
        } catch (ZipException e) {
            throw new ImportException("That file isn't a valid .zip.");
        }
        if (entries == 0)
            throw new ImportException("That file isn't a .zip (or it's empty).");
        if (metaText == null)
            throw new ImportException("There's no meta.json in this .zip, so it isn't an AdrenoTools driver package. "
                    + "Turnip releases made for AdrenoTools include one.");
        JSONObject meta;
        try {
            meta = new JSONObject(metaText);
        } catch (JSONException e) {
            throw new ImportException("The driver's meta.json can't be read.");
        }
        int minApi = meta.optInt("minApi", meta.optInt("minAPI", 0));
        if (minApi > Build.VERSION.SDK_INT)
            throw new ImportException("This driver needs Android API " + minApi + "; this phone has API "
                    + Build.VERSION.SDK_INT + ".");
        String libraryName = meta.optString("libraryName", meta.optString("libName", "")).trim();
        if (libraries.isEmpty())
            throw new ImportException("There's no driver library (.so) in this .zip.");
        String main = null;
        for (String path : libraries)
            if (!libraryName.isEmpty() && baseName(path).equalsIgnoreCase(libraryName))
                main = path;
        if (main == null && !libraryName.isEmpty())
            throw new ImportException("meta.json names " + libraryName + ", but it isn't in the .zip.");
        if (main == null)
            for (String path : libraries) {
                String lower = baseName(path).toLowerCase(Locale.ROOT);
                if (main == null && (lower.contains("vulkan") || lower.contains("freedreno") || lower.contains("turnip")))
                    main = path;
            }
        if (main == null)
            main = libraries.get(0);

        Driver d = new Driver();
        d.name = meta.optString("name", "").trim();
        d.description = meta.optString("description", "").trim();
        d.author = meta.optString("author", "").trim();
        d.version = meta.optString("packageVersion", meta.optString("version", "")).trim();
        d.vendor = meta.optString("vendor", "").trim();
        d.driverVersion = meta.optString("driverVersion", "").trim();
        if (d.name.isEmpty())
            d.name = d.description.isEmpty() ? baseName(main) : d.description;
        d.id = System.currentTimeMillis() + "-" + slug(d.name + " " + d.version);

        // Pass 2: every .so, with its original name (multi-file packages load companions by name).
        File dir = new File(folder(context), d.id);
        dir.mkdirs();
        String mainBase = baseName(main);
        try (ZipInputStream zip = open(context, uri)) {
            ZipEntry entry;
            Map<String, Boolean> seen = new HashMap<>();
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".so"))
                    continue;
                String base = baseName(entry.getName());
                if (seen.put(base, true) != null || base.startsWith("."))
                    continue;
                boolean isMain = entry.getName().equals(main);
                copyLibrary(zip, new File(dir, base), isMain);
            }
            d.library = new File(dir, mainBase);
            if (!d.library.isFile())
                throw new ImportException("The driver library didn't unpack.");
            JSONObject json = new JSONObject();
            json.put("name", d.name);
            json.put("description", d.description);
            json.put("author", d.author);
            json.put("version", d.version);
            json.put("vendor", d.vendor);
            json.put("driverVersion", d.driverVersion);
            json.put("library", mainBase);
            writeText(new File(dir, "driver.json"), json.toString(2));
        } catch (IOException | JSONException e) {
            ModInstaller.deleteRecursively(dir);
            if (e instanceof ImportException)
                throw (ImportException) e;
            throw new ImportException("Couldn't unpack the driver: " + e.getMessage());
        }
        return d;
    }

    /** Copies a .so, checking it's an ELF file (and, for the driver itself, a 64-bit ARM shared library). */
    private static void copyLibrary(InputStream in, File destination, boolean main) throws IOException {
        byte[] header = new byte[20];
        int read = 0;
        while (read < header.length) {
            int n = in.read(header, read, header.length - read);
            if (n < 0)
                throw new ImportException(destination.getName() + " is cut short.");
            read += n;
        }
        if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F')
            throw new ImportException(destination.getName() + " isn't a library (not an ELF file).");
        if (main) {
            int elfClass = header[4] & 0xFF;
            int type = (header[16] & 0xFF) | (header[17] & 0xFF) << 8;
            int machine = (header[18] & 0xFF) | (header[19] & 0xFF) << 8;
            if (elfClass != 2 || machine != 183 || type != 3)
                throw new ImportException(destination.getName() + " isn't a 64-bit ARM (arm64-v8a) driver.");
        }
        try (OutputStream out = new FileOutputStream(destination)) {
            out.write(header);
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0)
                out.write(buffer, 0, n);
        }
    }

    private static ZipInputStream open(Context context, Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null)
            throw new ImportException("Couldn't open that file.");
        return new ZipInputStream(new BufferedInputStream(in, 65536));
    }

    private static String baseName(String path) {
        String p = path.replace('\\', '/');
        return p.substring(p.lastIndexOf('/') + 1);
    }

    private static String slug(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 40)
            s = s.substring(0, 40);
        return s.isEmpty() ? "driver" : s;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0)
            out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
