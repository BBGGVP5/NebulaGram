import Foundation

/// Time-zone conversion in the message field (Android `NebulaInlineTime`):
/// "15:00 msk in tokyo =", "3pm est to utc+3 =", "now in london =", "токио =".
public enum NebulaInlineTime {
    private static let zones: [String: String] = {
        var zones: [String: String] = [:]
        func zone(_ id: String, _ names: String...) { for name in names { zones[name] = id } }
        zone("UTC", "utc", "gmt", "zulu")
        zone("Europe/Moscow", "msk", "мск", "moscow", "москва", "москве", "москвы", "по москве", "питер", "петербург", "петербурге", "saint petersburg", "st petersburg")
        zone("Europe/Kaliningrad", "kaliningrad", "калининград", "калининграде")
        zone("Europe/Samara", "samara", "самара", "самаре")
        zone("Asia/Yekaterinburg", "yekt", "yekaterinburg", "ekaterinburg", "екатеринбург", "екатеринбурге")
        zone("Asia/Omsk", "omsk", "омск", "омске")
        zone("Asia/Novosibirsk", "novosibirsk", "новосибирск", "новосибирске")
        zone("Asia/Krasnoyarsk", "krasnoyarsk", "красноярск", "красноярске")
        zone("Asia/Irkutsk", "irkutsk", "иркутск", "иркутске")
        zone("Asia/Yakutsk", "yakutsk", "якутск", "якутске")
        zone("Asia/Vladivostok", "vladivostok", "владивосток", "владивостоке")
        zone("Asia/Kamchatka", "kamchatka", "камчатка", "камчатке", "петропавловск-камчатский")
        zone("Europe/Kyiv", "kyiv", "kiev", "киев", "киеве", "київ", "kharkiv", "харьков", "odesa", "odessa", "одесса", "одессе")
        zone("Europe/Minsk", "minsk", "минск", "минске")
        zone("Asia/Almaty", "almaty", "алматы", "astana", "астана", "астане")
        zone("Asia/Tashkent", "tashkent", "ташкент", "ташкенте")
        zone("Asia/Bishkek", "bishkek", "бишкек", "бишкеке")
        zone("Asia/Tbilisi", "tbilisi", "тбилиси")
        zone("Asia/Yerevan", "yerevan", "ереван", "ереване")
        zone("Asia/Baku", "baku", "баку")
        zone("Europe/Istanbul", "istanbul", "стамбул", "стамбуле", "ankara", "анкара", "анкаре", "trt")
        zone("Asia/Dubai", "dubai", "дубай", "дубае", "abu dhabi", "абу-даби", "gst")
        zone("Europe/London", "london", "лондон", "лондоне", "bst", "uk")
        zone("Europe/Paris", "paris", "париж", "париже", "cet", "cest")
        zone("Europe/Berlin", "berlin", "берлин", "берлине", "munich", "мюнхен", "мюнхене")
        zone("Europe/Rome", "rome", "рим", "риме", "milan", "милан", "милане")
        zone("Europe/Madrid", "madrid", "мадрид", "мадриде", "barcelona", "барселона", "барселоне")
        zone("Europe/Amsterdam", "amsterdam", "амстердам", "амстердаме")
        zone("Europe/Warsaw", "warsaw", "варшава", "варшаве")
        zone("Europe/Prague", "prague", "прага", "праге")
        zone("Europe/Vienna", "vienna", "вена", "вене")
        zone("Europe/Helsinki", "helsinki", "хельсинки", "eet", "eest")
        zone("Europe/Riga", "riga", "рига", "риге")
        zone("Europe/Vilnius", "vilnius", "вильнюс", "вильнюсе")
        zone("Europe/Tallinn", "tallinn", "таллин", "таллине", "таллинн")
        zone("Europe/Chisinau", "chisinau", "кишинёв", "кишинев", "кишинёве", "кишиневе")
        zone("Europe/Athens", "athens", "афины", "афинах")
        zone("Europe/Lisbon", "lisbon", "лиссабон", "лиссабоне")
        zone("Asia/Jerusalem", "jerusalem", "иерусалим", "иерусалиме", "tel aviv", "тель-авив", "тель-авиве", "israel", "израиль", "израиле")
        zone("Asia/Kolkata", "ist", "india", "delhi", "new delhi", "дели", "нью-дели", "mumbai", "мумбаи", "kolkata")
        zone("Asia/Bangkok", "bangkok", "бангкок", "бангкоке", "ict")
        zone("Asia/Ho_Chi_Minh", "hanoi", "ханой", "ханое", "ho chi minh", "хошимин", "хошимине")
        zone("Asia/Jakarta", "jakarta", "джакарта", "джакарте", "bali", "бали")
        zone("Asia/Singapore", "singapore", "сингапур", "сингапуре", "sgt")
        zone("Asia/Hong_Kong", "hong kong", "гонконг", "гонконге", "hkt")
        zone("Asia/Shanghai", "beijing", "пекин", "пекине", "shanghai", "шанхай", "шанхае", "china", "китай", "китае")
        zone("Asia/Tokyo", "jst", "tokyo", "токио", "japan", "япония", "японии", "osaka", "осака")
        zone("Asia/Seoul", "kst", "seoul", "сеул", "сеуле")
        zone("Australia/Sydney", "aest", "aedt", "sydney", "сидней", "сиднее", "melbourne", "мельбурн", "мельбурне")
        zone("Pacific/Auckland", "nzst", "nzdt", "auckland", "окленд", "окленде")
        zone("America/New_York", "est", "edt", "et", "new york", "nyc", "нью-йорк", "нью-йорке", "washington", "вашингтон", "вашингтоне", "miami", "майами", "toronto", "торонто", "boston", "бостон", "бостоне")
        zone("America/Chicago", "cst", "cdt", "ct", "chicago", "чикаго", "dallas", "даллас", "далласе", "houston", "хьюстон", "хьюстоне")
        zone("America/Denver", "mst", "mdt", "denver", "денвер", "денвере")
        zone("America/Los_Angeles", "pst", "pdt", "pt", "los angeles", "la", "лос-анджелес", "лос-анджелесе", "san francisco", "сан-франциско", "seattle", "сиэтл", "сиэтле", "vancouver", "ванкувер", "ванкувере")
        zone("America/Sao_Paulo", "sao paulo", "são paulo", "сан-паулу", "rio", "рио", "brazil", "бразилия", "бразилии")
        zone("America/Mexico_City", "mexico city", "мехико")
        zone("America/Argentina/Buenos_Aires", "buenos aires", "буэнос-айрес", "буэнос-айресе")
        zone("Africa/Cairo", "cairo", "каир", "каире", "egypt", "египет", "египте")
        zone("Africa/Johannesburg", "johannesburg", "йоханнесбург", "йоханнесбурге")
        return zones
    }()

    private static let timePattern = try! NSRegularExpression(pattern: #"^(?:(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)|(\d{1,2}):(\d{2})|now|сейчас|time|время)(?:\s+|$)"#, options: [.caseInsensitive])
    private static let offsetPattern = try! NSRegularExpression(pattern: #"^(?:utc|gmt)?\s*([+-])\s*(\d{1,2})(?::?(\d{2}))?$"#)

    static func find(_ token: String) -> TimeZone? {
        let text = token.trimmingCharacters(in: .whitespaces).lowercased()
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        guard !text.isEmpty else { return nil }
        if let id = zones[text] { return TimeZone(identifier: id) }
        let ns = text as NSString
        guard let match = offsetPattern.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let hours = Int(ns.substring(with: match.range(at: 2))) ?? 99
        let minutes = match.range(at: 3).location == NSNotFound ? 0 : Int(ns.substring(with: match.range(at: 3))) ?? 99
        guard hours <= 14, minutes <= 59 else { return nil }
        let sign = ns.substring(with: match.range(at: 1)) == "-" ? -1 : 1
        return TimeZone(secondsFromGMT: sign * (hours * 3600 + minutes * 60))
    }

    /// The converted time for an expression without its "=", or nil.
    public static func convert(_ body: String, now: Date = Date(), local: TimeZone = .current) -> String? {
        for split in NebulaInlineUnits.splits(body) {
            guard let target = find(split.right) else { continue }
            if let result = convert(split.left, to: target, now: now, local: local, explicit: true) { return result }
        }
        // "15:00 msk" converts to the phone's zone; a bare city shows its current time.
        if let result = convert(body, to: local, now: now, local: local, explicit: false) { return result }
        guard let city = find(body) else { return nil }
        return show(now, in: city, from: local)
    }

    private static func convert(_ source: String, to target: TimeZone, now: Date, local: TimeZone, explicit: Bool) -> String? {
        let trimmed = source.trimmingCharacters(in: .whitespaces)
        let ns = trimmed as NSString
        var hour = -1, minute = 0, rest = trimmed
        let match = timePattern.firstMatch(in: trimmed, range: NSRange(location: 0, length: ns.length))
        func group(_ index: Int) -> String? {
            guard let match, match.range(at: index).location != NSNotFound else { return nil }
            return ns.substring(with: match.range(at: index))
        }
        if let match {
            if let h = group(1) {
                hour = Int(h) ?? 99; minute = Int(group(2) ?? "0") ?? 99
                guard (1...12).contains(hour) else { return nil }
                hour = hour % 12 + ((group(3) ?? "").lowercased().hasPrefix("p") ? 12 : 0)
            } else if let h = group(4) {
                hour = Int(h) ?? 99; minute = Int(group(5) ?? "") ?? 99
            }
            guard hour <= 23, minute <= 59 else { return nil }
            rest = ns.substring(from: match.range.upperBound).trimmingCharacters(in: .whitespaces)
                .replacingOccurrences(of: #"^(?:in|at|в|во)\s+"#, with: "", options: [.regularExpression, .caseInsensitive])
        } else if !explicit {
            return nil
        }
        // "15:00 in tokyo" starts from the phone's zone; "15:00 =" alone is not a question.
        if rest.isEmpty && (!explicit || match == nil) { return nil }
        guard let zone = rest.isEmpty ? local : find(rest) else { return nil }
        if hour < 0 { return show(now, in: target, from: zone) }
        if !explicit && zone.secondsFromGMT(for: now) == target.secondsFromGMT(for: now) { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        guard let instant = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: now) else { return nil }
        return show(instant, in: target, from: zone)
    }

    /// "21:00", or "03:00 (+1)" when the day differs from the source zone's.
    private static func show(_ instant: Date, in target: TimeZone, from source: TimeZone) -> String {
        var there = Calendar(identifier: .gregorian), here = Calendar(identifier: .gregorian)
        there.timeZone = target; here.timeZone = source
        let a = there.dateComponents([.year, .month, .day, .hour, .minute], from: instant)
        let b = here.dateComponents([.year, .month, .day], from: instant)
        let time = String(format: "%02d:%02d", a.hour ?? 0, a.minute ?? 0)
        // Calendar days compared in one fixed zone, so only the dates matter.
        var plain = Calendar(identifier: .gregorian)
        plain.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        guard let dayThere = plain.date(from: DateComponents(year: a.year, month: a.month, day: a.day)),
              let dayHere = plain.date(from: DateComponents(year: b.year, month: b.month, day: b.day)),
              let shift = plain.dateComponents([.day], from: dayHere, to: dayThere).day else { return time }
        return shift == 0 ? time : time + " (" + (shift > 0 ? "+" : "−") + String(abs(shift)) + ")"
    }
}
