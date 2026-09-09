load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")

swift_library(
    name = "NebulaLinkUI",
    module_name = "NebulaLinkUI",
    srcs = glob(["Sources/**/*.swift"]),
    deps = [
        "//submodules/NebulaLinkCore:NebulaLinkCore",
        "//submodules/TelegramCore:TelegramCore",
        "//submodules/Postbox:Postbox",
        "//submodules/SSignalKit/SwiftSignalKit:SwiftSignalKit",
    ],
    visibility = ["//visibility:public"],
)
