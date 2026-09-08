"""Exercise recovered menu geometry and verify the native integration points."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
overlay = root / "platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui"
tree = Path(sys.argv[1])
native = tree / "TMessagesProj/src/main/java/org/telegram/ui"
work = root / "build/menu-state-check"
work.mkdir(parents=True, exist_ok=True)


def method(text, signature):
    start = text.index(signature)
    brace = text.index("{", start)
    depth, end = 1, brace + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


counter = (overlay / "NebulaHeaderCounter.java").read_text(encoding="utf-8")
width = (overlay / "NebulaWidthMotion.java").read_text(encoding="utf-8")
width = width.replace("package app.nebulagram.ui;", "").replace(
    "public final class NebulaWidthMotion", "static final class NebulaWidthMotion")
source = r"""
public class CheckMenuState {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static class AndroidUtilities {
  static float density=1;
  static int dp(float v){return (int)Math.ceil(v*density);}
 }
 static class NebulaAppearance {
  static boolean unread,ios;
  static boolean headerUnread(){return unread;}
  static boolean iosUnread(){return ios;}
 }
 COUNTER
 WIDTH
 public static void main(String[] args) {
  for(float density:new float[]{1f,1.5f,2f,2.75f,3f}) {
   AndroidUtilities.density=density;
   for(boolean show:new boolean[]{false,true})
   for(boolean ios:new boolean[]{false,true}) {
    NebulaAppearance.unread=show;NebulaAppearance.ios=ios;
    check(backWidth(0)==AndroidUtilities.dp(58),"zero unread must be circular");
    check(backWidth(-1)==backWidth(0),"negative count must not expand");
    check(backWidth(1)==AndroidUtilities.dp(show&&ios?68:58),"positive count width");
    check(backWidth(100)==backWidth(1),"99+ must fit the same compact capsule");
    check(backWidth()==backWidth(1),"preview geometry must match positive counter");
   }
  }
  NebulaWidthMotion m=new NebulaWidthMotion();
  check(m.resolve(300,0,true)==300,"first layout must not grow from zero");
  check(m.resolve(200,100,true)==300,"target change must not jump");
  float mid=m.resolve(200,240,true);
  check(mid>200&&mid<300,"shrink should animate");
  check(m.resolve(360,240,true)==mid,"interrupted animation must be continuous");
  float last=mid;
  for(int t=241;t<=520;t++) {
   float next=m.resolve(360,t,true);
   check(next>=last&&next<=360,"width must not overshoot");last=next;
  }
  check(last==360,"animation must settle");
  check(m.resolve(180,521,false)==180,"disabled animation must snap");
  m.reset();
  check(m.resolve(90,600,true)==90,"reattached layout must discard old width");
  System.out.println("20 counter configurations and interrupted width transitions passed");
 }
}
""".replace("COUNTER", method(counter, "public static int backWidth()") + "\n" +
            method(counter, "public static int backWidth(int count)")).replace("WIDTH", width)
target = work / "CheckMenuState.java"
target.write_text(source, encoding="utf-8")
subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), str(target)], check=True)
subprocess.run(["java", "-cp", str(work), "CheckMenuState"], check=True)

bar = (native / "ActionBar/ActionBar.java").read_text(encoding="utf-8")
assert "NebulaHeaderCounter.backWidth()" not in bar, "native header uses preview count"
assert bar.count("NebulaHeaderCounter.backWidth(org.telegram.messenger.MessagesController") == 2
observer = bar[bar.index("private final NotificationCenter.NotificationCenterDelegate nebulaUnreadObserver"):
               bar.index("private void updateNebulaUnreadObserver")]
assert "updateNebulaAvatarLayout();" in observer, "Saved Messages margin does not follow count"
reveal = (overlay / "NebulaMenuReveal.java").read_text(encoding="utf-8")
assert "began = false" in method(reveal, "public void setAnchor(")
assert "began = false" in method(reveal, "public void reset(")
assert "stopTouch()" in method(reveal, "public void reset(")
assert "new Rect(" not in method(reveal, "public void clip("), "per-frame clip allocation"
popup = (native / "ActionBar/ActionBarPopupWindow.java").read_text(encoding="utf-8")
assert "nebulaReveal.reset()" in method(popup, "protected void onDetachedFromWindow(")
assert "NebulaMenuStyle.styleRows" in method(popup, "protected void dispatchDraw(")
assert "nebulaReveal.onTouch" in method(popup, "public boolean dispatchTouchEvent(")
chat = (native / "ChatActivity.java").read_text(encoding="utf-8")
assert "skipDraw && !nebulaLiftedMessage" in chat, "lifted original is rendered twice"
assert "popupLayout.nebulaReveal.setAnchor(v)" in chat
assert "if (app.nebulagram.ui.NebulaMenuStyle.animated()) scrimPopupWindow.startAnimation();" in chat
assert ("if (app.nebulagram.ui.NebulaAppearance.messageMenuBelow()\n"
        "                    &&") in chat, "below placement should not depend on blur"
tabs = (native / "MainTabsLayout.java").read_text(encoding="utf-8")
assert "nebulaWidthMotion.resolve" in tabs and "nebulaWidthMotion.reset()" in tabs
assert "removeCallbacks(nebulaWidthFrame)" in tabs
assert "drawnWidth = Math.min(Math.max(0, maxTotalWidthForTabs), drawnWidth)" in tabs
folders = (native / "Components/FilterTabsView.java").read_text(encoding="utf-8")
drag = method(folders, "public boolean dispatchTouchEvent(android.view.MotionEvent event)")
assert "delegate.canPerformActions()" in drag and "!delegate.isTabMenuVisible()" in drag
assert "action == android.view.MotionEvent.ACTION_UP && nebulaHover >= 0" in drag
assert "getParent().requestDisallowInterceptTouchEvent(false)" in drag
assert "nebulaLens.reset();" in folders
assert "NebulaHaptics.tick(tabView)" in folders
print("Native menu anchors, touch cleanup, contrast refresh, lifted message and width integration passed")
