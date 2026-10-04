package app.nebulagram.ui;

/** All trailing controls keep their full hit area before the text is measured. */
public final class NebulaComposerSlots {
    private NebulaComposerSlots() { }
    public static int toolsInset(int nativeInset, int emojiSlot, int gap, boolean ios, int giftSlot) {
        return Math.max(nativeInset, ios ? emojiSlot : 0) + giftSlot + gap;
    }
    public static int captionInset(int nativeRightMargin, int confirmationInset, int gap) {
        return Math.max(0, confirmationInset + gap - nativeRightMargin);
    }
}
