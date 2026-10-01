"""Exercise notification and quote decisions without the Android UI runtime."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import sys

source = Path(__file__).resolve().parents[1] / "platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFeaturePolicy.java"
with TemporaryDirectory() as directory:
    root = Path(directory)
    harness = root / "FeaturePolicyCheck.java"
    harness.write_text("""
import app.nebulagram.ui.NebulaFeaturePolicy;
public class FeaturePolicyCheck {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        check(NebulaFeaturePolicy.silenceUnknown(true, 123, false, false));
        check(!NebulaFeaturePolicy.silenceUnknown(true, 123, true, false));
        check(!NebulaFeaturePolicy.silenceUnknown(true, -123, false, false));
        check(!NebulaFeaturePolicy.silenceUnknown(true, 777, false, true));
        check(!NebulaFeaturePolicy.silenceUnknown(false, 123, false, false));
        check(NebulaFeaturePolicy.ignoreMention(true, "*", -123));
        check(NebulaFeaturePolicy.ignoreMention(true, "12,-123,56", -123));
        check(!NebulaFeaturePolicy.ignoreMention(true, "12,-123,56", -12));
        check(!NebulaFeaturePolicy.ignoreMention(false, "*", -123));
        check(!NebulaFeaturePolicy.ignoreMention(true, null, -123));
        String surrogate = "A\\uD83D\\uDE42B";
        check(NebulaFeaturePolicy.quoteEnd(surrogate, 2) == 1);
        check(NebulaFeaturePolicy.quoteEnd(surrogate, 3) == 3);
        check(NebulaFeaturePolicy.quoteEnd(surrogate, 99) == surrogate.length());
        check(NebulaFeaturePolicy.fade(0, true) == 0);
        check(NebulaFeaturePolicy.fade(1, true) == 1);
        check(NebulaFeaturePolicy.fade(0.5f, true) == 0.5f);
        check(NebulaFeaturePolicy.fade(0.25f, true) < 0.25f);
        check(NebulaFeaturePolicy.fade(0.25f, false) == 0.25f);
    }
}
""", encoding="utf-8")
    subprocess.run(["javac", "-d", str(root), str(source), str(harness)], check=True)
    subprocess.run(["java", "-cp", str(root), "FeaturePolicyCheck"], check=True)
print("NebulaGram feature policy passed")
