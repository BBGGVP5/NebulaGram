"""Check the exteraGram-style UI additions: menu drag, tab scroll threshold, tablet panes and the feed model."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
tree = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram'
work = root / 'build/extera-ui-check'
src = work / 'src'
(src / 'app/nebulagram/ui').mkdir(parents=True, exist_ok=True)
(src / 'org/telegram/messenger').mkdir(parents=True, exist_ok=True)


def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


(src / 'app/nebulagram/ui/NebulaFeatureSettings.java').write_text('''package app.nebulagram.ui;
public final class NebulaFeatureSettings {
    public static boolean value = true;
    public static boolean enabled(String key) { return value; }
}
''', encoding='utf-8')
(src / 'org/telegram/messenger/AndroidUtilities.java').write_text('''package org.telegram.messenger;
public final class AndroidUtilities { public static int dp(float v) { return Math.round(v); } }
''', encoding='utf-8')

panes = (ui / 'NebulaTabletPanes.java').read_text(encoding='utf-8')
constants = re.search(r'public static final float UNSET = -1f;\s*public static final float MIN_FRACTION = [^;]+;', panes).group(0)
(src / 'Panes.java').write_text('final class Panes {\n' + constants + '\n'
                                + method(panes, 'public static int width(') + '\n'
                                + method(panes, 'public static float fractionAt(') + '\n}\n', encoding='utf-8')

(src / 'CheckExteraUi.java').write_text(r'''
import app.nebulagram.ui.*;
import java.util.*;

public class CheckExteraUi {
    static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }

    static final class P implements NebulaFeedModel.Post {
        final long channel; final int id, date;
        P(long channel, int id, int date) { this.channel = channel; this.id = id; this.date = date; }
        public long channel() { return channel; }
        public int id() { return id; }
        public int date() { return date; }
    }

    public static void main(String[] args) {
        // Меню: отпускание на месте не выбирает пункт; выбор — только после сдвига.
        NebulaMenuDragTracker drag = new NebulaMenuDragTracker();
        check(!drag.move(10, 10), "no tracking before the menu is shown");
        drag.begin(100, 100, 10);
        check(!drag.move(105, 106) && !drag.active(), "a jitter under the slop keeps the menu as is");
        check(drag.move(100, 120) && drag.active(), "a real drag turns into selection");
        check(!drag.move(100, 140), "activation is reported once");
        drag.reset();
        check(!drag.tracking() && !drag.active(), "reset clears the gesture");
        int[] rects = {0, 0, 100, 40, 0, 40, 100, 80, 0, 60, 100, 100};
        check(NebulaMenuDragTracker.pick(50, 20, rects, 3) == 0, "first item");
        check(NebulaMenuDragTracker.pick(50, 70, rects, 3) == 2, "the upper overlapping item wins");
        check(NebulaMenuDragTracker.pick(50, 100, rects, 3) == -1, "bottom edge is exclusive");
        check(NebulaMenuDragTracker.pick(150, 20, rects, 3) == -1, "outside the menu");

        // Панель: лёгкая прокрутка её не прячет.
        NebulaTabScroll scroll = new NebulaTabScroll();
        for (int i = 0; i < 6; i++) check(!scroll.scrolled(10, false, true, 72, 32), "60 px stays visible");
        check(!scroll.scrolled(-5, false, true, 72, 32) && !scroll.hidden(), "direction change resets the path");
        for (int i = 0; i < 6; i++) scroll.scrolled(10, false, true, 72, 32);
        check(!scroll.hidden(), "the reset path must be travelled again");
        check(scroll.scrolled(20, false, true, 72, 32) && scroll.hidden(), "80 px continuous hides");
        check(!scroll.scrolled(-20, false, true, 72, 32) && scroll.hidden(), "a small pull back keeps it hidden");
        check(scroll.scrolled(-20, false, true, 72, 32) && !scroll.hidden(), "a deliberate pull back shows it");
        scroll.scrolled(200, false, true, 72, 32);
        check(!scroll.scrolled(-100, false, false, 72, 32) && scroll.hidden(), "programmatic scroll is ignored");
        check(scroll.scrolled(0, true, false, 72, 32) && !scroll.hidden(), "the top of the list always shows the bar");
        NebulaFeatureSettings.value = false;
        check(NebulaTabScroll.visible(), "disabled option never hides the bar");
        NebulaFeatureSettings.value = true;

        // Планшет: доля зажата, обе панели не уже минимумов.
        check(Panes.width(1600, 0, 0, Panes.UNSET, 280, 360, 560) == 560, "no saved width keeps Telegram's");
        check(Panes.width(1600, 0, 0, .5f, 280, 360, 560) == 800, "half of the content");
        check(Panes.width(1600, 40, 0, .5f, 280, 360, 560) == 40 + 780, "the left inset stays outside the fraction");
        check(Panes.width(1600, 0, 0, .9f, 280, 360, 560) == Math.round(1600 * Panes.MAX_FRACTION), "fraction upper clamp");
        check(Panes.width(1000, 0, 0, .62f, 280, 500, 560) == 500, "the right pane keeps its minimum");
        check(Panes.width(1000, 0, 0, .1f, 280, 360, 560) == 280, "the left pane keeps its minimum");
        check(Panes.width(500, 0, 0, .3f, 280, 360, 200) == 140, "too narrow: the right pane wins");
        check(Panes.fractionAt(5000, 1000, 0, 0) == Panes.MAX_FRACTION, "dragging past the edge clamps");
        check(Panes.fractionAt(150, 1000, 100, 0) == Panes.MIN_FRACTION, "a near-zero drag clamps");

        // Лента: категории, скрытие, удаление канала и формат хранения.
        NebulaFeedModel model = new NebulaFeedModel();
        model.setChannel(1, true); model.setChannel(2, true); model.setChannel(3, true);
        NebulaFeedModel.Category news = model.create("📰", "Новости | и всё\nпрочее");
        NebulaFeedModel.Category games = model.create("🎮", "Games");
        NebulaFeedModel.Category music = model.create("", "Music");
        news.channels.add(1L); news.channels.add(2L); games.channels.add(3L); games.channels.add(99L);
        check(model.channelsOf(games).equals(Arrays.asList(3L)), "a category only shows channels still in the feed");
        check(model.channelsOf(null).equals(Arrays.asList(1L, 2L, 3L)), "All lists the feed channels");
        model.move(2, 0);
        check(model.categories.get(0) == music && model.categories.get(1) == news, "drag reorders the categories");
        music.hidden = true;
        check(model.tabs().size() == 2 && model.tabs().get(0) == news, "hidden categories have no tab");
        model.setChannel(2, false);
        check(!news.channels.contains(2L), "a channel leaving the feed leaves every category");
        NebulaFeedModel copy = NebulaFeedModel.parse(model.serialize());
        check(copy.serialize().equals(model.serialize()), "the stored form round-trips");
        check(copy.categories.get(1).name.equals("Новости | и всё\nпрочее") && copy.categories.get(0).hidden, "names and visibility survive");
        NebulaFeedModel.Category created = copy.create("⭐", "New");
        check(created.id > games.id, "new ids never reuse stored ones");
        check(NebulaFeedModel.parse("garbage").categories.isEmpty(), "an unknown format starts empty");
        check(NebulaFeedModel.parse("v1\nk|x|0|a|b|\nk|7|0|%F0%9F%8E%B5|Ok|1\n").categories.size() == 1, "a broken line is skipped");
        List<P> posts = NebulaFeedModel.newestFirst(Arrays.asList(new P(1, 5, 100), new P(2, 9, 300), new P(1, 6, 300), new P(2, 9, 300)), 10);
        check(posts.size() == 3, "duplicates collapse");
        check(posts.get(0).channel == 1 && posts.get(0).id == 6 && posts.get(1).id == 9 && posts.get(2).id == 5, "newest first, stable order on ties");
        check(NebulaFeedModel.newestFirst(posts, 2).size() == 2, "limit");
        System.out.println("menu drag, tab scroll threshold, tablet panes and feed model hold");
    }
}
''', encoding='utf-8')

sources = [src / 'CheckExteraUi.java', src / 'Panes.java', src / 'app/nebulagram/ui/NebulaFeatureSettings.java',
           src / 'org/telegram/messenger/AndroidUtilities.java',
           ui / 'NebulaMenuDragTracker.java', ui / 'NebulaTabScroll.java', ui / 'NebulaFeedModel.java']
classes = work / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(classes)] + [str(s) for s in sources], check=True)
subprocess.run(['java', '-cp', str(classes), 'CheckExteraUi'], check=True)

# Хуки в Telegram на месте.
def read(path):
    return (tree / path).read_text(encoding='utf-8')

popup = read('ui/ActionBar/ActionBarPopupWindow.java')
assert popup.count('app.nebulagram.ui.NebulaMenuDrag.shown(this);') == 3, 'every popup show registers the drag'
assert 'NebulaMenuDrag.intercept(ev, super::dispatchTouchEvent)' in read('ui/LaunchActivity.java')
assert 'NebulaMenuDrag.openByDrag()' in read('ui/ActionBar/ActionBarMenuItem.java')
tab = read('ui/Components/glass/GlassTabView.java')
assert 'nebulaSpring.selectionScale()' in tab and 'canvas.restoreToCount(nebulaPressSave);' in tab
dialogs = read('ui/DialogsActivity.java')
assert 'NebulaTabScroll.onListScrolled(dy' in dialogs and 'NebulaTabScroll.visible()' in dialogs
assert 'NebulaTabletPanes.leftWidth(fullWidth, insetLeft, insetRight, nebulaDefault)' in read('messenger/AndroidUtilities.java')
launch = read('ui/LaunchActivity.java')
assert 'nebulaDivider.intercept(ev, nebulaSplit()' in launch and 'nebulaDivider.touch(ev)' in launch
camera = read('ui/Components/InstantCameraView2.java')
assert 'addView(nebulaZoomSlider, nebulaZoomParams);' in camera and 'session.setZoom(zoomProgress);\n        nebulaZoomSlider.setCurrent(factor);' in camera
art = (ui / 'NebulaProfileArt.java').read_text(encoding='utf-8')
assert 'profile_m3_buttons' in art and 'rect.height() / 2f' in art, 'Material 3 pills'
settings = (ui / 'NebulaFeedSettingsFragment.java').read_text(encoding='utf-8')
assert 'Categories appear as tabs at the top of the feed. Drag to reorder, tap the eye to hide a tab.' in settings
print('exteraGram UI hooks are in place')
