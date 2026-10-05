package app.nebulagram.ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Interoperable .icons ZIP reader. No extraction, scripts, references or unbounded images. */
public final class NebulaIconArchive {
    public final String id, name, author, version;
    public final Map<String, byte[]> icons;
    private NebulaIconArchive(JSONObject meta, Map<String, byte[]> icons) throws Exception {
        id = meta.getString("packId"); name = meta.getString("packName"); author = meta.optString("author"); version = meta.optString("version");
        if (!id.matches("[a-zA-Z0-9._-]{1,80}") || name.trim().isEmpty() || name.length() > 100 || author.length() > 100 || version.length() > 64) throw new IOException("Invalid metadata");
        this.icons = Collections.unmodifiableMap(icons);
    }
    public static String path(String name) throws IOException {
        if (name == null || name.length() > 200 || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.indexOf('\0') >= 0) throw new IOException("Invalid path");
        for (String part : name.split("/", -1)) if (part.equals("..") || part.equals(".") || part.isEmpty()) throw new IOException("Invalid path");
        return name;
    }
    public static NebulaIconArchive read(InputStream input) throws Exception {
        HashMap<String, byte[]> files = new HashMap<>(); int total = 0, count = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 2048) throw new IOException("Too many entries");
                String name = entry.getName();
                if (entry.isDirectory()) { path(name.substring(0, name.length() - 1)); continue; }
                path(name); if (files.containsKey(name)) throw new IOException("Duplicate entry");
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
                while ((n = zip.read(buffer)) != -1) {
                    if (out.size() + n > 262144 || (total += n) > 32_000_000) throw new IOException("Archive too large");
                    out.write(buffer, 0, n);
                }
                files.put(name, out.toByteArray());
            }
        }
        byte[] metadata = files.get("metadata.json"); if (metadata == null || metadata.length > 131072) throw new IOException("Missing metadata");
        JSONObject meta = new JSONObject(new String(metadata, StandardCharsets.UTF_8));
        if (meta.optInt("schemaVersion", 1) != 1) throw new IOException("Unsupported pack format");
        JSONObject mapping = meta.getJSONObject("icons");
        if (mapping.length() < 1 || mapping.length() > 512) throw new IOException("Invalid icon count");
        LinkedHashMap<String, byte[]> icons = new LinkedHashMap<>();
        for (Iterator<String> keys = mapping.keys(); keys.hasNext();) {
            String key = keys.next(); if (!key.matches("[a-z][a-z0-9_]{0,99}")) throw new IOException("Invalid resource name");
            String file = path(mapping.getString(key)); byte[] bytes = files.get(file);
            if (bytes == null || bytes.length == 0) throw new IOException("Missing icon");
            if (file.toLowerCase(Locale.ROOT).endsWith(".svg")) {
                String svg = new String(bytes, StandardCharsets.UTF_8), lower = svg.toLowerCase(Locale.ROOT);
                if (!lower.contains("<svg") || lower.contains("<!") || lower.contains("<script") || lower.contains("<image") || lower.contains("<foreignobject")
                        || lower.contains("href") || lower.matches("(?s).*\\bon[a-z]+\\s*=.*") || lower.contains("url(")) throw new IOException("Unsupported SVG");
                icons.put(key, bytes);
            } else if (file.toLowerCase(Locale.ROOT).endsWith(".png")) {
                if (bytes.length < 24 || bytes[0] != (byte) 137 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') throw new IOException("Invalid PNG");
                long w = uint(bytes, 16), h = uint(bytes, 20); if (w < 1 || h < 1 || w > 1024 || h > 1024) throw new IOException("Image too large");
                icons.put(key, bytes);
            } else if (file.toLowerCase(Locale.ROOT).endsWith(".webp")) {
                if (bytes.length < 16 || !new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") || !new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) throw new IOException("Invalid WebP");
                icons.put(key, bytes);
            } else throw new IOException("Use SVG, PNG or WebP");
        }
        return new NebulaIconArchive(meta, icons);
    }
    private static long uint(byte[] bytes, int at) { long value = 0; for (int i = 0; i < 4; i++) value = (value << 8) | (bytes[at + i] & 255); return value; }
}
