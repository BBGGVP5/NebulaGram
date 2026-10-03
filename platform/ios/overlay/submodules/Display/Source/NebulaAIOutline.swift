import UIKit

/// Shared outline for the composer and attachment caption controls.
public enum NebulaAIOutline {
    public static let image: UIImage = {
        UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24)).image { _ in
            UIColor.white.setStroke()
            let path = UIBezierPath()
            path.lineWidth = 1.8
            path.lineCapStyle = .round
            path.lineJoinStyle = .round
            path.move(to: CGPoint(x: 10, y: 3))
            path.addCurve(to: CGPoint(x: 18, y: 11), controlPoint1: CGPoint(x: 11.1, y: 8.2), controlPoint2: CGPoint(x: 12.8, y: 9.9))
            path.addCurve(to: CGPoint(x: 10, y: 19), controlPoint1: CGPoint(x: 12.8, y: 12.1), controlPoint2: CGPoint(x: 11.1, y: 13.8))
            path.addCurve(to: CGPoint(x: 2, y: 11), controlPoint1: CGPoint(x: 8.9, y: 13.8), controlPoint2: CGPoint(x: 7.2, y: 12.1))
            path.addCurve(to: CGPoint(x: 10, y: 3), controlPoint1: CGPoint(x: 7.2, y: 9.9), controlPoint2: CGPoint(x: 8.9, y: 8.2))
            path.close()
            for (start, end) in [(CGPoint(x: 19, y: 2), CGPoint(x: 19, y: 6)),
                                 (CGPoint(x: 17, y: 4), CGPoint(x: 21, y: 4)),
                                 (CGPoint(x: 20, y: 16), CGPoint(x: 20, y: 20)),
                                 (CGPoint(x: 18, y: 18), CGPoint(x: 22, y: 18))] {
                path.move(to: start); path.addLine(to: end)
            }
            path.stroke()
        }.withRenderingMode(.alwaysTemplate)
    }()
}
