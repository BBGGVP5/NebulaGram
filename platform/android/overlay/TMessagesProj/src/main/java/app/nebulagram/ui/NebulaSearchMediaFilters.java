package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Media-type chips over the in-chat search panel. One filter at a time, because
 * messages.search takes a single MessagesFilter; it combines with "from user".
 * The choice belongs to one chat screen (account + classGuid), so another chat
 * searching on the same account never inherits it.
 */
public final class NebulaSearchMediaFilters {
    public static final int NONE = 0, MEDIA = 1, FILES = 2, LINKS = 3, MUSIC = 4, VOICE = 5, GIFS = 6;
    static final int[] ORDER = {MEDIA, FILES, LINKS, MUSIC, VOICE, GIFS};

    private static final SparseArray<int[]> selected = new SparseArray<>();

    private NebulaSearchMediaFilters() {}

    public static TLRPC.MessagesFilter filter(int account, int guid) {
        return create(type(account, guid));
    }

    public static boolean active(int account, int guid) {
        return type(account, guid) != NONE;
    }

    static int type(int account, int guid) {
        int[] state = selected.get(account);
        return state != null && state[0] == guid ? state[1] : NONE;
    }

    static void set(int account, int guid, int type) {
        selected.put(account, new int[]{guid, type});
    }

    static TLRPC.MessagesFilter create(int type) {
        switch (type) {
            case MEDIA: return new TLRPC.TL_inputMessagesFilterPhotoVideo();
            case FILES: return new TLRPC.TL_inputMessagesFilterDocument();
            case LINKS: return new TLRPC.TL_inputMessagesFilterUrl();
            case MUSIC: return new TLRPC.TL_inputMessagesFilterMusic();
            case VOICE: return new TLRPC.TL_inputMessagesFilterRoundVoice();
            case GIFS: return new TLRPC.TL_inputMessagesFilterGif();
            default: return new TLRPC.TL_inputMessagesFilterEmpty();
        }
    }

    static String title(int type) {
        switch (type) {
            case MEDIA: return NebulaText.text("Фото и видео", "Photos & videos");
            case FILES: return NebulaText.text("Файлы", "Files");
            case LINKS: return NebulaText.text("Ссылки", "Links");
            case MUSIC: return NebulaText.text("Музыка", "Music");
            case VOICE: return NebulaText.text("Голосовые", "Voice & round");
            case GIFS: return "GIF";
            default: return "";
        }
    }

    /** Adds the chip row to {@code host} and keeps it just above {@code panel} while the panel shows. */
    public static Strip attach(FrameLayout host, View panel, int account, int guid,
                               Theme.ResourcesProvider resources, Runnable search) {
        set(account, guid, NONE);
        Strip strip = new Strip(host.getContext(), panel, account, guid, resources, search);
        host.addView(strip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP | Gravity.LEFT));
        return strip;
    }

    public static final class Strip extends HorizontalScrollView {
        private final View panel;
        private final int account, guid;
        private final Theme.ResourcesProvider resources;
        private final Runnable search;
        private final LinearLayout row;
        private final int[] panelLocation = new int[2], hostLocation = new int[2];
        private final ViewTreeObserver.OnPreDrawListener follow = () -> { follow(); return true; };

        Strip(Context context, View panel, int account, int guid, Theme.ResourcesProvider resources, Runnable search) {
            super(context);
            this.panel = panel;
            this.account = account;
            this.guid = guid;
            this.resources = resources;
            this.search = search;
            setHorizontalScrollBarEnabled(false);
            setClipToPadding(false);
            setVisibility(GONE);
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            for (int type : ORDER) {
                TextView chip = new TextView(context);
                chip.setTag(type);
                chip.setText(title(type));
                chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                chip.setGravity(Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
                chip.setOnClickListener(v -> choose(type));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(32));
                params.gravity = Gravity.CENTER_VERTICAL;
                params.rightMargin = AndroidUtilities.dp(6);
                row.addView(chip, params);
            }
            paint();
        }

        private void choose(int type) {
            set(account, guid, type(account, guid) == type ? NONE : type);
            paint();
            search.run();
        }

        /** Clears the choice without searching, for when the search UI closes. */
        public void reset() {
            set(account, guid, NONE);
            paint();
        }

        private void paint() {
            int current = type(account, guid);
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resources);
            int surface = Theme.getColor(Theme.key_windowBackgroundWhite, resources);
            int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resources);
            for (int i = 0; i < row.getChildCount(); i++) {
                TextView chip = (TextView) row.getChildAt(i);
                boolean on = (Integer) chip.getTag() == current;
                GradientDrawable background = new GradientDrawable();
                background.setCornerRadius(AndroidUtilities.dp(16));
                background.setColor(on ? accent : (surface & 0x00ffffff) | 0xe6000000);
                if (!on) background.setStroke(AndroidUtilities.dp(1), (text & 0x00ffffff) | 0x1f000000);
                chip.setBackground(background);
                chip.setTextColor(on ? 0xffffffff : text);
                chip.setTypeface(on ? AndroidUtilities.bold() : null);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(follow);
        }

        @Override
        protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(follow);
            super.onDetachedFromWindow();
        }

        private void follow() {
            boolean shown = panel.isShown() && panel.getAlpha() > 0.01f && panel.getParent() != null;
            int visibility = shown ? VISIBLE : GONE;
            if (getVisibility() != visibility) setVisibility(visibility);
            if (!shown) return;
            panel.getLocationInWindow(panelLocation);
            ((View) getParent()).getLocationInWindow(hostLocation);
            float y = panelLocation[1] - hostLocation[1] - AndroidUtilities.dp(46);
            if (getTranslationY() != y) setTranslationY(y);
            float alpha = panel.getAlpha();
            if (getAlpha() != alpha) setAlpha(alpha);
        }
    }
}
