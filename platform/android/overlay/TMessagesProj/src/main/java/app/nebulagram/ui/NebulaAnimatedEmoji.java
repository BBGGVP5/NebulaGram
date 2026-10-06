package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RLottieImageView;

/** Telegram animation when available, with a native emoji while its full document loads. */
public final class NebulaAnimatedEmoji extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final int account, size;
    private final TextView fallback;
    private final RLottieImageView animation;
    private final Rect visibleBounds = new Rect();
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::updatePlayback;
    private String emoji;
    private long documentId;
    private boolean attached;
    private ValueAnimator fallbackMotion;

    public NebulaAnimatedEmoji(Context context, int account, String emoji, int size) {
        super(context); this.account = account; this.size = size;
        fallback = new TextView(context); fallback.setGravity(Gravity.CENTER);
        fallback.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size * .72f);
        fallback.setTextColor(0xFFFFFFFF); fallback.setIncludeFontPadding(false);
        addView(fallback, new LayoutParams(-1, -1));
        animation = new RLottieImageView(context) {
            @Override protected void onLoaded() {
                // RLottieImageView reports a thumbnail too, before ImageReceiver installs it.
                post(() -> { if (attached) refresh(); });
            }
        };
        animation.setVisibility(INVISIBLE); addView(animation, new LayoutParams(-1, -1));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setEmoji(emoji);
    }
    public void setEmoji(String value) {
        emoji = value; documentId = 0;
        fallback.setVisibility(VISIBLE); animation.setVisibility(INVISIBLE);
        fallback.setText(Emoji.replaceEmoji(value, fallback.getPaint().getFontMetricsInt(), false));
        animation.clearAnimationDrawable(); if (attached) refresh();
    }
    private boolean motionAllowed() {
        return !NebulaGlass.reduced() && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
    }
    private void refresh() {
        TLRPC.Document document = MediaDataController.getInstance(account).getEmojiAnimatedSticker(emoji);
        if (document != null && documentId != document.id) {
            documentId = document.id; animation.setAutoRepeat(motionAllowed());
            animation.setAnimation(document, size, size);
        }
        org.telegram.messenger.ImageReceiver receiver = animation.getImageReceiver();
        boolean decoded = receiver != null && (receiver.getLottieAnimation() != null || receiver.getAnimation() != null);
        fallback.setVisibility(decoded ? GONE : VISIBLE);
        animation.setVisibility(decoded ? VISIBLE : INVISIBLE);
        updatePlayback();
    }
    private void updatePlayback() {
        boolean active = attached && motionAllowed() && isShown() && hasWindowFocus() && getGlobalVisibleRect(visibleBounds);
        org.telegram.messenger.ImageReceiver receiver = animation.getImageReceiver();
        if (receiver != null) {
            receiver.setAutoRepeat(active ? 1 : 0);
            receiver.setAllowStartAnimation(active); receiver.setAllowStartLottieAnimation(active);
        }
        if (active && animation.getVisibility() == VISIBLE) animation.playAnimation(); else animation.stopAnimation();
        // Not every Unicode emoji has a document in Telegram's animated set. The large
        // introduction still moves gently, using that same native glyph, while unavailable.
        if (active && size >= 72 && fallback.getVisibility() == VISIBLE) startFallbackMotion(); else stopFallbackMotion();
    }
    private void startFallbackMotion() {
        if (fallbackMotion != null) return;
        final ValueAnimator motion = ValueAnimator.ofFloat(0f, 1f); fallbackMotion = motion;
        motion.setDuration(2600); motion.setRepeatCount(ValueAnimator.INFINITE);
        motion.setInterpolator(new android.view.animation.LinearInterpolator());
        motion.addUpdateListener(value -> {
            if (fallbackMotion != motion) return;
            double phase = (Float) value.getAnimatedValue() * Math.PI * 2;
            float lift = (1f - (float) Math.cos(phase)) * .5f;
            fallback.setTranslationY(-AndroidUtilities.dpf2(2.5f) * lift);
            fallback.setRotation(3f * (float) Math.sin(phase));
            fallback.setScaleX(1f + .025f * lift); fallback.setScaleY(1f + .025f * lift);
        });
        motion.start();
    }
    private void stopFallbackMotion() {
        if (fallbackMotion != null) { ValueAnimator old = fallbackMotion; fallbackMotion = null; old.cancel(); }
        fallback.setTranslationY(0); fallback.setRotation(0); fallback.setScaleX(1); fallback.setScaleY(1);
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (attached) updatePlayback(); }
    @Override protected void onVisibilityChanged(View view, int visibility) { super.onVisibilityChanged(view, visibility); if (attached) updatePlayback(); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { super.onSizeChanged(w, h, oldW, oldH); if (attached) updatePlayback(); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true;
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.stickersDidLoad);
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        MediaDataController.getInstance(account).checkStickers(MediaDataController.TYPE_EMOJI); refresh();
    }
    @Override protected void onDetachedFromWindow() {
        attached = false; NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.stickersDidLoad);
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        animation.stopAnimation(); stopFallbackMotion(); super.onDetachedFromWindow();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) { if (attached && id == NotificationCenter.stickersDidLoad) refresh(); }
}
