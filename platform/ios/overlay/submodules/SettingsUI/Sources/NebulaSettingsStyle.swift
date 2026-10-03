import Display
import UIKit
import NebulaSettingsContract
import TelegramPresentationData

/// Presentation only. Native controls, previews and their state remain owned by callers.
public enum NebulaSettingsStyle {
    static func apply(theme: PresentationTheme?, to controller: UIViewController) {
        guard let theme else { return }
        controller.overrideUserInterfaceStyle = theme.overallDarkAppearance ? .dark : .light
        controller.view.backgroundColor = theme.list.blocksBackgroundColor.withAlphaComponent(1)
        controller.view.tintColor = theme.list.itemAccentColor
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = theme.list.blocksBackgroundColor.withAlphaComponent(1)
        appearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
        controller.navigationItem.standardAppearance = appearance
        controller.navigationItem.scrollEdgeAppearance = appearance
        controller.navigationItem.compactAppearance = appearance
        controller.navigationController?.navigationBar.tintColor = theme.list.itemAccentColor
        if let search = controller.navigationItem.searchController?.searchBar {
            search.overrideUserInterfaceStyle = theme.overallDarkAppearance ? .dark : .light
            search.tintColor = theme.list.itemAccentColor
            search.searchTextField.textColor = theme.list.itemPrimaryTextColor
            search.searchTextField.backgroundColor = theme.list.itemBlocksBackgroundColor
        }
    }

    static func accent(for symbol: String) -> UIColor {
        switch symbol {
        case "shield", "hand.raised", "lock.shield", "lock": return .systemGreen
        case "sparkles", "text.alignleft": return .systemPurple
        case "folder", "rectangle.bottomthird.inset.filled": return .systemOrange
        case "person.crop.circle": return .systemPink
        default: return .systemBlue
        }
    }

    public static func icon(symbol: String, color: UIColor? = nil) -> UIImage? {
        let pack = NebulaSettingsStore.shared.iconPack
        if pack == 0, let native = nativeSettingsIcon(symbol: symbol) { return native }
        let packed = NebulaIconPackArtwork.image(symbol: symbol, pack: pack)
        let glyph = packed == nil && pack == 1 ? NebulaSettingsSymbols.path(for: symbol) : nil
        let fallback = packed ?? UIImage(systemName: symbol,
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

    private static func nativeSettingsIcon(symbol: String) -> UIImage? {
        switch symbol {
        case "person.crop.circle": return PresentationResourcesSettings.myProfile
        case "bookmark": return PresentationResourcesSettings.savedMessages
        case "phone": return PresentationResourcesSettings.recentCalls
        case "desktopcomputer": return PresentationResourcesSettings.devices
        case "folder": return PresentationResourcesSettings.chatFolders
        case "bell": return PresentationResourcesSettings.notifications
        case "lock.shield", "lock": return PresentationResourcesSettings.security
        case "globe": return PresentationResourcesSettings.language
        case "photo": return PresentationResourcesSettings.photos
        case "video": return PresentationResourcesSettings.videos
        case "briefcase": return PresentationResourcesSettings.business
        default: return nil
        }
    }

    static func finish(_ cell: UITableViewCell, theme: PresentationTheme? = nil) {
        cell.backgroundColor = theme?.list.itemBlocksBackgroundColor ?? .secondarySystemGroupedBackground
        if let theme {
            cell.textLabel?.textColor = theme.list.itemPrimaryTextColor
            cell.detailTextLabel?.textColor = theme.list.itemSecondaryTextColor
            cell.tintColor = theme.list.itemAccentColor
            if let toggle = cell.accessoryView as? NebulaSwitchControl {
                toggle.onTintColor = theme.list.itemSwitchColors.contentColor
                toggle.tintColor = theme.list.itemSwitchColors.frameColor
                toggle.thumbTintColor = theme.list.itemSwitchColors.handleColor
            }
            let selection = UIView()
            selection.backgroundColor = theme.list.itemAccentColor.withAlphaComponent(0.12)
            cell.selectedBackgroundView = selection
        }
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.numberOfLines = 0
        if cell.accessoryView is NebulaSwitchControl { cell.imageView?.image = nil }
        cell.separatorInset = UIEdgeInsets(top: 0, left: cell.imageView?.image == nil ? 16 : 64, bottom: 0, right: 16)
    }
}
