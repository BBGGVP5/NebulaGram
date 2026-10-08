load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")
swift_library(
    name = "NebulaBrowserCore",
    module_name = "NebulaBrowserCore",
    srcs = glob(["Sources/**/*.swift"]),
    deps = ["//submodules/NebulaSettingsContract:NebulaSettingsContract"],
    copts = ["-warnings-as-errors"],
    visibility = ["//visibility:public"],
)
