import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Wire values match the existing Android client and badge service.
public enum NebulaBadge: String, CaseIterable {
    case supporter, dev, tester, star, heart, donor, sponsor, moderator

    public var title: String {
        switch self {
        case .supporter: return "Supporter"
        case .dev: return "Developer"
        case .tester: return "Tester"
        case .star: return "Mira"
        case .heart: return "Friend"
        case .donor: return "Donor"
        case .sponsor: return "Sponsor"
        case .moderator: return "Moderator"
        }
    }
}

/// Reads only the public assignment for an opened user profile. Never enumerates contacts.
public final class NebulaBadges {
    public static let shared = NebulaBadges(transport: NebulaBadges.fetch)

    typealias Transport = (Int64, @escaping (Result<Data, Error>) -> Void) -> Void
    private struct Entry {
        let badge: NebulaBadge?
        let expires: Date
    }
    private struct Payload: Decodable { let badge: String }
    enum FetchError: Error { case invalidResponse }

    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 15
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        return URLSession(configuration: configuration)
    }()

    private let lock = NSLock()
    private let transport: Transport
    private let now: () -> Date
    private let capacity: Int
    private var cache: [Int64: Entry] = [:]
    private var pending: [Int64: [(NebulaBadge?) -> Void]] = [:]

    init(capacity: Int = 256, now: @escaping () -> Date = Date.init, transport: @escaping Transport) {
        self.capacity = max(1, capacity)
        self.now = now
        self.transport = transport
    }

    /// All completions, including cached and invalid-ID results, run asynchronously on main.
    public func badge(for userId: Int64, completion: @escaping (NebulaBadge?) -> Void) {
        guard userId > 0 else {
            DispatchQueue.main.async { completion(nil) }
            return
        }
        lock.lock()
        if let entry = cache[userId], entry.expires > now() {
            lock.unlock()
            DispatchQueue.main.async { completion(entry.badge) }
            return
        }
        if pending[userId] != nil {
            pending[userId]?.append(completion)
            lock.unlock()
            return
        }
        pending[userId] = [completion]
        lock.unlock()

        transport(userId) { [weak self] result in
            guard let self = self else { return }
            let badge: NebulaBadge?
            let lifetime: TimeInterval
            do {
                badge = try Self.decode(result.get())
                lifetime = 24 * 60 * 60
            } catch {
                badge = nil
                lifetime = 60
            }
            self.lock.lock()
            let date = self.now()
            self.cache = self.cache.filter { $0.value.expires > date }
            if self.cache[userId] == nil, self.cache.count >= self.capacity,
               let oldest = self.cache.min(by: { $0.value.expires < $1.value.expires })?.key {
                self.cache.removeValue(forKey: oldest)
            }
            self.cache[userId] = Entry(badge: badge, expires: date.addingTimeInterval(lifetime))
            let callbacks = self.pending.removeValue(forKey: userId) ?? []
            self.lock.unlock()
            DispatchQueue.main.async { callbacks.forEach { $0(badge) } }
        }
    }

    static func decode(_ data: Data) throws -> NebulaBadge? {
        guard data.count <= 8192 else { throw FetchError.invalidResponse }
        let payload = try JSONDecoder().decode(Payload.self, from: data)
        return NebulaBadge(rawValue: payload.badge)
    }

    static func responseData(_ data: Data?, response: URLResponse?, error: Error?) -> Result<Data, Error> {
        if let error = error { return .failure(error) }
        guard let response = response as? HTTPURLResponse, response.statusCode == 200,
              let data = data, data.count <= 8192 else {
            return .failure(FetchError.invalidResponse)
        }
        return .success(data)
    }

    private static func fetch(_ userId: Int64, completion: @escaping (Result<Data, Error>) -> Void) {
        guard let url = URL(string: "https://hooks.nebulaguard.mooo.com/v1/badge/\(userId)") else {
            completion(.failure(FetchError.invalidResponse))
            return
        }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        session.dataTask(with: request) { data, response, error in
            completion(responseData(data, response: response, error: error))
        }.resume()
    }
}

/// Texts of the badge sheet, shared with Android `NebulaBadges`/`NebulaBadgeSheet`.
public extension NebulaBadge {
    /// Order of the "All NebulaGram badges" row.
    static let displayOrder: [NebulaBadge] = [.donor, .sponsor, .supporter, .dev, .moderator, .tester, .star, .heart]

    /// Badges anyone can earn by donating; their sheet offers to support the project.
    var isDonation: Bool { self == .donor || self == .sponsor || self == .supporter }

    func localizedTitle(russian: Bool) -> String {
        switch self {
        case .supporter: return russian ? "Поддержал проект" : "Supporter"
        case .dev: return russian ? "Разработчик" : "Developer"
        case .tester: return russian ? "Тестировщик" : "Tester"
        case .star: return "Mira"
        case .heart: return russian ? "Спасибо" : "Thanks"
        case .donor: return russian ? "Донатер" : "Donor"
        case .sponsor: return russian ? "Спонсор" : "Sponsor"
        case .moderator: return russian ? "Модератор" : "Moderator"
        }
    }

    func localizedDescription(russian: Bool) -> String {
        switch self {
        case .supporter: return russian ? "Значок участника, который поддерживает развитие NebulaGram." : "A community badge for someone supporting NebulaGram's development."
        case .dev: return russian ? "Значок команды разработчиков NebulaGram." : "A badge for the NebulaGram development team."
        case .tester: return russian ? "Значок участника тестирования NebulaGram." : "A badge for a NebulaGram beta tester."
        case .star: return russian ? "Значок Mira — символ сообщества NebulaGram." : "The Mira badge, a symbol of the NebulaGram community."
        case .heart: return russian ? "Особый знак благодарности от команды NebulaGram." : "A special thank-you from the NebulaGram team."
        case .donor: return russian ? "Этот человек поддержал NebulaGram донатом. Каждый донат помогает выпускать обновления." : "This person supported NebulaGram with a donation. Every donation helps ship updates."
        case .sponsor: return russian ? "Спонсор NebulaGram — регулярно и заметно поддерживает развитие проекта." : "A NebulaGram sponsor who supports the project regularly and substantially."
        case .moderator: return russian ? "Модератор сообщества NebulaGram: помогает в чатах поддержки и следит за порядком." : "A NebulaGram community moderator who helps in the support chats and keeps order."
        }
    }

    func howToGet(russian: Bool) -> String {
        switch self {
        case .donor: return russian ? "Выдаётся после любого доната проекту." : "Given after any donation to the project."
        case .sponsor: return russian ? "Выдаётся за регулярную поддержку проекта." : "Given for regular support of the project."
        case .supporter: return russian ? "Выдаётся тем, кто помогает проекту делом или донатом." : "Given to those who help the project with work or a donation."
        case .dev: return russian ? "Только у разработчиков NebulaGram." : "Only NebulaGram developers have it."
        case .moderator: return russian ? "Выдаётся модераторам чатов NebulaGram." : "Given to NebulaGram chat moderators."
        case .tester: return russian ? "Выдаётся участникам бета-тестирования." : "Given to beta testers."
        case .star: return russian ? "Особый значок сообщества, выдаётся командой." : "A special community badge given by the team."
        case .heart: return russian ? "Выдаётся командой в знак благодарности." : "Given by the team as a thank-you."
        }
    }
}
