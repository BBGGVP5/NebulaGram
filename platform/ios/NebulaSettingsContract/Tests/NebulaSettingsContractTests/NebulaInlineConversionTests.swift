import XCTest
@testable import NebulaSettingsContract

final class NebulaInlineConversionTests: XCTestCase {
    private let rates: [String: Double] = ["USD": 1, "EUR": 0.5, "RUB": 100, "GBP": 0.8]

    private func currency(_ body: String, default target: String = "RUB") -> String? {
        var wanted = false
        return NebulaInlineCurrency.convert(body, rates: rates, defaultCurrency: target, ratesWanted: &wanted)
    }

    func testCurrency() {
        XCTAssertEqual(currency("100€"), "20000.00 ₽")
        XCTAssertEqual(currency("$100 to euros"), "50.00 €")
        XCTAssertEqual(currency("5k ₽ to $"), "50.00 $")
        XCTAssertEqual(currency("100 долларов в рублях"), nil)
        XCTAssertEqual(currency("100 долларов в рубли"), "10000.00 ₽")
        XCTAssertEqual(currency("1 ₽ to usd"), "0.01 $")
        XCTAssertEqual(currency("100 ₽", default: "RUB"), "1.00 $")
        XCTAssertNil(currency("hello"))
        var wanted = false
        XCTAssertNil(NebulaInlineCurrency.convert("100€", rates: nil, defaultCurrency: "USD", ratesWanted: &wanted))
        XCTAssertTrue(wanted)
    }

    func testCurrencyByPhone() {
        XCTAssertEqual(NebulaInlineCurrency.byPhone("79161234567"), "RUB")
        XCTAssertEqual(NebulaInlineCurrency.byPhone("+7 701 123 45 67"), "KZT")
        XCTAssertEqual(NebulaInlineCurrency.byPhone("4915112345678"), "EUR")
        XCTAssertNil(NebulaInlineCurrency.byPhone(""))
    }

    func testRatesParsing() {
        let body = #"{"result":"success","rates":{"USD":1,"EUR":0.92,"bad":3,"XYZ":-1}}"#
        XCTAssertEqual(NebulaInlineRates.parse(Data(body.utf8)), ["USD": 1, "EUR": 0.92])
        XCTAssertNil(NebulaInlineRates.parse(Data(#"{"result":"error"}"#.utf8)))
    }

    func testTimeZones() {
        let moscow = TimeZone(identifier: "Europe/Moscow")!
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = moscow
        let noon = calendar.date(from: DateComponents(year: 2026, month: 1, day: 15, hour: 12))!
        XCTAssertEqual(NebulaInlineTime.convert("15:00 msk in tokyo", now: noon, local: moscow), "21:00")
        XCTAssertEqual(NebulaInlineTime.convert("23:00 msk to tokyo", now: noon, local: moscow), "05:00 (+1)")
        XCTAssertEqual(NebulaInlineTime.convert("3pm est to utc+3", now: noon, local: moscow), "23:00")
        XCTAssertEqual(NebulaInlineTime.convert("токио", now: noon, local: moscow), "18:00")
        XCTAssertEqual(NebulaInlineTime.convert("now in london", now: noon, local: moscow), "09:00")
        XCTAssertNil(NebulaInlineTime.convert("15:00", now: noon, local: moscow))
        XCTAssertNil(NebulaInlineTime.convert("hello", now: noon, local: moscow))
    }

    func testUnitsKeepTheirSplit() {
        XCTAssertEqual(NebulaInlineUnits.question("note\n5 km ="), "5 km")
        XCTAssertEqual(NebulaInlineUnits.explicit("5 km to m", russian: false), "5000 m")
        XCTAssertNil(NebulaInlineUnits.explicit("5 km", russian: false))
        XCTAssertEqual(NebulaInlineUnits.number("5k"), 5000)
        XCTAssertEqual(NebulaInlineUnits.number("1 500,5"), 1500.5)
    }
}
