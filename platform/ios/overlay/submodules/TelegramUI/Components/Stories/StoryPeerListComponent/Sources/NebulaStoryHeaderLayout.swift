import Foundation
import CoreGraphics

enum NebulaStoryHeaderLayout {
    // The photo remains 24 pt; its native story ring extends beyond that frame.
    static let collapsedItemWidth: CGFloat = 34.0
    static let collapsedItemDistance: CGFloat = 14.0

    static func collapseStartIndex(itemCount: Int, useHiddenList: Bool, accountStoryCount: Int?, accountHasUnseen: Bool, accountHasPending: Bool) -> Int {
        if useHiddenList {
            return 0
        } else if itemCount < 3, let accountStoryCount, accountStoryCount != 0 {
            return 1
        } else if accountStoryCount != nil && (accountHasUnseen || accountHasPending) {
            return 0
        } else {
            return 1
        }
    }

    static func storyWidth(count: Int) -> CGFloat {
        let count = max(0, min(3, count))
        return count == 0 ? 0.0 : collapsedItemWidth + CGFloat(count - 1) * collapsedItemDistance
    }

    static func storyCount(availableWidth: CGFloat, itemCount: Int, hasLock: Bool, hasStatus: Bool) -> Int {
        var count = max(0, min(3, itemCount))
        let accessoryWidth: CGFloat = (hasLock ? 20.0 : 0.0) + (hasStatus ? 26.0 : 0.0)
        while count > 1 && availableWidth - storyWidth(count: count) - 8.0 - accessoryWidth < 24.0 {
            count -= 1
        }
        return count
    }

    static func titleWidth(availableWidth: CGFloat, storyCount: Int, hasLock: Bool, hasStatus: Bool, hasActivity: Bool) -> CGFloat {
        let count = self.storyCount(availableWidth: availableWidth, itemCount: storyCount, hasLock: hasLock, hasStatus: hasStatus)
        let leadingWidth = hasActivity ? 24.0 : storyWidth(count: count)
        let spacing: CGFloat = leadingWidth > 0.0 ? 8.0 : 0.0
        let lockWidth: CGFloat = hasLock ? 20.0 : 0.0
        let statusWidth: CGFloat = hasStatus && !hasActivity ? 26.0 : 0.0
        return max(1.0, availableWidth - leadingWidth - spacing - lockWidth - statusWidth)
    }

    static func origin(containerWidth: CGFloat, contentWidth: CGFloat, minX: CGFloat, maxX: CGFloat) -> CGFloat {
        return max(minX, min((containerWidth - contentWidth) * 0.5, maxX - contentWidth))
    }
}
