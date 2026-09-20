import UIKit

/// Presentation only. Native controls, previews and their state remain owned by callers.
enum NebulaSettingsStyle {
    static func accent(for symbol: String) -> UIColor {
        switch symbol {
        case "shield", "hand.raised", "lock.shield", "lock": return .systemGreen
        case "sparkles", "text.alignleft": return .systemPurple
        case "folder", "rectangle.bottomthird.inset.filled": return .systemOrange
        case "person.crop.circle": return .systemPink
        default: return .systemBlue
        }
    }

    static func icon(symbol: String, color: UIColor? = nil) -> UIImage? {
        let glyph = NebulaSettingsSymbols.path(for: symbol)
        let fallback = UIImage(systemName: symbol,
            withConfiguration: UIImage.SymbolConfiguration(pointSize: 22, weight: .regular))
        guard glyph != nil || fallback != nil else { return nil }
        let accent = color ?? self.accent(for: symbol)
        return UIGraphicsImageRenderer(size: CGSize(width: 32, height: 32)).image { context in
            accent.setFill()
            UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 32, height: 32), cornerRadius: 9).fill()
            if let glyph = glyph {
                context.cgContext.translateBy(x: 5, y: 5)
                context.cgContext.scaleBy(x: 22.0 / 24.0, y: 22.0 / 24.0)
                UIColor.white.setStroke()
                glyph.stroke()
            } else if let image = fallback {
                let scale = min(22 / image.size.width, 22 / image.size.height)
                let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                image.withTintColor(.white, renderingMode: .alwaysOriginal).draw(in:
                    CGRect(x: (32 - size.width) / 2, y: (32 - size.height) / 2, width: size.width, height: size.height))
            }
        }.withRenderingMode(.alwaysOriginal)
    }

    static func finish(_ cell: UITableViewCell) {
        cell.backgroundColor = .secondarySystemGroupedBackground
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.numberOfLines = 0
        if cell.accessoryView is UISwitch { cell.imageView?.image = nil }
        cell.separatorInset = UIEdgeInsets(top: 0, left: cell.imageView?.image == nil ? 16 : 64, bottom: 0, right: 16)
    }
}
