import Foundation

/// Bounded sfnt check for an imported TTF/OTF (Android `NebulaFontFile`): the
/// file must have a sane table directory with `cmap` and `head` before the app
/// registers it and uses it for the whole interface.
public enum NebulaFontFile {
    public static let limit = 8 * 1024 * 1024
    /// Turns the custom font on; read once at launch by `Display.NebulaCustomFont`.
    public static let enabledKey = "nebula.font.custom"
    public static let fileName = "nebula-custom-font.sfnt"

    public enum Failure: Error, Equatable {
        case size, format, directory, table, missingTables
    }

    public static func validate(_ data: Data) throws {
        guard data.count >= 12, data.count <= limit else { throw Failure.size }
        let bytes = [UInt8](data)
        func u32(_ offset: Int) -> UInt32 {
            UInt32(bytes[offset]) << 24 | UInt32(bytes[offset + 1]) << 16 | UInt32(bytes[offset + 2]) << 8 | UInt32(bytes[offset + 3])
        }
        let format = u32(0)
        guard format == 0x0001_0000 || format == 0x4f54_544f || format == 0x7472_7565 else { throw Failure.format }
        let tables = Int(UInt16(bytes[4]) << 8 | UInt16(bytes[5]))
        let directory = 12 + tables * 16
        guard tables > 0, tables <= 256, directory <= bytes.count else { throw Failure.directory }
        var cmap = false, head = false
        for index in 0 ..< tables {
            let entry = 12 + index * 16
            let tag = u32(entry), offset = Int(u32(entry + 8)), length = Int(u32(entry + 12))
            guard offset >= directory, offset + length <= bytes.count else { throw Failure.table }
            if tag == 0x636d_6170 && length > 0 { cmap = true }
            if tag == 0x6865_6164 && length >= 54 { head = true }
        }
        guard cmap, head else { throw Failure.missingTables }
    }
}
