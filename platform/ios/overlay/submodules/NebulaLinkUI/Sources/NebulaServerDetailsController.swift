import UIKit

/// Local bookkeeping belongs to the stable endpoint, not its imported profile.
final class NebulaServerDetailsController: UITableViewController {
    private let server: [String: Any]
    private let russian: Bool
    private let onSave: () -> Void
    private let keys = ["provider", "plan", "amount", "currency", "due_date", "period_days", "notes"]
    private var fields: [UITextField] = []
    private var saving = false

    init(server: [String: Any], russian: Bool, onSave: @escaping () -> Void) {
        self.server = server; self.russian = russian; self.onSave = onSave
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = text("Провайдер и оплата", "Provider and payment")
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: text("Сохранить", "Save"), style: .done,
            target: self, action: #selector(save))
        let details = server["details"] as? [String: Any] ?? [:]
        for key in keys {
            let field = UITextField()
            field.font = .preferredFont(forTextStyle: .body)
            field.adjustsFontForContentSizeCategory = true
            field.clearButtonMode = .whileEditing
            field.autocorrectionType = .no
            field.accessibilityIdentifier = "NebulaLink.Details.\(key)"
            if key == "period_days" {
                let days = details[key] as? Int ?? 0
                field.text = days == 0 ? "" : String(days)
                field.keyboardType = .numberPad
            } else {
                field.text = details[key] as? String ?? ""
                if key == "amount" { field.keyboardType = .decimalPad }
                if key == "due_date" { field.placeholder = "YYYY-MM-DD"; field.keyboardType = .numbersAndPunctuation }
                if key == "currency" { field.placeholder = "RUB / USD / EUR"; field.autocapitalizationType = .allCharacters }
            }
            fields.append(field)
        }
        tableView.keyboardDismissMode = .interactive
        tableView.rowHeight = UITableView.automaticDimension
    }
    override func numberOfSections(in tableView: UITableView) -> Int { keys.count }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        [text("Провайдер", "Provider"), text("Тариф", "Plan"), text("Сумма", "Amount"),
         text("Валюта", "Currency"), text("Следующая оплата", "Next payment"),
         text("Период оплаты, дней", "Payment period, days"), text("Заметки", "Notes")][section]
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        let field = fields[indexPath.section]
        field.removeFromSuperview()
        field.translatesAutoresizingMaskIntoConstraints = false
        cell.contentView.addSubview(field)
        NSLayoutConstraint.activate([
            field.leadingAnchor.constraint(equalTo: cell.contentView.layoutMarginsGuide.leadingAnchor),
            field.trailingAnchor.constraint(equalTo: cell.contentView.layoutMarginsGuide.trailingAnchor),
            field.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 14),
            field.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -14),
            field.heightAnchor.constraint(greaterThanOrEqualToConstant: 28)
        ])
        cell.selectionStyle = .none
        return cell
    }
    @objc private func save() {
        guard !saving, let id = server["id"] as? String else { return }
        view.endEditing(true)
        var details: [String: Any] = [:]
        for (index, key) in keys.enumerated() {
            let value = (fields[index].text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if key == "period_days" {
                guard value.isEmpty || Int(value) != nil else { showError(); return }
                details[key] = Int(value) ?? 0
            } else { details[key] = key == "amount" ? value.replacingOccurrences(of: ",", with: ".") : value }
        }
        saving = true; navigationItem.rightBarButtonItem?.isEnabled = false
        NebulaLinkService.shared.call("server.details", payload: ["id": id, "details": details]) { [weak self] result in
            guard let self = self else { return }
            self.saving = false; self.navigationItem.rightBarButtonItem?.isEnabled = true
            switch result {
            case .success: self.onSave(); self.navigationController?.popViewController(animated: true)
            case .failure: self.showError()
            }
        }
    }
    private func showError() {
        let alert = UIAlertController(title: text("Не удалось сохранить", "Could not save"), message: text(
            "Проверьте сумму, код валюты, дату (ГГГГ-ММ-ДД) и период (0–3660 дней).",
            "Check amount, currency code, date (YYYY-MM-DD) and period (0–3660 days)."), preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
