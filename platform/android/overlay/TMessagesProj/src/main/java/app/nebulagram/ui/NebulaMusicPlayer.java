package app.nebulagram.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.palette.graphics.Palette;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieImageView;

/**
 * Material 3 Expressive layer over Telegram's AudioPlayerAlert. Telegram's sheet keeps playback,
 * the playlist and its menus; this layer only presents the current track and forwards actions.
 */
public final class NebulaMusicPlayer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private static final int BASE_TOP = 0xff2a2438, BASE_BOTTOM = 0xff120f18;

    private final BottomSheet sheet;
    private final int account;
    private final ActionBarMenuItem options, repeat;
    private final GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BASE_TOP, BASE_BOTTOM});
    private final LinearLayout content;
    private final FrameLayout stage;
    private final BackupImageView cover;
    private final NebulaLyricsView lyricsView;
    private final TextView title, author, time, duration, lyricsButton, returnButton;
    private final NebulaWaveSeekBar seekBar;
    private final ImageView play;
    private final PlayPauseDrawable playIcon = new PlayPauseDrawable(NebulaMediaPreferences.playerStyle() == NebulaMediaPreferences.STYLE_APPLE ? 44 : 30);
    private final GradientDrawable playShape = new GradientDrawable(), prevShape = new GradientDrawable(), nextShape = new GradientDrawable();
    private final RLottieImageView prev, next;
    private int top = BASE_TOP, bottom = BASE_BOTTOM, accent = 0xffe8def8, onAccent = 0xff1d192b;
    private ValueAnimator colors, shape;
    private float playRadius;
    private MessageObject track;
    private String trackKey;
    private boolean showingLyrics, playlist, released;
    private int lastSecond = -1;
    /** Вид Apple Music: размытая обложка фоном, тонкие шкалы, кнопки без подложек. */
    private final boolean apple = NebulaMediaPreferences.playerStyle() == NebulaMediaPreferences.STYLE_APPLE;
    private final NebulaAppleBackdrop backdrop = new NebulaAppleBackdrop();
    private NebulaAppleSlider slider, volume;
    private float dragStartY, dragStartX;
    private boolean draggingDown;

    public static NebulaMusicPlayer install(BottomSheet sheet, ViewGroup container, ActionBarMenuItem options, ActionBarMenuItem repeat, int account) {
        if (!NebulaMediaPreferences.expressivePlayer()) return null;
        NebulaMusicPlayer player = new NebulaMusicPlayer(container.getContext(), sheet, options, repeat, account);
        container.addView(player, new FrameLayout.LayoutParams(-1, -1));
        player.update();
        return player;
    }

    private NebulaMusicPlayer(Context context, BottomSheet sheet, ActionBarMenuItem options, ActionBarMenuItem repeat, int account) {
        super(context);
        this.sheet = sheet; this.options = options; this.repeat = repeat; this.account = account;
        setBackground(apple ? backdrop : background);
        backdrop.setCallback(this);
        backdrop.setMoving(!NebulaGlass.reduced() && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()));
        setClickable(true);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.statusBarHeight + AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(20));
        addView(content, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout header = new FrameLayout(context);
        ImageView collapse = icon(context, R.drawable.arrow_more, NebulaText.text("Свернуть", "Collapse"));
        collapse.setOnClickListener(v -> sheet.dismiss());
        header.addView(collapse, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, -12, 0, 0, 0));
        TextView heading = text(context, 14, true);
        heading.setText(NebulaText.text("Сейчас играет", "Now playing"));
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, LayoutHelper.createFrame(-1, -2, Gravity.CENTER, 56, 0, 56, 0));
        ImageView more = icon(context, R.drawable.ic_ab_other, NebulaText.text("Ещё", "More"));
        more.setOnClickListener(v -> { if (options != null && options.getVisibility() == VISIBLE) options.toggleSubMenu(); });
        header.addView(more, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, -12, 0));
        if (apple) {
            // Вместо заголовка — «ручка» листа; закрывается и смахиванием вниз.
            header.removeAllViews();
            View grabber = new View(context);
            grabber.setBackground(round(0x66ffffff, 3));
            header.addView(grabber, LayoutHelper.createFrame(40, 5, Gravity.CENTER));
            header.setOnClickListener(v -> sheet.dismiss());
            header.setContentDescription(NebulaText.text("Свернуть", "Collapse"));
        }
        content.addView(header, LayoutHelper.createLinear(-1, apple ? 36 : 52));

        stage = new FrameLayout(context) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, h);
                // The cover is the largest square that fits the free space.
                int side = Math.min(getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(16));
                LayoutParams params = (LayoutParams) cover.getLayoutParams();
                if (side > 0 && params.width != side) {
                    params.width = params.height = side;
                    super.onMeasure(w, h);
                }
            }
        };
        cover = new BackupImageView(context);
        cover.setRoundRadius(AndroidUtilities.dp(apple ? 14 : 28));
        cover.setBackground(round(0x33ffffff, apple ? 14 : 28));
        if (apple) { cover.setElevation(AndroidUtilities.dp(18)); cover.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(14)); }
        }); }
        cover.getImageReceiver().setDelegate((receiver, set, thumb, memCache) -> {
            if (set) { tint(receiver.getBitmap()); if (apple) backdrop.setCover(receiver.getBitmap()); }
        });
        stage.addView(cover, new FrameLayout.LayoutParams(AndroidUtilities.dp(280), AndroidUtilities.dp(280), Gravity.CENTER));
        lyricsView = new NebulaLyricsView(context);
        lyricsView.setAlpha(0f); lyricsView.setVisibility(GONE);
        lyricsView.setApple(apple);
        lyricsView.setOnSeek(ms -> {
            MessageObject current = MediaController.getInstance().getPlayingMessageObject();
            double length = current == null ? 0 : current.getDuration();
            if (length > 0) MediaController.getInstance().seekToProgress(current, (float) Math.min(1, ms / 1000.0 / length));
        });
        stage.addView(lyricsView, new FrameLayout.LayoutParams(-1, -1));
        content.addView(stage, LayoutHelper.createLinear(-1, 0, 1f, 0, 8, 0, 8));

        title = text(context, apple ? 22 : 24, true);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.MARQUEE); title.setMarqueeRepeatLimit(-1); title.setSelected(true);
        author = text(context, apple ? 20 : 16, false);
        author.setSingleLine(true); author.setEllipsize(TextUtils.TruncateAt.END); author.setAlpha(apple ? 0.6f : 0.72f);
        if (apple) {
            // Название и исполнитель слева, круглая «…» справа — как в Apple Music.
            FrameLayout info = new FrameLayout(context);
            LinearLayout names = new LinearLayout(context); names.setOrientation(LinearLayout.VERTICAL);
            names.addView(title, LayoutHelper.createLinear(-1, -2));
            names.addView(author, LayoutHelper.createLinear(-1, -2, 0, 1, 0, 0));
            info.addView(names, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL, 0, 0, 48, 0));
            ImageView dots = icon(context, R.drawable.ic_ab_other, NebulaText.text("Ещё", "More"));
            dots.setBackground(round(0x26ffffff, 16));
            dots.setOnClickListener(v -> { if (options != null && options.getVisibility() == VISIBLE) options.toggleSubMenu(); });
            info.addView(dots, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            content.addView(info, LayoutHelper.createLinear(-1, -2, 0, 16, 0, 14));
        } else {
            content.addView(title, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));
            content.addView(author, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 8));
        }

        seekBar = new NebulaWaveSeekBar(context);
        seekBar.setListener(new NebulaWaveSeekBar.Listener() {
            @Override public void onSeek(float progress) {
                MessageObject current = MediaController.getInstance().getPlayingMessageObject();
                if (current != null) MediaController.getInstance().seekToProgress(current, progress);
            }
            @Override public void onDrag(float progress) {
                if (track != null) time.setText(AndroidUtilities.formatShortDuration((int) (track.getDuration() * progress)));
            }
        });
        if (apple) {
            seekBar.setVisibility(GONE);
            slider = new NebulaAppleSlider(context);
            slider.setListener(new NebulaAppleSlider.Listener() {
                @Override public void onDrag(float value) {
                    if (track != null) { time.setText(AndroidUtilities.formatShortDuration((int) (track.getDuration() * value))); remaining(value); }
                }
                @Override public void onSeek(float value) {
                    MessageObject current = MediaController.getInstance().getPlayingMessageObject();
                    if (current != null) MediaController.getInstance().seekToProgress(current, value);
                }
            });
            content.addView(slider, LayoutHelper.createLinear(-1, 28));
        } else content.addView(seekBar, LayoutHelper.createLinear(-1, 40, -12, 0, -12, 0));
        FrameLayout times = new FrameLayout(context);
        time = text(context, 13, false); time.setAlpha(0.72f);
        duration = text(context, 13, false); duration.setAlpha(0.72f);
        times.addView(time, LayoutHelper.createFrame(-2, -2, Gravity.LEFT));
        times.addView(duration, LayoutHelper.createFrame(-2, -2, Gravity.RIGHT));
        if (apple) { time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12); duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12); time.setAlpha(.55f); duration.setAlpha(.55f); }
        content.addView(times, LayoutHelper.createLinear(-1, -2, 0, 0, 0, apple ? 8 : 16));

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER);
        prev = lottie(context, prevShape, false);
        prev.setOnClickListener(v -> { prev.playAnimation(); MediaController.getInstance().playPreviousMessage(); });
        play = new ImageView(context);
        play.setScaleType(ImageView.ScaleType.CENTER);
        play.setImageDrawable(playIcon);
        play.setBackground(playShape);
        play.setOnClickListener(v -> {
            MediaController controller = MediaController.getInstance();
            if (controller.isDownloadingCurrentMessage()) return;
            MessageObject current = controller.getPlayingMessageObject();
            if (controller.isMessagePaused()) controller.playMessage(current); else controller.pauseMessage(current);
        });
        next = lottie(context, nextShape, true);
        next.setOnClickListener(v -> { next.playAnimation(); MediaController.getInstance().playNextMessage(); });
        controls.addView(prev, LayoutHelper.createLinear(0, 64, 1f, 0, 0, 8, 0));
        controls.addView(play, LayoutHelper.createLinear(0, 88, 1.4f, 0, 0, 0, 0));
        controls.addView(next, LayoutHelper.createLinear(0, 64, 1f, 8, 0, 0, 0));
        if (apple) {
            // Крупные значки без подложек.
            prev.setBackground(null); next.setBackground(null); play.setBackground(null);
            prev.setAnimation(R.raw.player_prev, 34, 34); next.setAnimation(R.raw.player_prev, 34, 34);
            for (RLottieImageView view : new RLottieImageView[]{prev, next})
                for (String layer : new String[]{"Triangle 3", "Triangle 4", "Rectangle 4"}) view.setLayerColor(layer, 0xffffffff);
        }
        content.addView(controls, LayoutHelper.createLinear(-1, 88, 0, 0, 0, apple ? 8 : 16));
        if (apple) {
            // Громкость системы, как в Apple Music: тонкая шкала между значками.
            LinearLayout volumeRow = new LinearLayout(context);
            volumeRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView quiet = icon(context, R.drawable.msg_mute, null); quiet.setAlpha(.6f); quiet.setBackground(null);
            ImageView loud = icon(context, R.drawable.msg_unmute, null); loud.setAlpha(.6f); loud.setBackground(null);
            volume = new NebulaAppleSlider(context);
            final android.media.AudioManager audio = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            volume.setListener(new NebulaAppleSlider.Listener() {
                @Override public void onDrag(float value) { setVolume(audio, value); }
                @Override public void onSeek(float value) { setVolume(audio, value); }
            });
            volumeRow.addView(quiet, LayoutHelper.createLinear(28, 28));
            volumeRow.addView(volume, LayoutHelper.createLinear(0, 28, 1f, 8, 0, 8, 0));
            volumeRow.addView(loud, LayoutHelper.createLinear(28, 28));
            content.addView(volumeRow, LayoutHelper.createLinear(-1, 32, 0, 0, 0, 14));
            readVolume();
        }

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER);
        lyricsButton = chip(context, apple ? "" : NebulaText.text("Текст", "Lyrics"), apple ? R.drawable.menu_quote_specific : R.drawable.msg_photo_text_regular);
        lyricsButton.setOnClickListener(v -> toggleLyrics());
        TextView queue = chip(context, apple ? "" : NebulaText.text("Очередь", "Queue"), R.drawable.msg_list);
        queue.setOnClickListener(v -> setPlaylist(true));
        TextView order = chip(context, apple ? "" : NebulaText.text("Порядок", "Order"), R.drawable.player_new_order);
        order.setOnClickListener(v -> { if (repeat != null) repeat.toggleSubMenu(); });
        if (apple) {
            // Нижний ряд — только значки, разнесённые по ширине.
            lyricsButton.setContentDescription(NebulaText.text("Текст", "Lyrics"));
            queue.setContentDescription(NebulaText.text("Очередь", "Queue"));
            order.setContentDescription(NebulaText.text("Порядок", "Order"));
            for (TextView view : new TextView[]{lyricsButton, order, queue}) {
                view.setCompoundDrawablePadding(0); view.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
                view.setBackground(null);
                actions.addView(view, LayoutHelper.createLinear(0, 44, 1f));
                tintIcon(view, 0x99ffffff);
            }
        } else {
            actions.addView(lyricsButton, LayoutHelper.createLinear(-2, 40, 0, 0, 8, 0));
            actions.addView(queue, LayoutHelper.createLinear(-2, 40, 0, 0, 8, 0));
            actions.addView(order, LayoutHelper.createLinear(-2, 40));
        }
        content.addView(actions, LayoutHelper.createLinear(-1, 40));

        returnButton = chip(context, NebulaText.text("Плеер", "Player"), R.drawable.msg_filled_data_music);
        returnButton.setVisibility(GONE);
        returnButton.setOnClickListener(v -> setPlaylist(false));
        // Sits above Telegram's player panel (179 dp, 231 dp with the profile button).
        addView(returnButton, LayoutHelper.createFrame(-2, 40, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 16, 247));

        applyColors();
        setPlayShape(!MediaController.getInstance().isMessagePaused(), false);
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.messagePlayingDidStart);
        center.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        center.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        center.addObserver(this, NotificationCenter.messagePlayingDidReset);
    }

    private void remaining(float value) {
        if (!apple || track == null) return;
        int length = (int) track.getDuration();
        duration.setText(length > 0 ? "-" + AndroidUtilities.formatShortDuration(Math.max(0, length - (int) (length * value))) : "-:--");
    }
    private void setVolume(android.media.AudioManager audio, float value) {
        if (audio == null) return;
        int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
    }
    private void readVolume() {
        if (volume == null || volume.isDragging()) return;
        android.media.AudioManager audio = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        int max = Math.max(1, audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
        volume.setProgress(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / (float) max);
    }

    @Override protected boolean verifyDrawable(android.graphics.drawable.Drawable who) { return who == backdrop || super.verifyDrawable(who); }
    @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);
        if (apple && !playlist && !released && backdrop.animating()) postInvalidateOnAnimation();
    }

    /** Apple: лист смахивается вниз за любое свободное место. */
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!apple || playlist) return super.onInterceptTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: dragStartX = event.getX(); dragStartY = event.getY(); draggingDown = false; break;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getY() - dragStartY, dx = Math.abs(event.getX() - dragStartX);
                if (dy > AndroidUtilities.dp(16) && dy > dx * 2) { draggingDown = true; return true; }
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    /** True while the player covers Telegram's sheet, so the sheet must not treat taps as outside. */
    public boolean covering() { return !playlist && !released; }

    /** Keeps the layer under the status bar like Telegram's own full-size cover. */
    public void offsetTop(int topMargin) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params != null && params.topMargin != topMargin) params.topMargin = topMargin;
    }

    public void release() {
        if (released) return;
        released = true;
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.removeObserver(this, NotificationCenter.messagePlayingDidStart);
        center.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        center.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        center.removeObserver(this, NotificationCenter.messagePlayingDidReset);
        if (colors != null) colors.cancel();
        if (shape != null) shape.cancel();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (apple && draggingDown) {
            float dy = Math.max(0, event.getY() - dragStartY);
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) { setTranslationY(dy); return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                draggingDown = false;
                if (dy > AndroidUtilities.dp(120)) sheet.dismiss();
                else animate().translationY(0).setDuration(220).start();
                return true;
            }
        }
        return covering() && super.onTouchEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (playlist) {
            // Only the return chip is interactive while Telegram's queue is in front.
            float x = event.getX() - returnButton.getLeft(), y = event.getY() - returnButton.getTop();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && (x < 0 || y < 0 || x > returnButton.getWidth() || y > returnButton.getHeight())) return false;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (released) return;
        if (id == NotificationCenter.messagePlayingProgressDidChanged) progress();
        else if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            boolean playing = !MediaController.getInstance().isMessagePaused();
            playIcon.setPause(playing, true); setPlayShape(playing, true); seekBar.setPlaying(playing);
            float rest = apple ? 0.8f : 0.9f;
            cover.animate().scaleX(playing ? 1f : rest).scaleY(playing ? 1f : rest).setDuration(apple ? 520 : 320)
                .setInterpolator(apple && !NebulaGlass.reduced() ? new android.view.animation.OvershootInterpolator(1.4f) : new android.view.animation.DecelerateInterpolator()).start();
            update();
        } else update();
    }

    private void setPlaylist(boolean value) {
        playlist = value;
        content.setVisibility(value ? GONE : VISIBLE);
        setBackground(value ? null : apple ? backdrop : background);
        returnButton.setVisibility(value ? VISIBLE : GONE);
        setClickable(!value);
    }

    private void update() {
        MessageObject current = MediaController.getInstance().getPlayingMessageObject();
        if (current == null || !current.isMusic()) return;
        boolean playing = !MediaController.getInstance().isMessagePaused();
        playIcon.setPause(playing, false);
        seekBar.setPlaying(playing);
        float rest = apple ? 0.8f : 0.9f;
        cover.setScaleX(playing ? 1f : rest); cover.setScaleY(playing ? 1f : rest);
        String artist = current.getMusicAuthor(), name = current.getMusicTitle();
        int length = (int) current.getDuration();
        String key = current.getDialogId() + ":" + current.getId() + ":" + name + ":" + artist;
        title.setText(name); author.setText(artist);
        duration.setText(length > 0 ? AndroidUtilities.formatShortDuration(length) : "-:--");
        progress();
        readVolume();
        if (key.equals(trackKey)) return;
        track = current; trackKey = key;
        loadCover(current, artist, name, length);
        loadLyrics(artist, name, length);
    }

    private void progress() {
        MessageObject current = MediaController.getInstance().getPlayingMessageObject();
        if (current == null) return;
        seekBar.setProgress(current.audioProgress);
        if (slider != null) slider.setProgress(current.audioProgress);
        boolean dragging = slider != null ? slider.isDragging() : seekBar.isDragging();
        if (!dragging && current.audioProgressSec != lastSecond) {
            lastSecond = current.audioProgressSec;
            time.setText(AndroidUtilities.formatShortDuration(lastSecond));
            remaining(current.audioProgress);
        }
        lyricsView.setPosition((long) (current.audioProgress * current.getDuration() * 1000));
    }

    /**
     * Обложка в полном качестве. Мелкая превьюшка из Telegram (до 320 точек)
     * и встроенная в файл картинка ниже 600 точек идут только заглушкой, пока
     * грузится большая: из iTunes в 1200 точек или самая крупная копия файла.
     */
    private void loadCover(MessageObject current, String artist, String name, int length) {
        AudioInfo info = MediaController.getInstance().getAudioInfo();
        TLRPC.Document document = current.getDocument();
        TLRPC.PhotoSize small = document == null ? null : FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
        TLRPC.PhotoSize large = document == null ? null : FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 1280);
        if (!(large instanceof TLRPC.TL_photoSize) && !(large instanceof TLRPC.TL_photoSizeProgressive)) large = null;
        if (!(small instanceof TLRPC.TL_photoSize) && !(small instanceof TLRPC.TL_photoSizeProgressive)) small = large;
        final ImageLocation thumb = small != null ? ImageLocation.getForDocument(small, document) : null;
        final ImageLocation full = large != null ? ImageLocation.getForDocument(large, document) : null;
        android.graphics.Bitmap embedded = info != null ? info.getCover() : null;
        boolean sharpEmbedded = embedded != null && Math.min(embedded.getWidth(), embedded.getHeight()) >= 600;
        if (embedded != null) cover.setImageBitmap(embedded);
        if (!sharpEmbedded) {
            String artwork = current.getArtworkUrl(false);
            if (!TextUtils.isEmpty(artwork)) cover.setImage(ImageLocation.getForPath(artwork), null, thumb, null, null, 0, 1, current);
            else if (full != null) cover.setImage(full, null, thumb, null, null, 0, 1, current);
            else if (embedded == null) { cover.setImageDrawable(null); tint(null); }
        }
        if (sharpEmbedded || !NebulaMediaPreferences.appleCovers()) return;
        final String key = trackKey;
        // Animated Apple Music artwork needs a developer token; the iTunes Search
        // API gives the store's static artwork at full resolution instead.
        NebulaMusicService.cover(artist, name, length, url -> {
            if (released || url == null || !key.equals(trackKey)) return;
            cover.setImage(ImageLocation.getForPath(url), null, thumb, null, null, 0, 1, current);
        });
    }

    private void loadLyrics(String artist, String name, int length) {
        lyricsButton.setVisibility(NebulaMediaPreferences.lyrics() ? VISIBLE : GONE);
        if (!NebulaMediaPreferences.lyrics()) { if (showingLyrics) toggleLyrics(); return; }
        lyricsView.setLyrics(null, NebulaText.text("Ищем текст…", "Looking for lyrics…"));
        final String key = trackKey;
        NebulaMusicService.lyrics(artist, name, length, lrc -> {
            if (released || !key.equals(trackKey)) return;
            lyricsView.setLyrics(lrc, lrc == null ? NebulaText.text("Текст для этого трека не найден на lrclib.net", "No lyrics for this track on lrclib.net") : null);
            progress();
        });
    }

    private void toggleLyrics() {
        showingLyrics = !showingLyrics;
        View in = showingLyrics ? lyricsView : cover, out = showingLyrics ? cover : lyricsView;
        in.setVisibility(VISIBLE);
        in.animate().alpha(1f).setDuration(220).start();
        out.animate().alpha(0f).setDuration(220).withEndAction(() -> { if (showingLyrics == (out == cover)) out.setVisibility(GONE); }).start();
        lyricsButton.setSelected(showingLyrics);
        applyChip(lyricsButton);
    }

    private void tint(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) { animateTo(BASE_TOP, BASE_BOTTOM, 0xffe8def8); return; }
        Bitmap source = bitmap;
        try {
            Palette.from(source).maximumColorCount(16).generate(palette -> {
                if (released || palette == null) return;
                int base = palette.getDarkVibrantColor(palette.getDarkMutedColor(palette.getDominantColor(BASE_TOP)));
                int light = palette.getLightVibrantColor(palette.getVibrantColor(palette.getLightMutedColor(0xffe8def8)));
                if (apple) backdrop.setPalette(palette.getVibrantColor(base), palette.getMutedColor(base), palette.getDominantColor(base));
                animateTo(shade(base, 0.62f), shade(base, 0.22f), lift(light));
            });
        } catch (Exception ignore) {
            // A hardware bitmap cannot be sampled; keep the current colours.
        }
    }
    private static int shade(int color, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.min(hsv[2], value);
        return Color.HSVToColor(hsv);
    }
    private static int lift(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(hsv[1], 0.45f);
        hsv[2] = Math.max(hsv[2], 0.88f);
        return Color.HSVToColor(hsv);
    }
    private void animateTo(int newTop, int newBottom, int newAccent) {
        if (colors != null) colors.cancel();
        int fromTop = top, fromBottom = bottom, fromAccent = accent;
        ArgbEvaluator evaluator = new ArgbEvaluator();
        colors = ValueAnimator.ofFloat(0, 1);
        colors.setDuration(NebulaGlass.reduced() ? 0 : 650);
        colors.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            top = (int) evaluator.evaluate(t, fromTop, newTop);
            bottom = (int) evaluator.evaluate(t, fromBottom, newBottom);
            accent = (int) evaluator.evaluate(t, fromAccent, newAccent);
            onAccent = shade(newTop, 0.18f);
            applyColors();
        });
        colors.start();
    }
    private void applyColors() {
        background.setColors(new int[]{top, bottom});
        playShape.setColor(accent);
        playIcon.setColorFilter(new PorterDuffColorFilter(apple ? 0xffffffff : onAccent, PorterDuff.Mode.SRC_IN));
        int tonal = (accent & 0x00ffffff) | 0x33000000;
        prevShape.setColor(tonal); nextShape.setColor(tonal);
        seekBar.setColors(accent, 0x40ffffff);
        lyricsView.setColor(0xffffffff);
        applyChip(lyricsButton);
        if (returnButton != null) { returnButton.setBackground(round(accent, 20)); returnButton.setTextColor(onAccent); tintIcon(returnButton, onAccent); }
    }
    private void applyChip(TextView chip) {
        if (chip == null) return;
        boolean on = chip.isSelected();
        if (apple) {
            chip.setBackground(on ? round(0x33ffffff, 22) : null);
            tintIcon(chip, on ? 0xffffffff : 0x99ffffff);
            return;
        }
        chip.setBackground(round(on ? accent : 0x26ffffff, 20));
        chip.setTextColor(on ? onAccent : 0xffffffff);
        tintIcon(chip, on ? onAccent : 0xffffffff);
    }
    private static void tintIcon(TextView view, int color) {
        android.graphics.drawable.Drawable[] icons = view.getCompoundDrawablesRelative();
        if (icons[0] != null) icons[0].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }

    /** Expressive shape morph: a rounded square while paused, a pill while playing. */
    private void setPlayShape(boolean playing, boolean animated) {
        float target = AndroidUtilities.dp(playing ? 44 : 26);
        if (shape != null) shape.cancel();
        if (!animated || NebulaGlass.reduced()) { playRadius = target; playShape.setCornerRadius(target); return; }
        shape = ValueAnimator.ofFloat(playRadius, target);
        shape.setDuration(380);
        shape.setInterpolator(new android.view.animation.OvershootInterpolator(1.6f));
        shape.addUpdateListener(a -> { playRadius = (float) a.getAnimatedValue(); playShape.setCornerRadius(Math.max(0, playRadius)); });
        shape.start();
    }

    private RLottieImageView lottie(Context context, GradientDrawable shape, boolean mirrored) {
        RLottieImageView view = new RLottieImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setAnimation(R.raw.player_prev, 26, 26);
        for (String layer : new String[]{"Triangle 3", "Triangle 4", "Rectangle 4"}) view.setLayerColor(layer, 0xffffffff);
        if (mirrored) view.setRotation(180f);
        shape.setCornerRadius(AndroidUtilities.dp(32));
        view.setBackground(shape);
        view.setContentDescription(mirrored ? NebulaText.text("Следующий", "Next") : NebulaText.text("Предыдущий", "Previous"));
        return view;
    }
    private static ImageView icon(Context context, int resource, String description) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(resource);
        view.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
        view.setContentDescription(description);
        view.setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(0x33ffffff, 1, AndroidUtilities.dp(22)));
        return view;
    }
    private static TextView text(Context context, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setTextColor(0xffffffff);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }
    private static TextView chip(Context context, String label, int resource) {
        TextView view = text(context, 14, true);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(16), 0);
        android.graphics.drawable.Drawable icon = context.getResources().getDrawable(resource).mutate();
        icon.setBounds(0, 0, AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        view.setCompoundDrawablesRelative(icon, null, null, null);
        view.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        view.setBackground(round(0x26ffffff, 20));
        return view;
    }
    private static GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(radius));
        return drawable;
    }
}
