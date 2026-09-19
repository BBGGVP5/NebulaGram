import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Wire values match the existing Android client and badge service.
public enum NebulaBadge: String, CaseIterable {
    case supporter, dev, tester, star, heart

    public var title: String {
        switch self {
        case .supporter: return "Supporter"
        case .dev: return "Developer"
        case .tester: return "Tester"
        case .star: return "Mira"
        case .heart: return "Friend"
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
