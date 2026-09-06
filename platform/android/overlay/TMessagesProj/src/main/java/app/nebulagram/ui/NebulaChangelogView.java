package app.nebulagram.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import java.util.ArrayList;

/** Uses Telegram's animated emoji lifecycle; entity offsets remain UTF-16 offsets. */
public final class NebulaChangelogView extends AnimatedEmojiSpan.TextViewEmojis implements NotificationCenter.NotificationCenterDelegate {
    public NebulaChangelogView(Context context) {
        super(context);
        setTextSize(15); setTextColor(NebulaTheme.of(context).onSurface());
        setLinkTextColor(NebulaTheme.of(context).primary());
        setGravity(Gravity.START | Gravity.TOP); setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
    }

    public void setPost(TLRPC.Message post) {
        String body = post == null || post.message == null ? "" : post.message;
        if (body.isEmpty()) body = NebulaText.text("Новая сборка NebulaGram", "A new NebulaGram build");
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        if (post != null && post.entities != null) for (TLRPC.MessageEntity entity : post.entities) {
            if (NebulaChangelog.validRange(entity.offset, entity.length, body.length())) entities.add(entity);
        }
        SpannableStringBuilder rich = new SpannableStringBuilder(body);
        MessageObject.addEntitiesToText(rich, entities, false, false, false, false);
        CharSequence emoji = Emoji.replaceEmoji(rich, getPaint().getFontMetricsInt(), false);
        setText(MessageObject.replaceAnimatedEmoji(emoji, entities, getPaint().getFontMetricsInt()));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }
    @Override protected void onDetachedFromWindow() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded); super.onDetachedFromWindow();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) { invalidate(); }
}
