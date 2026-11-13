import Foundation

/// The day-long cache of fiat rates per US dollar behind inline currency
/// conversion (Android `NebulaInlineSettings.rates`). Only public exchange
/// rates are fetched; nothing from the draft leaves the device.
public final class NebulaInlineRates: @unchecked Sendable {
    public static let shared = NebulaInlineRates()
    /// Posted on the main queue after fresh rates arrive.
    public static let didUpdate = Notification.Name("NebulaInlineRatesDidUpdate")
    static let url = URL(string: "https://open.er-api.com/v6/latest/USD")!
    private static let day: TimeInterval = 24 * 60 * 60, retry: TimeInterval = 10 * 60
    private let lock = NSLock()
    private let defaults: UserDefaults
    private var rates: [String: Double]?
    private var time: TimeInterval = 0, attempt: TimeInterval = 0
    private var loading = false

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: "nebula_rates_usd"), let parsed = Self.parse(data) {
            rates = parsed
            time = defaults.double(forKey: "nebula_rates_time")
        }
    }

    /// Cached rates even when stale; a refresh starts in the background after a day.
    public func current() -> [String: Double]? {
        lock.lock()
        let value = rates, stale = Date().timeIntervalSince1970 - time > Self.day
        lock.unlock()
        if stale { load(nil) }
        return value
    }

    /// Fetches fresh rates at most every ten minutes; after a success `didUpdate` is posted and `done` runs on the main queue.
    public func load(_ done: (@Sendable () -> Void)?) {
        let now = Date().timeIntervalSince1970
        lock.lock()
        guard !loading, now - attempt >= Self.retry else { lock.unlock(); return }
        loading = true; attempt = now
        lock.unlock()
        var request = URLRequest(url: Self.url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        request.httpShouldHandleCookies = false
        URLSession.shared.dataTask(with: request) { [self] data, response, _ in
            var parsed: [String: Double]?
            if let data, data.count <= 512 * 1024, (response as? HTTPURLResponse)?.statusCode == 200 { parsed = Self.parse(data) }
            lock.lock()
            loading = false
            if let parsed {
                rates = parsed; time = Date().timeIntervalSince1970
                defaults.set(data, forKey: "nebula_rates_usd"); defaults.set(time, forKey: "nebula_rates_time")
            }
            lock.unlock()
            guard parsed != nil else { return }
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: Self.didUpdate, object: nil)
                done?()
            }
        }.resume()
    }

    static func parse(_ data: Data) -> [String: Double]? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              json["result"] as? String == "success", let values = json["rates"] as? [String: Any] else { return nil }
        var out: [String: Double] = [:]
        for (key, value) in values {
            guard key.count == 3, key.allSatisfy({ $0.isASCII && $0.isUppercase }), let number = (value as? NSNumber)?.doubleValue, number > 0 else { continue }
            out[key] = number
        }
        return out["USD"] == nil ? nil : out
    }
}
