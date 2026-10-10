import TelegramCore
import Postbox

extension ChatControllerImpl {
    func nebulaRefreshRecordingButtons() {
        guard isNodeLoaded else { return }
        var ids: [MessageId] = []
        chatDisplayNode.historyNode.forEachVisibleMessageItemNode { node in
            guard let message = node.item?.message,
                  message.effectiveMedia.contains(where: { ($0 as? TelegramMediaFile).map { $0.isVoice || $0.isInstantVideo } ?? false }) else { return }
            ids.append(message.id)
        }
        // Collect before requesting updates: relayout must not mutate the list
        // while its visible-node traversal is active.
        for id in ids { chatDisplayNode.historyNode.requestMessageUpdate(id) }
    }
}
