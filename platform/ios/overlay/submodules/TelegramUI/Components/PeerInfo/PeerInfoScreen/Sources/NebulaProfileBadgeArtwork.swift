import UIKit
import NebulaSettingsContract

/// Original colors are intentional: these are branded badges, not template glyphs.
enum NebulaProfileBadgeArtwork {
    static func image(for badge: NebulaBadge) -> UIImage? {
        switch badge {
        case .supporter: return NebulaProfileBadgeImages.supporter
        case .star: return NebulaProfileBadgeImages.star
        default: return vectorImages[badge]
        }
    }

    private static let vectorImages: [NebulaBadge: UIImage] = {
        var result: [NebulaBadge: UIImage] = [:]
        for badge in [NebulaBadge.dev, .tester, .heart] {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 3
            format.opaque = false
            result[badge] = UIGraphicsImageRenderer(size: CGSize(width: 28, height: 28), format: format).image { _ in
                draw(badge)
            }.withRenderingMode(.alwaysOriginal)
        }
        return result
    }()

    private static func fill(_ color: UInt32, _ points: [(CGFloat, CGFloat)]) {
        let path = UIBezierPath()
        for (index, point) in points.enumerated() {
            if index == 0 { path.move(to: CGPoint(x: point.0, y: point.1)) }
            else { path.addLine(to: CGPoint(x: point.0, y: point.1)) }
        }
        path.close()
        paint(color, path)
    }

    private static func paint(_ color: UInt32, _ path: UIBezierPath) {
        UIColor(red: CGFloat((color >> 16) & 255) / 255, green: CGFloat((color >> 8) & 255) / 255, blue: CGFloat(color & 255) / 255, alpha: 1).setFill()
        path.fill()
    }

    private static func draw(_ badge: NebulaBadge) {
        switch badge {
        case .dev:
            fill(0x7263D9, [(14,1),(25,7),(25,21),(14,27),(3,21),(3,7)])
            fill(0x9A89F0, [(14,1),(25,7),(14,14),(3,7)])
            let code = UIBezierPath()
            let segments: [[(CGFloat, CGFloat)]] = [[(10,10),(6,14),(10,18)],[(18,10),(22,14),(18,18)],[(15.5,8),(12.5,20)]]
            for segment in segments {
                for (index, point) in segment.enumerated() {
                    if index == 0 { code.move(to: CGPoint(x: point.0, y: point.1)) }
                    else { code.addLine(to: CGPoint(x: point.0, y: point.1)) }
                }
            }
            code.lineWidth = 1.8
            code.lineCapStyle = .round
            code.lineJoinStyle = .round
            UIColor.white.setStroke()
            code.stroke()
        case .tester:
            let shield = UIBezierPath()
            shield.move(to: CGPoint(x: 14, y: 1))
            shield.addLine(to: CGPoint(x: 25, y: 5))
            shield.addLine(to: CGPoint(x: 24, y: 17))
            shield.addQuadCurve(to: CGPoint(x: 14, y: 27), controlPoint: CGPoint(x: 22, y: 23))
            shield.addQuadCurve(to: CGPoint(x: 4, y: 17), controlPoint: CGPoint(x: 6, y: 23))
            shield.addLine(to: CGPoint(x: 3, y: 5))
            shield.close()
            paint(0x27A98F, shield)
            fill(0x6CD9B6, [(14,1),(25,5),(14,15),(3,5)])
            fill(0xFFFFFF, [(14,6),(16,12),(22,14),(16,16),(14,22),(12,16),(6,14),(12,12)])
            fill(0x27A98F, [(14,11),(17,14),(14,17),(11,14)])
        case .heart:
            let heart = UIBezierPath()
            heart.move(to: CGPoint(x: 14, y: 6))
            heart.addCurve(to: CGPoint(x: 25, y: 14), controlPoint1: CGPoint(x: 19, y: -2), controlPoint2: CGPoint(x: 30, y: 5))
            heart.addLine(to: CGPoint(x: 14, y: 26))
            heart.addLine(to: CGPoint(x: 3, y: 14))
            heart.addCurve(to: CGPoint(x: 14, y: 6), controlPoint1: CGPoint(x: -2, y: 5), controlPoint2: CGPoint(x: 9, y: -2))
            heart.close()
            paint(0xE46789, heart)
            let highlight = UIBezierPath()
            highlight.move(to: CGPoint(x: 14, y: 6))
            highlight.addCurve(to: CGPoint(x: 3, y: 14), controlPoint1: CGPoint(x: 9, y: -2), controlPoint2: CGPoint(x: -2, y: 5))
            highlight.addLine(to: CGPoint(x: 14, y: 26))
            highlight.addLine(to: CGPoint(x: 10, y: 13))
            highlight.close()
            paint(0xF59CB2, highlight)
            fill(0xFFFFFF, [(17,7),(18.5,11),(22,12.5),(18.5,14),(17,18),(15.5,14),(12,12.5),(15.5,11)])
        default: break
        }
    }
}
