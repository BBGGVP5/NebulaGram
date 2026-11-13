import UIKit
import NebulaSettingsContract

/// UIKit side of `NebulaProfilePagePolicy`: the page colour of an open profile and
/// the photo's average tone, measured once per image.
final class NebulaProfilePage {
    private weak var measuredImage: UIImage?
    private var measuredTone: UIColor?
    private var measuredDark: Bool?

    /// The page colour, or nil for Telegram's own background.
    func color(source: NebulaProfilePagePolicy.Source, peerColor: UIColor?, photo: UIImage?, dark: Bool) -> UIColor? {
        switch source {
        case .none:
            return nil
        case .peerColor:
            return peerColor
        case .photo:
            guard let photo else { return nil }
            if let measuredImage, measuredImage === photo, measuredDark == dark { return measuredTone }
            measuredImage = photo
            measuredDark = dark
            measuredTone = Self.average(photo).map { Self.color(NebulaProfilePagePolicy.photoTone($0, dark: dark)) }
            return measuredTone
        }
    }

    /// The colour lower down the page, toward the theme's list background.
    static func bottom(_ page: UIColor, base: UIColor, dark: Bool) -> UIColor {
        guard let a = rgb(page), let b = rgb(base) else { return page }
        return color(NebulaProfilePagePolicy.bottom(a, base: b, dark: dark))
    }

    static func rgb(_ color: UIColor) -> NebulaProfilePagePolicy.RGB? {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard color.getRed(&r, green: &g, blue: &b, alpha: &a) else { return nil }
        return NebulaProfilePagePolicy.RGB(red: Double(r), green: Double(g), blue: Double(b))
    }

    static func color(_ value: NebulaProfilePagePolicy.RGB) -> UIColor {
        UIColor(red: CGFloat(value.red), green: CGFloat(value.green), blue: CGFloat(value.blue), alpha: 1.0)
    }

    /// The image drawn into a single pixel.
    private static func average(_ image: UIImage) -> NebulaProfilePagePolicy.RGB? {
        guard let cgImage = image.cgImage else { return nil }
        var pixel = [UInt8](repeating: 0, count: 4)
        let drawn: Bool = pixel.withUnsafeMutableBytes { buffer in
            guard let context = CGContext(data: buffer.baseAddress, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                                          space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.interpolationQuality = .medium
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: 1, height: 1))
            return true
        }
        guard drawn, pixel[3] > 0 else { return nil }
        let alpha = Double(pixel[3])
        return NebulaProfilePagePolicy.RGB(red: Double(pixel[0]) / alpha, green: Double(pixel[1]) / alpha, blue: Double(pixel[2]) / alpha)
    }
}
