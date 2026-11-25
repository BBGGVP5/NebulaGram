package app.nebulagram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

/**
 * "Share it beautifully": the chosen message (or album) drawn as real bubbles on a gradient, ready
 * to send as a photo, save to the gallery or share. Setting "share_beautifully", off by default.
 */
public final class NebulaMessageImage {
    private NebulaMessageImage() { }
    public static final String KEY = "share_beautifully";
    public static final int OPTION = 7931;

    /** Pairs of colours, top-left to bottom-right; the night set is a darker version of each. */
    static final int[][] DAY = {
            {0xFF8FCB9B, 0xFFE3E7A1}, {0xFFB5E48C, 0xFF52B69A}, {0xFF89C2FF, 0xFFB8F2E6}, {0xFFC3B1E1, 0xFF9ADCFF},
            {0xFF56CCF2, 0xFF6FCF97}, {0xFFFFAFBD, 0xFFFFC3A0}, {0xFFE0AAFF, 0xFFFF9A9E}, {0xFFFFD86F, 0xFFFC6262}};

    public static boolean enabled() { return NebulaMessagePreferences.enabled(KEY, false); }

    /** 40 % of the colour kept: readable white bubbles in night mode, same hue. */
    static int night(int color) {
        int r = (int) (Color.red(color) * .4f), g = (int) (Color.green(color) * .4f), b = (int) (Color.blue(color) * .4f);
        return Color.argb(255, r, g, b);
    }

    static int[] colors(int index, boolean dark) {
        int[] pair = DAY[Math.floorMod(index, DAY.length)];
        return dark ? new int[]{night(pair[0]), night(pair[1])} : pair.clone();
    }

    /** At most this many messages: an album or one message. */
    static final int LIMIT = 10;

    static List<MessageObject> pick(MessageObject selected, MessageObject.GroupedMessages group) {
        ArrayList<MessageObject> out = new ArrayList<>();
        if (group != null && group.messages != null && !group.messages.isEmpty()) {
            for (MessageObject message : group.messages) if (message != null && out.size() < LIMIT) out.add(message);
        } else if (selected != null) out.add(selected);
        return out;
    }

    /** A detached copy of the message, so hiding its text never touches the chat. */
    static TLRPC.Message detached(TLRPC.Message message) {
        SerializedData out = null, in = null;
        try {
            out = new SerializedData(message.getObjectSize());
            message.serializeToStream(out);
            in = new SerializedData(out.toByteArray());
            TLRPC.Message copy = TLRPC.Message.TLdeserialize(in, in.readInt32(false), false);
            if (copy == null) return null;
            copy.dialog_id = message.dialog_id;
            copy.attachPath = message.attachPath;
            copy.out = message.out;
            return copy;
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        } finally {
            if (out != null) out.cleanup();
            if (in != null) in.cleanup();
        }
    }

    /** "Hide text": the whole text or caption under Telegram's own spoiler. */
    static void hideText(TLRPC.Message message) {
        if (message == null || message.message == null || message.message.isEmpty()) return;
        if (message.entities == null) message.entities = new ArrayList<>();
        for (int i = message.entities.size() - 1; i >= 0; i--) if (message.entities.get(i) instanceof TLRPC.TL_messageEntitySpoiler) message.entities.remove(i);
        TLRPC.TL_messageEntitySpoiler spoiler = new TLRPC.TL_messageEntitySpoiler();
        spoiler.offset = 0;
        spoiler.length = message.message.length();
        message.entities.add(spoiler);
    }

    /** Messages left in the picture: excluded ones out, but never all of them. */
    static List<Integer> kept(int count, java.util.Set<Integer> excluded) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; i < count; i++) if (!excluded.contains(i)) out.add(i);
        if (out.isEmpty() && count > 0) out.add(0);
        return out;
    }

    /** Sticker size: the longer side 512 px, as Telegram expects for webp stickers. */
    static int[] stickerSize(int width, int height) {
        if (width <= 0 || height <= 0) return new int[]{0, 0};
        float scale = 512f / Math.max(width, height);
        return new int[]{Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale))};
    }

    public static void show(ChatActivity chat, MessageObject selected, MessageObject.GroupedMessages group) {
        Activity activity = chat == null ? null : chat.getParentActivity();
        List<MessageObject> messages = pick(selected, group);
        if (activity == null || messages.isEmpty()) return;
        new Sheet(chat, activity, messages).show();
    }

    private static final class Sheet extends BottomSheet {
        private final ChatActivity chat;
        private final List<MessageObject> source;
        private final FrameLayout frame;
        private final LinearLayout bubbles;
        private final GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0, 0});
        private final ArrayList<View> swatches = new ArrayList<>();
        private int selected;
        private boolean dark, hideText, hideTime;
        private final java.util.HashSet<Integer> excluded = new java.util.HashSet<>();
        private TextView textChip, timeChip;

        Sheet(ChatActivity chat, Activity activity, List<MessageObject> messages) {
            super(activity, false, chat.getResourceProvider());
            this.chat = chat;
            this.source = messages;
            Context context = activity;
            NebulaTheme theme = NebulaTheme.of(context);
            dark = Theme.isCurrentThemeDark();
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int side = AndroidUtilities.dp(16);
            root.setPadding(side, AndroidUtilities.dp(12), side, AndroidUtilities.dp(16));

            FrameLayout head = new FrameLayout(context);
            TextView title = new TextView(context);
            title.setText(NebulaText.text("Поделиться красиво", "Share it beautifully"));
            title.setTextColor(theme.onSurface());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            head.addView(title, new FrameLayout.LayoutParams(-1, AndroidUtilities.dp(48), Gravity.CENTER));
            TextView night = new TextView(context);
            night.setText("☾");
            night.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            night.setGravity(Gravity.CENTER);
            night.setTextColor(theme.onSurface());
            night.setContentDescription(NebulaText.text("Ночной режим", "Night mode"));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(theme.surfaceContainer());
            night.setBackground(circle);
            night.setOnClickListener(v -> { dark = !dark; rebuild(); });
            head.addView(night, new FrameLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40), Gravity.END | Gravity.CENTER_VERTICAL));
            root.addView(head, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48)));

            frame = new FrameLayout(context);
            background.setCornerRadius(AndroidUtilities.dp(24));
            frame.setBackground(background);
            frame.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(14));
            bubbles = new LinearLayout(context);
            bubbles.setOrientation(LinearLayout.VERTICAL);
            frame.addView(bubbles, new FrameLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(-1, -2);
            frameParams.topMargin = AndroidUtilities.dp(8);
            root.addView(frame, frameParams);

            HorizontalScrollView scroller = new HorizontalScrollView(context);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < DAY.length; i++) {
                final int index = i;
                View swatch = new View(context);
                swatch.setOnClickListener(v -> { selected = index; rebuild(); });
                swatch.setContentDescription(NebulaText.text("Фон ", "Background ") + (i + 1));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), AndroidUtilities.dp(44));
                params.rightMargin = AndroidUtilities.dp(10);
                row.addView(swatch, params);
                swatches.add(swatch);
            }
            scroller.addView(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = AndroidUtilities.dp(14);
            root.addView(scroller, rowParams);

            LinearLayout chips = new LinearLayout(context);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            textChip = action(context, theme, "", false, () -> { hideText = !hideText; rebuild(); });
            timeChip = action(context, theme, "", false, () -> { hideTime = !hideTime; rebuild(); });
            chips.addView(textChip, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(40), 1));
            View chipGap = new View(context);
            chips.addView(chipGap, new LinearLayout.LayoutParams(AndroidUtilities.dp(8), 1));
            chips.addView(timeChip, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(40), 1));
            LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(-1, -2);
            chipsParams.topMargin = AndroidUtilities.dp(12);
            root.addView(chips, chipsParams);
            if (messages.size() > 1) {
                TextView hint = new TextView(context);
                hint.setText(NebulaText.text("Нажмите на сообщение, чтобы убрать его с картинки", "Tap a message to leave it out of the picture"));
                hint.setTextColor(theme.onSurfaceVariant());
                hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                hint.setGravity(Gravity.CENTER);
                root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            }

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(action(context, theme, NebulaText.text("Сохранить", "Save"), false, this::save), new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1));
            View gap = new View(context);
            actions.addView(gap, new LinearLayout.LayoutParams(AndroidUtilities.dp(8), 1));
            actions.addView(action(context, theme, NebulaText.text("Поделиться", "Share"), false, this::share), new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1));
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
            actionsParams.topMargin = AndroidUtilities.dp(16);
            root.addView(actions, actionsParams);
            LinearLayout as = new LinearLayout(context);
            as.setOrientation(LinearLayout.HORIZONTAL);
            as.addView(action(context, theme, NebulaText.text("Отправить файлом", "Send as file"), false, this::sendFile), new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1));
            View asGap = new View(context);
            as.addView(asGap, new LinearLayout.LayoutParams(AndroidUtilities.dp(8), 1));
            as.addView(action(context, theme, NebulaText.text("Отправить стикером", "Send as sticker"), false, this::sendSticker), new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1));
            LinearLayout.LayoutParams asParams = new LinearLayout.LayoutParams(-1, -2);
            asParams.topMargin = AndroidUtilities.dp(8);
            root.addView(as, asParams);
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(50));
            sendParams.topMargin = AndroidUtilities.dp(8);
            root.addView(action(context, theme, NebulaText.text("Отправить в этот чат", "Send to this chat"), true, this::send), sendParams);

            setCustomView(root);
            rebuild();
        }

        private TextView action(Context context, NebulaTheme theme, String label, boolean primary, Runnable run) {
            TextView button = new TextView(context);
            button.setText(label);
            button.setGravity(Gravity.CENTER);
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            button.setTypeface(AndroidUtilities.bold());
            button.setTextColor(primary ? Color.WHITE : theme.onSurface());
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(AndroidUtilities.dp(25));
            shape.setColor(primary ? theme.primary() : theme.surfaceContainer());
            button.setBackground(shape);
            button.setOnClickListener(v -> run.run());
            return button;
        }

        private void rebuild() {
            int[] pair = colors(selected, dark);
            background.setColors(pair);
            for (int i = 0; i < swatches.size(); i++) {
                GradientDrawable swatch = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors(i, dark));
                swatch.setShape(GradientDrawable.OVAL);
                if (i == selected) swatch.setStroke(AndroidUtilities.dp(3), NebulaTheme.of(getContext()).primary());
                swatches.get(i).setBackground(swatch);
            }
            if (textChip != null) textChip.setText((hideText ? "✓ " : "") + NebulaText.text("Скрыть текст", "Hide text"));
            if (timeChip != null) timeChip.setText((hideTime ? "✓ " : "") + NebulaText.text("Скрыть время", "Hide time"));
            bubbles.removeAllViews();
            Theme.ResourcesProvider provider = dark ? new DarkThemeResourceProvider() : null;
            int account = chat.getCurrentAccount();
            for (int i = 0; i < source.size(); i++) {
                MessageObject original = source.get(i);
                final int index = i;
                // A fresh copy: the chat's own object keeps its layout and animation state.
                TLRPC.Message owner = original.messageOwner;
                if (hideText) {
                    TLRPC.Message detached = detached(owner);
                    if (detached != null) { hideText(detached); owner = detached; }
                }
                MessageObject copy = new MessageObject(account, owner, true, true);
                ChatMessageCell cell = new ChatMessageCell(getContext(), account, false, null, provider);
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() { });
                cell.setFullyDraw(true);
                cell.nebulaHideTime = hideTime;
                cell.setMessageObject(copy, null, i + 1 < source.size(), i > 0, false);
                boolean out = excluded.contains(i);
                cell.setAlpha(out ? .3f : 1f);
                cell.setTag(out ? "excluded" : null);
                if (source.size() > 1) cell.setOnClickListener(v -> {
                    if (!excluded.remove(index)) { excluded.add(index); if (excluded.size() >= source.size()) excluded.remove(index); }
                    rebuild();
                });
                bubbles.addView(cell, new LinearLayout.LayoutParams(-1, -2));
            }
            TextView mark = new TextView(getContext());
            mark.setText("NebulaGram");
            mark.setTextColor(Color.WHITE);
            mark.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            mark.setTypeface(AndroidUtilities.bold());
            mark.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(6));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(AndroidUtilities.dp(16));
            pill.setColor(0x33000000);
            mark.setBackground(pill);
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            markParams.gravity = Gravity.CENTER_HORIZONTAL;
            markParams.topMargin = AndroidUtilities.dp(10);
            bubbles.addView(mark, markParams);
        }

        private File render() { return render(false); }

        /**
         * The framed preview at twice its size as a PNG, or for a sticker the bubbles alone on a
         * transparent background as a 512 px webp. Excluded messages are hidden while drawing.
         */
        private File render(boolean sticker) {
            int width = frame.getWidth(), height = frame.getHeight();
            if (width <= 0 || height <= 0) return null;
            ArrayList<View> hidden = new ArrayList<>();
            for (int i = 0; i < bubbles.getChildCount(); i++) {
                View child = bubbles.getChildAt(i);
                if ("excluded".equals(child.getTag()) || sticker && child instanceof TextView) { child.setVisibility(View.GONE); hidden.add(child); }
            }
            android.graphics.drawable.Drawable back = frame.getBackground();
            if (sticker) frame.setBackground(null);
            frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            frame.layout(frame.getLeft(), frame.getTop(), frame.getLeft() + width, frame.getTop() + frame.getMeasuredHeight());
            height = frame.getMeasuredHeight();
            Bitmap bitmap = null;
            try {
                int[] size = sticker ? stickerSize(width, height) : null;
                float scale = sticker ? size[0] / (float) width : Math.min(2f, 4096f / Math.max(width, height));
                bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(scale, scale);
                frame.draw(canvas);
                File file = new File(AndroidUtilities.getCacheDir(), "nebula_share_" + System.currentTimeMillis() + (sticker ? ".webp" : ".png"));
                try (FileOutputStream out = new FileOutputStream(file)) {
                    if (sticker) bitmap.compress(android.os.Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP, 92, out);
                    else bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                return file;
            } catch (Throwable error) {
                FileLog.e(error);
                return null;
            } finally {
                if (bitmap != null) bitmap.recycle();
                if (sticker) frame.setBackground(back);
                for (View child : hidden) child.setVisibility(View.VISIBLE);
                frame.requestLayout();
            }
        }

        private void sendDocument(boolean sticker) {
            File file = render(sticker);
            if (file == null) { failed(); return; }
            // A webp no larger than 512 px goes out as a sticker; a png document stays a file.
            SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(chat.getCurrentAccount()), file.getAbsolutePath(), file.getAbsolutePath(), null, null,
                    sticker ? "image/webp" : "image/png", chat.getDialogId(), null, null, null, null, null, true, 0, null, null, false);
            dismiss();
        }
        private void sendFile() { sendDocument(false); }
        private void sendSticker() { sendDocument(true); }

        private void failed() {
            Toast.makeText(getContext(), NebulaText.text("Не удалось создать картинку", "Could not create the image"), Toast.LENGTH_SHORT).show();
        }

        private void send() {
            File file = render();
            if (file == null) { failed(); return; }
            SendMessagesHelper.prepareSendingPhoto(AccountInstance.getInstance(chat.getCurrentAccount()), file.getAbsolutePath(), null, chat.getDialogId(),
                    null, null, null, null, null, null, null, 0, null, true, 0, 0, null);
            dismiss();
        }

        private void save() {
            File file = render();
            if (file == null) { failed(); return; }
            MediaController.saveFile(file.getAbsolutePath(), getContext(), 0, null, null);
            Toast.makeText(getContext(), NebulaText.text("Сохранено в галерею", "Saved to gallery"), Toast.LENGTH_SHORT).show();
        }

        private void share() {
            File file = render();
            if (file == null) { failed(); return; }
            try {
                Uri uri = FileProvider.getUriForFile(getContext(), ApplicationLoader.getApplicationId() + ".provider", file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("image/png");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContext().startActivity(Intent.createChooser(intent, NebulaText.text("Поделиться", "Share")));
            } catch (Exception error) {
                FileLog.e(error);
                failed();
            }
        }
    }
}
