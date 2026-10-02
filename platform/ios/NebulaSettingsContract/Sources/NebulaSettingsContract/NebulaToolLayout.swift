import Foundation

/// Shared with UIKit so compact widths and large text use the same measured layout.
public struct NebulaToolLayout {
    public struct Tile: Equatable {
        public let x: Double
        public let y: Double
        public let width: Double
        public let height: Double
    }
    public let tiles: [Tile]
    public let height: Double

    public init(width: Double, count: Int, minimumTileWidth: Double, rowHeight: Double, rtl: Bool = false) {
        guard width.isFinite, width > 0, count > 0 else { tiles = []; height = 0; return }
        let gap = 10.0
        let minimum = minimumTileWidth.isFinite ? max(96, minimumTileWidth) : 96
        let columns = min(3, max(1, Int((width + gap) / (minimum + gap))))
        let tileWidth = (width - Double(columns - 1) * gap) / Double(columns)
        let tileHeight = rowHeight.isFinite ? max(96, rowHeight) : 96
        let rows = (count + columns - 1) / columns
        height = Double(rows) * tileHeight + Double(rows - 1) * gap
        tiles = (0..<count).map { index in
            let row = index / columns
            let rowCount = min(columns, count - row * columns)
            let rowWidth = Double(rowCount) * tileWidth + Double(rowCount - 1) * gap
            let column = rtl ? rowCount - 1 - index % columns : index % columns
            return Tile(x: (width - rowWidth) / 2 + Double(column) * (tileWidth + gap),
                        y: Double(row) * (tileHeight + gap), width: tileWidth, height: tileHeight)
        }
    }
}

public enum NebulaLanguageOrder {
    public static func codes(supported: [String], locale: Locale) -> [String] {
        ["ru", "en"] + Set(supported).subtracting(["ru", "en", ""]).sorted {
            let first = locale.localizedString(forLanguageCode: $0) ?? $0
            let second = locale.localizedString(forLanguageCode: $1) ?? $1
            let order = first.compare(second, options: [.caseInsensitive, .numeric], locale: locale)
            return order == .orderedSame ? $0 < $1 : order == .orderedAscending
        }
    }
}
