import UIKit
import AccountContext
import TelegramCore
import Postbox
import SwiftSignalKit
import AnimatedStickerNode
import TelegramAnimatedStickerNode

/// Each introduction owns its playback; cached sticker data never shares a playhead.
final class NebulaAnimatedSettingsEmoji: UIView {
    private let context: AccountContext
    private let emoji: String
    private let fallback = UILabel()
    private let pack = MetaDisposable()
    private let fetch = MetaDisposable()
    private var sticker: AnimatedStickerNode?
    private var fileId: MediaId?
    private var pageVisible = true
    private var active = false
    private var rewind = true
    private var foreground = UIApplication.shared.applicationState == .active

    init(context: AccountContext, emoji: String) {
        self.context = context; self.emoji = emoji
        super.init(frame: .zero)
        isAccessibilityElement = false
        fallback.text = emoji; fallback.font = .systemFont(ofSize: 72)
        fallback.textAlignment = .center; addSubview(fallback)
        NotificationCenter.default.addObserver(self, selector: #selector(resumeApplication), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(pauseApplication), name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(updatePlayback), name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
        let native = context.engine.stickers.loadedStickerPack(reference: .animatedEmoji, forceActualized: false)
        let pages = context.engine.stickers.loadedStickerPack(reference: .name("RestrictedEmoji"), forceActualized: false)
        pack.set((combineLatest(pages, native) |> deliverOnMainQueue).start(next: { [weak self] pages, native in
            guard let self else { return }
            func match(_ result: LoadedStickerPack) -> TelegramMediaFile? {
                guard case let .result(_, items, _) = result else { return nil }
                for item in items where item.getStringRepresentationsOfIndexKeys().contains(where: { Self.normalized($0) == Self.normalized(self.emoji) }) {
                    let file = item.file._parse()
                    if file.isAnimatedSticker || file.isVideoSticker { return file }
                }
                return nil
            }
            if let file = match(pages) ?? match(native) { self.show(file) }
        }))
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { pack.dispose(); fetch.dispose(); NotificationCenter.default.removeObserver(self) }
    private static func normalized(_ value: String) -> String { value.replacingOccurrences(of: "\u{FE0F}", with: "") }
    private func show(_ file: TelegramMediaFile) {
        guard fileId != file.fileId else { return }
        fileId = file.fileId; sticker?.visibility = false; sticker?.pause()
        sticker?.view.removeFromSuperview(); fallback.isHidden = false
        let node = DefaultAnimatedStickerNodeImpl()
        node.automaticallyLoadFirstFrame = true
        node.started = { [weak self, weak node] in
            guard let self, self.sticker === node else { return }
            self.fallback.isHidden = true
        }
        sticker = node; insertSubview(node.view, belowSubview: fallback)
        let path = context.engine.resources.shortLivedResourceCachePathPrefix(id: EngineMediaResource.Id(file.resource.id))
        node.setup(source: AnimatedStickerResourceSource(account: context.account, resource: file.resource, isVideo: file.isVideoSticker),
            width: Int(88 * UIScreen.main.scale), height: Int(88 * UIScreen.main.scale), playbackMode: .once, mode: .direct(cachePathPrefix: path))
        fetch.set(context.engine.resources.fetch(reference: .media(media: .standalone(media: file), resource: file.resource), userLocation: .other, userContentType: .other).start())
        active = false; rewind = true; setNeedsLayout(); updatePlayback()
    }
    func setPageVisible(_ visible: Bool) {
        if pageVisible != visible { rewind = true }
        pageVisible = visible; updatePlayback()
    }
    override func didMoveToWindow() { super.didMoveToWindow(); rewind = true; updatePlayback() }
    override func layoutSubviews() {
        super.layoutSubviews(); fallback.frame = bounds; sticker?.frame = bounds; sticker?.updateLayout(size: bounds.size)
        updatePlayback()
    }
    @objc private func resumeApplication() { foreground = true; updatePlayback() }
    @objc private func pauseApplication() { foreground = false; updatePlayback() }
    @objc private func updatePlayback() {
        let visible = window != nil && pageVisible && foreground && !isHidden && alpha > 0.01
            && bounds.width > 0 && bounds.height > 0
        sticker?.visibility = visible
        guard visible else { sticker?.pause(); active = false; rewind = true; return }
        if UIAccessibility.isReduceMotionEnabled {
            sticker?.seekTo(.start); sticker?.pause(); active = false; return
        }
        if !active || rewind { sticker?.play(firstFrame: true, fromIndex: 0); rewind = false }
        active = true
    }
}
