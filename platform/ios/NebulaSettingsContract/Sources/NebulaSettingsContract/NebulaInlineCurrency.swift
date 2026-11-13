import Foundation

/// Currency conversion in the message field (Android `NebulaInlineCurrency`):
/// "100€ =", "100 dollars to euros =", "5k ₽ to $ =". Rates are units of a
/// currency per US dollar (`NebulaInlineRates`); crypto and Stars are not offered.
public enum NebulaInlineCurrency {
    private static let table: (names: [String: String], symbols: [String: String]) = {
        var names: [String: String] = [:], symbols: [String: String] = [:]
        func add(_ code: String, _ symbol: String?, _ words: String...) {
            names[code.lowercased()] = code
            if let symbol { symbols[code] = symbol; names[symbol.lowercased()] = code }
            for word in words { names[word] = code }
        }
        add("USD", "$", "us$", "dollar", "dollars", "доллар", "доллара", "долларов", "доллары", "долларах", "бакс", "бакса", "баксов", "баксы")
        add("EUR", "€", "euro", "euros", "евро")
        add("RUB", "₽", "rur", "ruble", "rubles", "rouble", "roubles", "руб", "руб.", "рубль", "рубля", "рублей", "рубли", "р", "р.")
        add("GBP", "£", "pound", "pounds", "pound sterling", "фунт", "фунта", "фунтов", "фунты", "фунт стерлингов", "фунтов стерлингов")
        add("JPY", "¥", "yen", "иена", "иены", "иен", "йена", "йены", "йен")
        add("CNY", nil, "rmb", "yuan", "юань", "юаня", "юаней", "юани", "元")
        add("UAH", "₴", "hryvnia", "hryvnias", "гривна", "гривны", "гривен", "гривня", "грн")
        add("KZT", "₸", "tenge", "тенге", "тг")
        add("BYN", nil, "br", "belarusian ruble", "belarusian rubles", "белорусский рубль", "белорусских рублей", "белорусских рубля", "бел. руб")
        add("TRY", "₺", "lira", "liras", "лира", "лиры", "лир")
        add("INR", "₹", "rupee", "rupees", "рупия", "рупии", "рупий")
        add("KRW", "₩", "won", "вона", "вон")
        add("ILS", "₪", "shekel", "shekels", "шекель", "шекеля", "шекелей")
        add("GEL", "₾", "lari", "лари")
        add("AZN", "₼", "manat", "манат", "маната", "манатов")
        add("AMD", "֏", "dram", "драм", "драма", "драмов")
        add("UZS", nil, "sum", "сум", "сума", "сумов")
        add("KGS", nil, "som", "сом", "сома", "сомов")
        add("PLN", nil, "zł", "zloty", "złoty", "злотый", "злотых", "злотого")
        add("CHF", nil, "franc", "francs", "swiss franc", "франк", "франка", "франков")
        add("AED", nil, "dirham", "dirhams", "дирхам", "дирхама", "дирхамов")
        add("THB", "฿", "baht", "бат", "бата", "батов")
        add("VND", "₫", "dong", "донг", "донга", "донгов")
        add("BRL", nil, "r$", "real", "reais", "реал", "реала", "реалов")
        add("CAD", nil, "c$", "canadian dollar", "canadian dollars", "канадский доллар", "канадских долларов")
        add("AUD", nil, "a$", "australian dollar", "australian dollars", "австралийский доллар", "австралийских долларов")
        add("MDL", nil, "lei", "лей", "лея", "леев")
        add("NGN", "₦", "naira", "найра")
        add("PHP", "₱", "peso", "pesos", "песо")
        add("HUF", nil, "forint", "forints", "форинт", "форинта", "форинтов")
        add("CZK", nil, "kč", "koruna", "крона", "кроны", "крон")
        return (names, symbols)
    }()

    /// An ISO code for a symbol, code or name; any three-letter code with a known rate also counts.
    static func code(_ token: String, rates: [String: Double]?) -> String? {
        let text = token.trimmingCharacters(in: .whitespaces).lowercased()
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        guard !text.isEmpty, text.count <= 32 else { return nil }
        if let code = table.names[text] { return code }
        if text.range(of: #"^[a-z]{3}$"#, options: .regularExpression) != nil, let rates, rates[text.uppercased()] != nil { return text.uppercased() }
        return nil
    }

    static func label(_ code: String) -> String { table.symbols[code] ?? code }

    /// Default currency by the account's phone number; nil when the prefix is not known.
    public static func byPhone(_ phone: String?) -> String? {
        guard let phone else { return nil }
        let digits = phone.filter(\.isNumber)
        let prefixes: [(String, String)] = [
            ("76", "KZT"), ("77", "KZT"), ("7", "RUB"), ("380", "UAH"), ("375", "BYN"), ("998", "UZS"), ("996", "KGS"), ("992", "TJS"),
            ("994", "AZN"), ("995", "GEL"), ("374", "AMD"), ("373", "MDL"), ("993", "TMT"), ("1", "USD"), ("44", "GBP"), ("90", "TRY"),
            ("91", "INR"), ("86", "CNY"), ("81", "JPY"), ("82", "KRW"), ("972", "ILS"), ("971", "AED"), ("966", "SAR"), ("48", "PLN"),
            ("420", "CZK"), ("36", "HUF"), ("46", "SEK"), ("47", "NOK"), ("45", "DKK"), ("41", "CHF"), ("55", "BRL"), ("52", "MXN"),
            ("61", "AUD"), ("64", "NZD"), ("65", "SGD"), ("66", "THB"), ("84", "VND"), ("62", "IDR"), ("60", "MYR"), ("63", "PHP"),
            ("20", "EGP"), ("27", "ZAR"), ("234", "NGN"), ("54", "ARS"), ("57", "COP"), ("56", "CLP"), ("92", "PKR"), ("880", "BDT"),
            ("381", "RSD"), ("40", "RON"), ("359", "BGN"), ("354", "ISK"),
            ("30", "EUR"), ("31", "EUR"), ("32", "EUR"), ("33", "EUR"), ("34", "EUR"), ("351", "EUR"), ("353", "EUR"), ("356", "EUR"),
            ("357", "EUR"), ("358", "EUR"), ("370", "EUR"), ("371", "EUR"), ("372", "EUR"), ("385", "EUR"), ("386", "EUR"), ("39", "EUR"),
            ("421", "EUR"), ("43", "EUR"), ("49", "EUR"), ("352", "EUR")
        ]
        var best: String?, length = 0
        for (prefix, code) in prefixes where digits.hasPrefix(prefix) && prefix.count > length { best = code; length = prefix.count }
        return best
    }

    /// The converted amount for an expression without its "=", or nil.
    /// `ratesWanted` is set when a currency was recognised but no rates are loaded yet.
    public static func convert(_ body: String, rates: [String: Double]?, defaultCurrency: String, ratesWanted: inout Bool) -> String? {
        for split in NebulaInlineUnits.splits(body) {
            guard let target = code(split.right, rates: rates) else { continue }
            if let result = convert(split.left, to: target, rates: rates, ratesWanted: &ratesWanted) { return result }
        }
        guard let source = amount(body, rates: rates) else { return nil }
        var target = defaultCurrency
        if target.isEmpty || target == source.code { target = source.code == "USD" ? "EUR" : "USD" }
        return convert(body, to: target, rates: rates, ratesWanted: &ratesWanted)
    }

    private static func convert(_ text: String, to target: String, rates: [String: Double]?, ratesWanted: inout Bool) -> String? {
        guard let parsed = amount(text, rates: rates), parsed.code != target else { return nil }
        guard let rates else { ratesWanted = true; return nil }
        guard let from = rates[parsed.code], let to = rates[target], from > 0 else { return nil }
        let value = parsed.value * to / from
        guard value.isFinite else { return nil }
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.usesGroupingSeparator = false
        if abs(value) >= 1 {
            formatter.minimumFractionDigits = 2; formatter.maximumFractionDigits = 2
        } else {
            formatter.usesSignificantDigits = true; formatter.maximumSignificantDigits = 3
        }
        guard let number = formatter.string(from: NSNumber(value: value)) else { return nil }
        return number + " " + label(target)
    }

    /// The currency may follow the amount ("100€", "5k ₽") or lead it ("$100").
    private static func amount(_ text: String, rates: [String: Double]?) -> (value: Double, code: String)? {
        let characters = Array(text.trimmingCharacters(in: .whitespaces))
        guard characters.count >= 2 else { return nil }
        for index in max(1, characters.count - 32) ..< characters.count {
            let character = characters[index]
            if character.isNumber || character.isWhitespace { continue }
            if character.isLetter && characters[index - 1].isLetter { continue }
            guard let code = code(String(characters[index...]), rates: rates),
                  let value = NebulaInlineUnits.number(String(characters[..<index])) else { continue }
            return (value: value, code: code)
        }
        var index = min(characters.count - 1, 32)
        while index > 0 {
            defer { index -= 1 }
            if characters[index].isLetter && characters[index - 1].isLetter { continue }
            guard let code = code(String(characters[..<index]), rates: rates),
                  let value = NebulaInlineUnits.number(String(characters[index...])) else { continue }
            return (value: value, code: code)
        }
        return nil
    }
}
