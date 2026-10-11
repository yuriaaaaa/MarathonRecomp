package com.marathonrecomp.android;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Sonic '06 Mod Manager patches: the official set (Knuxfan24/Sonic-06-Mod-Manager-Patches)
 * bundled in the app, plus any .mlua files in MarathonRecomp/patches (same name replaces).
 */
final class PatchLibrary {
    interface Source {
        List<String> names() throws IOException;

        InputStream open(String name) throws IOException;
    }

    /** Patches that Marathon Recompiled already has as built-in codes (same names). */
    static boolean isBuiltIn(String id) {
        for (String code : ModManager.CODES)
            if (code.equals(id))
                return true;
        return false;
    }

    static List<PatchEngine.Patch> load(Source bundled, File userFolder) {
        Map<String, PatchEngine.Patch> patches = new LinkedHashMap<>();
        try {
            for (String name : bundled.names()) {
                if (!name.toLowerCase(Locale.ROOT).endsWith(".mlua"))
                    continue;
                try (InputStream in = bundled.open(name)) {
                    add(patches, name, readAll(in));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        File[] files = userFolder == null ? null : userFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".mlua"))
                    continue;
                try (InputStream in = new FileInputStream(file)) {
                    add(patches, file.getName(), readAll(in));
                } catch (IOException ignored) {
                }
            }
        }
        List<PatchEngine.Patch> list = new ArrayList<>(patches.values());
        list.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        return list;
    }

    static Source folder(File folder) {
        return new Source() {
            @Override
            public List<String> names() {
                List<String> out = new ArrayList<>();
                String[] names = folder.list();
                if (names != null)
                    for (String name : names)
                        out.add(name);
                return out;
            }

            @Override
            public InputStream open(String name) throws IOException {
                return new FileInputStream(new File(folder, name));
            }
        };
    }

    private static void add(Map<String, PatchEngine.Patch> patches, String fileName, String text) {
        String id = fileName.substring(0, fileName.length() - ".mlua".length());
        patches.put(id.toLowerCase(Locale.ROOT), PatchEngine.parse(id, text));
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0)
            out.write(buffer, 0, n);
        byte[] bytes = out.toByteArray();
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, start, bytes.length - start)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, start, bytes.length - start, StandardCharsets.ISO_8859_1);
        }
    }
}
