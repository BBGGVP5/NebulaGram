#!/usr/bin/env python3
"""Palette swatches -> accent: compile the real NebulaPictureAccent and assert the mapping.

Also checks the wiring around it: NebulaTheme treats the picture as an accent
source next to Material You, the extraction runs androidx Palette off the UI
thread, the settings row and preview exist, and LaunchActivity starts the
watcher (patch 0310) when a prepared tree is given as argv[1].
"""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'

CHECK = r'''
import app.nebulagram.ui.NebulaPictureAccent;
public class CheckPictureAccent {
    static int checks;
    static void ok(boolean value, String what) { checks++; if (!value) throw new AssertionError(what); }
    static float[] hsl(int c) {
        float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, d = max - min, h = 0, s = 0;
        if (d > 0) {
            s = d / (1f - Math.abs(2f * l - 1f));
            if (max == r) h = ((g - b) / d) % 6f; else if (max == g) h = (b - r) / d + 2f; else h = (r - g) / d + 4f;
            h *= 60f; if (h < 0) h += 360f;
        }
        return new float[]{h, s, l};
    }
    static double luminance(int c) {
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            double x = (c >> (16 - 8 * i) & 255) / 255.0;
            v[i] = x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }
    static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }
    static float hueDistance(float a, float b) { float d = Math.abs(a - b) % 360f; return Math.min(d, 360f - d); }
    public static void main(String[] args) {
        // Grey pictures have no accent: the previous one must stay.
        ok(NebulaPictureAccent.seed(new int[]{0xFF808080, 0xFF101010, 0xFFF0F0F0}, new int[]{500, 300, 200}) == 0, "grey picture");
        ok(NebulaPictureAccent.seed(new int[0], new int[0]) == 0, "empty");
        ok(NebulaPictureAccent.seed(null, null) == 0, "null");
        ok(NebulaPictureAccent.seed(new int[]{0xFFFF0000}, new int[]{0}) == 0, "zero population");
        ok(NebulaPictureAccent.tone(0, true) == 0 && NebulaPictureAccent.tone(0, false) == 0, "no seed, no accent");
        // A portrait: mostly beige skin and a grey wall, a small vivid blue jacket.
        int skin = 0xFFD9B8A0, wall = 0xFF9A9A9A, jacket = 0xFF1E5BD8;
        int seed = NebulaPictureAccent.seed(new int[]{skin, wall, jacket}, new int[]{4000, 5000, 900});
        ok(seed == jacket, "colourful swatch beats the common dull one: " + Integer.toHexString(seed));
        // Population still matters among equally colourful swatches.
        ok(NebulaPictureAccent.seed(new int[]{0xFFE53935, 0xFF1E88E5}, new int[]{100, 3000}) == 0xFF1E88E5, "population");
        // Alpha in the input is ignored.
        ok(NebulaPictureAccent.seed(new int[]{0x001E88E5}, new int[]{10}) == 0xFF1E88E5, "alpha");
        int[] seeds = {jacket, 0xFFE53935, 0xFF43A047, 0xFFFFEB3B, 0xFF8E24AA, 0xFF00ACC1, 0xFFFF6F00, 0xFF3E2723, 0xFFFFCDD2, 0xFF0D47A1};
        for (int s : seeds) {
            int dark = NebulaPictureAccent.tone(s, true), light = NebulaPictureAccent.tone(s, false);
            ok((dark >>> 24) == 255 && (light >>> 24) == 255, "opaque");
            float[] d = hsl(dark), l = hsl(light), o = hsl(s);
            ok(d[2] >= .61f && d[2] <= .95f, "dark lightness " + Integer.toHexString(s) + " " + d[2]);
            ok(l[2] >= .05f && l[2] <= .49f, "light lightness " + Integer.toHexString(s) + " " + l[2]);
            ok(hueDistance(d[0], o[0]) < 4f && hueDistance(l[0], o[0]) < 4f, "hue kept " + Integer.toHexString(s));
            ok(d[1] >= .36f && l[1] >= .36f && d[1] <= .87f && l[1] <= .87f, "saturation band " + Integer.toHexString(s));
            // Readable: accent text on Telegram's dark and light surfaces.
            ok(contrast(dark, 0xFF1C242C) >= 3.0, "dark contrast " + Integer.toHexString(s) + " " + contrast(dark, 0xFF1C242C));
            ok(contrast(light, 0xFFFFFFFF) >= 3.0, "light contrast " + Integer.toHexString(s) + " " + contrast(light, 0xFFFFFFFF));
            ok(NebulaPictureAccent.tone(s, true) == dark, "deterministic");
        }
        // Preview order: best first, a permutation of all indices.
        int[] order = NebulaPictureAccent.order(new int[]{skin, wall, jacket}, new int[]{4000, 5000, 900});
        ok(order.length == 3 && order[0] == 2, "order puts the accent first");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i : order) seen.add(i);
        ok(seen.size() == 3, "order is a permutation");
        ok(NebulaPictureAccent.order(null, null).length == 0, "order of nothing");
        System.out.println(checks + " picture accent checks passed");
    }
}
'''


def main():
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        raise SystemExit('A JDK is required for the picture accent check')
    with tempfile.TemporaryDirectory(prefix='nebula-picture-') as temp:
        tree = Path(temp)
        (tree / 'CheckPictureAccent.java').write_text(CHECK, encoding='utf-8')
        subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(tree / 'classes'),
                        str(UI / 'NebulaPictureAccent.java'), str(tree / 'CheckPictureAccent.java')], check=True)
        subprocess.run([java, '-cp', str(tree / 'classes'), 'CheckPictureAccent'], check=True)

    theme = (UI / 'NebulaTheme.java').read_text(encoding='utf-8')
    picture = (UI / 'NebulaPictureTheme.java').read_text(encoding='utf-8')
    section = (UI / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
    preview = (UI / 'NebulaPictureAccentPreview.java').read_text(encoding='utf-8')
    assert 'int picture = pictureAccent(Theme.isCurrentThemeDark());' in theme, 'picture source is read first'
    assert 'int accent = picture != 0 ? picture :' in theme, 'picture accent outranks Material You'
    assert 'public static void refreshAccentSource(Context context)' in theme
    assert 'NebulaPictureAccent.tone(seed, dark)' in theme, 'accent follows the theme brightness'
    assert 'Palette.from(sample)' in picture and 'Utilities.globalQueue.postRunnable' in picture, 'Palette runs off the UI thread'
    for event in ('didSetNewWallpapper', 'activeAccountChanged', 'updateInterfaces', 'mainUserInfoChanged', 'fileLoaded'):
        assert 'NotificationCenter.' + event in picture, 'refresh on ' + event
    assert 'ticket != generation' in picture, 'a stale extraction must not win'
    assert 'Цвета из аватарки / обоев' in section and 'Colours from avatar / wallpaper' in section
    assert 'new NebulaPictureAccentPreview(context)' in section, 'live preview in Appearance'
    assert 'NebulaPictureTheme.clearListener(listener)' in preview, 'preview stops listening when detached'
    patch = ROOT / 'patches/android/0310-picture-accent-watch.patch'
    assert '+        app.nebulagram.ui.NebulaPictureTheme.start();' in patch.read_text(encoding='utf-8')
    if len(sys.argv) > 1:
        launch = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java'
        text = launch.read_text(encoding='utf-8')
        assert ('NebulaTheme.applyMaterialYou(this);\n        app.nebulagram.ui.NebulaPictureTheme.start();') in text, \
            'LaunchActivity starts the picture watcher next to Material You'
    print('picture accent wiring checked')


if __name__ == '__main__':
    main()
