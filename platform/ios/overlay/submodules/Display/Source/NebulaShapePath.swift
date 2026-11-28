import UIKit

/// Shared path for native avatar masks and their settings preview.
public enum NebulaShapePath {
    public static func path(in rect: CGRect, radii: [CGFloat]) -> CGPath {
        guard radii.count == 4 else { return UIBezierPath(rect: rect).cgPath }
        let limit = max(0, min(rect.width, rect.height) / 2)
        let r = radii.map { max(0, min(limit, $0.isFinite ? $0 : 0)) }
        let path = UIBezierPath()
        path.move(to: CGPoint(x: rect.minX + r[0], y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - r[1], y: rect.minY))
        path.addArc(withCenter: CGPoint(x: rect.maxX - r[1], y: rect.minY + r[1]), radius: r[1], startAngle: -.pi / 2, endAngle: 0, clockwise: true)
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r[2]))
        path.addArc(withCenter: CGPoint(x: rect.maxX - r[2], y: rect.maxY - r[2]), radius: r[2], startAngle: 0, endAngle: .pi / 2, clockwise: true)
        path.addLine(to: CGPoint(x: rect.minX + r[3], y: rect.maxY))
        path.addArc(withCenter: CGPoint(x: rect.minX + r[3], y: rect.maxY - r[3]), radius: r[3], startAngle: .pi / 2, endAngle: .pi, clockwise: true)
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + r[0]))
        path.addArc(withCenter: CGPoint(x: rect.minX + r[0], y: rect.minY + r[0]), radius: r[0], startAngle: .pi, endAngle: .pi * 1.5, clockwise: true)
        path.close()
        return path.cgPath
    }
}
