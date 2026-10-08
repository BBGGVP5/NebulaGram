import Foundation
import UIKit
import ImageIO
import ZipArchive
import Svg
import AppBundle
import NebulaSettingsContract

struct NebulaInstalledIconPack: Codable {
    let id: String
    let name: String
    let author: String
    let count: Int
}

enum NebulaImportedIcons {
    private static let indexKey = "nebula.ios.iconPacks"
    static let selectionKey = "nebula.ios.selectedIconPack"
    private static let queue = DispatchQueue(label: "NebulaIconImport", qos: .userInitiated)
    static var installed: [NebulaInstalledIconPack] { guard let data = UserDefaults.standard.data(forKey: indexKey) else { return [] }; return (try? JSONDecoder().decode([NebulaInstalledIconPack].self, from: data)) ?? [] }
    static var activeID: String? {
        guard let data = UserDefaults.standard.data(forKey: NebulaSettingsStore.storageKey), let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let settings = json["settings"] as? [String: Any], settings["icon_pack"] as? Int == 3,
              let id = UserDefaults.standard.string(forKey: selectionKey), UUID(uuidString: id) != nil else { return nil }
        return id
    }
    static var activeName: String? { installed.first { $0.id == activeID }?.name }
    static func directory(_ id: String) throws -> URL {
        guard UUID(uuidString: id) != nil else { throw NebulaIconPackManifest.Failure.invalid }
        return try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("NebulaIconPacks", isDirectory: true).appendingPathComponent(id, isDirectory: true)
    }
    static func image(semantic: String) -> UIImage? {
        guard let id = activeID, let folder = try? directory(id) else { return nil }
        return UIImage(contentsOfFile: folder.appendingPathComponent(semantic + ".png").path)?.withRenderingMode(.alwaysTemplate)
    }
    static func select(_ pack: NebulaInstalledIconPack?) throws {
        if let pack {
            guard installed.contains(where: { $0.id == pack.id }), let folder = try? directory(pack.id), FileManager.default.fileExists(atPath: folder.path) else { throw NebulaIconPackManifest.Failure.invalid }
            try NebulaSettingsStore.shared.set(.integer(3), for: "icon_pack")
            UserDefaults.standard.set(pack.id, forKey: selectionKey)
        } else { UserDefaults.standard.removeObject(forKey: selectionKey) }
    }
    static func remove(_ pack: NebulaInstalledIconPack) throws {
        if activeID == pack.id { try NebulaSettingsStore.shared.set(.integer(0), for: "icon_pack"); UserDefaults.standard.removeObject(forKey: selectionKey) }
        let next = installed.filter { $0.id != pack.id }
        UserDefaults.standard.set(try JSONEncoder().encode(next), forKey: indexKey)
        try? FileManager.default.removeItem(at: directory(pack.id))
    }
    static func install(data: Data, completion: @escaping (Result<NebulaInstalledIconPack, Error>) -> Void) {
        queue.async {
            let result = Result { try prepare(data) }
            DispatchQueue.main.async {
                do {
                    let pack = try result.get()
                    var values = installed
                    guard values.count < 40 else { try? FileManager.default.removeItem(at: directory(pack.id)); throw NebulaIconPackManifest.Failure.invalid }
                    values.append(pack); UserDefaults.standard.set(try JSONEncoder().encode(values), forKey: indexKey)
                    completion(.success(pack))
                } catch { completion(.failure(error)) }
            }
        }
    }
    private static func prepare(_ data: Data) throws -> NebulaInstalledIconPack {
        guard !data.isEmpty, data.count <= 16_000_000 else { throw NebulaIconPackManifest.Failure.invalid }
        let temp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".icons")
        try data.write(to: temp, options: .atomic); defer { try? FileManager.default.removeItem(at: temp) }
        guard let entries = SSZipArchive.getEntriesForFile(atPath: temp.path), entries.count <= 2048 else { throw NebulaIconPackManifest.Failure.invalid }
        var files: Set<String> = [], total = 0
        for entry in entries {
            if entry.path.hasSuffix("/") { guard NebulaIconPackManifest.validPath(String(entry.path.dropLast())) else { throw NebulaIconPackManifest.Failure.invalid }; continue }
            guard NebulaIconPackManifest.validPath(entry.path), files.insert(entry.path).inserted, entry.uncompressedSize <= 262144 else { throw NebulaIconPackManifest.Failure.invalid }
            total += Int(entry.uncompressedSize); guard total <= 32_000_000 else { throw NebulaIconPackManifest.Failure.invalid }
        }
        guard let metadata = SSZipArchive.nebulaReadFile(atPath: temp.path, filePath: "metadata.json", maximumSize: 131072) else { throw NebulaIconPackManifest.Failure.invalid }
        let manifest = try NebulaIconPackManifest.read(metadata, files: files)
        let id = UUID().uuidString, folder = try directory(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var committed = false
        defer { if !committed { try? FileManager.default.removeItem(at: folder) } }
        let sources: [String: [String]] = ["chat": ["msg_discussion"], "person": ["msg_contacts"], "gear": ["msg_settings", "msg_photo_settings"], "phone": ["msg_calls"], "camera": ["msg_camera"], "edit": ["msg_edit"], "search": ["msg_search"], "bookmark": ["msg_saved"], "folder": ["files_folder"], "bell": ["msg_notifications"], "lock": ["msg_secret"], "video": ["msg_videocall"], "attach": ["input_attach"], "photo": ["msg_gallery"], "archive": ["msg_archive"], "copy": ["msg_copy"], "trash": ["msg_delete"], "share": ["msg_share"], "globe": ["msg_language"], "info": ["msg_info"], "list": ["msg_list"], "download": ["msg_download"], "file": ["msg_sendfile"], "more": ["ic_ab_other", "msg_other"], "mic": ["input_mic", "input_mic_pressed"], "forward": ["msg_forward"]]
        var decoded: [String: UIImage] = [:]
        for (key, path) in manifest.icons {
            guard let bytes = SSZipArchive.nebulaReadFile(atPath: temp.path, filePath: path, maximumSize: 262144) else { throw NebulaIconPackManifest.Failure.invalid }
            let image: UIImage?
            if (path as NSString).pathExtension.lowercased() == "svg" {
                let svg = try NebulaIconPackManifest.validateSVG(bytes)
                image = drawSvgImage(data: Data(svg.utf8), size: CGSize(width: 32, height: 32), backgroundColor: nil, foregroundColor: .black, scale: 3, opaque: false)
            } else {
                guard let source = CGImageSourceCreateWithData(bytes as CFData, nil), let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
                      let width = properties[kCGImagePropertyPixelWidth] as? Int, let height = properties[kCGImagePropertyPixelHeight] as? Int, width > 0, height > 0, width <= 1024, height <= 1024 else { throw NebulaIconPackManifest.Failure.invalid }
                let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: 96, kCGImageSourceCreateThumbnailWithTransform: true] as CFDictionary)
                image = thumbnail.map { UIImage(cgImage: $0) }
            }
            guard let image, image.size.width > 0, image.size.height > 0 else { throw NebulaIconPackManifest.Failure.invalid }
            decoded[key] = image
        }
        var count = 0
        for (semantic, keys) in sources {
            guard let image = keys.compactMap({ decoded[$0] }).first else { continue }
            let format = UIGraphicsImageRendererFormat(); format.scale = 3; format.opaque = false
            let png = UIGraphicsImageRenderer(size: CGSize(width: 32, height: 32), format: format).pngData { _ in
                let scale = min(32 / image.size.width, 32 / image.size.height)
                let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                image.draw(in: CGRect(x: (32 - size.width) / 2, y: (32 - size.height) / 2, width: size.width, height: size.height))
            }
            try png.write(to: folder.appendingPathComponent(semantic + ".png"), options: .atomic); count += 1
        }
        guard count > 0 else { throw NebulaIconPackManifest.Failure.invalid }
        try data.write(to: folder.appendingPathComponent("source.icons"), options: .atomic)
        try FileManager.default.moveItem(at: folder, to: directory(id)); committed = true
        return NebulaInstalledIconPack(id: id, name: manifest.packName, author: manifest.author ?? "", count: count)
    }
}
