import Foundation
import UIKit
import UniformTypeIdentifiers
import NebulaSettingsContract

// This adapter depends only on UIKit and the Foundation contract, so CI can
// typecheck it against the real iOS SDK without mocking Telegram modules.
final class NebulaSettingsFileTransfer: NSObject, UIDocumentPickerDelegate {
    weak var host: UIViewController?
    private let store: NebulaSettingsStore
    private let isRussian: () -> Bool
    private let didImport: () -> Void
    private var busy = false
    private var exportDirectory: URL?

    init(store: NebulaSettingsStore, isRussian: @escaping () -> Bool, didImport: @escaping () -> Void) {
        self.store = store
        self.isRussian = isRussian
        self.didImport = didImport
    }

    private func text(_ ru: String, _ en: String) -> String { return isRussian() ? ru : en }

    func importFile() {
        guard !busy, let host, host.presentedViewController == nil else { return }
        busy = true
        let picker: UIDocumentPickerViewController
        if #available(iOS 14.0, *) {
            picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json], asCopy: false)
        } else {
            picker = UIDocumentPickerViewController(documentTypes: ["public.json"], in: .open)
        }
        picker.allowsMultipleSelection = false
        picker.delegate = self
        picker.modalPresentationStyle = .formSheet
        host.present(picker, animated: true)
    }

    func exportFile() {
        guard !busy, let host, host.presentedViewController == nil else { return }
        busy = true
        do {
            let data = try store.exportData()
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("NebulaSettings-\(UUID().uuidString)", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
            exportDirectory = directory
            let file = directory.appendingPathComponent("NebulaGram-settings.json")
            try data.write(to: file, options: .atomic)
            let picker: UIDocumentPickerViewController
            if #available(iOS 14.0, *) {
                picker = UIDocumentPickerViewController(forExporting: [file], asCopy: true)
            } else {
                picker = UIDocumentPickerViewController(url: file, in: .exportToService)
            }
            picker.delegate = self
            picker.modalPresentationStyle = .formSheet
            host.present(picker, animated: true)
        } catch {
            finish()
            showError()
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish()
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if exportDirectory != nil {
            // The system has finished copying the file; never delete its destination.
            finish()
            return
        }
        guard let url = urls.first, urls.count == 1 else { finish(); return }
        controller.dismiss(animated: true) { [weak self] in
            guard let self else { return }
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                let result = Result { try readNebulaSettingsFile(url) }
                DispatchQueue.main.async { [weak self] in
                    guard let self else { return }
                    do {
                        let data = try result.get()
                        let preview = try self.store.previewImport(data)
                        self.confirm(data, preview: preview)
                    } catch {
                        self.finish()
                        self.showError()
                    }
                }
            }
        }
    }

    private func confirm(_ data: Data, preview: SettingsTransferPreview) {
        guard let host, host.viewIfLoaded?.window != nil, host.presentedViewController == nil else { finish(); return }
        let message = text(
            "Параметров в файле: \(preview.document.settings.count). Подключено на iOS: \(preview.activeKeys.count). Будут сохранены для будущего переноса: \(preview.pendingKeys.count).\n\nТекущие настройки оформления будут заменены. Отсутствующие в файле параметры вернутся к значениям по умолчанию. Чаты, аккаунты и ключи доступа не изменятся.",
            "Settings in file: \(preview.document.settings.count). Connected on iOS: \(preview.activeKeys.count). Retained for future ports: \(preview.pendingKeys.count).\n\nCurrent appearance settings will be replaced. Settings absent from this file will return to defaults. Chats, accounts and access keys are not affected.")
        let alert = UIAlertController(title: text("Заменить настройки?", "Replace settings?"), message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel) { [weak self] _ in self?.finish() })
        alert.addAction(UIAlertAction(title: text("Заменить", "Replace"), style: .destructive) { [weak self, weak alert] _ in
            guard let self else { return }
            do {
                try self.store.importData(data)
                self.didImport()
                self.finish()
            } catch {
                self.finish()
                // Wait for the confirmation to dismiss before presenting another alert.
                alert?.dismiss(animated: true) { [weak self] in self?.showError() }
            }
        })
        host.present(alert, animated: true)
    }

    private func showError() {
        guard let host, host.viewIfLoaded?.window != nil, host.presentedViewController == nil else { return }
        let alert = UIAlertController(title: "NebulaGram", message: text(
            "Не удалось прочитать или сохранить настройки. Нужен JSON-файл NebulaGram версии 1 размером до 1 МБ, с допустимыми параметрами. Проверьте доступ к файлу. Настройки не сброшены.",
            "Could not read or save settings. Use a valid NebulaGram version 1 JSON file up to 1 MiB and check file access. Preferences have not been reset."), preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("ОК", "OK"), style: .default))
        host.present(alert, animated: true)
    }

    private func finish() {
        busy = false
        if let directory = exportDirectory {
            try? FileManager.default.removeItem(at: directory)
            exportDirectory = nil
        }
    }

    deinit {
        if let directory = exportDirectory { try? FileManager.default.removeItem(at: directory) }
    }
}

private func readNebulaSettingsFile(_ url: URL) throws -> Data {
    let scoped = url.startAccessingSecurityScopedResource()
    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
    let coordinator = NSFileCoordinator()
    var coordinationError: NSError?
    var result: Result<Data, Error>?
    coordinator.coordinate(readingItemAt: url, options: [], error: &coordinationError) { coordinatedURL in
        result = Result { try SettingsTransferFile.read(coordinatedURL) }
    }
    if let coordinationError { throw coordinationError }
    guard let result else { throw ContractError.invalidDocument }
    return try result.get()
}
