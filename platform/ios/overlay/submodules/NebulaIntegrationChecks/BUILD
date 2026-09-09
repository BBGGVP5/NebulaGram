# Compile the actual Telegram consumers, including settings routing. This is not
# an app target and cannot create an installable build with fixture credentials.
load("@build_bazel_rules_apple//apple:ios.bzl", "ios_build_test")

# Apply the same Apple platform transition as an iOS bundle, without packaging one.
ios_build_test(
    name = "NebulaIntegrationChecks",
    minimum_os_version = "13.0",
    targets = [
        "//submodules/SettingsUI:SettingsUI",
        "//submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen:PeerInfoScreen",
        "//submodules/TelegramUI/Components/ChatList/ChatListFilterTabContainerNode:ChatListFilterTabContainerNode",
    ],
    visibility = ["//visibility:public"],
)
