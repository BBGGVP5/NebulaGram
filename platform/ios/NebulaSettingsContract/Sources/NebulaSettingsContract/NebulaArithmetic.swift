import Foundation

/// Bounded decimal arithmetic, with no scripting engine or executable input.
public enum NebulaArithmetic {
    public static func result(_ text: String) -> String? {
        guard text.utf16.count <= 120 else { return nil }
        var source = text.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "×", with: "*").replacingOccurrences(of: "÷", with: "/").replacingOccurrences(of: ",", with: ".")
        let marked = source.hasSuffix("=")
        if marked { source.removeLast() }
        // A calendar date containing slashes must not turn into a division hint.
        if !marked && source.range(of: #"^\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}$"#, options: .regularExpression) != nil { return nil }
        // Suppress phone numbers, unspaced dates and plain identifiers.
        guard marked || (source.contains("+") && !source.hasPrefix("+")) || source.contains(where: { "*/%()".contains($0) }) || source.range(of: #"\s[+-]\s"#, options: .regularExpression) != nil else { return nil }
        guard source.allSatisfy({ "0123456789. +-*/%()\t\r\n".contains($0) }) else { return nil }
        var parser = Parser(Array(source))
        guard let value = try? parser.expression(), parser.finished, parser.operations > 0, !value.isNaN else { return nil }
        var rounded = Decimal(), input = value
        NSDecimalRound(&rounded, &input, 12, .plain)
        let output = NSDecimalNumber(decimal: rounded).stringValue
        return output.count <= 24 && !output.contains("E") && !output.contains("e") ? output : nil
    }

    private enum Invalid: Error { case expression }
    private struct Parser {
        let text: [Character]
        var position = 0, operations = 0, depth = 0
        init(_ text: [Character]) { self.text = text }
        mutating func spaces() { while position < text.count && text[position].isWhitespace { position += 1 } }
        var finished: Bool { mutating get { spaces(); return position == text.count } }
        mutating func take(_ char: Character) -> Bool {
            spaces(); guard position < text.count, text[position] == char else { return false }
            position += 1; return true
        }
        mutating func calculate(_ lhs: Decimal, _ rhs: Decimal, _ op: Character) throws -> Decimal {
            operations += 1; guard operations <= 40 else { throw Invalid.expression }
            var left = lhs, right = rhs, result = Decimal()
            let status: Decimal.CalculationError
            switch op {
            case "+": status = NSDecimalAdd(&result, &left, &right, .plain)
            case "-": status = NSDecimalSubtract(&result, &left, &right, .plain)
            case "*": status = NSDecimalMultiply(&result, &left, &right, .plain)
            default: status = NSDecimalDivide(&result, &left, &right, .plain)
            }
            guard status == .noError || status == .lossOfPrecision, !result.isNaN else { throw Invalid.expression }
            return result
        }
        mutating func expression() throws -> Decimal {
            var value = try product()
            while true {
                if take("+") { let rhs = try product(); value = try calculate(value, rhs, "+") }
                else if take("-") { let rhs = try product(); value = try calculate(value, rhs, "-") }
                else { return value }
            }
        }
        mutating func product() throws -> Decimal {
            var value = try number()
            while true {
                if take("*") { let rhs = try number(); value = try calculate(value, rhs, "*") }
                else if take("/") { let rhs = try number(); value = try calculate(value, rhs, "/") }
                else { return value }
            }
        }
        mutating func number() throws -> Decimal {
            depth += 1; defer { depth -= 1 }; guard depth <= 12 else { throw Invalid.expression }
            var value: Decimal
            if take("-") { value = try -number() }
            else if take("+") { value = try number() }
            else if take("(") { value = try expression(); guard take(")") else { throw Invalid.expression } }
            else {
                spaces(); let start = position
                while position < text.count && (text[position].isNumber || text[position] == ".") { position += 1 }
                let token = String(text[start..<position])
                guard !token.isEmpty, token.count <= 30, token.filter({ $0 == "." }).count <= 1,
                      token.contains(where: { $0.isNumber }), let number = Decimal(string: token, locale: Locale(identifier: "en_US_POSIX")) else { throw Invalid.expression }
                value = number
            }
            if take("%") { value = try calculate(value, 100, "/") }
            return value
        }
    }
}
