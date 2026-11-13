"""Run the missed-summary prompt builder on the JVM and pin its upstream hook and privacy wiring."""
from pathlib import Path
import hashlib, os, subprocess, sys, urllib.request

root = Path(__file__).resolve().parent.parent
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
work = root / 'build/missed-summary-check'
work.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(overlay / 'NebulaMissedPrompt.java'),
                str(root / 'tests/android/MissedSummaryCheck.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'MissedSummaryCheck'], check=True)

errors = []
prompt = (overlay / 'NebulaMissedPrompt.java').read_text(encoding='utf-8')
if 'import android' in prompt or 'org.telegram' in prompt:
    errors.append('NebulaMissedPrompt must stay pure Java')
missed = (overlay / 'NebulaMissed.java').read_text(encoding='utf-8')
for needle, why in [
    ('getBoolean(KEY, false)', 'the feature must be off by default'),
    ('NebulaAiAvailability.available()', 'an unconfigured provider must open AI settings'),
    ('new NebulaAiSettingsFragment()', 'the pill must open AI settings without a provider'),
    ('NebulaAiReplacements.request(', 'requests must go through the shared AI client'),
    ('providerLabel()', 'the sheet must name the provider'),
    ('NebulaMissedPrompt.MIN_UNREAD', 'the pill needs the 30-unread threshold'),
    ('scrollToMessageId(first', 'the sheet must link back to the first unread'),
    ('isEncryptedDialog', 'secret chats must be excluded'),
]:
    if needle not in missed:
        errors.append(why)
for forbidden in ['.media', 'FileLoader', 'getDocument', 'photo']:
    if forbidden in missed:
        errors.append('media must never be read for the summary: ' + forbidden)
settings = (overlay / 'NebulaAiSettingsFragment.java').read_text(encoding='utf-8')
if 'toggle(NebulaMissed.KEY' not in settings or 'NebulaMissed.providerLabel()' not in settings:
    errors.append('AI settings must expose the opt-in toggle and name the provider')
patch = (root / 'patches/android/0331-chat-missed-summary.patch').read_text(encoding='utf-8')
for needle in ['NebulaMissed.attach(this, contentView', 'NebulaMissed.position(nebulaMissed', 'nebulaMissed.destroy()']:
    if needle not in patch:
        errors.append('0331 lost hook: ' + needle)
if len(sys.argv) > 1:
    chat = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
    if chat.count('app.nebulagram.ui.NebulaMissed') < 3:
        errors.append('prepared ChatActivity is missing the missed-summary hooks')
if errors:
    raise SystemExit('\n'.join(errors))
print('missed summary wiring: ok')
