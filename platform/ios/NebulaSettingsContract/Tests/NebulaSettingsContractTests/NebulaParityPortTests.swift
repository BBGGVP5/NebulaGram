import XCTest
@testable import NebulaSettingsContract

final class NebulaParityPortTests: XCTestCase {
    private func defaults() -> (UserDefaults, String) {
        let suite = "ParityPortTests." + UUID().uuidString
        return (UserDefaults(suiteName: suite)!, suite)
    }

    func testSplitRowFollowsAndroidRules() {
        let reply = NebulaMessageMenuAction.reply.identifier, copy = NebulaMessageMenuAction.copy.identifier
        let forward = NebulaMessageMenuAction.forward.identifier, link = NebulaMessageMenuAction.copyLink.identifier
        let edit = NebulaMessageMenuSplit.editIdentifier
        let menu: [String?] = [reply, copy, nil, edit, forward, link, "nebula.message.report", nil]
        XCTAssertEqual(NebulaMessageMenuSplit.row(menu, enabled: true), [0, 1, 3, 4, 5])
        XCTAssertEqual(NebulaMessageMenuSplit.row(menu, enabled: false), [])
        // A lone icon is not worth a row.
        XCTAssertEqual(NebulaMessageMenuSplit.row([reply, nil, nil], enabled: true), [])
        // Nothing would stay in the list.
        XCTAssertEqual(NebulaMessageMenuSplit.row([reply, copy], enabled: true), [])
        // Row order is fixed, not menu order.
        XCTAssertEqual(NebulaMessageMenuSplit.row([forward, nil, copy], enabled: true), [2, 0])
    }

    func testSplitAndMotionDefaultOnAndPersist() {
        let (store, suite) = defaults(); defer { store.removePersistentDomain(forName: suite) }
        let prefs = NebulaMessageMenuPreferences(defaults: store)
        XCTAssertTrue(prefs.split); XCTAssertTrue(prefs.cherryMotion)
        prefs.setSplit(false); prefs.setCherryMotion(false)
        let restored = NebulaMessageMenuPreferences(defaults: store)
        XCTAssertFalse(restored.split); XCTAssertFalse(restored.cherryMotion)
        XCTAssertEqual(store.object(forKey: "nebula.messageMenu.cherryMotion") as? Bool, false)
    }

    func testMessageBlurDensities() {
        let (store, suite) = defaults(); defer { store.removePersistentDomain(forName: suite) }
        let prefs = NebulaMessagePreferences(defaults: store)
        XCTAssertFalse(prefs.messageBlur); XCTAssertEqual(prefs.messageBlurTint, 55)
        prefs.setMessageBlur(true); prefs.setMessageBlurTint(75)
        XCTAssertTrue(store.bool(forKey: "nebula.messages.message_blur"))
        XCTAssertEqual(store.integer(forKey: "nebula.messages.message_blur_tint"), 75)
        prefs.setMessageBlurTint(40); XCTAssertEqual(prefs.messageBlurTint, 75)
        store.set(12, forKey: NebulaMessagePreferences.messageBlurTintKey); XCTAssertEqual(prefs.messageBlurTint, 55)
        XCTAssertEqual(NebulaMessagePreferences.tintAlpha(colorAlpha: 1.0, percent: 35), 0.35, accuracy: 0.0001)
        XCTAssertEqual(NebulaMessagePreferences.tintAlpha(colorAlpha: 0.5, percent: 75), 0.375, accuracy: 0.0001)
    }

    func testEveryBadgeHasSheetTexts() {
        XCTAssertEqual(Set(NebulaBadge.displayOrder), Set(NebulaBadge.allCases))
        for badge in NebulaBadge.allCases {
            for russian in [true, false] {
                XCTAssertFalse(badge.localizedTitle(russian: russian).isEmpty)
                XCTAssertFalse(badge.localizedDescription(russian: russian).isEmpty)
                XCTAssertFalse(badge.howToGet(russian: russian).isEmpty)
            }
        }
        XCTAssertEqual(NebulaBadge.allCases.filter { $0.isDonation }, [.supporter, .donor, .sponsor])
    }
}

final class NebulaSentFromTests: XCTestCase {
    func testRoundVideoFingerprints() {
        XCTAssertEqual(NebulaSentFrom.round(fileName: nil, supportsStreaming: true), .android)
        XCTAssertEqual(NebulaSentFrom.round(fileName: "", supportsStreaming: true), .android)
        XCTAssertEqual(NebulaSentFrom.round(fileName: "video.mp4", supportsStreaming: false), .ios)
        XCTAssertEqual(NebulaSentFrom.round(fileName: "video.mp4", supportsStreaming: true), .unknown)
        XCTAssertEqual(NebulaSentFrom.round(fileName: nil, supportsStreaming: false), .unknown)
    }

    func testVoiceFingerprints() {
        let wave = Data([1, 2, 3])
        XCTAssertEqual(NebulaSentFrom.voice(mimeType: "audio/ogg", fileName: nil, waveform: wave), .officialApp)
        XCTAssertEqual(NebulaSentFrom.voice(mimeType: "AUDIO/OGG; codecs=opus", fileName: "", waveform: wave), .officialApp)
        XCTAssertEqual(NebulaSentFrom.voice(mimeType: "audio/ogg", fileName: "a.ogg", waveform: wave), .nonStandard)
        XCTAssertEqual(NebulaSentFrom.voice(mimeType: "audio/ogg", fileName: nil, waveform: nil), .nonStandard)
        XCTAssertEqual(NebulaSentFrom.voice(mimeType: "audio/mpeg", fileName: nil, waveform: wave), .nonStandard)
        XCTAssertFalse(NebulaSentFrom.unknown.label(russian: true).isEmpty)
    }
}

final class NebulaSelectionAndDustTests: XCTestCase {
    func testSelectLoadedLimitAndDustDefault() {
        XCTAssertEqual(NebulaSelectionPolicy.limit(unlimited: false), 100)
        let many = Array(0 ..< 250)
        XCTAssertEqual(NebulaSelectionPolicy.addingLoaded(many, to: [], limit: NebulaSelectionPolicy.limit(unlimited: false)).count, 100)
        XCTAssertEqual(NebulaSelectionPolicy.addingLoaded(many, to: [], limit: NebulaSelectionPolicy.limit(unlimited: true)).count, 250)
        let suite = "DustTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        let prefs = NebulaMessagePreferences(defaults: defaults)
        XCTAssertTrue(prefs.enabled("delete_dust"))
        prefs.set("delete_dust", false); XCTAssertFalse(NebulaMessagePreferences(defaults: defaults).enabled("delete_dust"))
    }
}

final class NebulaShapePreferencesTests: XCTestCase {
    func testTailsAndAvatarPresets() {
        let suite = "ShapeTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        XCTAssertTrue(NebulaShapePreferences.messageTails(defaults))
        NebulaShapePreferences.setMessageTails(false, defaults)
        XCTAssertFalse(NebulaShapePreferences.messageTails(defaults))
        XCTAssertEqual(defaults.object(forKey: "nebula.shape.message_tails") as? Bool, false)
        XCTAssertFalse(NebulaShapePreferences.chatCards(defaults))
        NebulaShapePreferences.setChatCards(true, defaults)
        XCTAssertTrue(NebulaShapePreferences.chatCards(defaults))
        XCTAssertFalse(NebulaShapePreferences.profileTabButtons(defaults))
        NebulaShapePreferences.setProfileTabButtons(true, defaults)
        XCTAssertTrue(NebulaShapePreferences.profileTabButtons(defaults))
        typealias Shape = NebulaShapePreferences.AvatarShape
        XCTAssertEqual(Shape.allCases.map { $0.roundness }, [100, 78, 46, 0])
        XCTAssertEqual(Shape.matching(roundness: 46), .rounded)
        XCTAssertNil(Shape.matching(roundness: 50))
        for shape in Shape.allCases { XCTAssertFalse(shape.title(russian: true).isEmpty) }
    }
}

final class NebulaBadgeTextStyleTests: XCTestCase {
    func testBadgeTextsHaveNoTrailingPeriod() {
        for badge in NebulaBadge.allCases {
            for russian in [true, false] {
                XCTAssertFalse(badge.localizedDescription(russian: russian).hasSuffix("."))
                XCTAssertFalse(badge.howToGet(russian: russian).hasSuffix("."))
            }
        }
    }
}

final class NebulaFontFileTests: XCTestCase {
    private func font(tags: [UInt32], lengths: [UInt32], format: UInt32 = 0x0001_0000) -> Data {
        var bytes: [UInt8] = []
        func put32(_ v: UInt32) { bytes += [UInt8(v >> 24), UInt8(v >> 16 & 0xff), UInt8(v >> 8 & 0xff), UInt8(v & 0xff)] }
        put32(format); bytes += [0, UInt8(tags.count), 0, 0, 0, 0, 0, 0]
        var offset = UInt32(12 + tags.count * 16)
        for (tag, length) in zip(tags, lengths) { put32(tag); put32(0); put32(offset); put32(length); offset += length }
        bytes += [UInt8](repeating: 0, count: Int(lengths.reduce(0, +)))
        return Data(bytes)
    }

    func testValidatesSfntDirectory() throws {
        let cmap: UInt32 = 0x636d_6170, head: UInt32 = 0x6865_6164
        XCTAssertNoThrow(try NebulaFontFile.validate(font(tags: [cmap, head], lengths: [4, 54])))
        XCTAssertNoThrow(try NebulaFontFile.validate(font(tags: [cmap, head], lengths: [4, 54], format: 0x4f54_544f)))
        XCTAssertThrowsError(try NebulaFontFile.validate(font(tags: [cmap], lengths: [4]))) { XCTAssertEqual($0 as? NebulaFontFile.Failure, .missingTables) }
        XCTAssertThrowsError(try NebulaFontFile.validate(font(tags: [cmap, head], lengths: [4, 54], format: 0x7774_6666))) { XCTAssertEqual($0 as? NebulaFontFile.Failure, .format) }
        XCTAssertThrowsError(try NebulaFontFile.validate(Data([0, 1, 0, 0]))) { XCTAssertEqual($0 as? NebulaFontFile.Failure, .size) }
        var broken = [UInt8](font(tags: [cmap, head], lengths: [4, 54])); broken.removeLast(10)
        XCTAssertThrowsError(try NebulaFontFile.validate(Data(broken))) { XCTAssertEqual($0 as? NebulaFontFile.Failure, .table) }
    }
}

final class NebulaRegistrationMonthTests: XCTestCase {
    func testParsesTelegramMonthShapes() {
        XCTAssertEqual(NebulaRegistrationMonth.parse("2022-05")?.month, 5)
        XCTAssertEqual(NebulaRegistrationMonth.parse("05.2022")?.year, 2022)
        XCTAssertNil(NebulaRegistrationMonth.parse("2022-13"))
        XCTAssertNil(NebulaRegistrationMonth.parse("1999-01"))
        XCTAssertNil(NebulaRegistrationMonth.parse(""))
        XCTAssertEqual(NebulaRegistrationMonth.display("2022-05", russian: false), "May 2022")
        XCTAssertEqual(NebulaRegistrationMonth.display("05.2022", russian: true), "май 2022")
        XCTAssertEqual(NebulaRegistrationMonth.display("spring", russian: false), "spring")
        XCTAssertNil(NebulaRegistrationMonth.display(nil, russian: true))
    }

    func testRegistrationRowIsOnByDefault() {
        let suite = "RegistrationTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        let prefs = NebulaMessagePreferences(defaults: defaults)
        XCTAssertTrue(prefs.enabled("profile_registration"))
        prefs.set("profile_registration", false); XCTAssertFalse(prefs.enabled("profile_registration"))
    }
}

final class NebulaHomeLinkTests: XCTestCase {
    func testLinkShortcutDefaultsOffAndYieldsToAi() {
        let suite = "HomeLinkTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        XCTAssertFalse(NebulaHomeLink.enabled(defaults))
        NebulaHomeLink.setEnabled(true, defaults)
        XCTAssertTrue(NebulaHomeLink.enabled(defaults))
        XCTAssertTrue(NebulaHomeLink.showsLink(aiShortcut: false, link: true))
        XCTAssertFalse(NebulaHomeLink.showsLink(aiShortcut: true, link: true))
        XCTAssertFalse(NebulaHomeLink.showsLink(aiShortcut: false, link: false))
    }
}

final class NebulaAiOutputTests: XCTestCase {
    func testComposeKeepsAnswerRange() {
        let source = NSAttributedString(string: "Привет"), result = NSAttributedString(string: "Hello")
        let only = NebulaAiOutput.compose(source: source, result: result, responseOnly: true)
        XCTAssertEqual(only.text.string, "Hello"); XCTAssertEqual(only.answer, NSRange(location: 0, length: 5))
        let both = NebulaAiOutput.compose(source: source, result: result, responseOnly: false)
        XCTAssertEqual(both.text.string, "Привет\n\nHello"); XCTAssertEqual(both.answer, NSRange(location: 8, length: 5))
        let empty = NebulaAiOutput.compose(source: NSAttributedString(), result: result, responseOnly: false)
        XCTAssertEqual(empty.text.string, "Hello")
        let suite = "AiOutputTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        XCTAssertTrue(NebulaAiOutput.responseOnly(defaults)); XCTAssertFalse(NebulaAiOutput.quoteAnswer(defaults))
        NebulaAiOutput.setResponseOnly(false, defaults); NebulaAiOutput.setQuoteAnswer(true, defaults)
        XCTAssertFalse(NebulaAiOutput.responseOnly(defaults)); XCTAssertTrue(NebulaAiOutput.quoteAnswer(defaults))
    }
}
