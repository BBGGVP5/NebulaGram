#!/usr/bin/env python3
"""Repeat of scheduled messages: the picker's choice reaches schedule_repeat_period (0320).

Usage: check-schedule-repeat.py [prepared Telegram tree]
"""
from pathlib import Path
import re
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java = tree / 'TMessagesProj/src/main/java/org/telegram'
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


alerts = (java / 'ui/Components/AlertsCreator.java').read_text(encoding='utf-8')
helper = (java / 'messenger/SendMessagesHelper.java').read_text(encoding='utf-8')
tl = (java / 'tgnet/TLRPC.java').read_text(encoding='utf-8')
repeat = (ui / 'NebulaScheduleRepeat.java').read_text(encoding='utf-8')
notice = (ui / 'NebulaScheduleRepeatNotice.java').read_text(encoding='utf-8')

# The native field exists in the schema, so NebulaGram uses it instead of a local imitation.
for method in ('TL_messages_sendMessage', 'TL_messages_sendMedia'):
    start = tl.find('public static class ' + method + ' ')
    check(start >= 0 and 'schedule_repeat_period' in tl[start:tl.find('public static class', start + 10)],
          method + ' must still carry schedule_repeat_period')
multi = tl.find('public static class TL_messages_sendMultiMedia ')
multi_body = tl[multi:tl.find('public static class', multi + 10)]
check(multi >= 0 and 'schedule_repeat_period' not in multi_body,
      'sendMultiMedia gained schedule_repeat_period: send it instead of the album notice')

# The picker remembers exactly what it hands to its delegate.
check(re.search(r'NebulaScheduleRepeat\.remember\(dialogId, \(int\) \(calendar\.getTimeInMillis\(\) / 1000\), '
                r'repeatTextView != null \? repeat\[0\] : 0\);[^\n]*\n\s*datePickerDelegate\.didSelectDate\(notify\[0\], '
                r'\(int\) \(calendar\.getTimeInMillis\(\) / 1000\), repeat\[0\]\);', alerts),
      'AlertsCreator must remember the repeat right before didSelectDate')
check(alerts.count('NebulaScheduleRepeat.remember(') == 1, 'one picker hook only')
check('isPremium()' in alerts and 'MessageScheduledRepeatPremium' in alerts,
      'the native Premium gate stays: the server only repeats for Premium accounts')

# The picker's real-server periods: none, daily, weekly, every 2 weeks, monthly (and longer).
match = re.search(r'\} : new int\[\] \{([^}]*)\};', alerts[alerts.find('final int[] repeatValues'):])
check(match is not None, 'repeatValues list not found')
if match:
    values = [eval(v.strip()) for v in match.group(1).split(',') if v.strip()]
    for wanted in (0, 86400, 7 * 86400, 14 * 86400, 30 * 86400):
        check(wanted in values, 'picker lost period %d' % wanted)
    periods = re.search(r'PERIODS = \{([^}]*)\}', repeat).group(1)
    check([eval(v.strip()) for v in periods.split(',')] == values, 'NebulaScheduleRepeat.PERIODS mirrors the picker')

# sendMessage resolves once, before any request is built.
start = helper.find('    public void sendMessage(SendMessageParams sendMessageParams) {')
body = helper[start:helper.find('    private void performSendDelayedMessage', start)]
hook = re.search(r'int scheduleRepeatPeriod = app\.nebulagram\.ui\.NebulaScheduleRepeatNotice\.forSend\(peer, sendMessageParams\.scheduleDate, '
                 r'sendMessageParams\.scheduleRepeatPeriod,\s*params != null && params\.containsKey\("groupId"\) && '
                 r'!"0"\.equalsIgnoreCase\(params\.get\("groupId"\)\)\);', body)
check(hook is not None, 'sendMessage must resolve the remembered repeat and know about albums')
if hook is not None:
    check(body.count('int scheduleRepeatPeriod =') == 1, 'resolved exactly once')
    check(body.index('HashMap<String, String> params = sendMessageParams.params;') < hook.start(), 'resolved after params is read')
    check(hook.start() < body.index('req.schedule_repeat_period = scheduleRepeatPeriod'), 'resolved before the first request')
check('NebulaScheduleRepeat.resolve(peer, scheduleDate, requested)' in notice and 'NebulaScheduleRepeat.sendable(period, grouped)' in notice,
      'the notice delegates to the checked pure logic')
check('NebulaText.text(' in notice and 'один раз' in notice, 'album notice is a localized, explicit message')

harness = r'''package app.nebulagram.ui;
public class RepeatCheck {
 static void check(boolean b, String why) { if (!b) throw new AssertionError(why); }
 public static void main(String[] a) {
  long t = 1_000_000L;
  NebulaScheduleRepeat.remember(42, 5000, 86400, t);
  check(NebulaScheduleRepeat.resolve(42, 5000, 0, t + 1000) == 86400, "dropped repeat restored");
  check(NebulaScheduleRepeat.resolve(42, 5000, 0, t + 2000) == 86400, "every message of one send keeps it");
  check(NebulaScheduleRepeat.resolve(42, 5000, 604800, t) == 604800, "explicit repeat wins");
  check(NebulaScheduleRepeat.resolve(43, 5000, 0, t) == 0, "other dialog untouched");
  check(NebulaScheduleRepeat.resolve(42, 5060, 0, t) == 0, "other time untouched");
  check(NebulaScheduleRepeat.resolve(42, 0, 0, t) == 0, "unscheduled message never repeats");
  check(NebulaScheduleRepeat.resolve(42, 5000, 0, t + NebulaScheduleRepeat.WINDOW_MS + 1) == 0, "expires");
  check(NebulaScheduleRepeat.resolve(42, 5000, 0, t + 1) == 0, "expiry clears the choice");
  NebulaScheduleRepeat.remember(42, 5000, 86400, t);
  NebulaScheduleRepeat.remember(42, 5000, 0, t + 5);
  check(NebulaScheduleRepeat.resolve(42, 5000, 0, t + 6) == 0, "choosing 'never' clears");
  NebulaScheduleRepeat.remember(-1, 7000, 1209600, t);
  check(NebulaScheduleRepeat.resolve(-100500, 7000, 0, t) == 1209600, "multi-dialog picker applies to its chats");
  NebulaScheduleRepeat.remember(42, 5000, 86400, t);
  check(NebulaScheduleRepeat.resolve(42, 5000, 0, t - 1) == 0, "clock going back does not apply");
  check(NebulaScheduleRepeat.sendable(86400, true) == 0, "albums are sent once, without a repeat badge");
  check(NebulaScheduleRepeat.sendable(86400, false) == 86400, "single messages keep the repeat");
  check(NebulaScheduleRepeat.sendable(0, true) == 0, "nothing to drop");
  System.out.println("schedule repeat: restore, scope, expiry and override passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-repeat-') as folder:
    p = Path(folder)
    (p / 'RepeatCheck.java').write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(ui / 'NebulaScheduleRepeat.java'), str(p / 'RepeatCheck.java')], check=True)
    run = subprocess.run(['java', '-cp', folder, 'app.nebulagram.ui.RepeatCheck'], capture_output=True, text=True, timeout=20)
    check(run.returncode == 0, 'harness failed: ' + run.stderr.strip())
    if run.returncode == 0:
        print(run.stdout.strip())

if failures:
    print('\n'.join('FAIL: ' + f for f in failures))
    sys.exit(1)
print('check-schedule-repeat: OK')
