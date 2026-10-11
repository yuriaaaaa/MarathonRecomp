package com.marathonrecomp.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sonic '06 asset packages (.pkg): a big-endian BINA file listing categories ("motion")
 * of friendly names mapped to file paths. Layout follows the Marathon library's
 * AssetPackage, which the Mod Manager uses for PackageAdd/PackageEdit/PackageErase.
 */
final class AssetPackage {
    static final class Category {
        String name;
        final List<String[]> files = new ArrayList<>(); // { friendly name, file path }
    }

    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");
    private static final int HEADER = 0x20;

    final List<Category> categories = new ArrayList<>();
    private byte[] header = new byte[HEADER];

    static AssetPackage read(byte[] data) throws IOException {
        if (data.length < HEADER + 16 || data[0x18] != 'B' || data[0x19] != 'I' || data[0x1A] != 'N' || data[0x1B] != 'A')
            throw new IOException("not a BINA package");
        ByteBuffer buffer = ByteBuffer.wrap(data); // big-endian
        AssetPackage pkg = new AssetPackage();
        System.arraycopy(data, 0, pkg.header, 0, HEADER);
        int categoryCount = buffer.getInt(HEADER + 8);
        int categoriesPos = buffer.getInt(HEADER + 12) + HEADER;
        for (int i = 0; i < categoryCount; i++) {
            int entry = categoriesPos + i * 12;
            Category category = new Category();
            category.name = string(data, buffer.getInt(entry) + HEADER);
            int count = buffer.getInt(entry + 4);
            int filesPos = buffer.getInt(entry + 8) + HEADER;
            for (int f = 0; f < count; f++) {
                int file = filesPos + f * 8;
                category.files.add(new String[] {
                        string(data, buffer.getInt(file) + HEADER),
                        string(data, buffer.getInt(file + 4) + HEADER) });
            }
            pkg.categories.add(category);
        }
        return pkg;
    }

    private static String string(byte[] data, int offset) throws IOException {
        if (offset < 0 || offset >= data.length)
            throw new IOException("package string offset out of range");
        int end = offset;
        while (end < data.length && data[end] != 0)
            end++;
        return new String(data, offset, end - offset, SHIFT_JIS);
    }

    byte[] write() {
        Body body = new Body();
        Map<String, List<Integer>> strings = new LinkedHashMap<>(); // string -> positions pointing at it
        List<Integer> offsets = new ArrayList<>();                  // absolute positions of every offset

        int fileCount = 0;
        for (Category category : categories)
            fileCount += category.files.size();

        body.u32(fileCount);
        int fileEntriesField = body.size();
        offsets.add(fileEntriesField);
        body.u32(0);
        body.u32(categories.size());
        int categoryEntriesField = body.size();
        offsets.add(categoryEntriesField);
        body.u32(0);

        body.patch(categoryEntriesField, body.size() - HEADER);
        int[] filesFields = new int[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            addString(body, strings, offsets, category.name);
            body.u32(category.files.size());
            filesFields[i] = body.size();
            offsets.add(filesFields[i]);
            body.u32(0);
        }

        body.patch(fileEntriesField, body.size() - HEADER);
        for (int i = 0; i < categories.size(); i++) {
            body.patch(filesFields[i], body.size() - HEADER);
            for (String[] file : categories.get(i).files) {
                addString(body, strings, offsets, file[0]);
                addString(body, strings, offsets, file[1]);
            }
        }

        // String table.
        body.pad(4);
        for (Map.Entry<String, List<Integer>> entry : strings.entrySet()) {
            for (int field : entry.getValue())
                body.patch(field, body.size() - HEADER);
            byte[] bytes = entry.getKey().getBytes(SHIFT_JIS);
            body.out.write(bytes, 0, bytes.length);
            body.out.write(0);
        }
        body.pad(4);

        // Offset table: each position as a delta (in words) from the previous one.
        int footerStart = body.size();
        int last = HEADER;
        for (int position : offsets) {
            int delta = (position - last) >> 2;
            if (delta <= 0x3F) {
                body.out.write(0x40 | delta);
            } else if (delta <= 0x3FFF) {
                body.out.write(0x80 | (delta >> 8));
                body.out.write(delta & 0xFF);
            } else {
                body.out.write(0xC0 | ((delta >> 24) & 0x3F));
                body.out.write((delta >> 16) & 0xFF);
                body.out.write((delta >> 8) & 0xFF);
                body.out.write(delta & 0xFF);
            }
            last = position;
        }
        body.pad(4);

        byte[] result = body.out.toByteArray();
        System.arraycopy(header, 0, result, 0, HEADER);
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putInt(0, result.length);
        buffer.putInt(4, footerStart - HEADER);
        buffer.putInt(8, result.length - footerStart);
        return result;
    }

    private static void addString(Body body, Map<String, List<Integer>> strings, List<Integer> offsets, String value) {
        if (value == null || value.isEmpty()) {
            body.u32(0);
            return;
        }
        int field = body.size();
        offsets.add(field);
        strings.computeIfAbsent(value, k -> new ArrayList<>()).add(field);
        body.u32(0);
    }

    private static final class Body {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final List<int[]> patches = new ArrayList<>();

        Body() {
            out.write(new byte[HEADER], 0, HEADER);
        }

        int size() {
            return out.size();
        }

        void u32(int value) {
            out.write(value >>> 24);
            out.write(value >>> 16);
            out.write(value >>> 8);
            out.write(value);
        }

        void pad(int alignment) {
            while (out.size() % alignment != 0)
                out.write(0);
        }

        // ByteArrayOutputStream can't seek, so values are patched in place via reflection-free copy.
        void patch(int position, int value) {
            byte[] current = out.toByteArray();
            current[position] = (byte) (value >>> 24);
            current[position + 1] = (byte) (value >>> 16);
            current[position + 2] = (byte) (value >>> 8);
            current[position + 3] = (byte) value;
            out.reset();
            out.write(current, 0, current.length);
        }
    }

    // ------------------------------------------------------------ edits (Mod Manager semantics)

    void add(String type, String name, String path) {
        for (Category category : categories)
            if (category.name.equals(type))
                category.files.add(new String[] { name, path });
    }

    void edit(String type, String name, String path) {
        for (Category category : categories) {
            if (!category.name.equals(type))
                continue;
            boolean found = false;
            for (int i = 0; i < category.files.size(); i++) {
                if (!category.files.get(i)[0].equals(name))
                    continue;
                found = true;
                if (path != null && !path.isEmpty()) {
                    category.files.get(i)[1] = path;
                } else {
                    category.files.remove(i);
                    i--;
                }
            }
            if (!found)
                category.files.add(new String[] { name, path });
        }
    }

    void erase(String type, String name) {
        for (Category category : categories)
            if (category.name.equals(type))
                category.files.removeIf(file -> file[0].equals(name));
    }
}
