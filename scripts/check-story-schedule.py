#!/usr/bin/env python3
"""Scheduled stories published by the phone (0321-0324).

The server cannot schedule stories (stories.sendStory has no schedule_date in this schema), so the
story waits as a Telegram draft and an alarm publishes it. Usage: check-story-schedule.py [tree]
"""
from pathlib import Path
import re
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
main = tree / 'TMessagesProj/src/main'
java = main / 'java/org/telegram'
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def read(path):
    return path.read_text(encoding='utf-8')


stories_tl = read(java / 'tgnet/tl/TL_stories.java')
start = stories_tl.find('public static class TL_stories_sendStory ')
send_story = stories_tl[start:stories_tl.find('public static class', start + 10)]
check(start >= 0, 'TL_stories_sendStory not found')
check('schedule_date' not in send_story,
      'stories.sendStory now has schedule_date: use the server instead of local publishing')

sheet = read(java / 'ui/Stories/recorder/StoryPrivacyBottomSheet.java')
recorder = read(java / 'ui/Stories/recorder/StoryRecorder.java')
drafts = read(java / 'ui/Stories/recorder/DraftsController.java')
launch = read(java / 'ui/LaunchActivity.java')
manifest = read(main / 'AndroidManifest.xml')
schedule = read(ui / 'NebulaStorySchedule.java')
fragment = read(ui / 'NebulaScheduledStoriesFragment.java')
settings = read(ui / 'NebulaSettingsFragment.java')

# 0321: the sheet offers the choice only for a new story and reuses the real Post path.
check('NebulaStorySchedulePlan.disarm(); // NebulaGram' in sheet and sheet.index('NebulaStorySchedulePlan.disarm()') < sheet.index('onButton1Click(v);'),
      'plain Post must disarm a stale schedule')
check('NebulaStorySchedule.pick(getContext(), resourcesProvider, () -> onButton1Click(button))' in sheet,
      'schedule button must go through onButton1Click')
check('pageType == PAGE_TYPE_SHARE && nebulaAllowSchedule && !isEdit && !isLive && !liveSettings' in sheet,
      'schedule button only on the share page of a new, non-live story')

# 0322: the recorder takes the choice once and schedules instead of uploading.
up = recorder[recorder.find('    private void uploadInternal(boolean asStory) {'):]
up = up[:up.find('\n    }\n')]
check('.nebulaAllowSchedule(app.nebulagram.ui.NebulaStorySchedule.schedulable(outputEntry))' in recorder, 'sheet told whether this entry can wait')
check('NebulaStorySchedulePlan.take(System.currentTimeMillis())' in up, 'uploadInternal takes the chosen time once')
check(up.find('NebulaStorySchedule.schedule(currentAccount, entry, nebulaPublishAt)') != -1
      and up.find('NebulaStorySchedule.schedule(') < up.find('.uploadStory(entry, asStory)'),
      'scheduling must happen before uploadStory')
check(re.search(r'NebulaStorySchedule\.schedule\(currentAccount, entry, nebulaPublishAt\)\) \{[^\n]*\n\s*entry\.cancelCheckStickers\(\);\n\s*return;', up),
      'a scheduled entry must not also be uploaded')

# 0323: scheduled drafts are kept apart from the gallery's drafts.
check('if (!app.nebulagram.ui.NebulaStorySchedule.adopt(currentAccount, entry)) {' in drafts, 'drafts load must hand scheduled entries over')
check(drafts.find('NebulaStorySchedule.draftsLoaded(currentAccount)') > drafts.find('loaded = true;'), 'draftsLoaded after loaded=true')

# 0324: the alarm and the resume hook.
check('app.nebulagram.ui.NebulaStorySchedule.onWake();' in launch, 'LaunchActivity.onResume must publish missed stories')
receiver = re.search(r'<receiver android:name="app\.nebulagram\.ui\.NebulaStoryScheduleReceiver" android:exported="false">(.*?)</receiver>', manifest, re.S)
check(receiver is not None, 'receiver registered and not exported')
if receiver:
    for action in ('app.nebulagram.story.PUBLISH', 'android.intent.action.BOOT_COMPLETED', 'android.intent.action.MY_PACKAGE_REPLACED'):
        check(action in receiver.group(1), 'receiver listens for ' + action)
check('ACTION_PUBLISH = "app.nebulagram.story.PUBLISH"' in schedule, 'alarm action matches the manifest')
check('android.permission.SCHEDULE_EXACT_ALARM' in manifest, 'exact alarm permission declared')
check('canScheduleExactAlarms()' in schedule and 'setAndAllowWhileIdle' in schedule, 'falls back to inexact alarms when exact ones are denied')
check('FLAG_IMMUTABLE' in schedule, 'alarm PendingIntent is immutable')

# The UI is explicit that the phone publishes.
check('Телефон должен быть включён' in schedule and 'должен быть включён и в сети' in fragment,
      'bulletin and list must say the phone has to be on')
check('NebulaStorySchedule.cancel(item)' in fragment, 'list items can be cancelled')
check('new NebulaScheduledStoriesFragment()' in settings, 'NebulaGram settings open the scheduled stories list')
for path in (ui / 'NebulaStorySchedule.java', ui / 'NebulaScheduledStoriesFragment.java'):
    for literal in re.findall(r'\.(?:setText|setMessage|title|subtitle)\("([^"]+)"', read(path)):
        failures.append(path.name + ': unlocalized string ' + literal)

harness = r'''package app.nebulagram.ui;
import java.util.*;
public class StoryPlanCheck {
 static void check(boolean b, String why) { if (!b) throw new AssertionError(why); }
 public static void main(String[] a) {
  long now = 10_000_000L, minute = 60_000L, day = 86_400_000L;
  check(NebulaStorySchedulePlan.validate(now + 30_000, now) == NebulaStorySchedulePlan.TOO_SOON, "too soon");
  check(NebulaStorySchedulePlan.validate(now + minute, now) == NebulaStorySchedulePlan.OK, "a minute ahead is fine");
  check(NebulaStorySchedulePlan.validate(now + 6 * day, now) == NebulaStorySchedulePlan.OK, "six days ahead is fine");
  check(NebulaStorySchedulePlan.validate(now + 7 * day, now) == NebulaStorySchedulePlan.TOO_LATE, "drafts expire after a week");
  check(NebulaStorySchedulePlan.MAX_AHEAD_MS < 7 * day, "keeps a margin before draft expiry");

  NebulaStorySchedulePlan.arm(now + day, now);
  check(NebulaStorySchedulePlan.take(now + 1000) == now + day, "armed time taken");
  check(NebulaStorySchedulePlan.take(now + 2000) == 0, "taken once");
  NebulaStorySchedulePlan.arm(now + day, now);
  NebulaStorySchedulePlan.disarm();
  check(NebulaStorySchedulePlan.take(now) == 0, "plain Post disarms");
  NebulaStorySchedulePlan.arm(now + day, now);
  check(NebulaStorySchedulePlan.take(now + NebulaStorySchedulePlan.PENDING_MS + 1) == 0, "stale choice ignored");

  NebulaStorySchedulePlan.Item x = new NebulaStorySchedulePlan.Item();
  x.account = 2; x.draftId = -77L; x.publishAt = now + day; x.dialogId = -1001234L; x.pinned = true; x.allowScreenshots = false; x.period = 43200;
  NebulaStorySchedulePlan.Item y = new NebulaStorySchedulePlan.Item();
  y.account = 0; y.draftId = 5L; y.publishAt = now - minute; y.dialogId = 99L;
  NebulaStorySchedulePlan.Item z = new NebulaStorySchedulePlan.Item();
  z.account = 0; z.draftId = 6L; z.publishAt = now - 2 * minute; z.state = 2;
  List<NebulaStorySchedulePlan.Item> items = new ArrayList<>(Arrays.asList(x, y, z));
  String stored = NebulaStorySchedulePlan.encodeAll(items) + "\n" + x.encode() + "\ngarbage\n1:0:5:5:0:0:0:0";
  List<NebulaStorySchedulePlan.Item> back = NebulaStorySchedulePlan.decodeAll(stored);
  check(back.size() == 3, "duplicates and garbage dropped: " + back.size());
  check(back.get(0).draftId == 6L && back.get(2).draftId == -77L, "sorted by time");
  NebulaStorySchedulePlan.Item bx = NebulaStorySchedulePlan.find(back, 2, -77L);
  check(bx != null && bx.dialogId == -1001234L && bx.pinned && !bx.allowScreenshots && bx.period == 43200, "round trip keeps target and options");
  check(NebulaStorySchedulePlan.find(back, 0, -77L) == null, "account is part of the key");
  List<NebulaStorySchedulePlan.Item> due = NebulaStorySchedulePlan.due(back, now);
  check(due.size() == 1 && due.get(0).draftId == 5L, "missed story is due, failed one is not");
  check(NebulaStorySchedulePlan.nextAlarm(back) == now - minute, "alarm at the earliest waiting story");
  back.remove(NebulaStorySchedulePlan.find(back, 0, 5L));
  check(NebulaStorySchedulePlan.nextAlarm(back) == now + day, "alarm moves to the next one");
  back.remove(bx);
  check(NebulaStorySchedulePlan.nextAlarm(back) == 0, "nothing waiting, no alarm");
  check(NebulaStorySchedulePlan.decodeAll("").isEmpty() && NebulaStorySchedulePlan.decodeAll(null).isEmpty(), "empty storage");
  System.out.println("story schedule: limits, one-shot choice, storage, due and alarm passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-story-schedule-') as folder:
    p = Path(folder)
    (p / 'StoryPlanCheck.java').write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(ui / 'NebulaStorySchedulePlan.java'), str(p / 'StoryPlanCheck.java')], check=True)
    run = subprocess.run(['java', '-cp', folder, 'app.nebulagram.ui.StoryPlanCheck'], capture_output=True, text=True, timeout=20)
    check(run.returncode == 0, 'harness failed: ' + run.stderr.strip())
    if run.returncode == 0:
        print(run.stdout.strip())

if failures:
    print('\n'.join('FAIL: ' + f for f in failures))
    sys.exit(1)
print('check-story-schedule: OK')
