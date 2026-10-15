import UIKit
import AsyncDisplayKit
import ObjectiveC
import TelegramPresentationData
import NebulaSettingsContract

private var nebulaPrivacyAssociation: UInt8 = 0

/// A neutral opaque view. It never receives names, messages, photos or peer IDs.
private final class NebulaPrivateChatRow: UIView {
    private let avatar = UIImageView(image: UIImage(systemName: "person.fill"))
    private let title = UILabel()
    private let detail = UILabel()
    override init(frame: CGRect) {
        super.init(frame: frame); isOpaque = true; clipsToBounds = true; isUserInteractionEnabled = false; accessibilityElementsHidden = true
        avatar.contentMode = .center; avatar.layer.masksToBounds = true
        title.font = UIFontMetrics(forTextStyle: .body).scaledFont(for: .systemFont(ofSize: 16), maximumPointSize: 24)
        detail.font = UIFontMetrics(forTextStyle: .subheadline).scaledFont(for: .systemFont(ofSize: 13), maximumPointSize: 19)
        title.adjustsFontForContentSizeCategory = true; detail.adjustsFontForContentSizeCategory = true
        addSubview(avatar); addSubview(title); addSubview(detail)
        // Native subnodes may be added later during their asynchronous apply.
        layer.zPosition = 10_000
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(theme: PresentationTheme, russian: Bool, archive: Bool) {
        backgroundColor = theme.chatList.backgroundColor.withAlphaComponent(1)
        avatar.backgroundColor = theme.list.itemAccentColor.withAlphaComponent(0.12); avatar.tintColor = theme.list.itemAccentColor
        title.textColor = theme.list.itemPrimaryTextColor; detail.textColor = theme.list.itemSecondaryTextColor
        title.text = archive ? (russian ? "Архив" : "Archive") : (russian ? "Чат скрыт" : "Chat hidden")
        detail.text = russian ? "Предпросмотр скрыт" : "Preview hidden"
        setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let rtl = effectiveUserInterfaceLayoutDirection == .rightToLeft
        let diameter = min(52, max(0, bounds.height - 16))
        avatar.frame = CGRect(x: rtl ? bounds.width - 12 - diameter : 12, y: (bounds.height - diameter) / 2, width: diameter, height: diameter); avatar.layer.cornerRadius = diameter / 2
        let left: CGFloat = rtl ? 16 : 78; let width = max(0, bounds.width - 94)
        title.textAlignment = rtl ? .right : .left; detail.textAlignment = title.textAlignment
        let titleHeight = min(max(0, bounds.height - 8), max(25, title.font.lineHeight))
        let detailHeight = max(23, detail.font.lineHeight)
        detail.isHidden = titleHeight + detailHeight + 10 > bounds.height
        let total = detail.isHidden ? titleHeight : titleHeight + detailHeight + 2
        let top = (bounds.height - total) / 2
        title.frame = CGRect(x: left, y: top, width: width, height: titleHeight)
        detail.frame = CGRect(x: left, y: top + titleHeight + 2, width: width, height: detailHeight)
    }
}

private final class NebulaPrivateRowLifetime {
    weak var node: ASDisplayNode?
    var theme: PresentationTheme
    var russian: Bool
    var archive: Bool
    var size: CGSize
    private var view: NebulaPrivateChatRow?
    private var observation: SettingsObservation?
    init(node: ASDisplayNode, size: CGSize, theme: PresentationTheme, russian: Bool, archive: Bool) {
        self.node = node; self.size = size; self.theme = theme; self.russian = russian; self.archive = archive
        observation = NebulaSettingsStore.shared.observe { [weak self] in self?.refresh() }
    }
    deinit { observation?.cancel() }
    func refresh() {
        guard let node else { return }
        let enabled = NebulaPresentationPrivacy.enabled
        if enabled && view == nil { let mask = NebulaPrivateChatRow(frame: .zero); node.view.addSubview(mask); view = mask }
        guard let view else { return }
        UIView.performWithoutAnimation {
            view.isHidden = !enabled
            view.frame = CGRect(origin: .zero, size: self.size)
            if enabled { view.update(theme: self.theme, russian: self.russian, archive: self.archive) }
        }
    }
}

enum NebulaChatListPrivacy {
    static func update(node: ASDisplayNode, size: CGSize, theme: PresentationTheme, russian: Bool, archive: Bool) {
        let lifetime: NebulaPrivateRowLifetime
        if let existing = objc_getAssociatedObject(node, &nebulaPrivacyAssociation) as? NebulaPrivateRowLifetime { lifetime = existing }
        else {
            lifetime = NebulaPrivateRowLifetime(node: node, size: size, theme: theme, russian: russian, archive: archive)
            objc_setAssociatedObject(node, &nebulaPrivacyAssociation, lifetime, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        }
        lifetime.size = size; lifetime.theme = theme; lifetime.russian = russian; lifetime.archive = archive; lifetime.refresh()
    }
}
