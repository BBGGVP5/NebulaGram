import Foundation

/// The Cherrygram-style profile page (Android `NebulaProfileStyle.page`/`bannerPage`):
/// the profile colour continues below the header under the cards and the media
/// tabs. A profile without its own colour but with a photo takes the photo's
/// average tone, darkened at night and lightened by day, so the banner flows into
/// the page. Pure arithmetic; the screen converts to and from `UIColor`.
public enum NebulaProfilePagePolicy {
    public struct RGB: Equatable {
        public var red: Double, green: Double, blue: Double
        public init(red: Double, green: Double, blue: Double) { self.red = red; self.green = green; self.blue = blue }
    }

    public enum Source: Equatable { case none, peerColor, photo }

    /// Which colour the page takes; the peer colour wins over the photo tone.
    public static func source(style: Bool, background: Bool, photoBanner: Bool, hasPeerColor: Bool, hasPhoto: Bool, editing: Bool) -> Source {
        guard style, !editing else { return .none }
        if hasPeerColor { return background ? .peerColor : .none }
        return photoBanner && hasPhoto ? .photo : .none
    }

    /// Android `bannerPage`: saturation at most 0.42 (night) / 0.38 (day); lightness
    /// 0.45× clamped to 0.12…0.22 at night, at least 0.86 by day.
    public static func photoTone(_ average: RGB, dark: Bool) -> RGB {
        let (h, s, l) = hsl(average)
        return rgb(h, min(s, dark ? 0.42 : 0.38), dark ? min(max(l * 0.45, 0.12), 0.22) : max(l, 0.86))
    }

    /// The page fades toward the theme's plain list background lower down
    /// (Android `PageBackground`: 30% at night, 45% by day).
    public static func bottom(_ page: RGB, base: RGB, dark: Bool) -> RGB {
        let t = dark ? 0.3 : 0.45
        return RGB(red: page.red + (base.red - page.red) * t, green: page.green + (base.green - page.green) * t, blue: page.blue + (base.blue - page.blue) * t)
    }

    static func hsl(_ c: RGB) -> (Double, Double, Double) {
        let r = clamp(c.red), g = clamp(c.green), b = clamp(c.blue)
        let maxValue = max(r, g, b), minValue = min(r, g, b), l = (maxValue + minValue) / 2
        guard maxValue != minValue else { return (0, 0, l) }
        let d = maxValue - minValue
        let s = l > 0.5 ? d / (2 - maxValue - minValue) : d / (maxValue + minValue)
        var h: Double
        if maxValue == r { h = (g - b) / d + (g < b ? 6 : 0) } else if maxValue == g { h = (b - r) / d + 2 } else { h = (r - g) / d + 4 }
        h /= 6
        return (h, s, l)
    }

    static func rgb(_ h: Double, _ s: Double, _ l: Double) -> RGB {
        guard s > 0 else { return RGB(red: l, green: l, blue: l) }
        let q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q
        func channel(_ value: Double) -> Double {
            var t = value
            if t < 0 { t += 1 }
            if t > 1 { t -= 1 }
            if t < 1 / 6 { return p + (q - p) * 6 * t }
            if t < 1 / 2 { return q }
            if t < 2 / 3 { return p + (q - p) * (2 / 3 - t) * 6 }
            return p
        }
        return RGB(red: channel(h + 1 / 3), green: channel(h), blue: channel(h - 1 / 3))
    }

    private static func clamp(_ value: Double) -> Double { value.isFinite ? min(1, max(0, value)) : 0 }
}
