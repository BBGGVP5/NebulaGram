"""Guard the requested settings hierarchy and full-page tools/task presentation."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
home=(ui/'NebulaSettingsFragment.java').read_text(encoding='utf-8')
body=home[home.index('private void buildSections('):home.index('private NebulaRow hub(')]
assert body.rfind('sections.addView(')==body.index('sections.addView(about,')
assert 'NebulaSupportFragment' not in body and 'NebulaMessageToolsFragment' not in body
assert 'Чаты и инструменты' in body and 'Браузер и блокировка рекламы' not in body
hub=(ui/'NebulaSettingsHubFragment.java').read_text(encoding='utf-8')
for target in ['NebulaAiSettingsFragment','NebulaTranslationFragment','NebulaMessageToolsFragment','NebulaTasksFragment']:
    assert target in hub,target
section=(ui/'NebulaSectionFragment.java').read_text(encoding='utf-8')
about=section[section.index('private void buildAbout('):section.index('private String versions(')]
general=section[section.index('private void buildGeneral('):section.index('private void buildAbout(')]
assert 'NebulaSupportFragment' in about and 'NebulaCommunityCard' in about and 'NebulaUpdatesFragment' in about
assert 'NebulaBrowserSettingsFragment' in general
for file in ['NebulaTasksFragment.java','NebulaTaskEditorFragment.java','NebulaMessageToolsFragment.java']:
    assert 'new NebulaSettingsHero' in (ui/file).read_text(encoding='utf-8'),file
tools=(ui/'NebulaMessageToolsFragment.java').read_text(encoding='utf-8')
assert 'if (popup) grid.addEmoji' in tools and 'else card.add(new NebulaRow' in tools
assert 'NebulaFormUi.group(column, t("Действия"' in tools
editor=(ui/'NebulaAiServiceEditorFragment.java').read_text(encoding='utf-8')
assert 'NebulaFormUi.cardField' in editor and 'NebulaButton.STYLE_TEXT' in editor
link=(ui/'NebulaMenuFragment.java').read_text(encoding='utf-8')
assert 'SCREEN_HOME.equals(screenId)) content.addView(new NebulaSettingsHero' in link
assert 'NebulaSettingsEmoji.forIcon(resource)' in (ui/'NebulaRow.java').read_text(encoding='utf-8')
assert not (ui/'NebulaShieldDrawable.java').exists()
native=(root/'patches/android/0183-settings-emoji-replay-profile-controls.patch').read_text(encoding='utf-8')
assert 'replayPage(resumedView)' in native and 'fragmentView instanceof app.nebulagram.ui.NebulaSettingsLayout' in native
print('Settings organization: About last, support/community/updates in About, grouped chat/AI/tools/tasks, browser under General, emoji introductions and compact message sheet retained')
