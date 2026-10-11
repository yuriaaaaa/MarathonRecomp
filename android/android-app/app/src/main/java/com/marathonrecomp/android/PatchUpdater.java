package com.marathonrecomp.android;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Fetches the latest official Mod Manager patches, the way the Sonic '06 Mod Manager's
 * "Fetch the latest patches" does: the Knuxfan24/Sonic-06-Mod-Manager-Patches repository on GitHub.
 * A downloaded set replaces the one bundled in the app (it's kept in the app's private files).
 */
final class PatchUpdater {
    static final String REPO = "Knuxfan24/Sonic-06-Mod-Manager-Patches";
    private static final String BRANCH = "master";
    /** The set bundled in assets/patches. */
    static final String BUNDLED_SHA = "52ac81009e2580d6e93a52d87c7d1e4296977760";
    static final long BUNDLED_DATE = 1763745073L; // 21 Nov 2025 17:11:13 UTC

    interface Progress {
        boolean update(String message, long done, long total);
    }

    static final class Version {
        String sha = BUNDLED_SHA;
        long date = BUNDLED_DATE;    // commit time, seconds
        long checked;                // last check, seconds (0 = never)
        boolean bundled = true;
    }

    static final class Result {
        boolean upToDate;
        Version version;
        final List<String> added = new ArrayList<>(), changed = new ArrayList<>(), removed = new ArrayList<>();
    }

    static File folder(Context context) {
        return new File(context.getFilesDir(), "patches-online");
    }

    private static File versionFile(Context context) {
        return new File(context.getFilesDir(), "patches-online.properties");
    }

    /** The patch set in use: the downloaded one if there is one newer than the bundled set. */
    static Version current(Context context) {
        Version v = new Version();
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(versionFile(context))) {
            p.load(in);
        } catch (IOException e) {
            return v;
        }
        v.checked = parseLong(p.getProperty("checked"));
        String sha = p.getProperty("sha", "");
        long date = parseLong(p.getProperty("date"));
        File folder = folder(context);
        String[] files = folder.list();
        if (!sha.isEmpty() && date >= BUNDLED_DATE && files != null && files.length > 0) {
            v.sha = sha;
            v.date = date;
            v.bundled = false;
        }
        return v;
    }

    /** Use the downloaded folder instead of the app's assets? */
    static boolean useDownloaded(Context context) {
        return !current(context).bundled;
    }

    /** Checks GitHub and, if the patches changed, downloads them. Runs on a worker thread. */
    static Result update(Context context, PatchLibrary.Source currentSet, Progress progress) throws IOException {
        Version current = current(context);
        Result result = new Result();

        // 1. Latest commit (optional: if the API is rate-limited, compare the files instead).
        String sha = null;
        long date = 0;
        progress.update("Checking GitHub…", 0, 0);
        try {
            JSONObject commit = getJson("https://api.github.com/repos/" + REPO + "/commits/" + BRANCH);
            sha = commit.optString("sha", null);
            JSONObject info = commit.optJSONObject("commit");
            JSONObject committer = info != null ? info.optJSONObject("committer") : null;
            if (committer != null && !committer.optString("date").isEmpty())
                date = Instant.parse(committer.optString("date")).getEpochSecond();
        } catch (Exception ignored) {
            sha = null;
        }
        if (sha != null && sha.equalsIgnoreCase(current.sha)) {
            saveVersion(context, current.bundled ? null : current.sha, current.bundled ? 0 : current.date);
            result.upToDate = true;
            result.version = current(context);
            return result;
        }

        // 2. Download the repository and unpack the .mlua files.
        File cache = new File(context.getCacheDir(), "patch-update");
        ModInstaller.deleteRecursively(cache);
        cache.mkdirs();
        try {
            File zip = new File(cache, "patches.zip");
            download("https://github.com/" + REPO + "/archive/" + (sha != null ? sha : BRANCH) + ".zip", zip, progress);
            File unpacked = new File(cache, "unpacked");
            progress.update("Unpacking…", 0, 0);
            ArchiveExtractor.extract(zip, unpacked, (done, total) -> progress.update("Unpacking…", done, total));
            Map<String, File> fresh = new HashMap<>();
            collect(unpacked, fresh, 0);
            if (fresh.isEmpty())
                throw new IOException("The download had no patches in it");

            // 3. Compare with the set in use.
            Map<String, byte[]> old = new HashMap<>();
            Map<String, String> oldNames = new HashMap<>();
            try {
                for (String name : currentSet.names()) {
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".mlua"))
                        continue;
                    try (InputStream in = currentSet.open(name)) {
                        old.put(name.toLowerCase(Locale.ROOT), readAll(in));
                        oldNames.put(name.toLowerCase(Locale.ROOT), name);
                    }
                }
            } catch (IOException ignored) {
            }
            for (Map.Entry<String, File> entry : fresh.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                String id = entry.getKey().substring(0, entry.getKey().length() - ".mlua".length());
                byte[] before = old.remove(key);
                if (before == null)
                    result.added.add(id);
                else if (!Arrays.equals(before, readFile(entry.getValue())))
                    result.changed.add(id);
            }
            for (String key : old.keySet()) {
                String name = oldNames.get(key);
                result.removed.add(name.substring(0, name.length() - ".mlua".length()));
            }
            result.added.sort(String::compareToIgnoreCase);
            result.changed.sort(String::compareToIgnoreCase);
            result.removed.sort(String::compareToIgnoreCase);

            if (result.added.isEmpty() && result.changed.isEmpty() && result.removed.isEmpty()) {
                // Same files (the API didn't answer, or only non-patch files changed).
                if (sha != null && date >= BUNDLED_DATE && !current.bundled)
                    saveVersion(context, sha, date);
                else
                    saveVersion(context, current.bundled ? null : current.sha, current.bundled ? 0 : current.date);
                result.upToDate = true;
                result.version = current(context);
                return result;
            }

            // 4. Swap the new set in.
            progress.update("Saving…", 0, 0);
            File target = folder(context);
            File staging = new File(context.getFilesDir(), "patches-online.new");
            ModInstaller.deleteRecursively(staging);
            staging.mkdirs();
            for (Map.Entry<String, File> entry : fresh.entrySet())
                copy(entry.getValue(), new File(staging, entry.getKey()));
            ModInstaller.deleteRecursively(target);
            if (!staging.renameTo(target))
                throw new IOException("Couldn't save the new patches");
            saveVersion(context, sha != null ? sha : "unknown-" + (System.currentTimeMillis() / 1000),
                    date > 0 ? date : System.currentTimeMillis() / 1000);
            result.version = current(context);
            return result;
        } finally {
            ModInstaller.deleteRecursively(cache);
        }
    }

    /** Goes back to the patches bundled with the app. */
    static void reset(Context context) {
        ModInstaller.deleteRecursively(folder(context));
        versionFile(context).delete();
    }

    private static void saveVersion(Context context, String sha, long date) throws IOException {
        Properties p = new Properties();
        if (sha != null) {
            p.setProperty("sha", sha);
            p.setProperty("date", String.valueOf(date));
        }
        p.setProperty("checked", String.valueOf(System.currentTimeMillis() / 1000));
        try (OutputStream out = new FileOutputStream(versionFile(context))) {
            p.store(out, "Mod Manager patches downloaded from GitHub");
        }
    }

    private static void collect(File folder, Map<String, File> out, int depth) {
        File[] children = folder.listFiles();
        if (children == null || depth > 4)
            return;
        for (File child : children) {
            if (child.isDirectory())
                collect(child, out, depth + 1);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".mlua"))
                out.put(child.getName(), child);
        }
    }

    private static JSONObject getJson(String url) throws IOException, JSONException {
        HttpURLConnection connection = GameBanana.open(url);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int code = connection.getResponseCode();
            if (code != 200)
                throw new IOException("GitHub answered " + code);
            try (InputStream in = connection.getInputStream()) {
                return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String url, File destination, Progress progress) throws IOException {
        HttpURLConnection connection = GameBanana.open(url);
        try {
            int code = connection.getResponseCode();
            if (code != 200)
                throw new IOException("GitHub answered " + code);
            long total = connection.getContentLengthLong();
            long done = 0;
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    done += n;
                    if (!progress.update("Downloading the patches…", done, total))
                        throw new IOException("Cancelled");
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0)
            out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0)
                out.write(buffer, 0, n);
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
