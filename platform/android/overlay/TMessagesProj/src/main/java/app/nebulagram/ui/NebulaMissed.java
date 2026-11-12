package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;

import java.util.*;

/**
 * "What did I miss": an opt-in pill for chats with a long unread backlog. Only message text and
 * captions go to the selected AI connection; media, files and voice notes are never read or sent.
 */
public final class NebulaMissed {
    public static final String KEY = "missed_summary";
    public static boolean enabled() { return NebulaAiOptions.prefs().getBoolean(KEY, false); }

    private final ChatActivity chat;
    private final int account;
    private final long dialog, owner;
    private final int readMax, unread;
    private LinearLayout pill;
    private NebulaAiClient client;
    private int request;
    private boolean closed;

    private NebulaMissed(ChatActivity chat, int account, long dialog, int readMax, int unread) {
        this.chat = chat; this.account = account; this.dialog = dialog; this.readMax = readMax; this.unread = unread;
        owner = UserConfig.getInstance(account).getClientUserId();
    }

    /** Captures the unread state at open time, before Telegram starts marking the backlog read. */
    public static NebulaMissed attach(ChatActivity chat, FrameLayout content, int account, long dialog, boolean unsupported) {
        if (!enabled() || unsupported || chat == null || content == null || dialog == 0
                || DialogObject.isEncryptedDialog(dialog) || dialog == UserConfig.getInstance(account).getClientUserId()) return null;
        TLRPC.Dialog state = MessagesController.getInstance(account).dialogs_dict.get(dialog);
        if (state == null || state.unread_count < NebulaMissedPrompt.MIN_UNREAD) return null;
        NebulaMissed missed = new NebulaMissed(chat, account, dialog, state.read_inbox_max_id, state.unread_count);
        missed.build(content);
        return missed;
    }

    private void build(FrameLayout content) {
        Context c = content.getContext();
        Theme.ResourcesProvider resources = chat.getResourceProvider();
        int fg = Theme.getColor(Theme.key_chat_serviceText, resources);
        pill = new LinearLayout(c);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Theme.getColor(Theme.key_chat_serviceBackground, resources));
        pill.setBackground(bg);
        TextView label = new TextView(c);
        label.setTextColor(fg);
        label.setTextSize(13);
        label.setTypeface(AndroidUtilities.bold());
        label.setSingleLine(true);
        label.setText("✨ " + text("Кратко о пропущенном", "Catch me up") + " · " + unread);
        label.setPadding(dp(12), 0, dp(4), 0);
        pill.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32));
        TextView close = new TextView(c);
        close.setTextColor(fg);
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        close.setText("×");
        close.setContentDescription(text("Скрыть", "Hide"));
        close.setOnClickListener(v -> hide());
        pill.addView(close, LayoutHelper.createLinear(32, 32));
        pill.setOnClickListener(v -> open());
        pill.setContentDescription(text("Кратко о пропущенном: ", "Catch me up: ") + unread);
        content.addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
    }

    /** Called with the floating date position; the pill yields while the date chip is visible. */
    public static void position(NebulaMissed missed, float y, float dateAlpha) {
        if (missed == null || missed.pill == null) return;
        missed.pill.setTranslationY(y);
        missed.pill.setAlpha(Math.max(0f, 1f - dateAlpha));
    }

    private void hide() {
        closed = true;
        if (pill != null) pill.setVisibility(View.GONE);
    }

    public void destroy() {
        closed = true;
        if (client != null) client.cancel();
        if (request != 0) ConnectionsManager.getInstance(account).cancelRequest(request, true);
        request = 0;
    }

    static String providerLabel() {
        android.content.SharedPreferences p = NebulaAiOptions.prefs();
        int provider = p.getInt("provider", 0);
        if (provider == NebulaAiClient.NANO) return text("Gemini Nano на устройстве", "Gemini Nano on this device");
        NebulaAiServices.Service service = NebulaAiServices.find(NebulaAiServices.selected());
        String name = NebulaAiServices.providerName(provider);
        if (provider == NebulaAiClient.CUSTOM) {
            try { name = new java.net.URL(p.getString("endpoint", "")).getHost(); } catch (Exception ignored) { }
        }
        return service != null && !TextUtils.isEmpty(service.name) && !service.name.equals(name) ? name + " · " + service.name : name;
    }

    private void open() {
        if (closed || chat.getParentActivity() == null) return;
        if (!NebulaAiAvailability.available()) {
            android.widget.Toast.makeText(chat.getParentActivity(), text("Сначала подключите сервис ИИ", "Connect an AI service first"), android.widget.Toast.LENGTH_SHORT).show();
            chat.presentFragment(new NebulaAiSettingsFragment());
            return;
        }
        Context c = chat.getParentActivity();
        Theme.ResourcesProvider resources = chat.getResourceProvider();
        int primary = Theme.getColor(Theme.key_dialogTextBlack, resources), secondary = Theme.getColor(Theme.key_dialogTextGray3, resources);
        BottomSheet.Builder builder = new BottomSheet.Builder(c, false, resources);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(16));
        root.addView(label(c, text("Кратко о пропущенном", "What you missed"), 20, primary, true));
        TextView provider = label(c, text("Текст сообщений без медиа отправляется в: ", "Message text, no media, is sent to: ") + providerLabel(), 13, secondary, false);
        root.addView(provider, top(dp(4)));
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView status = label(c, text("Загружаю непрочитанные…", "Loading unread messages…"), 15, primary, false);
        body.addView(status);
        root.addView(body, top(dp(14)));
        TextView jump = label(c, text("К первому непрочитанному", "Go to first unread"), 15, Theme.getColor(Theme.key_featuredStickers_buttonText, resources), true);
        jump.setGravity(Gravity.CENTER);
        GradientDrawable jumpBg = new GradientDrawable();
        jumpBg.setCornerRadius(dp(12));
        jumpBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resources));
        jump.setBackground(jumpBg);
        jump.setVisibility(View.GONE);
        LinearLayout.LayoutParams jumpParams = new LinearLayout.LayoutParams(-1, dp(48));
        jumpParams.topMargin = dp(18);
        root.addView(jump, jumpParams);
        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        sheet.setOnDismissListener(d -> { if (client != null) client.cancel(); if (request != 0) ConnectionsManager.getInstance(account).cancelRequest(request, true); request = 0; });
        chat.showDialog(sheet);
        load(new ArrayList<>(), readMax + 1, messages -> {
            if (messages.isEmpty()) { status.setText(text("Непрочитанных текстовых сообщений не найдено.", "No unread text messages found.")); return; }
            int first = messages.get(0).id;
            jump.setVisibility(View.VISIBLE);
            jump.setOnClickListener(v -> { sheet.dismiss(); chat.scrollToMessageId(first, 0, true, 0, true, 0); });
            List<NebulaMissedPrompt.Item> items = new ArrayList<>();
            for (TLRPC.Message m : messages) items.add(new NebulaMissedPrompt.Item(sender(m), m.message));
            NebulaMissedPrompt.Request input = NebulaMissedPrompt.build(items);
            if (input.used == 0) { status.setText(text("Среди непрочитанных только медиа — пересказывать нечего.", "The unread messages are media only; nothing to summarize.")); return; }
            status.setText(text("Отправляю текст ", "Sending the text of ") + input.used + text(" сообщ. в ", " messages to ") + providerLabel() + "…");
            String language = LocaleController.getInstance().getCurrentLocale().getLanguage();
            client = NebulaAiReplacements.request(NebulaMissedPrompt.instruction(language), input.input, null, answer -> {
                if (closed || owner != UserConfig.getInstance(account).getClientUserId()) return;
                List<String> bullets = NebulaMissedPrompt.bullets(answer);
                body.removeAllViews();
                if (bullets.isEmpty()) bullets.add(answer.trim());
                for (String line : bullets) body.addView(label(c, "•  " + line, 15, primary, false), top(dp(6)));
                if (input.truncated) body.addView(label(c, text("Учтены первые ", "Covers the first ") + input.used + text(" из ", " of ") + input.total
                        + text(" текстовых сообщений.", " text messages."), 13, secondary, false), top(dp(10)));
            }, error -> {
                if (closed) return;
                status.setText(text("Не удалось получить сводку. Проверьте сервис и модель в настройках ИИ.", "Could not get a summary. Check the service and model in AI settings."));
            });
        }, () -> status.setText(text("Не удалось загрузить сообщения. Проверьте соединение.", "Could not load messages. Check your connection.")));
    }

    private interface Done { void accept(List<TLRPC.Message> messages); }

    /** Oldest unread first, at most {@link NebulaMissedPrompt#MAX_MESSAGES}, two history pages. */
    private void load(List<TLRPC.Message> collected, int from, Done done, Runnable failed) {
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(account).getInputPeer(dialog);
        req.offset_id = from; req.add_offset = -100; req.limit = 100;
        request = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            request = 0;
            if (closed || owner != UserConfig.getInstance(account).getClientUserId()) return;
            if (error != null || !(response instanceof TLRPC.messages_Messages)) { failed.run(); return; }
            TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
            MessagesController.getInstance(account).putUsers(result.users, false);
            MessagesController.getInstance(account).putChats(result.chats, false);
            int max = from;
            boolean added = false;
            for (TLRPC.Message m : result.messages) {
                if (m.id <= readMax || m instanceof TLRPC.TL_messageService || m instanceof TLRPC.TL_messageEmpty) continue;
                boolean dup = false;
                for (TLRPC.Message known : collected) if (known.id == m.id) { dup = true; break; }
                if (dup) continue;
                collected.add(m); added = true; max = Math.max(max, m.id + 1);
            }
            Collections.sort(collected, (a, b) -> Integer.compare(a.id, b.id));
            if (added && result.messages.size() >= 100 && collected.size() < NebulaMissedPrompt.MAX_MESSAGES) { load(collected, max, done, failed); return; }
            while (collected.size() > NebulaMissedPrompt.MAX_MESSAGES) collected.remove(collected.size() - 1);
            done.accept(collected);
        }));
    }

    private String sender(TLRPC.Message m) {
        MessagesController mc = MessagesController.getInstance(account);
        long peer = MessageObject.getPeerId(m.from_id != null ? m.from_id : m.peer_id);
        if (peer > 0) { TLRPC.User u = mc.getUser(peer); return u == null ? "" : UserObject.getUserName(u); }
        TLRPC.Chat chatPeer = mc.getChat(-peer);
        return chatPeer == null ? "" : chatPeer.title;
    }

    private static TextView label(Context c, CharSequence value, int size, int color, boolean bold) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }
    private static LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = margin;
        return params;
    }
}
