import Foundation

/// Unit conversion in the message field (Android `NebulaInlineUnits`): a line
/// ending with "=" such as "5 km =" or "100 °F to °C =" shows the converted
/// value next to the caret. Length, weight, temperature, speed and time.
/// Without a target the value moves to the other measurement system.
public enum NebulaInlineUnits {
    enum Kind { case length, weight, temperature, speed, time }
    enum System { case neutral, metric, imperial }

    struct Unit {
        let id: String, kind: Kind, system: System, factor: Double, en: String, ru: String
        func label(_ russian: Bool) -> String { russian ? ru : en }
    }

    private static let table: (byName: [String: Unit], byId: [String: Unit]) = {
        var byName: [String: Unit] = [:], byId: [String: Unit] = [:]
        func unit(_ id: String, _ kind: Kind, _ system: System, _ factor: Double, _ en: String, _ ru: String, _ names: String...) {
            let value = Unit(id: id, kind: kind, system: system, factor: factor, en: en, ru: ru)
            byId[id] = value
            for name in [id, en.lowercased(), ru.lowercased()] + names { byName[name] = value }
        }
        unit("mm", .length, .metric, 0.001, "mm", "мм", "millimeter", "millimeters", "миллиметр", "миллиметра", "миллиметров")
        unit("cm", .length, .metric, 0.01, "cm", "см", "centimeter", "centimeters", "сантиметр", "сантиметра", "сантиметров")
        unit("m", .length, .metric, 1, "m", "м", "meter", "meters", "metre", "metres", "метр", "метра", "метров")
        unit("km", .length, .metric, 1000, "km", "км", "kilometer", "kilometers", "километр", "километра", "километров")
        unit("in", .length, .imperial, 0.0254, "in", "дюйм.", "inch", "inches", "дюйм", "дюйма", "дюймов")
        unit("ft", .length, .imperial, 0.3048, "ft", "фут.", "foot", "feet", "фут", "фута", "футов")
        unit("yd", .length, .imperial, 0.9144, "yd", "ярд.", "yard", "yards", "ярд", "ярда", "ярдов")
        unit("mi", .length, .imperial, 1609.344, "mi", "миль", "mile", "miles", "миля", "мили")
        unit("mg", .weight, .metric, 1e-6, "mg", "мг", "milligram", "milligrams")
        unit("g", .weight, .metric, 0.001, "g", "г", "gram", "grams", "грамм", "грамма", "граммов", "гр")
        unit("kg", .weight, .metric, 1, "kg", "кг", "kilogram", "kilograms", "kilo", "kilos", "килограмм", "килограмма", "килограммов")
        unit("t", .weight, .metric, 1000, "t", "т", "tonne", "tonnes", "тонна", "тонны", "тонн")
        unit("oz", .weight, .imperial, 0.028349523125, "oz", "унц.", "ounce", "ounces", "унция", "унции", "унций")
        unit("lb", .weight, .imperial, 0.45359237, "lb", "фнт", "lbs", "pound", "pounds", "фунт", "фунта", "фунтов")
        unit("c", .temperature, .metric, 0, "°C", "°C", "℃", "celsius", "цельсия")
        unit("f", .temperature, .imperial, 0, "°F", "°F", "℉", "fahrenheit", "фаренгейта")
        unit("kelvin", .temperature, .neutral, 0, "K", "K", "kelvins", "кельвин", "кельвинов")
        unit("m/s", .speed, .metric, 1, "m/s", "м/с", "mps")
        unit("km/h", .speed, .metric, 1 / 3.6, "km/h", "км/ч", "kmh", "kph")
        unit("mph", .speed, .imperial, 0.44704, "mph", "миль/ч", "mi/h")
        unit("kn", .speed, .neutral, 1852 / 3600.0, "kn", "уз", "knot", "knots", "узлов")
        unit("s", .time, .neutral, 1, "s", "с", "sec", "second", "seconds", "сек", "секунд")
        unit("min", .time, .neutral, 60, "min", "мин", "mins", "minute", "minutes", "минут", "минуты")
        unit("h", .time, .neutral, 3600, "h", "ч", "hr", "hrs", "hour", "hours", "час", "часа", "часов")
        unit("d", .time, .neutral, 86400, "d", "д", "day", "days", "дн", "день", "дня", "дней")
        unit("yr", .time, .neutral, 31556952, "yr", "г.", "year", "years", "год", "года", "лет")
        // "5k" is a thousand; Kelvin is the capital K or the word.
        byName["k"] = nil
        return (byName, byId)
    }()

    static func find(_ token: String) -> Unit? {
        let text = token.trimmingCharacters(in: .whitespaces)
        if text == "K" { return table.byId["kelvin"] }
        if text == "M" { return nil }
        return table.byName[text.lowercased()]
    }

    /// The converted value for the draft's last line, or nil.
    public static func result(_ draft: String, russian: Bool, imperial: Bool) -> String? {
        guard let body = question(draft) else { return nil }
        return explicit(body, russian: russian) ?? implicit(body, russian: russian, imperial: imperial)
    }

    /// The draft's last line without its final "=", when it asks for a conversion.
    public static func question(_ draft: String) -> String? {
        let lastLine = draft.components(separatedBy: "\n").last ?? ""
        let line = lastLine.trimmingCharacters(in: .whitespaces)
        guard line.count <= 120, line.hasSuffix("=") else { return nil }
        let body = String(line.dropLast()).trimmingCharacters(in: .whitespaces)
        return body.isEmpty ? nil : body
    }

    /// "5 km to mi": a conversion with a named target unit.
    public static func explicit(_ body: String, russian: Bool) -> String? {
        for split in splits(body) {
            if let target = find(split.right), let source = amount(split.left), source.unit.kind == target.kind, source.unit.id != target.id {
                return show(source.value, source.unit, target, russian)
            }
        }
        return nil
    }

    /// "5 km": the value in the other measurement system, or a duration broken down.
    public static func implicit(_ body: String, russian: Bool, imperial: Bool) -> String? {
        guard let source = amount(body) else { return nil }
        let from = source.unit, value = source.value
        if from.kind == .time { return duration(value * from.factor, from: from, russian: russian) }
        var system: System = from.system == .neutral ? (imperial ? .imperial : .metric) : (from.system == .metric ? .imperial : .metric)
        if from.kind == .speed && from.system == .neutral { system = imperial ? .imperial : .metric }
        guard let target = best(from.kind, system, value * from.factor) else { return nil }
        return show(value, from, target, russian)
    }

    static func splits(_ body: String) -> [(left: String, right: String)] {
        var result: [(left: String, right: String)] = []
        for connector in [" to ", " in ", " into ", " в ", " во ", " на ", "->", "→", "=>"] {
            var search = body.startIndex ..< body.endIndex
            while let range = body.range(of: connector, options: .caseInsensitive, range: search) {
                let left = body[..<range.lowerBound].trimmingCharacters(in: .whitespaces)
                let right = body[range.upperBound...].trimmingCharacters(in: .whitespaces)
                if !left.isEmpty && !right.isEmpty { result.append((left: left, right: right)) }
                search = range.upperBound ..< body.endIndex
            }
        }
        return result
    }

    private static func amount(_ text: String) -> (value: Double, unit: Unit)? {
        let characters = Array(text.trimmingCharacters(in: .whitespaces))
        guard characters.count >= 2 else { return nil }
        for index in max(1, characters.count - 32) ..< characters.count {
            let character = characters[index]
            if character.isNumber || character.isWhitespace || character == "." || character == "," { continue }
            if character.isLetter && characters[index - 1].isLetter { continue }
            guard let unit = find(String(characters[index...])) else { continue }
            guard let value = plain(String(characters[..<index])) else { continue }
            return (value: value, unit: unit)
        }
        return nil
    }

    private static func plain(_ text: String) -> Double? {
        let number = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        guard !number.isEmpty, number.allSatisfy({ $0.isNumber || $0 == "." || $0 == "-" }) else { return nil }
        return Double(number)
    }

    /// "1 500", "2,5", "5k" or "5к": an amount for currencies.
    static func number(_ text: String) -> Double? {
        var source = text.filter { !$0.isWhitespace }
        var scale = 1.0
        if let last = source.last, "kKкК".contains(last) { scale = 1000; source.removeLast() }
        guard source.first?.isNumber == true || source.first == "-", let value = plain(source) else { return nil }
        return value * scale
    }

    private static func best(_ kind: Kind, _ system: System, _ base: Double) -> Unit? {
        let a = abs(base)
        let id: String
        switch kind {
        case .length: id = system == .metric ? (a >= 1000 ? "km" : a >= 1 ? "m" : a >= 0.01 ? "cm" : "mm") : (a >= 1609.344 ? "mi" : a >= 0.3048 ? "ft" : "in")
        case .weight: id = system == .metric ? (a >= 1000 ? "t" : a >= 1 ? "kg" : a >= 0.001 ? "g" : "mg") : (a >= 0.45359237 ? "lb" : "oz")
        case .temperature: id = system == .metric ? "c" : "f"
        case .speed: id = system == .metric ? "km/h" : "mph"
        case .time: return nil
        }
        return table.byId[id]
    }

    private static func show(_ value: Double, _ from: Unit, _ to: Unit, _ russian: Bool) -> String? {
        let result: Double
        if from.kind == .temperature {
            let kelvin = from.id == "c" ? value + 273.15 : from.id == "f" ? (value - 32) * 5 / 9 + 273.15 : value
            guard kelvin >= 0 else { return nil }
            result = to.id == "c" ? kelvin - 273.15 : to.id == "f" ? (kelvin - 273.15) * 9 / 5 + 32 : kelvin
        } else {
            result = value * from.factor / to.factor
        }
        guard let number = approximate(result) else { return nil }
        return number + (to.kind == .temperature && to.id != "kelvin" ? "" : " ") + to.label(russian)
    }

    /// About six significant digits: what a conversion is accurate to.
    static func approximate(_ value: Double) -> String? {
        guard value.isFinite else { return nil }
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.usesGroupingSeparator = false
        if abs(value) >= 100_000 {
            formatter.maximumFractionDigits = 0
        } else {
            formatter.usesSignificantDigits = true
            formatter.maximumSignificantDigits = 6
        }
        return formatter.string(from: NSNumber(value: value))
    }

    /// "90 min" → "1 h 30 min": the two largest parts, rounded to the smaller one.
    private static func duration(_ seconds: Double, from: Unit, russian: Bool) -> String? {
        let ids = ["yr", "d", "h", "min", "s"]
        guard seconds.isFinite, seconds <= 1e12 else { return nil }
        let total = Int64(seconds.rounded())
        guard total >= 60 else { return nil }
        for (index, id) in ids.enumerated() {
            guard let big = table.byId[id] else { continue }
            var count = total / Int64(big.factor)
            if count == 0 { continue }
            var parts = ["\(count) \(big.label(russian))"]
            if index + 1 < ids.count, let small = table.byId[ids[index + 1]] {
                var rest = Int64((Double(total - count * Int64(big.factor)) / small.factor).rounded())
                if Double(rest) * small.factor >= big.factor {
                    count += 1
                    rest = 0
                    parts = ["\(count) \(big.label(russian))"]
                }
                if rest > 0 { parts.append("\(rest) \(small.label(russian))") }
            }
            if big.id == from.id && parts.count == 1 { return nil }
            return parts.joined(separator: " ")
        }
        return nil
    }
}
