# NebulaGram settings preview

Local interactive design prototype for the Android Nebula settings surface.
Open `index.html` directly, or from the repository root run:

```powershell
python -m http.server 8765 --bind 127.0.0.1 --directory design
```

Then visit http://127.0.0.1:8765/settings-preview/.

The preview contains 23 routes: the root, all 11 root destinations and their nested appearance, connection, AI, updates and transfer screens. Fifty native switch labels/descriptions are generated from `NebulaSectionFragment.java` and Android's Russian resources:

```powershell
python design/settings-preview/generate-controls.py
```

The browser uses temporary demonstration values. Network, account, archive, AI and update actions explain their role without contacting any service. Theme and accent selectors are preview controls. Import/export uses a separate `NebulaGram-design-preview` format, not the application's transfer format. Reload resets the preview.

Native Android changes use the existing fragments and preference callbacks: shared group surfaces, navigation icon tiles, text-first switch rows, introductions, divider alignment, and root search through the existing index. iOS applies the same presentation to its supported native settings with ItemListUI/UIKit. Existing native previews and their state callbacks are retained on both platforms. The browser prototype illustrates the design direction; it is not a device screenshot and does not replace device QA.

## Verification

- JavaScript syntax check passed.
- All 23 preview routes inspected through browser navigation; search, switch, selection sheet, AI draft retention, light/dark theme checked.
- At 390 × 844 the document width is 390 and the phone viewport fits without horizontal overflow.
- Native source/behavior checks: settings-root (36 geometry combinations), settings-design (600 AI tab transitions), appearance-controls (1080 geometry cases), settings-localization, NebulaLink layout guards.
- Android Java compilation passed for the runtime fixes; APK assembly and device testing were not performed.

## Runtime-fix preview

The folder example now shows the tab bar above/below a compact chat list; tabs can
be selected and scrolled. The profile example shows five owned badge illustrations
using exactly the Android vector paths. Regenerate them with
`python design/settings-preview/generate-badges.py`.
The screenshots `preview-folders-fixed.png` and `preview-badges-dark.png` show the
browser prototype, not Android device captures.

The subsequent native-design integration preserves all existing native preview
implementations. Its Android regression checks and iOS patch/contract checks pass;
full Android recompilation was blocked by JVM native memory exhaustion. The added
Android introduction method compiled in isolation against the SDK/native classes.
The iOS UIKit/Swift build still requires macOS acceptance.
