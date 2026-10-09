import XCTest
@testable import NebulaSettingsContract

final class NebulaArithmeticTests: XCTestCase {
    func testDecimalPrecedenceAndPercent() {
        for (source, result) in [("2+3*4", "14"), ("(2+3)×4", "20"), ("0,1+0,2", "0.3"), ("200*10%", "20"), ("10 - 3", "7"), ("1/4", "0.25"), ("-2*-3", "6"), ("10-3=", "7")] {
            XCTAssertEqual(NebulaArithmetic.result(source), result, source)
        }
    }
    func testDraftsAreNotMistakenForExpressions() {
        for source in ["", "12345", "+79991234567", "2026-10-09", "123-45-67", "https://example.com", "hello 2+3", "1/0", "1..2+3", "2+", "()", "2(3)", "2**3", "1e9+2", "1;2+3"] {
            XCTAssertNil(NebulaArithmetic.result(source), source)
        }
    }
    func testBoundsAndNoCodeEvaluation() {
        XCTAssertNil(NebulaArithmetic.result(String(repeating: "(", count: 13) + "1+2" + String(repeating: ")", count: 13)))
        XCTAssertNil(NebulaArithmetic.result(String(repeating: "1+", count: 41) + "1"))
        XCTAssertNil(NebulaArithmetic.result(String(repeating: "1", count: 121) + "+2"))
        XCTAssertNil(NebulaArithmetic.result("abs(-2)"))
        XCTAssertEqual(NebulaArithmetic.result("1/3"), "0.333333333333")
    }
    func testSwipeOrderAndBounds() {
        XCTAssertEqual(NebulaSwipeActions.parse("3,0,1,3,unknown,22"), [.translate, .reply, .copy])
        XCTAssertEqual(NebulaSwipeActions.parse(""), [.reply])
        XCTAssertEqual(NebulaSwipeActions.parse(String(repeating: "0,", count: 22)), [.reply])
        let order: [NebulaSwipeAction] = [.copy, .reply, .tools, .translate]
        XCTAssertEqual(NebulaSwipeActions.action(order: order, verticalOffset: -200), .copy)
        XCTAssertEqual(NebulaSwipeActions.action(order: order, verticalOffset: 56), .reply)
        XCTAssertEqual(NebulaSwipeActions.action(order: order, verticalOffset: 5000), .translate)
        XCTAssertEqual(NebulaSwipeActions.action(order: order, verticalOffset: .nan), .reply)
    }
    func testCalendarDatesDoNotBecomeDivisionHints() {
        XCTAssertNil(NebulaArithmetic.result("09/10/2026"))
        XCTAssertNil(NebulaArithmetic.result("9/10/26"))
        XCTAssertNil(NebulaArithmetic.result("09.10.2026"))
        XCTAssertEqual(NebulaArithmetic.result("12/3/2"), "2")
        XCTAssertNotNil(NebulaArithmetic.result("09/10/2026="))
    }
}
