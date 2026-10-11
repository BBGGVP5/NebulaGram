import Foundation

/// Shared geometry for the native profile foreground and its regression checks.
public enum NebulaProfileBannerPolicy {
    public static func fadeFrame(width: CGFloat, bottom: CGFloat, protectedBottom: CGFloat, pixel: CGFloat) -> CGRect {
        guard width.isFinite, bottom.isFinite, protectedBottom.isFinite, pixel.isFinite,
              width > 0.0, bottom > 0.0 else { return .zero }
        let top = min(bottom, max(max(0.0, bottom - 148.0), protectedBottom))
        guard top < bottom else { return .zero }
        return CGRect(x: 0.0, y: top, width: width, height: bottom - top + max(0.0, min(1.0, pixel)))
    }

    public static func opacity(style: Bool, photoBanner: Bool, hasPhoto: Bool, editing: Bool) -> CGFloat {
        return style && photoBanner && hasPhoto && !editing ? 1.0 : 0.0
    }
}
