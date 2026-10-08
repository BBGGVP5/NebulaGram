import Foundation
import WebKit
import CryptoKit
import NebulaSettingsContract

public final class NebulaBrowserContentRules {
    private weak var webView: WKWebView?
    private var applied: WKContentRuleList?
    private var observer: NSObjectProtocol?
    private var revision = 0
    private static var cached: [String: WKContentRuleList] = [:]
    public init(webView: WKWebView, ready: @escaping () -> Void) {
        self.webView = webView
        observer = NotificationCenter.default.addObserver(forName: NebulaBrowserPreferences.changed, object: nil, queue: .main) { [weak self] _ in self?.refresh() }
        refresh(ready: ready)
    }
    public static func compile(_ rules: NebulaBrowserRules, completion: @escaping (Result<WKContentRuleList, Error>) -> Void) {
        let id = "nebula-browser-" + SHA256.hash(data: Data(rules.json.utf8)).map { String(format: "%02x", $0) }.joined()
        if let value = cached[id] { completion(.success(value)); return }
        WKContentRuleListStore.default().lookUpContentRuleList(forIdentifier: id) { value, _ in
            if let value { Self.remember(value, id: id); completion(.success(value)); return }
            WKContentRuleListStore.default().compileContentRuleList(forIdentifier: id, encodedContentRuleList: rules.json) { value, error in
                if let value { Self.remember(value, id: id); completion(.success(value)) }
                else { completion(.failure(error ?? NebulaBrowserRules.Failure.noSupportedRules)) }
            }
        }
    }
    private static func remember(_ value: WKContentRuleList, id: String) {
        if cached.count >= 4 { cached.removeAll() }; cached[id] = value
    }
    private func refresh(ready: (() -> Void)? = nil) {
        revision += 1; let version = revision
        guard let view = webView else { ready?(); return }
        guard NebulaBrowserPreferences.shared.enabled else {
            if let applied { view.configuration.userContentController.remove(applied) }; applied = nil; ready?(); return
        }
        do {
            let rules = try NebulaBrowserPreferences.shared.rules()
            Self.compile(rules) { [weak self] result in
                defer { ready?() }
                guard let self, self.revision == version, let view = self.webView else { return }
                if case let .success(value) = result {
                    if let old = self.applied { view.configuration.userContentController.remove(old) }
                    view.configuration.userContentController.add(value); self.applied = value
                }
            }
        } catch { ready?() }
    }
    deinit { if let observer { NotificationCenter.default.removeObserver(observer) } }
}
