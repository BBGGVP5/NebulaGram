import Foundation
import NebulaSettingsContract
import UIKit
import TranslateUI
import TelegramPresentationData

enum NebulaResultLanguage {
    static func codes(russian: Bool) -> [String] {
        let locale = Locale(identifier: russian ? "ru" : "en")
        return NebulaLanguageOrder.codes(supported: supportedTranslationLanguages, locale: locale)
    }
    static func title(_ code: String, russian: Bool) -> String {
        Locale(identifier: russian ? "ru" : "en").localizedString(forLanguageCode: code)?.localizedCapitalized ?? code
    }
    static func show(from host: UIViewController, selected: String, russian: Bool, theme: PresentationTheme?, choose: @escaping (String) -> Void) {
        let languages = codes(russian: russian)
        NebulaChoiceController.show(from: host, title: russian ? "Язык результата" : "Result language",
            choices: languages.map { title($0, russian: russian) }, selected: languages.firstIndex(of: selected),
            russian: russian, theme: theme, sectionStart: 2,
            subtitles: languages.map { Locale(identifier: $0).localizedString(forLanguageCode: $0) ?? $0 }, searchable: true) { index in
                choose(languages[index])
            }
    }
}
