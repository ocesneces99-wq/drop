package com.humanlike.mkr;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Reads and writes the custom .mkr recording format (gzip-compressed binary). */
public final class RecordingIO {

    private static final int MAGIC = 0x4D4B5231; // "MKR1"
    private static final int VERSION = 1;
    public static final String EXTENSION = ".mkr";

    public record Info(String name, Path path, long durationNanos, int count) {
    }

    private RecordingIO() {
    }

    public static String sanitize(String name) {
        if (name == null) {
            return "recording";
        }
        String s = name.trim().replaceAll("[^A-Za-z0-9 _.\\-]", "_");
        if (s.length() > 60) {
            s = s.substring(0, 60);
        }
        s = s.trim();
        return s.isEmpty() ? "recording" : s;
    }

    public static Path pathFor(Path dir, String name) {
        return dir.resolve(sanitize(name) + EXTENSION);
    }

    public static void save(Path dir, Recording r) throws IOException {
        Files.createDirectories(dir);
        r.name = sanitize(r.name);
        Path target = pathFor(dir, r.name);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(r.name);
            out.writeInt(r.windowW);
            out.writeInt(r.windowH);
            out.writeLong(r.durationNanos);
            out.writeInt(r.events.size());
            for (Recording.Ev e : r.events) {
                out.writeByte(e.type);
                out.writeLong(e.t);
                switch (e.type) {
                    case Recording.Ev.KEY -> {
                        out.writeInt(e.a);
                        out.writeInt(e.b);
                        out.writeInt(e.c);
                        out.writeInt(e.d);
                    }
                    case Recording.Ev.CHAR -> out.writeInt(e.a);
                    case Recording.Ev.MOVE -> {
                        out.writeBoolean(e.captured);
                        out.writeDouble(e.x);
                        out.writeDouble(e.y);
                        out.writeDouble(e.ax);
                        out.writeDouble(e.ay);
                    }
                    case Recording.Ev.BUTTON -> {
                        out.writeInt(e.a);
                        out.writeInt(e.c);
                        out.writeInt(e.d);
                    }
                    case Recording.Ev.SCROLL -> {
                        out.writeDouble(e.x);
                        out.writeDouble(e.y);
                    }
                    default -> throw new IOException("Unknown event type " + e.type);
                }
            }
        }
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static Recording load(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            Recording r = new Recording();
            int count = readHeader(in, r);
            for (int i = 0; i < count; i++) {
                Recording.Ev e = new Recording.Ev();
                e.type = in.readByte();
                e.t = in.readLong();
                switch (e.type) {
                    case Recording.Ev.KEY -> {
                        e.a = in.readInt();
                        e.b = in.readInt();
                        e.c = in.readInt();
                        e.d = in.readInt();
                    }
                    case Recording.Ev.CHAR -> e.a = in.readInt();
                    case Recording.Ev.MOVE -> {
                        e.captured = in.readBoolean();
                        e.x = in.readDouble();
                        e.y = in.readDouble();
                        e.ax = in.readDouble();
                        e.ay = in.readDouble();
                    }
                    case Recording.Ev.BUTTON -> {
                        e.a = in.readInt();
                        e.c = in.readInt();
                        e.d = in.readInt();
                    }
                    case Recording.Ev.SCROLL -> {
                        e.x = in.readDouble();
                        e.y = in.readDouble();
                    }
                    default -> throw new IOException("Corrupt recording: unknown event type " + e.type);
                }
                r.events.add(e);
            }
            return r;
        }
    }

    private static int readHeader(DataInputStream in, Recording r) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a .mkr recording");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported .mkr version " + version);
        }
        r.name = in.readUTF();
        r.windowW = in.readInt();
        r.windowH = in.readInt();
        r.durationNanos = in.readLong();
        return in.readInt();
    }

    public static List<Info> list(Path dir) {
        List<Info> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + EXTENSION)) {
            for (Path p : stream) {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(p))))) {
                    Recording r = new Recording();
                    int count = readHeader(in, r);
                    result.add(new Info(r.name, p, r.durationNanos, count));
                } catch (Exception ignored) {
                    // skip unreadable files
                }
            }
        } catch (IOException ignored) {
            // empty list
        }
        result.sort(Comparator.comparing(i -> i.name().toLowerCase()));
        return result;
    }

    public static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
}
