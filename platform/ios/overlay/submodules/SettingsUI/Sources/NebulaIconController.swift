import UIKit

/// A native grid of square, rounded icon previews. Selection uses iOS alternate icons.
public final class NebulaIconController: UICollectionViewController, UICollectionViewDelegateFlowLayout {
    private let russian: Bool
    private let names = ["Blue", "Ocean", "Aurora", "Sunset", "Graphite", "Pearl", "Ink", "Paper", "Mint", "Lavender", "Tangerine", "Rose", "Orbit", "Blueprint", "Nova", "Monogram"]

    public init(russian: Bool) {
        self.russian = russian
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 12
        layout.minimumLineSpacing = 16
        super.init(collectionViewLayout: layout)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    public override func viewDidLoad() {
        super.viewDidLoad()
        title = russian ? "Иконка приложения" : "App Icon"
        collectionView.backgroundColor = .systemGroupedBackground
        collectionView.contentInset = UIEdgeInsets(top: 20, left: 0, bottom: 24, right: 0)
        collectionView.register(NebulaIconTile.self, forCellWithReuseIdentifier: "NebulaIconTile")
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
    }

    @objc private func close() { dismiss(animated: true) }

    public override func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        names.count + 1
    }

    private func iconName(at index: Int) -> String? {
        index == 0 ? nil : "Nebula\(names[index - 1])Icon"
    }

    private func artwork(named name: String?) -> UIImage? {
        let resource = name ?? "NebulaBlueIcon"
        let file = resource + "@3x"
        let url = Bundle.main.url(forResource: file, withExtension: "png", subdirectory: resource + ".alticon")
            ?? Bundle.main.url(forResource: file, withExtension: "png")
        return url.flatMap { UIImage(contentsOfFile: $0.path) }
    }

    public override func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "NebulaIconTile", for: indexPath) as! NebulaIconTile
        let name = iconName(at: indexPath.item)
        let label = name == nil ? (russian ? "Основная" : "Default") : names[indexPath.item - 1]
        cell.configure(title: label, image: artwork(named: name), selected: UIApplication.shared.alternateIconName == name)
        return cell
    }

    public func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout,
                               sizeForItemAt indexPath: IndexPath) -> CGSize {
        let columns: CGFloat = collectionView.bounds.width < 360 ? 3 : 4
        let available = collectionView.bounds.width - 32 - (columns - 1) * 12
        let side = floor(available / columns)
        return CGSize(width: side, height: side + 25)
    }

    public func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout,
                               insetForSectionAt section: Int) -> UIEdgeInsets {
        UIEdgeInsets(top: 0, left: 16, bottom: 0, right: 16)
    }

    public override func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        guard UIApplication.shared.supportsAlternateIcons else {
            showError(russian ? "На этом устройстве смена иконки недоступна." : "Changing the icon is unavailable on this device.")
            return
        }
        let name = iconName(at: indexPath.item)
        UIApplication.shared.setAlternateIconName(name) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error { self?.showError(error.localizedDescription) }
                else { self?.collectionView.reloadData() }
            }
        }
    }

    private func showError(_ message: String) {
        let alert = UIAlertController(title: russian ? "Не удалось сменить иконку" : "Could not change icon", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

private final class NebulaIconTile: UICollectionViewCell {
    private let artwork = UIImageView()
    private let title = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        artwork.contentMode = .scaleAspectFill
        artwork.clipsToBounds = true
        artwork.layer.cornerRadius = 18
        artwork.layer.cornerCurve = .continuous
        contentView.addSubview(artwork)
        title.font = .preferredFont(forTextStyle: .caption1)
        title.textAlignment = .center
        title.textColor = .label
        title.adjustsFontForContentSizeCategory = true
        title.lineBreakMode = .byTruncatingTail
        contentView.addSubview(title)
        isAccessibilityElement = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        let side = bounds.width - 8
        artwork.frame = CGRect(x: 4, y: 0, width: side, height: side)
        title.frame = CGRect(x: 0, y: side + 4, width: bounds.width, height: 20)
    }

    func configure(title label: String, image: UIImage?, selected: Bool) {
        title.text = label
        artwork.image = image
        artwork.backgroundColor = image == nil ? .secondarySystemGroupedBackground : .clear
        artwork.layer.borderWidth = selected ? 3 : 0
        artwork.layer.borderColor = UIColor(red: 0.235, green: 0.553, blue: 0.941, alpha: 1).cgColor
        accessibilityLabel = label
        accessibilityTraits = selected ? [.button, .selected] : .button
    }
}
