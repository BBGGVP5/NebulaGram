# Compile the actual Telegram consumers, including settings routing. This is not
# an app target and cannot create an installable build with fixture credentials.
filegroup(
    name = "NebulaIntegrationChecks",
    srcs = [
        "//submodules/SettingsUI:SettingsUI",
        "//submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen:PeerInfoScreen",
        "//submodules/TelegramUI/Components/ChatList/ChatListFilterTabContainerNode:ChatListFilterTabContainerNode",
    ],
    visibility = ["//visibility:public"],
)
