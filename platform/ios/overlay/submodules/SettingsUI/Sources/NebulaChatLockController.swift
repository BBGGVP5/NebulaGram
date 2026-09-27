import UIKit
import ObjectiveC
import NebulaSettingsContract

/// Covers a protected chat before it is shown and whenever the app leaves the
/// foreground. The lock belongs to this device and this Telegram account.
public final class NebulaChatLockGate: UIView {
    private static var associationKey: UInt8 = 0
    private weak var host: UIViewController?
    private let account: Int64
    private let peer: Int64
    private let russian: Bool
    private let input = UITextField()
    private let status = UILabel()
    private let unlock = UIButton(type: .system)
    private var observer: NSObjectProtocol?
    private var busy = false

    public static func attach(to host: UIViewController, account: Int64, peer: Int64, russian: Bool) {
        let protected: Bool
        do { protected = try NebulaChatLocks.shared.hasLock(account: account, peer: peer) }
        catch { protected = true } // Do not reveal chat content on a Keychain error.
        if !protected {
            (objc_getAssociatedObject(host, &associationKey) as? NebulaChatLockGate)?.removeFromSuperview()
            objc_setAssociatedObject(host, &associationKey, nil, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
            return
        }
        if let existing = objc_getAssociatedObject(host, &associationKey) as? NebulaChatLockGate {
            existing.seal()
            return
        }
        let gate = NebulaChatLockGate(host: host, account: account, peer: peer, russian: russian)
        objc_setAssociatedObject(host, &associationKey, gate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        gate.seal()
    }

    public static func seal(host: UIViewController) {
        (objc_getAssociatedObject(host, &associationKey) as? NebulaChatLockGate)?.seal()
    }

    private init(host: UIViewController, account: Int64, peer: Int64, russian: Bool) {
        self.host = host
        self.account = account
        self.peer = peer
        self.russian = russian
        super.init(frame: host.view.bounds)
        autoresizingMask = [.flexibleWidth, .flexibleHeight]
        backgroundColor = .systemBackground
        accessibilityViewIsModal = true
        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 18
        stack.alignment = .fill
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: safeAreaLayoutGuide.centerYAnchor)
        ])
        let title = UILabel()
        title.text = russian ? "Чат защищён" : "Chat is locked"
        title.textAlignment = .center
        title.font = .preferredFont(forTextStyle: .title2)
        title.adjustsFontForContentSizeCategory = true
        stack.addArrangedSubview(title)
        input.borderStyle = .roundedRect
        input.isSecureTextEntry = true
        input.textContentType = .password
        input.placeholder = russian ? "PIN-код или пароль" : "PIN or password"
        input.autocorrectionType = .no
        input.autocapitalizationType = .none
        stack.addArrangedSubview(input)
        status.textAlignment = .center
        status.textColor = .secondaryLabel
        status.numberOfLines = 0
        status.font = .preferredFont(forTextStyle: .footnote)
        stack.addArrangedSubview(status)
        unlock.setTitle(russian ? "Открыть чат" : "Unlock chat", for: .normal)
        unlock.addTarget(self, action: #selector(unlockTapped), for: .touchUpInside)
        unlock.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        stack.addArrangedSubview(unlock)
        let back = UIButton(type: .system)
        back.setTitle(russian ? "Назад" : "Back", for: .normal)
        back.addTarget(self, action: #selector(backTapped), for: .touchUpInside)
        stack.addArrangedSubview(back)
        observer = NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification,
                                                          object: nil, queue: .main) { [weak self] _ in self?.seal() }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { if let observer = observer { NotificationCenter.default.removeObserver(observer) } }

    private func seal() {
        guard let host = host else { return }
        input.text = ""
        status.text = nil
        busy = false
        unlock.isEnabled = true
        frame = host.view.bounds
        if superview !== host.view { host.view.addSubview(self) }
        host.view.bringSubviewToFront(self)
    }

    @objc private func backTapped() { host?.navigationController?.popViewController(animated: true) }
    @objc private func unlockTapped() {
        guard !busy, let credential = input.text, !credential.isEmpty else { return }
        input.text = ""
        busy = true
        unlock.isEnabled = false
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            let success = (try? NebulaChatLocks.shared.verify(account: self.account, peer: self.peer, credential: credential)) == true
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self.busy = false
                self.unlock.isEnabled = true
                if success { self.removeFromSuperview() }
                else { self.status.text = self.russian ? "Неверный код или временная задержка. Попробуйте позже." : "Wrong code or temporarily rate-limited. Try again later." }
            }
        }
    }
}

public final class NebulaChatLockEditorController: UIViewController {
    public var onChanged: (() -> Void)?
    private let account: Int64
    private let peer: Int64
    private let russian: Bool
    private let mode = UISegmentedControl(items: ["PIN", "Password"])
    private let current = UITextField()
    private let newCredential = UITextField()
    private let confirm = UITextField()
    private let status = UILabel()
    private let save = UIButton(type: .system)
    private let remove = UIButton(type: .system)
    private var existing = false
    private var busy = false

    public init(account: Int64, peer: Int64, russian: Bool) {
        self.account = account; self.peer = peer; self.russian = russian
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    public override func viewDidLoad() {
        super.viewDidLoad()
        title = russian ? "Пароль чата" : "Chat password"
        view.backgroundColor = .systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 18
        stack.layoutMargins = UIEdgeInsets(top: 24, left: 20, bottom: 30, right: 20)
        stack.isLayoutMarginsRelativeArrangement = true
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor)
        ])
        mode.selectedSegmentIndex = 0
        mode.setTitle(russian ? "Пароль" : "Password", forSegmentAt: 1)
        mode.addTarget(self, action: #selector(modeChanged), for: .valueChanged)
        stack.addArrangedSubview(mode)
        for field in [current, newCredential, confirm] {
            field.borderStyle = .roundedRect
            field.isSecureTextEntry = true
            field.autocorrectionType = .no
            field.autocapitalizationType = .none
            field.textContentType = .password
            field.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
            stack.addArrangedSubview(field)
        }
        current.placeholder = russian ? "Текущий PIN или пароль" : "Current PIN or password"
        newCredential.placeholder = russian ? "Новый PIN" : "New PIN"
        confirm.placeholder = russian ? "Повтори PIN" : "Repeat PIN"
        status.numberOfLines = 0
        status.textColor = .secondaryLabel
        stack.addArrangedSubview(status)
        let explanation = UILabel()
        explanation.numberOfLines = 0
        explanation.font = .preferredFont(forTextStyle: .footnote)
        explanation.textColor = .secondaryLabel
        explanation.text = russian
            ? "Код действует только на этом устройстве и не синхронизируется. При выходе чат снова блокируется. На других клиентах Telegram он остаётся доступным."
            : "This code is local to this device and does not sync. The chat locks again when you leave it. Other Telegram clients can still open it."
        stack.addArrangedSubview(explanation)
        save.setTitle(russian ? "Сохранить" : "Save", for: .normal)
        save.addTarget(self, action: #selector(saveTapped), for: .touchUpInside)
        save.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        stack.addArrangedSubview(save)
        remove.setTitle(russian ? "Убрать защиту" : "Remove lock", for: .normal)
        remove.setTitleColor(.systemRed, for: .normal)
        remove.addTarget(self, action: #selector(removeTapped), for: .touchUpInside)
        stack.addArrangedSubview(remove)
        do {
            existing = try NebulaChatLocks.shared.hasLock(account: account, peer: peer)
            if let selected = try NebulaChatLocks.shared.mode(account: account, peer: peer) { mode.selectedSegmentIndex = selected == .pin ? 0 : 1 }
        } catch { status.text = russian ? "Хранилище кодов временно недоступно." : "The device credential store is unavailable." }
        current.isHidden = !existing
        remove.isHidden = !existing
        modeChanged()
    }
    @objc private func close() { dismiss(animated: true) }
    @objc private func modeChanged() {
        let pin = mode.selectedSegmentIndex == 0
        newCredential.keyboardType = pin ? .numberPad : .default
        confirm.keyboardType = pin ? .numberPad : .default
        newCredential.placeholder = pin ? (russian ? "Новый PIN (4–12 цифр)" : "New PIN (4–12 digits)") : (russian ? "Новый пароль (от 6 символов)" : "New password (6+ characters)")
        confirm.placeholder = pin ? (russian ? "Повтори PIN" : "Repeat PIN") : (russian ? "Повтори пароль" : "Repeat password")
    }
    private func setBusy(_ value: Bool) { busy = value; save.isEnabled = !value; remove.isEnabled = !value }
    @objc private func saveTapped() {
        guard !busy else { return }
        let value = newCredential.text ?? ""
        guard value == confirm.text else { status.text = russian ? "Значения не совпадают." : "Values do not match."; return }
        let old = existing ? current.text : nil
        let selected: NebulaChatLocks.Mode = mode.selectedSegmentIndex == 0 ? .pin : .password
        current.text = ""; newCredential.text = ""; confirm.text = ""
        performChange { try NebulaChatLocks.shared.set(account: self.account, peer: self.peer, current: old, new: value, mode: selected) }
    }
    @objc private func removeTapped() {
        guard !busy else { return }
        let old = current.text ?? ""
        current.text = ""; newCredential.text = ""; confirm.text = ""
        performChange { try NebulaChatLocks.shared.remove(account: self.account, peer: self.peer, current: old) }
    }
    private func performChange(_ change: @escaping () throws -> Void) {
        setBusy(true)
        status.text = russian ? "Сохранение…" : "Saving…"
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            do { try change(); DispatchQueue.main.async { [weak self] in self?.setBusy(false); self?.onChanged?(); self?.dismiss(animated: true) } }
            catch { DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self.setBusy(false)
                self.status.text = self.russian ? "Не удалось сохранить. Проверьте текущий код или попробуйте позже." : "Could not save. Check the current code or try again later."
            } }
        }
    }
}
