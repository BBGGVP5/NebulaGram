import Foundation
import UIKit
import CoreText

/// Imported interface font (Android `NebulaFont`, custom mode). `Font.regular`,
/// `Font.medium`, `Font.semibold`, `Font.bold`, `Font.light` and `Font.with`
/// ask here first, so the whole interface follows one switch. Monospaced
/// fonts are not replaced: code in messages relies on their alignment.
///
/// The file is read and registered once per launch; changing it applies after
/// a restart, like Telegram's own font size cache.
public enum NebulaCustomFont {
    /// Keep in sync with `NebulaFontFile.enabledKey` / `fileName` in NebulaSettingsContract.
    public static let enabledKey = "nebula.font.custom"
    public static let fileName = "nebula-custom-font.sfnt"

    public static var fileURL: URL? {
        guard let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return nil }
        return directory.appendingPathComponent(fileName)
    }

    public static var hasFile: Bool {
        guard let url = fileURL else { return false }
        return FileManager.default.fileExists(atPath: url.path)
    }

    private static let resolvedName: String? = {
        guard UserDefaults.standard.bool(forKey: enabledKey), let url = fileURL, let data = try? Data(contentsOf: url) else { return nil }
        return register(data)
    }()

    /// Registers the font for this process and returns its PostScript name.
    @discardableResult
    public static func register(_ data: Data) -> String? {
        guard let provider = CGDataProvider(data: data as CFData), let font = CGFont(provider), let name = font.postScriptName as String? else { return nil }
        if UIFont(name: name, size: 12.0) == nil {
            var error: Unmanaged<CFError>?
            if !CTFontManagerRegisterGraphicsFont(font, &error) {
                _ = error?.takeRetainedValue()
                return UIFont(name: name, size: 12.0) == nil ? nil : name
            }
        }
        return name
    }

    /// Validated data replaces the stored file atomically. Returns the font name.
    public static func install(_ data: Data) throws -> String {
        guard let url = fileURL, let name = register(data) else { throw CocoaError(.fileReadCorruptFile) }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
        return name
    }

    public static func font(size: CGFloat, weight: UIFont.Weight, italic: Bool = false) -> UIFont? {
        guard let name = resolvedName, let base = UIFont(name: name, size: size) else { return nil }
        var traits: UIFontDescriptor.SymbolicTraits = []
        if weight.rawValue >= UIFont.Weight.semibold.rawValue { traits.insert(.traitBold) }
        if italic { traits.insert(.traitItalic) }
        guard !traits.isEmpty, let descriptor = base.fontDescriptor.withSymbolicTraits(traits) else { return base }
        return UIFont(descriptor: descriptor, size: size)
    }
}
