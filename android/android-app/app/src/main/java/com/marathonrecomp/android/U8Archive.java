package com.marathonrecomp.android;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Sonic '06 U8 archives (.arc): a big-endian node table, a name table, then
 * each file's data (zlib-compressed by the game's tools), 32-byte aligned.
 *
 * Merging works on the node tree and copies each file's stored (compressed)
 * bytes straight from its source archive, so nothing is recompressed.
 */
final class U8Archive {
    private static final int SIGNATURE = 0x55AA382D;
    private static final int NODE_SIZE = 16;
    private static final int ALIGNMENT = 0x20;
    // Header words after the data offset, identical in every game archive.
    private static final int HEADER_WORD_4 = 0xE4F91200;
    private static final int HEADER_WORD_5 = 0x00000402;

    static final class Node {
        String name;
        boolean directory;
        final List<Node> children = new ArrayList<>();
        // Files: where the stored bytes live (a source file, or stored in memory after an edit).
        File source;
        long dataOffset;
        int length;
        int uncompressedSize;
        byte[] stored;
    }

    final Node root;

    private U8Archive(Node root) {
        this.root = root;
    }

    static U8Archive read(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[16];
            input.readFully(header);
            ByteBuffer headerBuffer = ByteBuffer.wrap(header);
            if (headerBuffer.getInt(0) != SIGNATURE)
                throw new IOException(file.getName() + " is not a U8 archive");

            int tableOffset = headerBuffer.getInt(4);
            int tableLength = headerBuffer.getInt(8);
            if (tableOffset < 16 || tableLength < NODE_SIZE || tableOffset + (long) tableLength > input.length())
                throw new IOException(file.getName() + " has a damaged node table");

            byte[] table = new byte[tableLength];
            input.seek(tableOffset);
            input.readFully(table);
            ByteBuffer tableBuffer = ByteBuffer.wrap(table);

            int count = tableBuffer.getInt(8);
            if (count < 1 || count * (long) NODE_SIZE > tableLength)
                throw new IOException(file.getName() + " has a damaged node count");

            int namesBase = count * NODE_SIZE;
            Node[] nodes = new Node[count];
            int[] ends = new int[count];
            for (int i = 0; i < count; i++) {
                int base = i * NODE_SIZE;
                int flags = tableBuffer.getInt(base);
                Node node = new Node();
                node.directory = (flags >>> 24) == 1;
                node.name = readName(table, namesBase + (flags & 0xFFFFFF));
                if (node.directory) {
                    ends[i] = tableBuffer.getInt(base + 8);
                } else {
                    node.source = file;
                    node.dataOffset = tableBuffer.getInt(base + 4) & 0xFFFFFFFFL;
                    node.length = tableBuffer.getInt(base + 8);
                    node.uncompressedSize = tableBuffer.getInt(base + 12);
                }
                nodes[i] = node;
            }

            if (!nodes[0].directory)
                throw new IOException(file.getName() + " has no root directory");

            buildTree(nodes, ends, 0);
            return new U8Archive(nodes[0]);
        }
    }

    private static int buildTree(Node[] nodes, int[] ends, int index) throws IOException {
        Node directory = nodes[index];
        int end = Math.min(ends[index], nodes.length);
        int child = index + 1;
        while (child < end) {
            directory.children.add(nodes[child]);
            child = nodes[child].directory ? buildTree(nodes, ends, child) : child + 1;
        }
        return Math.max(end, index + 1);
    }

    private static String readName(byte[] table, int offset) throws IOException {
        if (offset < 0 || offset >= table.length)
            throw new IOException("Archive name offset out of range");
        int end = offset;
        while (end < table.length && table[end] != 0)
            end++;
        return new String(table, offset, end - offset, StandardCharsets.ISO_8859_1);
    }

    // ------------------------------------------------------------ file edits

    /** Finds a file or directory by its path inside the archive ("xenon/player/amy.lub"), ignoring case. */
    Node find(String path) {
        Node current = root;
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty())
                continue;
            if (!current.directory)
                return null;
            Node next = child(current, part);
            if (next == null)
                return null;
            current = next;
        }
        return current;
    }

    private static Node child(Node directory, String name) {
        for (Node node : directory.children)
            if (node.name.equals(name))
                return node;
        for (Node node : directory.children)
            if (node.name.equalsIgnoreCase(name))
                return node;
        return null;
    }

    /** Finds a file, creating it (and its directories) if needed. */
    Node createFile(String path) throws IOException {
        String[] parts = path.replace('\\', '/').split("/");
        Node current = root;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty())
                continue;
            boolean last = i == parts.length - 1;
            Node next = child(current, part);
            if (next == null) {
                next = new Node();
                next.name = part;
                next.directory = !last;
                if (last)
                    next.stored = new byte[0];
                current.children.add(next);
            } else if (next.directory == last) {
                throw new IOException(path + ": a " + (last ? "folder" : "file") + " has that name");
            }
            current = next;
        }
        return current;
    }

    /** Removes a file or directory; returns whether it existed. */
    boolean remove(String path) {
        String clean = path.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        Node parent = slash < 0 ? root : find(clean.substring(0, slash));
        if (parent == null || !parent.directory)
            return false;
        Node node = child(parent, clean.substring(slash + 1));
        return node != null && parent.children.remove(node);
    }

    /** The file's contents, decompressed. */
    byte[] read(Node node) throws IOException {
        byte[] storedBytes = storedBytes(node);
        if (node.length == 0 || node.uncompressedSize == 0)
            return storedBytes;
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(storedBytes);
            byte[] out = new byte[node.uncompressedSize];
            int total = 0;
            while (total < out.length && !inflater.finished()) {
                int n = inflater.inflate(out, total, out.length - total);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary()))
                    break;
                total += n;
            }
            if (total != out.length)
                throw new IOException(node.name + " is damaged (" + total + " of " + out.length + " bytes)");
            return out;
        } catch (DataFormatException e) {
            throw new IOException(node.name + " can't be decompressed: " + e.getMessage());
        } finally {
            inflater.end();
        }
    }

    private static byte[] storedBytes(Node node) throws IOException {
        if (node.stored != null)
            return node.stored;
        byte[] bytes = new byte[node.length];
        try (RandomAccessFile input = new RandomAccessFile(node.source, "r")) {
            input.seek(node.dataOffset);
            input.readFully(bytes);
        }
        return bytes;
    }

    /** Replaces the file's contents (stored zlib-compressed, like the game's own files). */
    static void setContents(Node node, byte[] contents) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(contents);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, contents.length / 2));
            byte[] buffer = new byte[64 * 1024];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
            node.stored = out.toByteArray();
        } finally {
            deflater.end();
        }
        node.source = null;
        node.dataOffset = 0;
        node.length = node.stored.length;
        node.uncompressedSize = contents.length;
        if (contents.length == 0) {
            node.stored = new byte[0];
            node.length = 0;
        }
    }

    /** Files directly inside (or, if recursive, anywhere below) a directory, as path -> node. */
    Map<String, Node> files(String directoryPath, boolean recursive) {
        Map<String, Node> out = new java.util.LinkedHashMap<>();
        Node directory = directoryPath.isEmpty() ? root : find(directoryPath);
        if (directory != null && directory.directory)
            collect(directory, directoryPath.isEmpty() ? "" : directoryPath.replace('\\', '/') + "/", recursive, out);
        return out;
    }

    private static void collect(Node directory, String prefix, boolean recursive, Map<String, Node> out) {
        for (Node node : directory.children) {
            if (!node.directory)
                out.put(prefix + node.name, node);
            else if (recursive)
                collect(node, prefix + node.name + "/", true, out);
        }
    }

    /** A deep copy of the tree; file contents are shared until edited. */
    U8Archive copy() {
        return new U8Archive(copy(root));
    }

    private static Node copy(Node node) {
        Node clone = new Node();
        clone.name = node.name;
        clone.directory = node.directory;
        clone.source = node.source;
        clone.dataOffset = node.dataOffset;
        clone.length = node.length;
        clone.uncompressedSize = node.uncompressedSize;
        clone.stored = node.stored;
        for (Node child : node.children)
            clone.children.add(copy(child));
        return clone;
    }

    static String lower(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    /** Adds or replaces every file of {@code other} in this archive (by path, case-insensitive). */
    void mergeFrom(U8Archive other) {
        mergeDirectory(root, other.root);
    }

    private static void mergeDirectory(Node target, Node source) {
        for (Node incoming : source.children) {
            int existingIndex = -1;
            for (int i = 0; i < target.children.size(); i++) {
                if (target.children.get(i).name.equals(incoming.name)) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex < 0) {
                for (int i = 0; i < target.children.size(); i++) {
                    if (target.children.get(i).name.equalsIgnoreCase(incoming.name)) {
                        existingIndex = i;
                        break;
                    }
                }
            }

            if (existingIndex < 0) {
                target.children.add(incoming);
                continue;
            }

            Node existing = target.children.get(existingIndex);
            if (existing.directory && incoming.directory) {
                mergeDirectory(existing, incoming);
            } else {
                // Keep the game's spelling of the name; take the mod's content.
                incoming.name = existing.name;
                target.children.set(existingIndex, incoming);
            }
        }
    }

    void write(File output) throws IOException {
        List<Node> order = new ArrayList<>();
        List<Integer> parents = new ArrayList<>();
        flatten(root, 0, order, parents);
        int count = order.size();
        int[] ends = new int[count];
        computeEnds(order, ends);

        // Name table: root's empty name first, then each node's name in order.
        java.io.ByteArrayOutputStream names = new java.io.ByteArrayOutputStream();
        int[] nameOffsets = new int[count];
        for (int i = 0; i < count; i++) {
            nameOffsets[i] = names.size();
            byte[] bytes = (i == 0 ? "" : order.get(i).name).getBytes(StandardCharsets.ISO_8859_1);
            names.write(bytes, 0, bytes.length);
            names.write(0);
        }

        int tableLength = count * NODE_SIZE + names.size();
        long dataStart = align(0x20L + tableLength);

        long[] offsets = new long[count];
        long cursor = dataStart;
        boolean first = true;
        for (int i = 0; i < count; i++) {
            Node node = order.get(i);
            if (node.directory)
                continue;
            if (!first)
                cursor = align(cursor);
            first = false;
            offsets[i] = cursor;
            cursor += node.length;
        }
        if (cursor > 0xFFFFFFFFL)
            throw new IOException("Merged archive is larger than 4 GB");

        ByteBuffer table = ByteBuffer.allocate((int) dataStart);
        table.putInt(SIGNATURE).putInt(0x20).putInt(tableLength).putInt((int) dataStart);
        table.putInt(HEADER_WORD_4).putInt(HEADER_WORD_5).putInt(0).putInt(0);
        for (int i = 0; i < count; i++) {
            Node node = order.get(i);
            if (node.directory) {
                table.putInt((1 << 24) | nameOffsets[i]);
                table.putInt(i == 0 ? 0 : parents.get(i));
                table.putInt(ends[i]);
                table.putInt(0);
            } else {
                table.putInt(nameOffsets[i]);
                table.putInt((int) offsets[i]);
                table.putInt(node.length);
                table.putInt(node.uncompressedSize);
            }
        }
        table.put(names.toByteArray());

        Map<File, RandomAccessFile> sources = new HashMap<>();
        byte[] buffer = new byte[1 << 20];
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output), 1 << 20)) {
            out.write(table.array(), 0, (int) dataStart);
            long written = dataStart;
            for (int i = 0; i < count; i++) {
                Node node = order.get(i);
                if (node.directory)
                    continue;
                while (written < offsets[i]) {
                    out.write(0);
                    written++;
                }
                if (node.stored != null) {
                    out.write(node.stored);
                    written += node.length;
                    continue;
                }
                RandomAccessFile input = sources.get(node.source);
                if (input == null) {
                    input = new RandomAccessFile(node.source, "r");
                    sources.put(node.source, input);
                }
                input.seek(node.dataOffset);
                int remaining = node.length;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0)
                        throw new IOException(node.source.getName() + " ends early (" + node.name + ")");
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
                written += node.length;
            }
        } finally {
            for (RandomAccessFile input : sources.values())
                input.close();
        }
    }

    private static void flatten(Node node, int parent, List<Node> order, List<Integer> parents) {
        int index = order.size();
        order.add(node);
        parents.add(parent);
        if (node.directory) {
            for (Node child : node.children)
                flatten(child, index, order, parents);
        }
    }

    private static int computeEnds(List<Node> order, int[] ends) {
        return computeEnds(order, ends, 0);
    }

    private static int computeEnds(List<Node> order, int[] ends, int index) {
        Node node = order.get(index);
        int next = index + 1;
        if (node.directory) {
            for (int c = 0; c < node.children.size(); c++)
                next = computeEnds(order, ends, next);
            ends[index] = next;
        }
        return next;
    }

    private static long align(long value) {
        return (value + ALIGNMENT - 1) & ~(long) (ALIGNMENT - 1);
    }
}
