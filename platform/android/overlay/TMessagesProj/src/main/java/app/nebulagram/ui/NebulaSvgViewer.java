package app.nebulagram.ui;

import android.app.Activity;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

/**
 * Routes SVG documents from chats and shared media to the built-in viewer (setting "svg_viewer",
 * off by default). The content decides, not the name: the first 4 KB must open an svg element.
 */
public final class NebulaSvgViewer {
    private NebulaSvgViewer() { }
    /** Larger drawings are left to other apps: the viewer reads the whole file into memory. */
    static final long MAX_BYTES = 8L * 1024 * 1024;

    public static boolean isSvg(String fileName, String mimeType) {
        return "image/svg+xml".equalsIgnoreCase(mimeType)
                || fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    /** Opens {@code file} and returns true, or returns false to keep Telegram's behaviour. */
    public static boolean open(File file, String fileName, String mimeType, Activity activity, boolean restricted) {
        if (!NebulaMediaPreferences.svgViewer() || file == null || !file.exists() || file.length() > MAX_BYTES || !isSvg(fileName, mimeType)) return false;
        if (!looksLikeSvg(head(file))) return false;
        BaseFragment host = LaunchActivity.getSafeLastFragment();
        if (host == null || activity == null) return false;
        String title = fileName == null || fileName.isEmpty() ? file.getName() : fileName;
        return host.presentFragment(new NebulaSvgViewerFragment(file, title, restricted));
    }

    private static String head(File file) {
        byte[] buffer = new byte[4096];
        try (FileInputStream input = new FileInputStream(file)) {
            int n = input.read(buffer);
            return n <= 0 ? "" : new String(buffer, 0, n, StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "";
        }
    }

    /** An XML prolog, comments or a doctype may come first; an svg start tag must follow. */
    static boolean looksLikeSvg(String head) {
        if (head == null) return false;
        String s = head.startsWith("﻿") ? head.substring(1) : head;
        s = s.trim();
        if (!s.startsWith("<")) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        int tag = lower.indexOf("<svg");
        if (tag < 0 || tag + 4 >= lower.length()) return false;
        char next = lower.charAt(tag + 4);
        if (!(Character.isWhitespace(next) || next == '>' || next == '/')) return false;
        // Nothing but prolog, comments and doctype before the root element.
        String before = lower.substring(0, tag).replaceAll("(?s)<\\?xml.*?\\?>|<!--.*?-->|<!doctype[^>]*>", "").trim();
        return before.isEmpty();
    }

    /** A viewBox lets the drawing scale to the screen; without one it keeps its own size. */
    static boolean fits(String svg) {
        String lower = svg.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("<svg");
        int end = start < 0 ? -1 : lower.indexOf('>', start);
        return end > start && lower.substring(start, end).contains("viewbox");
    }

    /**
     * The page the WebView shows: the drawing centred on the theme background, scaled to fit.
     * Scripts stay off in the WebView and every external load is refused, so the file is shown, never run.
     */
    static String page(String svg, int background, boolean checkerboard) {
        String color = String.format(Locale.ROOT, "#%06X", background & 0xFFFFFF);
        String board = checkerboard
                ? "background-color:#fff;background-image:linear-gradient(45deg,#ddd 25%,transparent 25%),linear-gradient(-45deg,#ddd 25%,transparent 25%),linear-gradient(45deg,transparent 75%,#ddd 75%),linear-gradient(-45deg,transparent 75%,#ddd 75%);background-size:20px 20px;background-position:0 0,0 10px,10px -10px,-10px 0;"
                : "";
        String body = svg.replaceFirst("^﻿", "").replaceFirst("(?s)^\\s*<\\?xml.*?\\?>", "");
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,minimum-scale=0.25,maximum-scale=8,user-scalable=yes\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:\">"
                + "<style>html,body{margin:0;height:100%;background:" + color + ";}"
                + "body{display:flex;align-items:center;justify-content:center;}"
                + ".f{" + board + "max-width:100vw;max-height:100vh;display:flex;}"
                + (fits(body) ? ".f>svg{width:100vw;height:100vh;}" : ".f>svg{max-width:100vw;max-height:100vh;}") + "</style></head>"
                + "<body><div class=\"f\">" + body + "</div></body></html>";
    }
}
