package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RLottieImageView;

/** Standard Telegram animated emoji; decoding and downloads are owned by its existing sticker cache. */
public final class NebulaAnimatedEmoji extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final int account, size;
    private final TextView fallback;
    private final RLottieImageView animation;
    private String emoji;
    private long documentId;
    private boolean attached;
    public NebulaAnimatedEmoji(Context context, int account, String emoji, int size) {
        super(context); this.account=account;this.size=size;this.emoji=emoji;
        fallback=new TextView(context);fallback.setGravity(Gravity.CENTER);fallback.setTextSize(size*.65f);
        addView(fallback,new LayoutParams(-1,-1));
        animation=new RLottieImageView(context) {
            @Override protected void onLoaded() { fallback.setVisibility(GONE); }
        };
        addView(animation,new LayoutParams(-1,-1));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setEmoji(emoji);
    }
    public void setEmoji(String value) {
        emoji=value;documentId=0;fallback.setVisibility(VISIBLE);
        fallback.setText(Emoji.replaceEmoji(value,fallback.getPaint().getFontMetricsInt(),false));
        animation.clearAnimationDrawable(); if(attached)refresh();
    }
    private void refresh() {
        TLRPC.Document document=MediaDataController.getInstance(account).getEmojiAnimatedSticker(emoji);
        if(document==null)return;
        boolean animate=Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled();
        animation.setAutoRepeat(animate);
        if(documentId!=document.id) {documentId=document.id;animation.setAnimation(document,size,size);}
        if(animate && attached && isShown() && hasWindowFocus())animation.playAnimation();else animation.stopAnimation();
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if(attached)refresh(); }
    @Override protected void onVisibilityChanged(android.view.View view,int visibility) { super.onVisibilityChanged(view,visibility); if(attached)refresh(); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();attached=true;
        NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.stickersDidLoad);
        MediaDataController.getInstance(account).checkStickers(MediaDataController.TYPE_EMOJI);refresh();
    }
    @Override protected void onDetachedFromWindow() {
        attached=false;NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.stickersDidLoad);
        animation.stopAnimation();super.onDetachedFromWindow();
    }
    @Override public void didReceivedNotification(int id,int account,Object... args) {if(attached&&id==NotificationCenter.stickersDidLoad)refresh();}
}
