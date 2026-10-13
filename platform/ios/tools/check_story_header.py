"""Check iOS story title wiring and execute production geometry on macOS."""
from pathlib import Path
import subprocess
import re

HELPER = Path('submodules/TelegramUI/Components/Stories/StoryPeerListComponent/Sources/NebulaStoryHeaderLayout.swift')


def check(header: str, stories: str, item: str, work: Path, execute: bool) -> None:
    assert 'component.maxTitleX - component.minTitleX - 46.0' not in stories, 'fixed 46 pt title budget ignores story rings, lock and status'
    assert 'NebulaStoryHeaderLayout.titleWidth(' in stories
    assert 'NebulaStoryHeaderLayout.collapsedItemWidth' in stories
    assert stories.count('NebulaStoryHeaderLayout.collapseStartIndex(') == 2
    assert 'NebulaStoryHeaderLayout.origin(' in stories
    assert 'max(1, collapseEndIndex - collapseStartIndex)' in stories
    assert stories.index('self.sortedItems.removeAll(keepingCapacity: true)', stories.index('func update(component: StoryPeerListComponent')) < stories.index('let titleAvailableWidth =')
    assert 'centerContentLeftInset = leftInset + (nextLeftButtonX > 0.0 ? max(44.0, nextLeftButtonX) : 0.0) + 8.0' in header
    assert 'centerContentRightInset = rightInset + (nextRightButtonX > 0.0 ? max(44.0, nextRightButtonX) : 0.0) + 8.0' in header
    assert 'let minimizedDefaultItemScale: CGFloat = 24.0 / 52.0' in stories
    assert 'collapseFraction: 1.0 - component.storiesFraction' in header
    assert 'self.storyPeerAction?(peer)' in header and 'self.storyComposeAction?(offset)' in header
    assert 'isInteractive: true, isVisible: false' not in header, 'toolbar control materials are hidden'
    print('OK: story header reserves native ring/stack/status footprints and toolbar bounds', flush=True)
    if not execute:
        return
    constants = {}
    for name in ['baseRadius', 'collapsedRadius', 'baseLineUnseenWidth', 'minimizedLineWidth']:
        constants[name] = re.search(r'let ' + name + r': CGFloat = ([0-9.]+)', item)[1]
    scale = re.search(r'let minimizedDefaultItemScale: CGFloat = ([0-9.]+ / [0-9.]+)', stories)[1]
    start = stories.index('                    var collapseDistance: CGFloat =')
    end = stories.index('                    collapsedMaxItemFrame.origin.x', start)
    distance = stories[start:end]
    program = '''import Foundation
import CoreGraphics
func nativeCollapseDistance(collapseIndex: Int, collapseStartIndex: Int, collapseEndIndex: Int) -> CGFloat {
__DISTANCE__
 return collapseDistance
}
var cases = 0
for width: CGFloat in [320, 375, 390, 430, 768, 1024] {
 for left: CGFloat in [0, 44, 72] {
  for right: CGFloat in [0, 44, 88] {
   let minX = 16 + left + 8
   let maxX = width - 16 - right - 8
   for count in 0...3 {
    for lock in [false, true] {
     for status in [false, true] {
      for activity in [false, true] {
       let textBudget = NebulaStoryHeaderLayout.titleWidth(availableWidth: maxX - minX, storyCount: count, hasLock: lock, hasStatus: status, hasActivity: activity)
       let visibleCount = NebulaStoryHeaderLayout.storyCount(availableWidth: maxX - minX, itemCount: count, hasLock: lock, hasStatus: status)
       let leading = activity ? 24 : NebulaStoryHeaderLayout.storyWidth(count: visibleCount)
       let prefix = leading + (leading > 0 ? 8 : 0)
       let suffix: CGFloat = (lock ? 20 : 0) + (status && !activity ? 26 : 0)
       for naturalTextWidth: CGFloat in [34, 112, 600] {
        let textWidth = min(textBudget, naturalTextWidth)
        let contentWidth = prefix + suffix + textWidth
        let origin = NebulaStoryHeaderLayout.origin(containerWidth: width, contentWidth: contentWidth, minX: minX, maxX: maxX)
        precondition(origin >= minX - 0.001 && origin + contentWidth <= maxX + 0.001, "collapsed group covers toolbar")
        let expanded = NebulaStoryHeaderLayout.origin(containerWidth: width, contentWidth: textWidth + suffix, minX: minX, maxX: maxX)
        precondition(expanded >= minX && expanded + textWidth + suffix <= maxX + 0.001, "expanded title covers toolbar")
        if visibleCount > 0 && !activity {
         // Actual StoryPeerListItemComponent: 52 pt photo, native radius and stroke.
         let scale: CGFloat = __SCALE__
         let radius = __baseRadius__ * scale + __collapsedRadius__ * (1 - scale)
         let stroke = __baseLineUnseenWidth__ * scale + __minimizedLineWidth__ * (1 - scale)
         let ringHalfWidth = (radius + stroke / 2) * scale
         let ringRight = origin + 17 + CGFloat(visibleCount - 1) * 14 + ringHalfWidth
         precondition(ringRight + 8 <= origin + prefix + 0.001, "story ring covers title")
         precondition(origin + 17 - ringHalfWidth >= minX, "story ring covers left toolbar")
        }
        cases += 1
       }
      }
     }
    }
   }
  }
 }
}
for hidden in [false, true] {
 for count in 0...6 {
  for accountStoryCount: Int? in [nil, 0, 1] {
   for unseen in [false, true] {
    for pending in [false, true] {
     let start = NebulaStoryHeaderLayout.collapseStartIndex(itemCount: count, useHiddenList: hidden, accountStoryCount: accountStoryCount, accountHasUnseen: unseen, accountHasPending: pending)
     let expected: Int
     if hidden { expected = 0 }
     else if count < 3, let accountStoryCount, accountStoryCount != 0 { expected = 1 }
     else if accountStoryCount != nil && (unseen || pending) { expected = 0 }
     else { expected = 1 }
     precondition(start == expected, "account story selection changed")
     precondition(NebulaStoryHeaderLayout.storyWidth(count: max(0, count - start)).isFinite)
     cases += 1
    }
   }
  }
 }
}
for start in 0...1 {
 for count in 1...3 {
  for index in 0...6 {
   let distance = nativeCollapseDistance(collapseIndex: index, collapseStartIndex: start, collapseEndIndex: start + count - 1)
   precondition(distance.isFinite && distance >= 0 && distance <= 1, "single-avatar animation has invalid coordinates")
   cases += 1
  }
 }
}
print("OK: production story layout passed \\(cases) narrow/wide, controls, ring, lock/status/activity and account-selection cases")
'''.replace('__DISTANCE__', distance).replace('__SCALE__', scale)
    for name, value in constants.items():
        program = program.replace('__' + name + '__', value)
    main = work / 'main.swift'
    main.write_text(program, encoding='utf-8')
    executable = work / 'story-header-check'
    subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', str(work / HELPER), str(main), '-o', str(executable)], check=True)
    subprocess.run([str(executable)], check=True)
