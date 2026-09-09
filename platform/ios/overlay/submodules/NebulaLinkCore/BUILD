load("@build_bazel_rules_apple//apple:apple.bzl", "apple_static_xcframework_import")

# Generated from this checkout's bind/mobile and runtime/xray, never a remote binary.
apple_static_xcframework_import(
    name = "NebulaLinkCore",
    xcframework_imports = glob(["NebulaLink.xcframework/**"]),
    sdk_frameworks = ["Foundation", "Security", "CFNetwork"],
    sdk_dylibs = ["resolv", "z"],
    visibility = ["//visibility:public"],
)
