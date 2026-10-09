package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;

/** Owns the receiver for one attachment; only a decoded frame can replace the fallback. */
public final class NebulaAnimatedEmoji extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private static final String PAGE_EMOJI_SET = "RestrictedEmoji";
    // Same foreground level as Telegram's CACHE_TYPE_ALERT_STANDARD_EMOJI.
    public static final int DIALOG_LAYER = 6656;
    private final int account, size, layer;
    private final ImageView fallbackImage;
    private final Rect visibleBounds = new Rect();
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::updatePlayback;
    private String emoji;
    private long documentId;
    private boolean attached, visualPosted;
    private int attachment;
    private ImageReceiver receiver;
    private boolean activeLast, rewindPending = true;

    public NebulaAnimatedEmoji(Context c, int account, String emoji, int size) {
        this(c, account, emoji, size, 7);
    }
    public NebulaAnimatedEmoji(Context c, int account, String emoji, int size, int layer) {
        super(c); this.account=account; this.size=size; this.layer=layer; setWillNotDraw(false);
        fallbackImage=new ImageView(c);fallbackImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Page introductions use the same canvas before and after decoding.
        int inset=size>=72?0:AndroidUtilities.dp(size*.14f);fallbackImage.setPadding(inset,inset,inset,inset);
        addView(fallbackImage,new LayoutParams(-1,-1));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setEmoji(emoji);
    }
    public void setEmoji(String value) {
        emoji=value;
        android.graphics.drawable.Drawable glyph=Emoji.getEmojiBigDrawable(value);
        if(glyph==null)glyph=Emoji.getEmojiBigDrawable(normalize(value));
        if(glyph instanceof Emoji.EmojiDrawable){((Emoji.EmojiDrawable)glyph).fullSize=false;((Emoji.EmojiDrawable)glyph).preload();}
        fallbackImage.setImageDrawable(glyph);
        releaseReceiver(); showFallback(true); if(attached)refresh();
    }
    private boolean motionAllowed() { return !NebulaGlass.reduced() && (Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled()); }
    private View fallback() { return fallbackImage; }
    private void showFallback(boolean show) {
        fallbackImage.setVisibility(show?VISIBLE:GONE);
    }
    private boolean frameReady() {
        return receiver!=null && (receiver.getLottieAnimation()!=null&&receiver.getLottieAnimation().hasBitmap()
                || receiver.getAnimation()!=null&&receiver.getAnimation().hasBitmap());
    }
    private static String normalize(String value) {return value==null?"":value.replace("\uFE0F","");}
    private TLRPC.Document pageDocument() {
        if(size<72)return null;
        MediaDataController data=MediaDataController.getInstance(account);
        TLRPC.TL_messages_stickerSet set=data.getStickerSetByEmojiOrName(PAGE_EMOJI_SET);
        if(set==null)set=data.getStickerSetByName(PAGE_EMOJI_SET);
        if(set==null)return null;
        String wanted=normalize(emoji);
        for(TLRPC.TL_stickerPack pack:set.packs)if(wanted.equals(normalize(pack.emoticon))) {
            for(Long id:pack.documents)for(TLRPC.Document document:set.documents)if(document.id==id&&animated(document))return document;
        }
        for(TLRPC.Document document:set.documents)
            if(animated(document)&&!wanted.isEmpty()&&normalize(MessageObject.findAnimatedEmojiEmoticon(document,null)).contains(wanted))return document;
        return null;
    }
    private static boolean animated(TLRPC.Document document) {
        return "application/x-tgsticker".equals(document.mime_type)||"video/webm".equals(document.mime_type);
    }
    private void loadPageSet() {
        if(size<72)return;
        // This native loader actualizes its disk cache after 24 hours, unlike
        // a permanently cached getStickerSetByName result. It deduplicates loads.
        MediaDataController.getInstance(account).loadStickersByEmojiOrName(PAGE_EMOJI_SET,false,true);
    }
    private void refresh() {
        if(!attached)return;
        TLRPC.Document document=pageDocument();
        if(document==null)document=MediaDataController.getInstance(account).getEmojiAnimatedSticker(emoji);
        if(document!=null&&(receiver==null||documentId!=document.id)) {
            releaseReceiver(); documentId=document.id;
            final ImageReceiver next=receiver=new ImageReceiver(this);
            final int epoch=attachment;
            next.setCurrentAccount(account); next.setAllowLoadingOnAttachedOnly(true); next.setAspectFit(true);
            // A cached decoder must not share its playhead with another visible emoji.
            next.setUniqKeyPrefix("nebula_emoji_"+Integer.toHexString(System.identityHashCode(this))+"_");
            next.setAutoRepeat(1); next.setAutoRepeatCount(-1); next.setAllowDecodeSingleFrame(true); next.setLayerNum(layer);
            next.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                @Override public void didSetImage(ImageReceiver r,boolean set,boolean thumb,boolean cache) { if(set&&!thumb)postVisual(next,epoch); }
                @Override public void onAnimationReady(ImageReceiver r) { postVisual(next,epoch); }
            });
            next.onAttachedToWindow();
            String filter=size+"_"+size+("video/webm".equals(document.mime_type)?"_"+ImageLoader.AUTOPLAY_FILTER:"");
            next.setImage(ImageLocation.getForDocument(document),filter,null,null,null,document.size,null,document,1);
        }
        updateVisual();
    }
    private void postVisual(ImageReceiver source,int epoch) {
        if(visualPosted||!attached||source!=receiver||epoch!=attachment)return; visualPosted=true;
        post(()->{if(epoch!=attachment||source!=receiver)return;visualPosted=false;if(attached)updateVisual();});
    }
    private void updateVisual() { showFallback(!frameReady()); updatePlayback(); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(receiver==null)return;
        // Child layout/image callbacks can run before the dialog's ancestors
        // have visible bounds. Recheck once those bounds are final, otherwise
        // a paused decoder without a first frame has no callback to restart it.
        updatePlayback();
        receiver.setImageCoords(0,0,getWidth(),getHeight()); boolean ready=frameReady();
        int save=canvas.save(); if(!ready)canvas.clipRect(0,0,0,0);
        receiver.draw(canvas); canvas.restoreToCount(save);
        if(ready&&fallback().getVisibility()==VISIBLE)postVisual(receiver,attachment);
        else if(!ready&&fallback().getVisibility()!=VISIBLE)postVisual(receiver,attachment);
    }
    private void releaseReceiver() {
        documentId=0; visualPosted=false; attachment++; activeLast=false; rewindPending=true;
        if(receiver!=null) { ImageReceiver old=receiver;receiver=null;old.setDelegate(null);old.stopAnimation();old.onDetachedFromWindow(); }
    }
    private void updatePlayback() {
        boolean active=attached&&motionAllowed()&&isShown()&&hasWindowFocus()&&getGlobalVisibleRect(visibleBounds);
        if(active&&!activeLast)rewindPending=true;
        activeLast=active;
        if(receiver!=null) {
            receiver.setAutoRepeat(active?1:0); receiver.setAllowStartAnimation(active);receiver.setAllowStartLottieAnimation(active);
            if(active) {
                if(receiver.getLottieAnimation()!=null) {
                    org.telegram.ui.Components.RLottieDrawable animation=receiver.getLottieAnimation();
                    animation.setAutoRepeat(1);animation.setAutoRepeatCount(-1);
                    if(rewindPending){animation.setCurrentFrame(0,true,true);rewindPending=false;}
                    // ImageReceiver.startAnimation() calls restart(false), which refuses
                    // a stopped infinite loop. Start the decoder itself instead.
                    animation.start();
                } else if(receiver.getAnimation()!=null) {
                    if(rewindPending){receiver.getAnimation().seekTo(0,false,true);rewindPending=false;}
                    receiver.startAnimation();
                }
            } else receiver.stopAnimation();
        }
    }
    /** Navigation can resume an existing attached view without layout or focus changes. */
    public void replay() { rewindPending=true;if(attached){loadPageSet();refresh();updatePlayback();} }
    public static void replayPage(View view) {
        if(view instanceof NebulaAnimatedEmoji)((NebulaAnimatedEmoji)view).replay();
        else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)replayPage(group.getChildAt(i));}
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(attached)updatePlayback();}
    @Override protected void onVisibilityChanged(View v,int state){super.onVisibilityChanged(v,state);if(attached)updatePlayback();}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);if(attached)updatePlayback();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;attachment++;
        NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.stickersDidLoad);getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.groupStickersDidLoad);
        NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.diceStickersDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this,NotificationCenter.emojiLoaded);
        loadPageSet();MediaDataController.getInstance(account).checkStickers(MediaDataController.TYPE_EMOJI);refresh();post(()->{if(attached)updateVisual();});}
    @Override protected void onDetachedFromWindow(){attached=false;NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.groupStickersDidLoad);
        NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.diceStickersDidLoad);
        NotificationCenter.getGlobalInstance().removeObserver(this,NotificationCenter.emojiLoaded);
        if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        releaseReceiver();showFallback(true);super.onDetachedFromWindow();}
    @Override public void didReceivedNotification(int id,int account,Object...args){if(!attached)return;
        if(id==NotificationCenter.stickersDidLoad||id==NotificationCenter.groupStickersDidLoad||id==NotificationCenter.diceStickersDidLoad)refresh();else if(id==NotificationCenter.emojiLoaded)fallbackImage.invalidate();}
}
