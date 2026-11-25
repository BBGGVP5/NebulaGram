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
        private boolean dark;

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

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(action(context, theme, NebulaText.text("Сохранить", "Save"), false, this::save), new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1));
            View gap = new View(context);
            actions.addView(gap, new LinearLayout.LayoutParams(AndroidUtilities.dp(8), 1));
            actions.addView(action(context, theme, NebulaText.text("Поделиться", "Share"), false, this::share), new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1));
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
            actionsParams.topMargin = AndroidUtilities.dp(16);
            root.addView(actions, actionsParams);
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
            bubbles.removeAllViews();
            Theme.ResourcesProvider provider = dark ? new DarkThemeResourceProvider() : null;
            int account = chat.getCurrentAccount();
            for (int i = 0; i < source.size(); i++) {
                MessageObject original = source.get(i);
                // A fresh copy: the chat's own object keeps its layout and animation state.
                MessageObject copy = new MessageObject(account, original.messageOwner, true, true);
                ChatMessageCell cell = new ChatMessageCell(getContext(), account, false, null, provider);
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() { });
                cell.setFullyDraw(true);
                cell.setMessageObject(copy, null, i + 1 < source.size(), i > 0, false);
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

        /** The framed preview at twice its size, as a PNG in the cache directory, or null. */
        private File render() {
            int width = frame.getWidth(), height = frame.getHeight();
            if (width <= 0 || height <= 0) return null;
            float scale = Math.min(2f, 4096f / Math.max(width, height));
            Bitmap bitmap = null;
            try {
                bitmap = Bitmap.createBitmap(Math.round(width * scale), Math.round(height * scale), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(scale, scale);
                frame.draw(canvas);
                File file = new File(AndroidUtilities.getCacheDir(), "nebula_share_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                return file;
            } catch (Throwable error) {
                FileLog.e(error);
                return null;
            } finally {
                if (bitmap != null) bitmap.recycle();
            }
        }

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
