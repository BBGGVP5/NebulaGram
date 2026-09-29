import UIKit
import ComponentFlow
import GlassBackgroundComponent
import NebulaSettingsContract

final class NebulaGlassController: UITableViewController {
    private let ru: Bool
    private let store = NebulaSettingsStore.shared
    private var observation: SettingsObservation?
    private var writeFailed = false
    private lazy var preview = NebulaGlassPreview(russian: ru)
    private var styles: [String] { ru ? ["Как в Telegram", "Жидкое стекло", "Матовое стекло"] : ["Telegram default", "Liquid glass", "Frosted glass"] }
    private var qualities: [String] { ru ? ["Автоматически", "Полное", "Облегчённое"] : ["Automatic", "Full", "Light"] }

    init(russian: Bool) { ru = russian; super.init(style: .insetGrouped); title = russian ? "Стекло" : "Glass" }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 58
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        observation = store.observe { [weak self] in self?.preview.setNeedsLayout() }
        for name in [Notification.Name.NSProcessInfoPowerStateDidChange, ProcessInfo.thermalStateDidChangeNotification, UIAccessibility.reduceTransparencyStatusDidChangeNotification, UIApplication.didBecomeActiveNotification] {
            NotificationCenter.default.addObserver(self, selector: #selector(refresh), name: name, object: nil)
        }
    }
    @objc private func close() { dismiss(animated: true) }
    @objc private func refresh() { tableView.reloadData(); preview.setNeedsLayout() }
    override func numberOfSections(in tableView: UITableView) -> Int { 4 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 1 ? styles.count : (section == 3 ? 5 : 1)
    }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 1 ? (ru ? "Оформление" : "Appearance") : nil
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 { return ru ? "Двигайте пилюлю по тексту. Превью использует тот же эффект, что и стеклянные элементы интерфейса." : "Drag the capsule over the text. The preview uses the same effect as glass surfaces in the app." }
        if section == 2 { return ru ? "Тонировка применяется к жидкому и матовому стилям. Размытие и преломление системного стекла регулирует iOS." : "Tint applies to liquid and frosted styles. iOS controls the blur and refraction of system glass." }
        guard section == 3 else { return nil }
        if writeFailed { return ru ? "Не удалось сохранить настройку." : "Could not save this setting." }
        if UIAccessibility.isReduceTransparencyEnabled { return ru ? "iOS уменьшает прозрачность: используется сплошной фон." : "Reduce Transparency is on: an opaque background is used." }
        let reduced = NebulaGlassPolicy.reduced(mode: store.glassQuality, lowPower: ProcessInfo.processInfo.isLowPowerModeEnabled,
            hot: ProcessInfo.processInfo.thermalState.rawValue >= ProcessInfo.ThermalState.serious.rawValue, reduceTransparency: false)
        if reduced { return ru ? "Сейчас облегчённый материал. Авто включает его при энергосбережении и нагреве." : "Light material is active. Auto uses it in Low Power Mode or during thermal pressure." }
        if #available(iOS 26.0, *) { return ru ? "Анимация действует только для системного Liquid Glass. Блики и глубина нашего стекла настраиваются отдельно; материал Telegram регулирует iOS." : "Animation applies only to system Liquid Glass. Nebula glass highlights and depth have separate controls; iOS controls Telegram's material." }
        return ru ? "На этой версии iOS доступно матовое размытие. Для Liquid Glass нужна iOS 26." : "This iOS version uses frosted blur. Liquid Glass requires iOS 26."
    }
    override func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        indexPath.section == 0 ? 240 : UITableView.automaticDimension
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        defer { NebulaSettingsStyle.finish(cell) }
        switch indexPath.section {
        case 0:
            preview.removeFromSuperview(); cell.contentView.addSubview(preview)
            preview.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([preview.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor), preview.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor), preview.topAnchor.constraint(equalTo: cell.contentView.topAnchor), preview.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor)])
            cell.selectionStyle = .none
        case 1:
            cell.textLabel?.text = styles[indexPath.row]
            cell.accessoryType = store.iosGlassStyle == indexPath.row ? .checkmark : .none
        case 2:
            cell.textLabel?.text = tintTitle()
            let slider = UISlider(frame: CGRect(x: 0, y: 0, width: 140, height: 44))
            slider.minimumValue = 0; slider.maximumValue = 60; slider.value = Float(store.iosGlassTint)
            slider.isEnabled = store.iosGlassStyle != 0
            slider.accessibilityLabel = ru ? "Тонировка стекла" : "Glass tint"
            slider.accessibilityValue = "\(store.iosGlassTint)%"
            slider.addTarget(self, action: #selector(tintChanged(_:)), for: .valueChanged)
            cell.accessoryView = slider; cell.selectionStyle = .none
        default:
            if indexPath.row == 0 {
                cell.textLabel?.text = ru ? "Качество" : "Quality"
                cell.detailTextLabel?.text = qualities[store.glassQuality]; cell.accessoryType = .disclosureIndicator
            } else if indexPath.row == 1 {
                cell.textLabel?.text = ru ? "Анимация жидкого стекла" : "Liquid glass animation"
                cell.detailTextLabel?.text = ru ? "Отклик системного эффекта" : "System glass interaction"
                let toggle = UISwitch()
                toggle.isOn = store.liquidAnimations
                if #available(iOS 26.0, *) {
                    toggle.isEnabled = store.iosGlassStyle == 1 && !store.hasLoadError
                } else {
                    toggle.isEnabled = false
                }
                toggle.accessibilityLabel = cell.textLabel?.text
                toggle.addTarget(self, action: #selector(animationsChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle
                cell.selectionStyle = .none
            } else if indexPath.row == 2 {
                cell.textLabel?.text = ru ? "Блики и контур" : "Highlights and rim"
                cell.detailTextLabel?.text = ru ? "Подчеркнуть края нашего стекла" : "Accent the edges of Nebula glass"
                let toggle = UISwitch()
                toggle.isOn = store.glassHighlights
                if #available(iOS 26.0, *) {
                    toggle.isEnabled = store.iosGlassStyle != 0 && !store.hasLoadError
                } else {
                    toggle.isEnabled = !store.hasLoadError
                }
                toggle.accessibilityLabel = cell.textLabel?.text
                toggle.addTarget(self, action: #selector(highlightsChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle
                cell.selectionStyle = .none
            } else if indexPath.row == 3 {
                cell.textLabel?.text = ru ? "Тень и объём" : "Shadow and depth"
                cell.detailTextLabel?.text = ru ? "Глубина краёв стекла" : "Depth around glass surfaces"
                let toggle = UISwitch()
                toggle.isOn = store.glassDepthEnabled
                toggle.isEnabled = supportsDepth && !store.hasLoadError
                toggle.accessibilityLabel = cell.textLabel?.text
                toggle.addTarget(self, action: #selector(depthEnabledChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle
                cell.selectionStyle = .none
            } else {
                cell.textLabel?.text = depthTitle()
                let slider = UISlider(frame: CGRect(x: 0, y: 0, width: 140, height: 44))
                slider.minimumValue = 0; slider.maximumValue = 100; slider.value = Float(store.glassDepth)
                slider.isEnabled = supportsDepth && store.glassDepthEnabled && !store.hasLoadError
                slider.accessibilityLabel = ru ? "Глубина стекла" : "Glass depth"
                slider.accessibilityValue = "\(store.glassDepth)%"
                slider.addTarget(self, action: #selector(depthChanged(_:)), for: .valueChanged)
                cell.accessoryView = slider; cell.selectionStyle = .none
            }
        }
        return cell
    }
    private func tintTitle() -> String { (ru ? "Тонировка · " : "Tint · ") + "\(store.iosGlassTint)%" }
    private var supportsDepth: Bool {
        if #available(iOS 26.0, *) { return store.iosGlassStyle != 0 }
        return true
    }
    private func depthTitle() -> String { (ru ? "Глубина · " : "Depth · ") + "\(store.glassDepth)%" }
    private func set(_ value: Int, key: String) {
        do { try store.set(.integer(value), for: key); writeFailed = false } catch { writeFailed = true }
    }
    @objc private func tintChanged(_ slider: UISlider) {
        set(Int(slider.value.rounded()), key: "ios_glass_tint")
        slider.accessibilityValue = "\(store.iosGlassTint)%"
        tableView.cellForRow(at: IndexPath(row: 0, section: 2))?.textLabel?.text = tintTitle()
    }
    @objc private func depthChanged(_ slider: UISlider) {
        set(Int(slider.value.rounded()), key: "glass_depth")
        slider.accessibilityValue = "\(store.glassDepth)%"
        tableView.cellForRow(at: IndexPath(row: 4, section: 3))?.textLabel?.text = depthTitle()
    }
    @objc private func depthEnabledChanged(_ toggle: UISwitch) {
        do { try store.set(.boolean(toggle.isOn), for: "glass_depth_enabled"); writeFailed = false }
        catch { writeFailed = true; toggle.isOn = store.glassDepthEnabled }
        refresh()
    }
    @objc private func animationsChanged(_ toggle: UISwitch) {
        do { try store.set(.boolean(toggle.isOn), for: "liquid_animations"); writeFailed = false }
        catch { writeFailed = true; toggle.isOn = store.liquidAnimations }
        refresh()
    }
    @objc private func highlightsChanged(_ toggle: UISwitch) {
        do { try store.set(.boolean(toggle.isOn), for: "glass_highlights"); writeFailed = false }
        catch { writeFailed = true; toggle.isOn = store.glassHighlights }
        refresh()
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 1 { set(indexPath.row, key: "ios_glass_style"); refresh() }
        if indexPath.section == 3 && indexPath.row == 0 {
            NebulaChoiceController.show(from: self, title: ru ? "Качество стекла" : "Glass quality", choices: qualities,
                selected: store.glassQuality, russian: ru) { [weak self] mode in self?.set(mode, key: "glass_quality"); self?.refresh() }
        }
    }
}

private final class NebulaGlassPreview: UIView {
    private let glass = GlassBackgroundView(frame: .zero)
    private let titleLabel = UILabel()
    private var lines: [UILabel] = []
    private var position: CGFloat = 0.5
    init(russian: Bool) {
        super.init(frame: .zero)
        backgroundColor = .systemGroupedBackground; layer.cornerRadius = 20; clipsToBounds = true
        let texts = russian ? ["Сообщения под стеклом", "Текст остаётся на месте", "NebulaGram · 0123456789", "Двигайте пилюлю вверх и вниз"]
            : ["Messages behind glass", "Text stays in place", "NebulaGram · 0123456789", "Drag the capsule up and down"]
        for text in texts {
            let label = UILabel(); label.text = text; label.textColor = .secondaryLabel
            label.font = .preferredFont(forTextStyle: .body); label.adjustsFontForContentSizeCategory = true
            label.backgroundColor = .secondarySystemGroupedBackground; label.layer.cornerRadius = 12; label.clipsToBounds = true
            addSubview(label); lines.append(label)
        }
        addSubview(glass); glass.isUserInteractionEnabled = false
        titleLabel.text = "NebulaGram"; titleLabel.font = .preferredFont(forTextStyle: .headline); titleLabel.textAlignment = .center
        glass.contentView.addSubview(titleLabel)
        addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(movePreview(_:))))
        isAccessibilityElement = true; accessibilityTraits = [.adjustable]
        accessibilityLabel = russian ? "Превью стекла, положение пилюли" : "Glass preview, capsule position"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews()
        for (index, label) in lines.enumerated() { label.frame = CGRect(x: 16, y: 18 + CGFloat(index) * 52, width: max(0, bounds.width - 32), height: 40) }
        let size = CGSize(width: max(0, bounds.width - 64), height: 64)
        glass.frame = CGRect(origin: CGPoint(x: 32, y: 8 + position * max(0, bounds.height - 80)), size: size)
        glass.update(size: size, cornerRadius: 32, isDark: traitCollection.userInterfaceStyle == .dark, tintColor: .init(kind: .panel), isInteractive: true, transition: .immediate)
        titleLabel.frame = CGRect(origin: .zero, size: size)
        accessibilityValue = "\(Int(position * 100))%"
    }
    @objc private func movePreview(_ gesture: UIPanGestureRecognizer) {
        position = min(1, max(0, (gesture.location(in: self).y - 40) / max(1, bounds.height - 80))); setNeedsLayout()
    }
    override func accessibilityIncrement() { position = min(1, position + 0.2); setNeedsLayout() }
    override func accessibilityDecrement() { position = max(0, position - 0.2); setNeedsLayout() }
}
