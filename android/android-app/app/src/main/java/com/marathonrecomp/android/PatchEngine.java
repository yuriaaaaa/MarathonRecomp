package com.marathonrecomp.android;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import unluac.decompile.Decompiler;
import unluac.decompile.Output;
import unluac.decompile.OutputProvider;
import unluac.parse.BHeader;
import unluac.parse.LFunction;

/**
 * Runs Sonic '06 Mod Manager patches (.mlua) and tweaks against the game's archives,
 * following the Mod Manager's own PatchEngine/TweakEngine (hyperbx/SonicNextModManager,
 * Sonic-06-Mod-Manager/src/UnifyPatcher.cs) as closely as possible.
 *
 * What it can't do: patches that change the game's code. The game runs code recompiled
 * ahead of time, so writes into the executable's code sections are reported as unsupported.
 * Writes into its data sections are collected and applied in memory by the game at startup.
 */
final class PatchEngine {
    // default.xex (the one release Marathon Recompiled supports): image and code ranges.
    static final long IMAGE_BASE = 0x82000000L, IMAGE_END = 0x82D98000L;
    private static final long[][] CODE = { { 0x82160000L, 0x82AEAA6CL }, { 0x82AEAC00L, 0x82B0ABB8L } };

    static final String VERSION = "patch-engine-1";

    // ------------------------------------------------------------------ patches

    static final class XexWrite {
        final long address;
        final byte[] bytes;

        XexWrite(long address, byte[] bytes) {
            this.address = address;
            this.bytes = bytes;
        }
    }

    static final class Patch {
        String id = "", title = "", author = "", platform = "", blurb = "", description = "";
        String source = "";            // the script, for signatures
        List<String> lines = new ArrayList<>();
        boolean supported = true;
        String reason = "";
        final List<XexWrite> xexWrites = new ArrayList<>();
        final Set<String> archives = new LinkedHashSet<>(); // lower-case game paths, "xenon/archives/player.arc"

        void unsupported(String why) {
            if (supported) {
                supported = false;
                reason = why;
            }
        }
    }

    private static final Set<String> BLOCK_COMMANDS = new LinkedHashSet<>(Arrays.asList(
            "WriteByte", "WriteBytes", "WriteNullBytes", "WriteTextBytes", "WriteNopPPC", "WriteBase64",
            "Rename", "RenameByExtension", "Delete", "Copy", "Ignore", "Search",
            "ParameterAdd", "ParameterEdit", "ParameterErase", "ParameterRename", "StringReplace",
            "PackageAdd", "PackageEdit", "PackageErase"));

    static Patch parse(String id, String text) {
        Patch patch = new Patch();
        patch.id = id;
        patch.source = text;
        for (String raw : text.split("\r\n|\n|\r"))
            patch.lines.add(raw.replaceAll("\\s+$", ""));
        for (String line : patch.lines) {
            if (line.startsWith("Title(")) patch.title = single(line);
            else if (line.startsWith("Author(")) patch.author = single(line);
            else if (line.startsWith("Platform(")) patch.platform = single(line);
            else if (line.startsWith("Blurb(")) patch.blurb = single(line);
            else if (line.startsWith("Description(")) patch.description = single(line).replace("\\n", "\n");
        }
        if (patch.title.isEmpty())
            patch.title = id;
        if (patch.platform.equals("PlayStation 3")) {
            patch.unsupported("Made for the PlayStation 3 version.");
            return patch;
        }

        boolean inBlock = false;
        for (String line : activeLines(patch)) {
            String command = command(line);
            if (command == null)
                continue;
            String[] p = params(line);
            try {
                switch (command) {
                    case "Title": case "Author": case "Platform": case "Blurb": case "Description":
                    case "DecryptExecutable": case "EncryptExecutable":
                        break;
                    case "BeginBlock":
                        inBlock = true;
                        patch.archives.add(U8Archive.lower(coreReplace(single(line))));
                        break;
                    case "EndBlock":
                        inBlock = false;
                        break;
                    case "WriteVirtualBytes":
                        addXexWrite(patch, parseHex(p[0]), hexBytes(p[1]));
                        break;
                    case "WriteVirtualNop":
                        addXexWrite(patch, parseHex(p[0]), nops(p.length == 2 ? Integer.parseInt(p[1].trim()) : 1));
                        break;
                    case "TextAdd": case "TextEdit":
                        patch.unsupported("Edits text tables, which this build can't do yet.");
                        break;
                    default:
                        if (p.length > 0 && p[0].equals("Executable")) {
                            byte[] bytes = executableBytes(command, p);
                            if (bytes == null)
                                patch.unsupported("Uses " + command + " on the executable.");
                            else
                                addXexWrite(patch, IMAGE_BASE + parseHex(p[1]) - 0x3000, bytes);
                        } else if (!BLOCK_COMMANDS.contains(command)) {
                            patch.unsupported("Uses " + command + ", which this build doesn't know.");
                        } else if (!inBlock && !command.equals("Ignore") && !command.equals("Search")) {
                            patch.unsupported("Changes files in the game folder directly.");
                        }
                        break;
                }
            } catch (RuntimeException e) {
                patch.unsupported("Couldn't read the line " + line.trim());
            }
        }
        if (patch.xexWrites.isEmpty() && patch.archives.isEmpty() && patch.supported)
            patch.unsupported("Doesn't change anything this build can apply.");
        return patch;
    }

    private static void addXexWrite(Patch patch, long address, byte[] bytes) {
        long end = address + bytes.length;
        if (address < IMAGE_BASE || end > IMAGE_END) {
            patch.unsupported("Writes outside the game's executable.");
            return;
        }
        for (long[] range : CODE) {
            if (address < range[1] && end > range[0]) {
                patch.unsupported("Changes the game's code, which is recompiled ahead of time in this build.");
                return;
            }
        }
        patch.xexWrites.add(new XexWrite(address, bytes));
    }

    private static byte[] executableBytes(String command, String[] p) {
        switch (command) {
            case "WriteByte": return new byte[] { (byte) parseHex(p[2]) };
            case "WriteBytes": return hexBytes(p[2]);
            case "WriteNullBytes": return new byte[Integer.parseInt(p[2].trim())];
            case "WriteTextBytes": return p[2].getBytes(StandardCharsets.US_ASCII);
            case "WriteNopPPC": return nops(p.length == 3 ? Integer.parseInt(p[2].trim()) : 1);
            default: return null;
        }
    }

    /** Lines the Mod Manager would run on the Xbox 360 (it skips the PlayStation 3 sections). */
    private static List<String> activeLines(Patch patch) {
        List<String> out = new ArrayList<>();
        boolean allSystems = patch.platform.equals("All Systems");
        boolean reached = !allSystems;
        for (String line : patch.lines) {
            if (allSystems) {
                if (line.startsWith("All Systems") || line.startsWith("Xbox 360"))
                    reached = true;
                else if (line.startsWith("PlayStation 3"))
                    reached = false;
            }
            if (reached)
                out.add(line);
        }
        return out;
    }

    private static String command(String line) {
        int paren = line.indexOf('(');
        if (paren <= 0)
            return null;
        String name = line.substring(0, paren);
        if (!Character.isLetter(name.charAt(0)))
            return null;
        for (int i = 0; i < name.length(); i++)
            if (!Character.isLetterOrDigit(name.charAt(i)))
                return null;
        return name;
    }

    /** DeserialiseParameterList: split by '|', strip the quotes of quoted values. */
    private static String[] params(String line) {
        String inner = line.substring(line.indexOf('(') + 1);
        inner = inner.substring(0, Math.max(0, inner.length() - 1));
        String[] parts = inner.split("\\|", -1);
        for (int i = 0; i < parts.length; i++)
            if (parts[i].startsWith("\"") && parts[i].length() >= 2)
                parts[i] = parts[i].substring(1, parts[i].length() - 1);
        return parts;
    }

    /** DeserialiseParameter: the one quoted value of Title("...") or BeginBlock("..."). */
    private static String single(String line) {
        String inner = line.substring(line.indexOf('(') + 1);
        if (inner.length() < 2)
            return "";
        inner = inner.substring(0, inner.length() - 2);
        return inner.startsWith("\"") ? inner.substring(1) : inner;
    }

    private static long parseHex(String value) {
        String v = value.trim();
        if (v.startsWith("0x") || v.startsWith("0X"))
            v = v.substring(2);
        return Long.parseLong(v, 16);
    }

    private static byte[] hexBytes(String value) {
        String v = value.replace(" ", "").trim();
        if (v.startsWith("0x") || v.startsWith("0X"))
            v = v.substring(2);
        byte[] out = new byte[v.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(v.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static byte[] nops(int count) {
        byte[] out = new byte[count * 4];
        for (int i = 0; i < count; i++)
            out[i * 4] = 0x60;
        return out;
    }

    /** Literal.CoreReplace: "core\..." means the platform folder, "xenon" on the Xbox 360. */
    static String coreReplace(String path) {
        String clean = path.replace('\\', '/');
        String[] parts = clean.split("/", -1);
        if (parts.length > 0 && parts[0].equals("core")) {
            parts[0] = "xenon";
            return String.join("/", parts);
        }
        return clean;
    }

    // ------------------------------------------------------------------ tweaks

    /** The Mod Manager's Tweaks tab (the parts that work on Xbox 360 game files). */
    static final class Tweaks {
        static final int RETAIL = 0, TGS = 1, E3 = 2;
        int cameraType = RETAIL;
        int cameraDistance = 650;   // hundredths of a metre
        int cameraHeight = 70;
        int hammerRange = 50;
        int startingRings = 0;

        boolean gameArc() {
            return cameraType != RETAIL || cameraDistance != 650;
        }

        boolean playerArc() {
            return cameraType == TGS || cameraHeight != 70 || hammerRange != 50 || startingRings != 0;
        }

        boolean any() {
            return gameArc() || playerArc();
        }

        Set<String> archives() {
            Set<String> out = new LinkedHashSet<>();
            if (gameArc()) out.add("xenon/archives/game.arc");
            if (playerArc()) out.add("xenon/archives/player.arc");
            return out;
        }

        String signature() {
            return cameraType + "," + cameraDistance + "," + cameraHeight + "," + hammerRange + "," + startingRings;
        }
    }

    // ------------------------------------------------------------------ running

    /** Pending edits to one archive; committed only if a whole patch succeeds. */
    private static final class Session {
        final U8Archive archive;
        final Map<String, byte[]> changed = new LinkedHashMap<>(); // lower path -> contents (null = deleted)
        final Map<String, String> names = new HashMap<>();          // lower path -> path as written

        Session(U8Archive archive) {
            this.archive = archive;
        }

        boolean exists(String path) {
            String key = U8Archive.lower(path);
            if (changed.containsKey(key))
                return changed.get(key) != null;
            U8Archive.Node node = archive.find(path);
            return node != null && !node.directory;
        }

        boolean isDirectory(String path) {
            U8Archive.Node node = archive.find(path);
            return node != null && node.directory;
        }

        byte[] read(String path) throws IOException {
            String key = U8Archive.lower(path);
            if (changed.containsKey(key)) {
                byte[] data = changed.get(key);
                if (data == null)
                    throw new IOException(path + " was deleted");
                return data;
            }
            U8Archive.Node node = archive.find(path);
            if (node == null || node.directory)
                throw new IOException(path + " isn't in the archive");
            return archive.read(node);
        }

        void write(String path, byte[] data) {
            String key = U8Archive.lower(path);
            changed.put(key, data);
            names.putIfAbsent(key, path);
        }

        void delete(String path) {
            String key = U8Archive.lower(path);
            changed.put(key, null);
            names.putIfAbsent(key, path);
        }

        /** Files directly in a directory (as the Mod Manager's TopDirectoryOnly listing), including new ones. */
        List<String> filesIn(String directory, String extension) {
            String prefix = U8Archive.lower(directory);
            if (!prefix.endsWith("/"))
                prefix += "/";
            List<String> out = new ArrayList<>();
            for (String path : archive.files(directory, false).keySet())
                if (path.toLowerCase(Locale.ROOT).endsWith(extension) && exists(path))
                    out.add(path);
            for (Map.Entry<String, byte[]> entry : changed.entrySet()) {
                String key = entry.getKey();
                if (entry.getValue() != null && key.startsWith(prefix) && key.indexOf('/', prefix.length()) < 0
                        && key.endsWith(extension) && archive.find(key) == null)
                    out.add(names.get(key));
            }
            return out;
        }

        void commit() throws IOException {
            for (Map.Entry<String, byte[]> entry : changed.entrySet()) {
                String path = names.get(entry.getKey());
                if (entry.getValue() == null) {
                    archive.remove(path);
                } else {
                    U8Archive.Node node = archive.find(path);
                    if (node == null)
                        node = archive.createFile(path);
                    U8Archive.setContents(node, entry.getValue());
                }
            }
            changed.clear();
        }
    }

    /**
     * Applies the patches' blocks for one archive (then the tweaks for it) to {@code archive}.
     * Patches that fail are left out and listed in {@code problems}.
     */
    static void applyToArchive(U8Archive archive, String archivePath, List<Patch> patches, Tweaks tweaks,
                               List<String> problems) {
        String key = U8Archive.lower(archivePath);
        for (Patch patch : patches) {
            if (!patch.archives.contains(key))
                continue;
            Session session = new Session(archive);
            try {
                runPatch(patch, key, session);
                session.commit();
            } catch (Exception e) {
                problems.add(patch.title + ": " + message(e));
            }
        }
        if (tweaks != null) {
            Session session = new Session(archive);
            try {
                runTweaks(tweaks, key, session);
                session.commit();
            } catch (Exception e) {
                problems.add("Tweaks (" + archivePath + "): " + message(e));
            }
        }
    }

    private static String message(Exception e) {
        String text = e.getMessage() != null ? e.getMessage() : e.toString();
        return text.replace('\n', ' ').replace('\t', ' ');
    }

    private static void runPatch(Patch patch, String archiveKey, Session session) throws IOException {
        boolean active = false;
        List<String> ignore = new ArrayList<>(), search = new ArrayList<>();
        for (String line : activeLines(patch)) {
            String command = command(line);
            if (command == null)
                continue;
            if (command.equals("BeginBlock")) {
                active = U8Archive.lower(coreReplace(single(line))).equals(archiveKey);
                continue;
            }
            if (command.equals("EndBlock")) {
                active = false;
                continue;
            }
            String[] p = params(line);
            if (command.equals("Ignore")) {
                ignore = new ArrayList<>(Arrays.asList(p));
                continue;
            }
            if (command.equals("Search")) {
                search = new ArrayList<>(Arrays.asList(p));
                continue;
            }
            if (!active || (p.length > 0 && p[0].equals("Executable")))
                continue;
            run(command, p, session, ignore, search);
        }
    }

    private static void run(String command, String[] p, Session s, List<String> ignore, List<String> search)
            throws IOException {
        String path = p.length > 0 ? coreReplace(p[0]) : "";
        switch (command) {
            case "WriteByte":
                writeBytes(s, path, parseHex(p[1]), new byte[] { (byte) parseHex(p[2]) });
                break;
            case "WriteBytes":
                writeBytes(s, path, parseHex(p[1]), hexBytes(p[2]));
                break;
            case "WriteNullBytes":
                writeBytes(s, path, parseHex(p[1]), new byte[Integer.parseInt(p[2].trim())]);
                break;
            case "WriteTextBytes":
                writeBytes(s, path, parseHex(p[1]), p[2].getBytes(StandardCharsets.US_ASCII));
                break;
            case "WriteNopPPC":
                writeBytes(s, path, parseHex(p[1]), nops(p.length == 3 ? Integer.parseInt(p[2].trim()) : 1));
                break;
            case "WriteBase64":
                s.write(path, Base64.getDecoder().decode(p[1].trim()));
                break;
            case "Rename": {
                if (!s.exists(path))
                    break;
                String target = parent(path) + fileName(p[1].replace('\\', '/'));
                if (!s.exists(target)) {
                    s.write(target, s.read(path));
                    s.delete(path);
                }
                break;
            }
            case "RenameByExtension": {
                String extension = p[1].startsWith("*") ? p[1].substring(1).toLowerCase(Locale.ROOT) : p[1].toLowerCase(Locale.ROOT);
                for (String file : s.filesIn(path, extension)) {
                    String renamed = file.substring(0, file.lastIndexOf('.')) + (p[2].startsWith(".") ? p[2] : "." + p[2]);
                    if (!s.exists(renamed)) {
                        s.write(renamed, s.read(file));
                        s.delete(file);
                    }
                }
                break;
            }
            case "Delete":
                if (s.exists(path)) {
                    s.delete(path);
                } else if (s.isDirectory(path)) {
                    for (String file : s.archive.files(path, true).keySet())
                        s.delete(file);
                }
                break;
            case "Copy": {
                if (!s.exists(path))
                    break;
                String target = coreReplace(p[1]);
                boolean overwrite = p.length > 2 && p[2].trim().equalsIgnoreCase("true");
                if (s.exists(target) && !overwrite)
                    throw new IOException("Copy target " + target + " already exists");
                s.write(target, s.read(path));
                break;
            }
            case "ParameterAdd":
                forEachLua(s, path, ".lub", ignore, search, file -> parameterAdd(s, file, p[1], p[2]));
                break;
            case "ParameterEdit":
                forEachLua(s, path, ".lub", ignore, search, file -> parameterEdit(s, file, p[1], p[2]));
                break;
            case "ParameterErase":
                forEachLua(s, path, ".lub", ignore, search, file -> parameterErase(s, file, p[1]));
                break;
            case "ParameterRename":
                forEachLua(s, path, ".lub", ignore, search, file -> parameterRename(s, file, p[1], p[2]));
                break;
            case "StringReplace":
                forEachLua(s, path, ".lub", ignore, search, file -> stringReplace(s, file, p[1], p[2]));
                break;
            case "PackageAdd":
                forEachLua(s, path, ".pkg", ignore, search, file -> editPackage(s, file, pkg -> pkg.add(p[1], p[2], p[3])));
                break;
            case "PackageEdit":
                forEachLua(s, path, ".pkg", ignore, search, file -> editPackage(s, file, pkg -> pkg.edit(p[1], p[2], p.length > 3 ? p[3] : "")));
                break;
            case "PackageErase":
                forEachLua(s, path, ".pkg", ignore, search, file -> editPackage(s, file, pkg -> pkg.erase(p[1], p[2])));
                break;
            default:
                throw new IOException("unsupported command " + command);
        }
    }

    private interface FileAction {
        void run(String file) throws IOException;
    }

    private interface PackageAction {
        void run(AssetPackage pkg);
    }

    /** A single file, or every matching file directly inside a folder (minus Ignore, limited by Search). */
    private static void forEachLua(Session s, String path, String extension, List<String> ignore, List<String> search,
                                   FileAction action) throws IOException {
        if (s.exists(path)) {
            action.run(path);
            return;
        }
        if (!s.isDirectory(path))
            return;
        for (String file : s.filesIn(path, extension)) {
            String name = fileName(file);
            boolean ignored = false;
            for (String phrase : ignore)
                if (!phrase.isEmpty() && name.contains(phrase))
                    ignored = true;
            if (ignored)
                continue;
            if (!search.isEmpty()) {
                boolean found = false;
                for (String phrase : search)
                    if (name.contains(phrase))
                        found = true;
                if (!found)
                    continue;
            }
            action.run(file);
        }
    }

    private static void writeBytes(Session s, String path, long offset, byte[] bytes) throws IOException {
        if (!s.exists(path))
            return;
        byte[] data = s.read(path);
        int end = (int) offset + bytes.length;
        if (end > data.length)
            data = Arrays.copyOf(data, end);
        else
            data = data.clone();
        System.arraycopy(bytes, 0, data, (int) offset, bytes.length);
        s.write(path, data);
    }

    private static void editPackage(Session s, String file, PackageAction action) throws IOException {
        AssetPackage pkg = AssetPackage.read(s.read(file));
        action.run(pkg);
        s.write(file, pkg.write());
    }

    // ---- Lua (as text, decompiled with unluac/unlub like the Mod Manager does)

    static boolean isBytecode(byte[] data) {
        return data.length >= 4 && data[0] == 0x1B && data[1] == 'L' && data[2] == 'u' && data[3] == 'a';
    }

    static String luaText(byte[] data) throws IOException {
        if (!isBytecode(data))
            return new String(data, StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            BHeader header = new BHeader(buffer);
            LFunction main = header.function.parse(buffer, header);
            Decompiler decompiler = new Decompiler(main);
            decompiler.decompile();
            StringBuilder out = new StringBuilder();
            decompiler.print(new Output(new OutputProvider() {
                @Override
                public void print(String text) {
                    out.append(text);
                }

                @Override
                public void println() {
                    out.append('\n');
                }
            }));
            return out.toString();
        } catch (RuntimeException e) {
            throw new IOException("Lua script couldn't be decompiled (" + e + ")");
        }
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty())
            out.remove(out.size() - 1);
        return out;
    }

    private static void saveLines(Session s, String file, byte[] original, List<String> before, List<String> after) {
        if (before.equals(after) && original != null)
            return;
        StringBuilder text = new StringBuilder();
        for (String line : after)
            text.append(line).append('\n');
        s.write(file, text.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void parameterAdd(Session s, String file, String parameter, String value) throws IOException {
        byte[] data = s.read(file);
        List<String> before = lines(luaText(data));
        List<String> after = new ArrayList<>(before);
        after.add(parameter + " = " + value.replace("\\\"", "\""));
        saveLines(s, file, data, before, after);
    }

    private static void parameterEdit(Session s, String file, String parameter, String value) throws IOException {
        byte[] data = s.read(file);
        List<String> before = lines(luaText(data));
        List<String> after = new ArrayList<>(before);
        String v = value.replace("\\\"", "\"");
        for (int i = 0; i < after.size(); i++) {
            String line = after.get(i);
            if (line.startsWith(parameter)) {
                String[] split = line.split(" ", -1);
                if (split.length < 3)
                    throw new IOException("can't edit \"" + line + "\" in " + fileName(file));
                split[2] = v;
                for (int j = 3; j < split.length; j++)
                    split[j] = "";
                after.set(i, String.join(" ", split));
                break;
            }
        }
        saveLines(s, file, data, before, after);
    }

    private static void parameterErase(Session s, String file, String parameter) throws IOException {
        byte[] data = s.read(file);
        List<String> before = lines(luaText(data));
        List<String> after = new ArrayList<>();
        for (String line : before)
            if (!line.contains(parameter))
                after.add(line);
        saveLines(s, file, data, before, after);
    }

    private static void parameterRename(Session s, String file, String parameter, String renamed) throws IOException {
        byte[] data = s.read(file);
        List<String> before = lines(luaText(data));
        List<String> after = new ArrayList<>(before);
        for (int i = 0; i < after.size(); i++) {
            if (after.get(i).startsWith(parameter)) {
                String[] split = after.get(i).split(" ", -1);
                split[0] = renamed;
                after.set(i, String.join(" ", split));
                break;
            }
        }
        saveLines(s, file, data, before, after);
    }

    private static void stringReplace(Session s, String file, String find, String replacement) throws IOException {
        byte[] data = s.read(file);
        String text = luaText(data);
        String result = text.replace(find.replace("\\n", "\n"), replacement.replace("\\n", "\n"));
        if (!result.equals(text))
            s.write(file, result.getBytes(StandardCharsets.ISO_8859_1));
    }

    // ---- tweaks (TweakEngine)

    private static void runTweaks(Tweaks t, String archiveKey, Session s) throws IOException {
        if (archiveKey.equals("xenon/archives/game.arc")) {
            String file = "xenon/cameraparam.lub";
            if (t.cameraType != Tweaks.RETAIL)
                editLines(s, file, lines -> cameraType(lines, t.cameraType));
            if (t.cameraDistance != 650)
                editLines(s, file, lines -> {
                    for (int i = 0; i < lines.size(); i++)
                        if (lines.get(i).startsWith("distance"))
                            lines.set(i, setWord(lines.get(i), 2, decimal(t.cameraDistance, 100)));
                });
        } else if (archiveKey.equals("xenon/archives/player.arc")) {
            String common = "xenon/player/common.lub";
            if (t.cameraType == Tweaks.TGS)
                editLines(s, common, lines -> cameraType(lines, t.cameraType));
            if (t.cameraHeight != 70)
                editLines(s, common, lines -> {
                    String height = decimal(t.cameraHeight, 100);
                    for (int i = 0; i < lines.size(); i++) {
                        if (!lines.get(i).startsWith("c_camera"))
                            continue;
                        if (lines.get(i).contains("c_camera = { x ="))
                            lines.set(i, "c_camera = { x = 0 * meter, y = " + height + " * meter, z = 0 * meter }");
                        else if (i + 2 < lines.size())
                            lines.set(i += 2, "  y = " + height + " * meter,");
                    }
                });
            if (t.hammerRange != 50)
                editLines(s, "xenon/player/amy.lub", lines -> {
                    for (int i = 0; i < lines.size(); i++)
                        if (lines.get(i).startsWith("c_hammer_head"))
                            lines.set(i, "c_hammer_head = " + decimal(t.hammerRange, 100) + " * meter");
                });
            if (t.startingRings != 0) {
                for (String file : s.archive.files("xenon/player", true).keySet()) {
                    if (!file.toLowerCase(Locale.ROOT).endsWith(".lub"))
                        continue;
                    editLines(s, file, lines -> {
                        lines.removeIf(line -> line.contains("c_default_ring"));
                        lines.add("c_default_ring = " + t.startingRings);
                    });
                }
            }
        }
    }

    private interface LineEdit {
        void run(List<String> lines);
    }

    private static void editLines(Session s, String file, LineEdit edit) throws IOException {
        if (!s.exists(file))
            throw new IOException(file + " isn't in the archive");
        byte[] data = s.read(file);
        List<String> before = lines(luaText(data));
        List<String> after = new ArrayList<>(before);
        edit.run(after);
        saveLines(s, file, data, before, after);
    }

    /** TweakEngine.CameraType (with the default field of view of 90). */
    private static void cameraType(List<String> lines, int type) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("distance"))
                line = setWord(line, 2, type == Tweaks.RETAIL ? "6.5" : type == Tweaks.TGS ? "4.5" : "5.5");
            if (line.startsWith("springK"))
                line = setWord(line, 2, type == Tweaks.TGS ? "0.225" : "0.98");
            if (line.startsWith("altitude"))
                line = setWord(line, 2, type == Tweaks.TGS ? "-15" : "15");
            if (line.startsWith("az_driveK"))
                line = setWord(line, 2, type == Tweaks.TGS ? "50000" : type == Tweaks.E3 ? "690" : "3250");
            if (line.startsWith("az_dampingK"))
                line = setWord(line, 2, type == Tweaks.TGS ? "2500" : type == Tweaks.E3 ? "100" : "250");
            lines.set(i, line);
        }
    }

    private static String setWord(String line, int index, String value) {
        String[] split = line.split(" ", -1);
        if (split.length <= index)
            return line;
        split[index] = value;
        return String.join(" ", split);
    }

    /** C# decimal.Divide(value, by).ToString(): 650/100 -> "6.5", 700/100 -> "7". */
    static String decimal(int value, int by) {
        BigDecimal result = new BigDecimal(value).divide(new BigDecimal(by));
        String text = result.stripTrailingZeros().toPlainString();
        return text;
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
