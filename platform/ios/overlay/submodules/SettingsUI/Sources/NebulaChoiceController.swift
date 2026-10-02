import UIKit
import TelegramPresentationData

/// Centered, dimmed choice popup with scrollable Dynamic Type rows.
final class NebulaChoiceController: UITableViewController, UISearchResultsUpdating {
    private let choices: [String]
    private let selected: Int?
    private let detail: String?
    private let choose: (Int) -> Void
    private let ru: Bool
    private let theme: PresentationTheme?
    private var selectionIndicatorVisible = true
    private var sectionStart: Int?
    private var subtitles: [String] = []
    private var filtered: [Int]?
    private let popupTransition = NebulaPopupTransition()

    private func indices(_ section: Int) -> [Int] {
        if let filtered { return filtered }
        if let start = sectionStart {
            return section == 0 ? Array(0..<start) : Array(start..<choices.count)
        }
        return Array(choices.indices)
    }
    func updateSearchResults(for searchController: UISearchController) {
        let query = (searchController.searchBar.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        filtered = query.isEmpty ? nil : choices.indices.filter {
            choices[$0].localizedCaseInsensitiveContains(query) || (subtitles.indices.contains($0) && subtitles[$0].localizedCaseInsensitiveContains(query))
        }
        tableView.reloadData()
    }
    override func numberOfSections(in tableView: UITableView) -> Int { filtered == nil && sectionStart != nil ? 2 : 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 1 && filtered == nil ? (ru ? "Другие языки" : "Other languages") : nil
    }

    init(title: String, choices: [String], selected: Int?, detail: String?, russian: Bool, theme: PresentationTheme?, choose: @escaping (Int) -> Void) {
        self.choices = choices; self.selected = selected; self.detail = detail; self.choose = choose; self.ru = russian; self.theme = theme
        super.init(style: .insetGrouped)
        self.title = title
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        definesPresentationContext = true
        if let theme {
            tableView.backgroundColor = theme.list.blocksBackgroundColor.withAlphaComponent(1)
            tableView.separatorColor = theme.list.itemSecondaryTextColor.withAlphaComponent(0.12)
            view.tintColor = theme.list.itemAccentColor
        }
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
        tableView.sectionHeaderHeight = 12
        tableView.sectionFooterHeight = 12
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: ru ? "Отмена" : "Cancel", style: .plain, target: self, action: #selector(close))
    }
    @objc private func close() { dismiss(animated: true) }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { indices(section).count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? { detail }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let index = indices(indexPath.section)[indexPath.row]
        let cell = UITableViewCell(style: subtitles.isEmpty ? .default : .subtitle, reuseIdentifier: nil)
        if subtitles.indices.contains(index) { cell.detailTextLabel?.text = subtitles[index] }
        cell.textLabel?.text = choices[index]
        cell.accessoryType = selectionIndicatorVisible && index == selected ? .checkmark : .none
        if index == selected { cell.accessibilityTraits.insert(.selected) }
        NebulaSettingsStyle.finish(cell, theme: theme)
        if !selectionIndicatorVisible && index == selected {
            cell.backgroundColor = view.tintColor.withAlphaComponent(0.18)
            cell.textLabel?.textColor = view.tintColor
        }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let action = choose
        let index = indices(indexPath.section)[indexPath.row]
        dismiss(animated: true) { action(index) }
    }
    static func show(from host: UIViewController, title: String, choices: [String], selected: Int? = nil,
                     detail: String? = nil, russian: Bool, selectionIndicatorVisible: Bool = true,
                     theme: PresentationTheme? = nil, sectionStart: Int? = nil, subtitles: [String] = [], searchable: Bool = false, choose: @escaping (Int) -> Void) {
        guard host.presentedViewController == nil else { return }
        let controller = NebulaChoiceController(title: title, choices: choices, selected: selected, detail: detail, russian: russian, theme: theme, choose: choose)
        controller.selectionIndicatorVisible = selectionIndicatorVisible
        controller.sectionStart = sectionStart
        controller.subtitles = subtitles
        if searchable {
            let search = UISearchController(searchResultsController: nil)
            search.obscuresBackgroundDuringPresentation = false
            search.searchResultsUpdater = controller
            search.searchBar.placeholder = russian ? "Поиск языка" : "Search languages"
            controller.navigationItem.searchController = search
            controller.navigationItem.hidesSearchBarWhenScrolling = false
        }
        let navigation = UINavigationController(rootViewController: controller)
        if let theme {
            navigation.view.tintColor = theme.list.itemAccentColor
            let appearance = UINavigationBarAppearance()
            appearance.configureWithOpaqueBackground()
            appearance.backgroundColor = theme.list.blocksBackgroundColor.withAlphaComponent(1)
            appearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
            navigation.navigationBar.standardAppearance = appearance
            navigation.navigationBar.scrollEdgeAppearance = appearance
        }
        navigation.modalPresentationStyle = .custom
        navigation.transitioningDelegate = controller.popupTransition
        navigation.preferredContentSize = CGSize(width: 390, height: min(620, CGFloat(choices.count) * 64 + (searchable ? 190 : 100)))
        host.present(navigation, animated: true)
    }
}

/// Shared with native chat entry points without exporting the UIKit implementation.
public func nebulaPresentChoice(from host: UIViewController, title: String, choices: [String], russian: Bool,
                                theme: PresentationTheme, choose: @escaping (Int) -> Void) {
    NebulaChoiceController.show(from: host, title: title, choices: choices, russian: russian, theme: theme, choose: choose)
}

private final class NebulaPopupTransition: NSObject, UIViewControllerTransitioningDelegate {
    func presentationController(forPresented presented: UIViewController, presenting: UIViewController?, source: UIViewController) -> UIPresentationController? {
        NebulaPopupPresentation(presentedViewController: presented, presenting: presenting)
    }
}

private final class NebulaPopupPresentation: UIPresentationController {
    private let dim = UIView()
    private var keyboardFrame: CGRect = .null
    deinit { NotificationCenter.default.removeObserver(self) }
    override var frameOfPresentedViewInContainerView: CGRect {
        guard let containerView else { return .zero }
        var safe = containerView.bounds.inset(by: containerView.safeAreaInsets).insetBy(dx: 24, dy: 24)
        if !keyboardFrame.isNull {
            let keyboard = containerView.convert(keyboardFrame, from: nil)
            if keyboard.intersects(containerView.bounds), keyboard.width >= safe.width {
                safe.size.height = max(0, min(safe.maxY, keyboard.minY - 12) - safe.minY)
            }
        }
        let preferred = presentedViewController.preferredContentSize
        let size = CGSize(width: min(safe.width, preferred.width), height: min(safe.height, preferred.height))
        return CGRect(x: safe.midX - size.width / 2, y: safe.midY - size.height / 2, width: size.width, height: size.height)
    }
    override func presentationTransitionWillBegin() {
        guard let containerView else { return }
        NotificationCenter.default.addObserver(self, selector: #selector(keyboardChanged(_:)), name: UIResponder.keyboardWillChangeFrameNotification, object: nil)
        dim.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        dim.frame = containerView.bounds
        dim.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(close)))
        containerView.insertSubview(dim, at: 0)
        dim.alpha = 0
        presentedViewController.transitionCoordinator?.animate(alongsideTransition: { _ in self.dim.alpha = 1 })
        if presentedViewController.transitionCoordinator == nil { dim.alpha = 1 }
    }
    override func dismissalTransitionWillBegin() {
        presentedViewController.transitionCoordinator?.animate(alongsideTransition: { _ in self.dim.alpha = 0 })
    }
    override func containerViewWillLayoutSubviews() {
        dim.frame = containerView?.bounds ?? .zero
        presentedView?.frame = frameOfPresentedViewInContainerView
        presentedView?.layer.cornerRadius = 24
        presentedView?.clipsToBounds = true
    }
    @objc private func close() { presentedViewController.dismiss(animated: true) }
    @objc private func keyboardChanged(_ notification: Notification) {
        keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect ?? .null
        let duration = notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? 0.25
        UIView.animate(withDuration: duration) {
            self.presentedView?.frame = self.frameOfPresentedViewInContainerView
            self.presentedView?.layoutIfNeeded()
        }
    }
}
