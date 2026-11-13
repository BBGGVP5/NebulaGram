import Foundation

/// Month a user registered, as Telegram reports it in peer settings (Android
/// `NebulaRegistrationDate`, Telegram's own month only: no estimates and no
/// extra requests, so nothing about the user leaves the device).
public enum NebulaRegistrationMonth {
    /// Year and month from "YYYY-MM" or "MM.YYYY"; nil for anything else.
    public static func parse(_ raw: String?) -> (year: Int, month: Int)? {
        guard let raw = raw?.trimmingCharacters(in: .whitespaces), !raw.isEmpty else { return nil }
        let dash = raw.split(separator: "-"), dot = raw.split(separator: ".")
        var year: Int?, month: Int?
        if dash.count == 2, dash[0].count == 4 { year = Int(dash[0]); month = Int(dash[1]) }
        else if dot.count == 2, dot[1].count == 4 { year = Int(dot[1]); month = Int(dot[0]) }
        guard let year, let month, (2013 ... 2100).contains(year), (1 ... 12).contains(month) else { return nil }
        return (year, month)
    }

    /// "май 2022" / "May 2022"; the raw value when it has another shape.
    public static func display(_ raw: String?, russian: Bool) -> String? {
        guard let raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        guard let parsed = parse(raw) else { return raw }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        guard let date = calendar.date(from: DateComponents(year: parsed.year, month: parsed.month, day: 15)) else { return raw }
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.locale = Locale(identifier: russian ? "ru_RU" : "en_US")
        formatter.dateFormat = "LLLL yyyy"
        return formatter.string(from: date)
    }
}
