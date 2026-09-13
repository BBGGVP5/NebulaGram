"""Exercise the actual AI tab method; guard native settings presentation contracts."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui"
IOS = ROOT / "platform/ios/overlay/submodules/SettingsUI/Sources"
ai = (UI / "NebulaAiFragment.java").read_text(encoding="utf-8")
privacy = (UI / "NebulaPrivacyFragment.java").read_text(encoding="utf-8")
method = ai[ai.index("    private void selectPage("):ai.index("    private TextView label(")]
work = ROOT / "build/settings-design-check"
work.mkdir(parents=True, exist_ok=True)
sources = {
    "android/graphics/Color.java": "package android.graphics; public class Color { public static final int TRANSPARENT=0; }",
    "android/graphics/drawable/GradientDrawable.java": "package android.graphics.drawable; public class GradientDrawable { public void setCornerRadius(int x){} public void setColor(int x){} }",
    "Check.java": """public class Check {
    static class View { static final int VISIBLE=0,GONE=8; int visibility; boolean selected;
        String draft="keep this draft"; Object getContext(){return this;}
        void setVisibility(int x){visibility=x;} void setSelected(boolean x){selected=x;}
        void setTextColor(int x){} void setBackground(Object x){} }
    static class NebulaTheme { static NebulaTheme of(Object x){return new NebulaTheme();}
        int onPrimaryContainer(){return 1;} int onSurfaceVariant(){return 2;} int primaryContainer(){return 3;} }
    View content=new View(); View[] pages={new View(),new View(),new View()};
    View[] tabs={new View(),new View(),new View()}; int selectedPage=-1;
    int dp(int x){return x;}
""" + method + """
    public static void main(String[] args) {
        Check check=new Check(); View[] original=check.pages.clone();
        for(int n=0;n<600;n++) {
            int page=n%3; check.selectPage(page);
            if(check.selectedPage!=page) throw new AssertionError("selected page");
            for(int i=0;i<3;i++) {
                if(check.pages[i]!=original[i] || !check.pages[i].draft.equals("keep this draft"))
                    throw new AssertionError("tab switch recreated or reset content");
                if(check.pages[i].visibility!=(i==page?View.VISIBLE:View.GONE) || check.tabs[i].selected!=(i==page))
                    throw new AssertionError("selection/visibility drift");
            }
        }
        System.out.println("AI tab state: 600 transitions passed; content retained");
    }
}
"""
}
for name, source in sources.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source, encoding="utf-8", newline="\n")
java_home = os.environ.get("JAVA_HOME")
def java_tool(name):
    return str(Path(java_home) / "bin" / (name + (".exe" if os.name == "nt" else ""))) if java_home else name
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", str(work), *[str(work / n) for n in sources]], check=True)
subprocess.run([java_tool("java"), "-cp", str(work), "Check"], check=True)
assert 'pages[0].addView(responseCard' in ai
assert 'selectPage(0); responseCard.setVisibility' in ai  # model request errors remain visible
assert 'key.setText(NebulaAiSecrets.read' not in ai
assert 'client != task || getParentActivity() == null' in ai
assert 'Как работает сохранение' in privacy and 'Background capture requires' in privacy
assert 'confirmClear(BaseFragment fragment, int account, long peer, Runnable done)' in privacy
for name in ("NebulaAiController.swift", "NebulaPrivacyController.swift"):
    source = (IOS / name).read_text(encoding="utf-8")
    assert 'hero.fit(in: tableView)' in source
    assert 'UITableViewCell(style: .subtitle' in source
hero = (IOS / "NebulaSettingsHero.swift").read_text(encoding="utf-8")
assert 'systemLayoutSizeFitting' in hero and 'adjustsFontForContentSizeCategory = true' in hero
assert 'frame.width - width' in hero and 'frame.height - height' in hero
print("Settings presentation guards passed (not a substitute for on-device visual QA)")
