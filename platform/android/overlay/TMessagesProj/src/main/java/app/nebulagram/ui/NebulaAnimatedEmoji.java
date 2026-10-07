package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;

/** Owns the receiver for one attachment; only a decoded frame can replace the fallback. */
public final class NebulaAnimatedEmoji extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final int account, size;
    private final TextView text;
    private final ImageView shield;
    private final Rect visibleBounds = new Rect();
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::updatePlayback;
    private String emoji;
    private long documentId;
    private boolean attached, visualPosted;
    private int attachment;
    private ImageReceiver receiver;
    private ValueAnimator fallbackMotion;

    public NebulaAnimatedEmoji(Context c, int account, String emoji, int size) {
        super(c); this.account=account; this.size=size; setWillNotDraw(false);
        text=new TextView(c); text.setGravity(Gravity.CENTER); text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,size*.72f);
        text.setTextColor(0xFFFFFFFF); text.setIncludeFontPadding(false); addView(text,new LayoutParams(-1,-1));
        shield=new ImageView(c); shield.setImageDrawable(new NebulaShieldDrawable());
        shield.setScaleType(ImageView.ScaleType.FIT_CENTER); int inset=AndroidUtilities.dp(size/12);shield.setPadding(inset,inset,inset,inset);
        addView(shield,new LayoutParams(-1,-1)); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setEmoji(emoji);
    }
    public void setEmoji(String value) {
        emoji=value; text.setText(value); releaseReceiver(); showFallback(true); if(attached)refresh();
    }
    private boolean motionAllowed() { return !NebulaGlass.reduced() && (Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled()); }
    private View fallback() { return emoji.replace("\uFE0F", "").equals("🛡")?shield:text; }
    private void showFallback(boolean show) {
        text.setVisibility(show&&fallback()==text?VISIBLE:GONE); shield.setVisibility(show&&fallback()==shield?VISIBLE:GONE);
    }
    private boolean frameReady() {
        return receiver!=null && (receiver.getLottieAnimation()!=null&&receiver.getLottieAnimation().hasBitmap()
                || receiver.getAnimation()!=null&&receiver.getAnimation().hasBitmap());
    }
    private void refresh() {
        if(!attached)return;
        TLRPC.Document document=MediaDataController.getInstance(account).getEmojiAnimatedSticker(emoji);
        if(document!=null&&(receiver==null||documentId!=document.id)) {
            releaseReceiver(); documentId=document.id;
            final ImageReceiver next=receiver=new ImageReceiver(this);
            final int epoch=attachment;
            next.setCurrentAccount(account); next.setAllowLoadingOnAttachedOnly(true); next.setAspectFit(true);
            next.setAutoRepeat(1); next.setAutoRepeatCount(-1); next.setAllowDecodeSingleFrame(true); next.setLayerNum(7);
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
        receiver.setImageCoords(0,0,getWidth(),getHeight()); boolean ready=frameReady();
        int save=canvas.save(); if(!ready)canvas.clipRect(0,0,0,0);
        receiver.draw(canvas); canvas.restoreToCount(save);
        if(ready&&fallback().getVisibility()==VISIBLE)postVisual(receiver,attachment);
        else if(!ready&&fallback().getVisibility()!=VISIBLE)postVisual(receiver,attachment);
    }
    private void releaseReceiver() {
        documentId=0; visualPosted=false; attachment++;
        if(receiver!=null) { ImageReceiver old=receiver;receiver=null;old.setDelegate(null);old.stopAnimation();old.onDetachedFromWindow(); }
    }
    private void updatePlayback() {
        boolean active=attached&&motionAllowed()&&isShown()&&hasWindowFocus()&&getGlobalVisibleRect(visibleBounds);
        if(receiver!=null) {
            receiver.setAutoRepeat(active?1:0); receiver.setAllowStartAnimation(active);receiver.setAllowStartLottieAnimation(active);
            if(active)receiver.startAnimation();else receiver.stopAnimation();
        }
        if(active&&size>=72&&fallback().getVisibility()==VISIBLE)startFallbackMotion();else stopFallbackMotion();
    }
    private void startFallbackMotion() {
        if(fallbackMotion!=null)return; final ValueAnimator motion=fallbackMotion=ValueAnimator.ofFloat(0f,1f);
        motion.setDuration(2600);motion.setRepeatCount(ValueAnimator.INFINITE);motion.setInterpolator(new android.view.animation.LinearInterpolator());
        motion.addUpdateListener(v->{if(fallbackMotion!=motion)return;double phase=(Float)v.getAnimatedValue()*Math.PI*2;float lift=(1f-(float)Math.cos(phase))*.5f;
            View glyph=fallback();glyph.setTranslationY(-AndroidUtilities.dpf2(2.5f)*lift);glyph.setRotation(3f*(float)Math.sin(phase));glyph.setScaleX(1+.025f*lift);glyph.setScaleY(1+.025f*lift);});
        motion.start();
    }
    private void stopFallbackMotion() {
        if(fallbackMotion!=null){ValueAnimator old=fallbackMotion;fallbackMotion=null;old.cancel();}
        for(View glyph:new View[]{text,shield}){glyph.setTranslationY(0);glyph.setRotation(0);glyph.setScaleX(1);glyph.setScaleY(1);}
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(attached)updatePlayback();}
    @Override protected void onVisibilityChanged(View v,int state){super.onVisibilityChanged(v,state);if(attached)updatePlayback();}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);if(attached)updatePlayback();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;attachment++;
        NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.stickersDidLoad);getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        MediaDataController.getInstance(account).checkStickers(MediaDataController.TYPE_EMOJI);refresh();post(()->{if(attached)updateVisual();});}
    @Override protected void onDetachedFromWindow(){attached=false;NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.stickersDidLoad);
        if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        releaseReceiver();stopFallbackMotion();showFallback(true);super.onDetachedFromWindow();}
    @Override public void didReceivedNotification(int id,int account,Object...args){if(attached&&id==NotificationCenter.stickersDidLoad)refresh();}
}
