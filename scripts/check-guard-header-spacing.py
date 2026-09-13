"""Focused source/resource guards; device visual QA remains separate."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "platform/android/overlay/TMessagesProj/src/main"
expected = {
    "values-ru": "Ваш лучший сервис для свободного интернета",
    "values": "Your go-to service for internet freedom",
}
for locale, text in expected.items():
    strings = ET.parse(ANDROID / "res" / locale / "strings_nebula_menu.xml")
    assert strings.find("./string[@name='nl_guard_sub']").text == text, locale
menu = (ROOT / "core/settings/menu.go").read_text(encoding="utf-8")
assert 'SubtitleKey: "nl_guard_sub", Subtitle: "' + expected["values"] + '"' in menu
assert 'Key: "guard", Type: RowAction, Command: "guard.open"' in menu
section = (ANDROID / "java/app/nebulagram/ui/NebulaSectionFragment.java").read_text(encoding="utf-8")
build = section[section.index("    private void build(Context"):section.index("    // --- разделы")]
assert "heroParams = new LinearLayout.LayoutParams(" in build
assert "ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);" in build
assert "heroParams.bottomMargin = AndroidUtilities.dp(12);" in build
assert "english[summary])), heroParams);" in build
assert build.index("heroParams.bottomMargin") < build.index("switch (section)")
print("PASS: Guard subtitle/action, section hero external 12dp spacing (source guards)")
