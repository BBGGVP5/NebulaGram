import Foundation

public final class NebulaAudioPreferences {
    public static let shared = NebulaAudioPreferences()
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public func migrate(services: NebulaAiServices) {
        guard !defaults.bool(forKey: "nebula.audio.initialized") else { return }
        defaults.set("device", forKey: "nebula.audio.speechService")
        if let active = services.active, Self.supports(active.provider) { defaults.set(active.id, forKey: "nebula.audio.transcriptionService") }
        defaults.set(true, forKey: "nebula.audio.initialized")
    }
    public static func supports(_ provider: NebulaAiProvider) -> Bool { provider == .openAI || provider == .gemini }
    public var transcriptionEnabled: Bool {
        get { defaults.object(forKey: "nebula.audio.transcriptionEnabled") as? Bool ?? defaults.bool(forKey: "nebula.ai.enabled") }
        set { defaults.set(newValue, forKey: "nebula.audio.transcriptionEnabled") }
    }
    public var transcriptionService: String { get { defaults.string(forKey: "nebula.audio.transcriptionService") ?? "" } set { defaults.set(newValue, forKey: "nebula.audio.transcriptionService") } }
    public var localTranscription: Bool { transcriptionService == NebulaLocalAudioPolicy.service }
    public var transcriptionLocale: String {
        get { NebulaLocalAudioPolicy.locale(defaults.string(forKey: "nebula.audio.transcriptionLocale") ?? "") ?? (Locale.current.languageCode == "ru" ? "ru-RU" : "en-US") }
        set { if let value = NebulaLocalAudioPolicy.locale(newValue) { defaults.set(value, forKey: "nebula.audio.transcriptionLocale") } }
    }
    public var speechService: String { get { defaults.string(forKey: "nebula.audio.speechService") ?? "device" } set { defaults.set(newValue, forKey: "nebula.audio.speechService") } }
    public var style: String { get { let value = defaults.string(forKey: "nebula.audio.style") ?? "neutral"; return ["neutral", "warm", "calm", "lively"].contains(value) ? value : "neutral" } set { if ["neutral", "warm", "calm", "lively"].contains(newValue) { defaults.set(newValue, forKey: "nebula.audio.style") } } }
    public var speed: Double { get { let value = defaults.object(forKey: "nebula.audio.speed") as? Double ?? 1; return value.isFinite && (0.8...1.2).contains(value) ? value : 1 } set { if newValue.isFinite && (0.8...1.2).contains(newValue) { defaults.set(newValue, forKey: "nebula.audio.speed") } } }
    public var deviceVoice: String { get { defaults.string(forKey: "nebula.audio.deviceVoice") ?? "" } set { defaults.set(String(newValue.prefix(256)), forKey: "nebula.audio.deviceVoice") } }
    public func model(speech: Bool, connection: NebulaAiConnection) -> String {
        defaults.string(forKey: "nebula.audio.\(speech ? "speech" : "transcription")Model.\(connection.provider.rawValue)") ?? (speech ? connection.provider == .openAI ? "gpt-4o-mini-tts" : "gemini-3.8-flash-tts" : connection.provider == .openAI ? "gpt-4o-mini-transcribe" : connection.model)
    }
    public func setModel(_ value: String, speech: Bool, provider: NebulaAiProvider) throws { defaults.set(try NebulaAudioProtocol.model(value), forKey: "nebula.audio.\(speech ? "speech" : "transcription")Model.\(provider.rawValue)") }
    public func voice(provider: NebulaAiProvider) -> String {
        let choices = provider == .openAI ? NebulaAudioProtocol.openAIVoices : NebulaAudioProtocol.geminiVoices
        let value = defaults.string(forKey: "nebula.audio.voice.\(provider.rawValue)") ?? choices[0]; return choices.contains(value) ? value : choices[0]
    }
    public func setVoice(_ value: String, provider: NebulaAiProvider) {
        guard (provider == .openAI ? NebulaAudioProtocol.openAIVoices : NebulaAudioProtocol.geminiVoices).contains(value) else { return }; defaults.set(value, forKey: "nebula.audio.voice.\(provider.rawValue)")
    }
    public func connection(speech: Bool, services: NebulaAiServices) -> NebulaAiConnection? {
        services.connections.first { $0.id == (speech ? speechService : transcriptionService) && Self.supports($0.provider) }
    }
}
