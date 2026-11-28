package app.nebulagram.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded sfnt reader shared by the document importer and regression tests. */
public final class NebulaFontFile {
    public static final int LIMIT = 8 * 1024 * 1024;
    private NebulaFontFile() { }
    public static byte[] read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing font");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > LIMIT) throw new IOException("Font exceeds 8 MiB");
            out.write(buffer, 0, count);
        }
        byte[] data = out.toByteArray();
        validate(data);
        return data;
    }
    public static void validate(byte[] data) throws IOException {
        if (data.length < 12 || data.length > LIMIT) throw new IOException("Invalid font size");
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int format = b.getInt();
        if (format != 0x00010000 && format != 0x4f54544f && format != 0x74727565)
            throw new IOException("Expected TTF or OTF");
        int tables = b.getShort() & 0xffff;
        int directory = 12 + tables * 16;
        if (tables == 0 || tables > 256 || directory > data.length) throw new IOException("Invalid font directory");
        boolean cmap = false, head = false;
        for (int i = 0; i < tables; i++) {
            b.position(12 + i * 16);
            int tag = b.getInt(); b.getInt();
            long offset = b.getInt() & 0xffffffffL, length = b.getInt() & 0xffffffffL;
            if (offset < directory || offset + length > data.length) throw new IOException("Invalid font table");
            cmap |= tag == 0x636d6170 && length > 0;
            head |= tag == 0x68656164 && length >= 54;
        }
        if (!cmap || !head) throw new IOException("Missing required font tables");
    }
}
