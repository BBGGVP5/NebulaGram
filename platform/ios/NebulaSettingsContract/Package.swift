// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NebulaSettingsContract",
    platforms: [.macOS(.v10_15), .iOS(.v13)],
    products: [.library(name: "NebulaSettingsContract", targets: ["NebulaSettingsContract"])],
    targets: [
        .target(name: "NebulaSettingsContract", resources: [.process("Resources")]),
        .testTarget(name: "NebulaSettingsContractTests", dependencies: ["NebulaSettingsContract"])
    ]
)
