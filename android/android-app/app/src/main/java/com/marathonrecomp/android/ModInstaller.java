package com.marathonrecomp.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Downloads a mod file from GameBanana and unpacks it straight into MarathonRecomp/mods,
 * without going through the Downloads folder. Each installed mod folder gets a
 * .gamebanana.json so the browser can show what's installed and offer updates.
 * Downloads that hold only Mod Manager patches (.mlua) go to MarathonRecomp/patches instead.
 */
final class ModInstaller {
    static final String METADATA = ".gamebanana.json";
    private static final String PATCH_METADATA_PREFIX = ".gamebanana-";
    private static final File INCOMING = new File(ModManager.MODS, ".marathon/incoming");

    interface Progress {
        /** Return false to cancel. */
        boolean update(String message, long done, long total);
    }

    static final class Installed {
        File folder;            // mod folder, or null for a patches-only install
        File metadata;
        final List<File> patchFiles = new ArrayList<>();
        int modId, fileId;
        String name = "", fileName = "", version = "";
        long updated, fileAdded, installedAt;

        String where() {
            if (folder != null)
                return "mods/" + folder.getName();
            return patchFiles.size() == 1 ? "patches/" + patchFiles.get(0).getName()
                    : patchFiles.size() + " patches in MarathonRecomp/patches";
        }
    }

    static final class Result {
        Installed installed;
        boolean looksLikeMod;   // has mod.ini / mod.json, or a game-like folder layout
    }

    /** Everything installed from GameBanana. */
    static List<Installed> installedAll() {
        List<Installed> out = new ArrayList<>();
        File[] folders = ModManager.MODS.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                Installed i = readMetadata(new File(folder, METADATA));
                if (i != null) {
                    i.folder = folder;
                    out.add(i);
                }
            }
        }
        File[] metas = ModManager.USER_PATCHES.listFiles(
                f -> f.isFile() && f.getName().startsWith(PATCH_METADATA_PREFIX) && f.getName().endsWith(".json"));
        if (metas != null) {
            for (File meta : metas) {
                Installed i = readMetadata(meta);
                if (i != null)
                    out.add(i);
            }
        }
        return out;
    }

    /** The most recent install of each mod, by mod id. */
    static Map<Integer, Installed> installed() {
        Map<Integer, Installed> out = new HashMap<>();
        for (Installed i : installedAll()) {
            Installed other = out.get(i.modId);
            if (other == null || i.installedAt > other.installedAt)
                out.put(i.modId, i);
        }
        return out;
    }

    private static Installed readMetadata(File meta) {
        if (!meta.isFile())
            return null;
        try {
            JSONObject json = new JSONObject(read(meta));
            Installed i = new Installed();
            i.metadata = meta;
            i.modId = json.optInt("modId");
            i.name = json.optString("name", "");
            i.fileId = json.optInt("fileId");
            i.fileName = json.optString("file", "");
            i.version = json.optString("version", "");
            i.updated = json.optLong("updated");
            i.fileAdded = json.optLong("fileAdded");
            i.installedAt = json.optLong("installed");
            JSONArray patches = json.optJSONArray("patches");
            if (patches != null)
                for (int p = 0; p < patches.length(); p++)
                    i.patchFiles.add(new File(ModManager.USER_PATCHES, patches.optString(p)));
            return i.modId != 0 ? i : null;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /**
     * Downloads, checks and unpacks the file into MarathonRecomp/mods (or, for patches, MarathonRecomp/patches).
     * replace is an earlier install of the same mod to swap out, or null to install alongside.
     */
    static Result install(File cacheDir, GameBanana.Mod mod, GameBanana.ModFile file, Installed replace,
                          Progress progress) throws IOException {
        File downloads = new File(cacheDir, "gamebanana");
        downloads.mkdirs();
        File archive = new File(downloads, file.id + "-" + safeName(file.name));
        File incoming = new File(INCOMING, String.valueOf(file.id));
        try {
            download(file, archive, progress);

            deleteRecursively(incoming);
            if (!incoming.mkdirs())
                throw new IOException("Can't write to MarathonRecomp/mods. Allow file access in the launcher.");
            if (isArchive(file.name)) {
                progress.update("Unpacking " + file.name + "…", 0, archive.length());
                ArchiveExtractor.extract(archive, incoming, (done, total) ->
                        progress.update("Unpacking " + file.name + "…", done, total));
            } else {
                // A single loose file (an .arc or an .mlua, say): keep it as it is.
                copy(archive, new File(incoming, file.name));
            }
            if (!containsFile(incoming))
                throw new IOException("The download is empty");
            if (!progress.update("Installing…", 0, 0))
                throw new IOException("Cancelled");

            Result result = new Result();
            result.looksLikeMod = looksLikeMod(incoming, 0);
            List<File> patches = new ArrayList<>();
            if (!result.looksLikeMod)
                collect(incoming, ".mlua", patches, 0);

            JSONObject meta = metadata(mod, file);
            Installed installed;
            if (!result.looksLikeMod && !patches.isEmpty()) {
                installed = installPatches(patches, file, meta);
                if (replace != null && replace.folder != null)
                    deleteRecursively(replace.folder);
                else if (replace != null)
                    removePatches(replace, installed.patchFiles);
            } else {
                installed = installFolder(incoming, mod, file, replace, meta);
                if (replace != null && replace.folder == null)
                    removePatches(replace, new ArrayList<>());
            }
            installed.modId = mod.id;
            installed.name = mod.name;
            installed.fileId = file.id;
            installed.fileName = file.name;
            installed.version = meta.optString("version");
            installed.updated = mod.updated;
            installed.fileAdded = file.added;
            installed.installedAt = meta.optLong("installed");
            result.installed = installed;
            return result;
        } finally {
            archive.delete();
            deleteRecursively(INCOMING);
        }
    }

    private static Installed installFolder(File incoming, GameBanana.Mod mod, GameBanana.ModFile file,
                                           Installed replace, JSONObject meta) throws IOException {
        File target;
        if (replace != null && replace.folder != null) {
            target = replace.folder;
        } else {
            String base = safeName(mod.name);
            target = new File(ModManager.MODS, base);
            if (target.exists())
                target = new File(ModManager.MODS, base + " - " + safeName(stripExtension(file.name)));
            if (target.exists())
                target = new File(ModManager.MODS, base + " (" + file.id + ")");
            for (int n = 2; target.exists(); n++)
                target = new File(ModManager.MODS, base + " (" + file.id + "-" + n + ")");
        }

        // Swap the new copy in, keeping the old one until the new one is in place.
        File backup = null;
        if (target.exists()) {
            backup = new File(INCOMING, "previous-" + file.id);
            deleteRecursively(backup);
            if (!target.renameTo(backup))
                throw new IOException("Can't replace " + target.getName() + " (is a file in it open?)");
        }
        if (!incoming.renameTo(target)) {
            if (backup != null)
                backup.renameTo(target);
            throw new IOException("Can't move the mod into " + target);
        }
        if (backup != null)
            deleteRecursively(backup);

        write(new File(target, METADATA), meta.toString());
        Installed installed = new Installed();
        installed.folder = target;
        installed.metadata = new File(target, METADATA);
        return installed;
    }

    private static Installed installPatches(List<File> patches, GameBanana.ModFile file, JSONObject meta)
            throws IOException {
        ModManager.USER_PATCHES.mkdirs();
        Installed installed = new Installed();
        JSONArray names = new JSONArray();
        for (File patch : patches) {
            File destination = new File(ModManager.USER_PATCHES, patch.getName());
            copy(patch, destination);
            installed.patchFiles.add(destination);
            names.put(patch.getName());
        }
        try {
            meta.put("patches", names);
        } catch (JSONException ignored) {
        }
        installed.metadata = new File(ModManager.USER_PATCHES, PATCH_METADATA_PREFIX + file.id + ".json");
        write(installed.metadata, meta.toString());
        return installed;
    }

    /** Removes an install: its folder, or its patch files. */
    static void uninstall(Installed installed) {
        if (installed.folder != null)
            deleteRecursively(installed.folder);
        else
            removePatches(installed, new ArrayList<>());
    }

    private static void removePatches(Installed installed, List<File> keep) {
        for (File patch : installed.patchFiles)
            if (!keep.contains(patch))
                patch.delete();
        if (installed.metadata != null)
            installed.metadata.delete();
    }

    private static JSONObject metadata(GameBanana.Mod mod, GameBanana.ModFile file) {
        JSONObject meta = new JSONObject();
        try {
            meta.put("modId", mod.id);
            meta.put("name", mod.name);
            meta.put("fileId", file.id);
            meta.put("file", file.name);
            meta.put("version", file.version.isEmpty() ? mod.version : file.version);
            meta.put("updated", mod.updated);
            meta.put("fileAdded", file.added);
            meta.put("installed", System.currentTimeMillis() / 1000);
            meta.put("source", mod.profileUrl);
        } catch (JSONException ignored) {
        }
        return meta;
    }

    private static void download(GameBanana.ModFile file, File destination, Progress progress) throws IOException {
        String url = "https://gamebanana.com/dl/" + file.id;
        HttpURLConnection connection = GameBanana.open(url);
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try {
            int code = connection.getResponseCode();
            if (code != 200)
                throw new IOException("The download failed (GameBanana answered " + code + ")");
            long total = connection.getContentLengthLong() > 0 ? connection.getContentLengthLong() : file.size;
            long done = 0, lastReport = 0;
            if (!progress.update("Downloading " + file.name + "…", 0, total))
                throw new IOException("Cancelled");
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[256 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    md5.update(buffer, 0, n);
                    done += n;
                    if (done - lastReport >= 256 * 1024 || done == total) {
                        lastReport = done;
                        if (!progress.update("Downloading " + file.name + "…", done, total))
                            throw new IOException("Cancelled");
                    }
                }
            }
            if (total > 0 && done < total)
                throw new IOException("The download stopped early. Try again.");
        } finally {
            connection.disconnect();
        }
        if (!file.md5.isEmpty()) {
            StringBuilder hex = new StringBuilder();
            for (byte b : md5.digest())
                hex.append(String.format(Locale.ROOT, "%02x", b));
            if (!hex.toString().equalsIgnoreCase(file.md5))
                throw new IOException("The download is damaged (checksum mismatch). Try again.");
        }
    }

    /** mod.ini, mod.json, or a xenon/win32 folder within three levels. */
    private static boolean looksLikeMod(File folder, int depth) {
        File[] children = folder.listFiles();
        if (children == null)
            return false;
        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.ROOT);
            if (child.isFile() && (name.equals("mod.ini") || name.equals("mod.json")))
                return true;
            if (child.isDirectory() && (name.equals("xenon") || name.equals("win32")))
                return true;
        }
        if (depth >= 3)
            return false;
        for (File child : children)
            if (child.isDirectory() && looksLikeMod(child, depth + 1))
                return true;
        return false;
    }

    private static void collect(File folder, String extension, List<File> out, int depth) {
        File[] children = folder.listFiles();
        if (children == null || depth > 4)
            return;
        for (File child : children) {
            if (child.isDirectory())
                collect(child, extension, out, depth + 1);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(extension))
                out.add(child);
        }
    }

    private static boolean containsFile(File folder) {
        File[] children = folder.listFiles();
        if (children == null)
            return false;
        for (File child : children)
            if (child.isFile() || containsFile(child))
                return true;
        return false;
    }

    static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : new String[] { ".zip", ".7z", ".rar", ".tar", ".gz", ".tgz", ".xz", ".bz2" })
            if (lower.endsWith(ext))
                return true;
        return false;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String safeName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        while (clean.startsWith("."))
            clean = clean.substring(1).trim();
        if (clean.length() > 80)
            clean = clean.substring(0, 80).trim();
        return clean.isEmpty() ? "GameBanana mod" : clean;
    }

    static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                deleteRecursively(child);
        file.delete();
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[1 << 20];
            int n;
            while ((n = in.read(buffer)) > 0)
                out.write(buffer, 0, n);
        }
    }

    private static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int off = 0;
            while (off < bytes.length) {
                int n = in.read(bytes, off, bytes.length - off);
                if (n < 0)
                    break;
                off += n;
            }
            return new String(bytes, 0, off, StandardCharsets.UTF_8);
        }
    }

    private static void write(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
