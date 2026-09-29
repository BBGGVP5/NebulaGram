package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/** Shared full-page/sheet conversation. Opening it never sends text. */
public final class NebulaAiChatView extends LinearLayout {
    private final NebulaTheme theme;
    private final NebulaAiConversation conversation = new NebulaAiConversation();
    private final ScrollView scroll;
    private final LinearLayout messages;
    private final EditText composer;
    private final ImageButton send;
    private final TextView status;
    private final TextView chatsButton;
    private final Runnable settings;
    private NebulaAiClient active;
    private Thread worker;
    private LinearLayout waitingRow;
    private TextView waitingText;
    private Runnable waitingTimer;
    private Pulse pulse;
    private boolean disposed;
    private int completed;
    private int activeProvider = -1;
    private static volatile Thread nanoInFlight;
    private TextView cancellationNotice;
    private String chatId;
    private boolean restoring;

    public NebulaAiChatView(Context context, String initial, Runnable settings) {
        super(context);
        this.settings = settings;
        theme = NebulaTheme.of(context);
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(8), dp(4), dp(8));
        setBackgroundColor(theme.opaqueSurface());
        LinearLayout bar = new LinearLayout(context); bar.setOrientation(VERTICAL);
        status = label("", 13, theme.onSurface());
        status.setTypeface(AndroidUtilities.bold());
        status.setMaxLines(2); status.setEllipsize(TextUtils.TruncateAt.END);
        status.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(shape(theme.surfaceContainer(), 18));
        status.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.msg_arrowright, 0);
        status.setCompoundDrawablePadding(dp(6));
        if (status.getCompoundDrawables()[2] != null) status.getCompoundDrawables()[2].mutate().setColorFilter(theme.primary(), PorterDuff.Mode.SRC_IN);
        status.setOnClickListener(v -> settings.run());
        status.setContentDescription(text("Выбранная модель. Изменить подключение", "Selected model. Change connection"));
        bar.addView(status, new LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(context); actions.setGravity(Gravity.CENTER_VERTICAL);
        chatsButton = control(text("Чаты", "Chats"));
        chatsButton.setBackground(shape(theme.surfaceContainer(), 18));
        chatsButton.setOnClickListener(v -> showChats());
        LayoutParams chatsParams = new LayoutParams(0, -2, 1); chatsParams.topMargin = dp(8);
        actions.addView(chatsButton, chatsParams);
        TextView reset = control(text("Новый чат", "New chat"));
        reset.setBackground(shape(theme.surfaceContainer(), 18));
        reset.setCompoundDrawablesWithIntrinsicBounds(R.drawable.msg_edit, 0, 0, 0);
        reset.setCompoundDrawablePadding(dp(6));
        if (reset.getCompoundDrawables()[0] != null) reset.getCompoundDrawables()[0].mutate().setColorFilter(theme.primary(), PorterDuff.Mode.SRC_IN);
        LayoutParams resetParams = new LayoutParams(0, -2, 1); resetParams.leftMargin = dp(8); resetParams.topMargin = dp(8);
        reset.setOnClickListener(v -> resetConversation());
        actions.addView(reset, resetParams); bar.addView(actions); addView(bar);
        scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        messages = new LinearLayout(context); messages.setOrientation(VERTICAL); messages.setPadding(dp(8), dp(16), dp(8), dp(16));
        scroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1));
        LinearLayout input = new LinearLayout(context); input.setGravity(Gravity.BOTTOM);
        input.setPadding(dp(12), dp(4), dp(6), dp(4)); input.setBackground(shape(theme.surfaceContainer(), 26));
        composer = new EditText(context); composer.setTextSize(16); composer.setTextColor(theme.onSurface());
        composer.setHintTextColor(theme.onSurfaceVariant()); composer.setHint(text("Напишите запрос…", "Write a prompt…"));
        composer.setBackgroundColor(Color.TRANSPARENT); composer.setPadding(dp(4), dp(10), dp(4), dp(10));
        composer.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setMaxLines(5); composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50000)});
        composer.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        composer.setText(initial); input.addView(composer, new LayoutParams(0, -2, 1));
        send = new ImageButton(context); send.setImageResource(R.drawable.msg_send); send.setColorFilter(theme.onPrimaryContainer());
        send.setBackground(shape(theme.primaryContainer(), 22)); send.setContentDescription(text("Отправить", "Send"));
        send.setOnClickListener(v -> { if (active != null) stop(); else send(); });
        LayoutParams sendParams = new LayoutParams(dp(44), dp(44)); sendParams.gravity = Gravity.BOTTOM; sendParams.bottomMargin = dp(2);
        input.addView(send, sendParams); addView(input, new LayoutParams(-1, -2));
        composer.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEND) { if (active == null) send(); return true; } return false; });
        TextView note = label(text("ИИ может ошибаться. Важное проверяйте.", "AI can make mistakes. Check important details."), 11, theme.onSurfaceVariant());
        note.setGravity(Gravity.CENTER); note.setPadding(0, dp(8), 0, 0); addView(note);
        restoreChat(NebulaAiChats.current()); refreshStatus();
    }
    private void resetConversation() {
        stop(); restoreChat(NebulaAiChats.fresh()); composer.setText("");
    }
    private void showChats() {
        java.util.ArrayList<NebulaAiChats.Chat> chats = NebulaAiChats.list();
        CharSequence[] names = new CharSequence[chats.size()];
        for (int i = 0; i < chats.size(); i++) {
            NebulaAiChats.Chat chat = chats.get(i);
            names[i] = (chat.id.equals(chatId) ? "✓  " : "")
                    + (chat.title.isEmpty() ? text("Новый чат", "New chat") : chat.title);
        }
        new NebulaDialog.Builder(getContext()).setTitle(text("Чаты Nebula AI", "Nebula AI chats"))
                .setItems(names, (dialog, which) -> {
                    stop(); restoreChat(NebulaAiChats.select(chats.get(which).id)); composer.setText("");
                }).show();
    }
    private void restoreChat(NebulaAiChats.Chat chat) {
        if (chat == null) { welcome(); return; }
        updateChatsLabel();
        chatId = chat.id; conversation.clear();
        conversation.select(chat.identity);
        completed = chat.turns.size();
        if (completed == 0) { welcome(); return; }
        messages.removeAllViews(); restoring = true;
        for (String[] turn : chat.turns) { conversation.add(turn[0], turn[1]); user(turn[0]); answer(turn[1]); }
        restoring = false; bottom();
    }
    private void updateChatsLabel() {
        int count = 0;
        for (NebulaAiChats.Chat chat : NebulaAiChats.list()) if (!chat.turns.isEmpty()) count++;
        chatsButton.setText(text("Чаты", "Chats") + (count == 0 ? "" : " · " + count));
    }
    public String draft() { return composer.getText().toString(); }
    public void refreshStatus() {
        NebulaAiChats.Chat selected = NebulaAiChats.current();
        if (selected != null && !selected.id.equals(chatId)) {
            if (active != null) stop();
            restoreChat(selected);
        } else updateChatsLabel();
        SharedPreferences p = getContext().getSharedPreferences("nebula_ai_settings", 0);
        int provider = p.getInt("provider", 0);
        String model = p.getString("model_" + provider, "").trim();
        if (provider == NebulaAiClient.NANO) {
            status.setText("Gemini Nano · " + (p.getBoolean("nano_preview", false) ? "Preview" : "Stable")
                    + " · " + (p.getBoolean("nano_fast", false) ? text("Быстрая", "Fast") : text("Полная", "Full")));
        } else {
            String name = provider == NebulaAiClient.CLAUDE ? "Claude" : provider == NebulaAiClient.GEMINI ? "Gemini"
                    : provider == NebulaAiClient.OPENAI ? "GPT" : text("Свой сервис", "Custom service");
            status.setText(name + " · " + (model.isEmpty() ? text("выберите модель", "choose a model") : model));
        }
    }
    private void welcome() {
        cancellationNotice = null;
        messages.removeAllViews();
        ImageView glyph = new ImageView(getContext()); glyph.setImageResource(R.drawable.nebula_ai_spark); glyph.setColorFilter(theme.primary());
        LayoutParams icon = new LayoutParams(dp(48), dp(48)); icon.topMargin = dp(20); icon.bottomMargin = dp(20); messages.addView(glyph, icon);
        TextView title = label(text("С чего начнём?", "Where shall we start?"), 28, theme.onSurface());
        title.setTypeface(AndroidUtilities.bold()); messages.addView(title);
        TextView hint = label(text("Задайте вопрос, разберите текст или придумайте что-нибудь вместе.", "Ask a question, work through a text, or create something together."), 15, theme.onSurfaceVariant());
        hint.setPadding(0, dp(12), 0, dp(24)); messages.addView(hint);
        for (String suggestion : new String[]{text("Объясни простыми словами", "Explain in simple terms"), text("Помоги написать текст", "Help me write"), text("Предложи идеи", "Suggest ideas")}) {
            TextView chip = control(suggestion + "  ↗"); chip.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            chip.setBackground(shape(theme.surfaceContainer(), 18));
            LayoutParams params = new LayoutParams(-1, -2); params.bottomMargin = dp(8); messages.addView(chip, params);
            chip.setOnClickListener(v -> { composer.setText(suggestion + " "); composer.setSelection(composer.length()); composer.requestFocus(); AndroidUtilities.showKeyboard(composer); });
        }
    }
    private TextView label(String value, int size, int color) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3), 1); return view;
    }
    private TextView control(String value) {
        TextView view = label(value, 13, theme.primary()); view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(12), dp(14), dp(12)); view.setMinHeight(dp(48)); return view;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private void bottom() { scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); }
    private void user(String value) {
        TextView view = label(value, 16, theme.onSurface()); view.setTextIsSelectable(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12)); view.setBackground(shape(theme.surfaceContainer(), 22));
        LayoutParams params = new LayoutParams(-2, -2); params.gravity = Gravity.END; params.leftMargin = dp(32); params.bottomMargin = dp(18);
        messages.addView(view, params);
    }
    private void answer(String raw) {
        TextView view = label("", 16, theme.onSurface()); view.setTextIsSelectable(true);
        NebulaAiMarkdown.Result parsed = NebulaAiMarkdown.parse(raw);
        SpannableStringBuilder rich = new SpannableStringBuilder(parsed.text);
        for (NebulaAiMarkdown.Mark mark : parsed.marks) {
            Object span = mark.kind == NebulaAiMarkdown.CODE ? new TypefaceSpan("monospace")
                    : new StyleSpan(mark.kind == NebulaAiMarkdown.ITALIC ? Typeface.ITALIC : Typeface.BOLD);
            rich.setSpan(span, mark.start, mark.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (mark.kind == NebulaAiMarkdown.HEADING) rich.setSpan(new RelativeSizeSpan(1.13f), mark.start, mark.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(rich); messages.addView(view, new LayoutParams(-1, -2));
        TextView copy = control(text("Копировать", "Copy"));
        copy.setBackground(shape(theme.surfaceContainer(), 18));
        copy.setCompoundDrawablesWithIntrinsicBounds(R.drawable.msg_copy, 0, 0, 0);
        copy.setCompoundDrawablePadding(dp(8));
        if (copy.getCompoundDrawables()[0] != null) copy.getCompoundDrawables()[0].mutate().setColorFilter(theme.primary(), PorterDuff.Mode.SRC_IN);
        copy.setOnClickListener(v -> { AndroidUtilities.addToClipboard(raw); NebulaHaptics.tick(v); copy.setText(text("Скопировано", "Copied")); });
        LayoutParams params = new LayoutParams(-2, -2); params.bottomMargin = dp(18); messages.addView(copy, params);
        if (!restoring && animations()) { view.setAlpha(0); view.setTranslationY(dp(8)); view.animate().alpha(1).translationY(0).setDuration(220).start(); }
        view.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        // Preserve the start of a long answer instead of jumping to its end.
        if (!restoring) scroll.post(() -> scroll.smoothScrollTo(0, view.getTop()));
    }
    private void waiting() {
        waitingRow = new LinearLayout(getContext()); waitingRow.setGravity(Gravity.CENTER_VERTICAL);
        pulse = new Pulse(getContext()); waitingRow.addView(pulse, new LayoutParams(dp(36), dp(36)));
        waitingText = label(text("Думаю…", "Thinking…"), 15, theme.onSurfaceVariant()); waitingText.setPadding(dp(12), 0, 0, 0);
        waitingText.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE); waitingRow.addView(waitingText);
        messages.addView(waitingRow); bottom();
        final long started = android.os.SystemClock.elapsedRealtime();
        waitingTimer = new Runnable() {
            @Override public void run() {
                if (waitingRow == null || waitingText == null || disposed) return;
                long seconds = (android.os.SystemClock.elapsedRealtime() - started) / 1000;
                waitingText.setText(activeProvider == NebulaAiClient.NANO
                        ? text("Обработка на устройстве · ", "On-device processing · ") + seconds + text(" с", " s")
                        : text("Ожидание ответа · ", "Waiting for response · ") + seconds + text(" с", " s"));
                waitingRow.postDelayed(this, 5000);
            }
        };
        waitingRow.postDelayed(waitingTimer, 5000);
    }
    private void finish() {
        active = null; worker = null;
        activeProvider = -1;
        if (waitingRow != null && waitingTimer != null) waitingRow.removeCallbacks(waitingTimer);
        waitingTimer = null; waitingText = null;
        if (pulse != null) pulse.stop();
        if (waitingRow != null) messages.removeView(waitingRow);
        waitingRow = null; pulse = null;
        send.setImageResource(R.drawable.msg_send); send.setContentDescription(text("Отправить", "Send"));
    }
    private void send() {
        if (disposed || active != null) return;
        final String message = composer.getText().toString().trim(); if (message.isEmpty()) return;
        if (!NebulaAiAvailability.available()) { settings.run(); return; }
        SharedPreferences p = getContext().getSharedPreferences("nebula_ai_settings", 0);
        final int provider = p.getInt("provider", 0);
        if (provider == NebulaAiClient.NANO && nanoInFlight != null && nanoInFlight.isAlive()) {
            Toast.makeText(getContext(), text("Предыдущий запрос ещё завершается на устройстве", "The previous on-device request is still finishing"), Toast.LENGTH_SHORT).show();
            return;
        }
        final String model = p.getString("model_" + provider, ""), endpoint = p.getString("endpoint", "https://api.openai.com/v1");
        final String instruction = p.getString("prompt", "");
        if (provider == NebulaAiClient.NANO && instruction.length() > 4000) {
            Toast.makeText(getContext(), text("Сократите инструкции до 4000 символов для Gemini Nano", "Shorten instructions to 4000 characters for Gemini Nano"), Toast.LENGTH_LONG).show(); settings.run(); return;
        }
        final String key;
        try { key = provider == NebulaAiClient.NANO ? "" : NebulaAiSecrets.read(provider); }
        catch (Exception e) { Toast.makeText(getContext(), text("Введите API-ключ заново", "Re-enter your API key"), Toast.LENGTH_SHORT).show(); settings.run(); return; }
        final String identity = provider + ":" + model + ":" + endpoint + ":" + p.getBoolean("nano_preview", false) + ":" + p.getBoolean("nano_fast", false);
        NebulaAiChats.Chat selectedChat = NebulaAiChats.current();
        if (selectedChat != null && !selectedChat.id.equals(chatId)) restoreChat(selectedChat);
        if (!selectedChat.identity.isEmpty() && !selectedChat.identity.equals(identity)) restoreChat(NebulaAiChats.fresh());
        conversation.select(identity);
        final String request;
        try { request = conversation.request(message, provider == NebulaAiClient.NANO ? 4000 : 49000); }
        catch (IllegalArgumentException e) { Toast.makeText(getContext(), text("Сократите сообщение для выбранной модели", "Shorten this message for the selected model"), Toast.LENGTH_LONG).show(); return; }
        if (completed == 0) messages.removeAllViews();
        cancellationNotice = null;
        while (messages.getChildCount() > 36) messages.removeViewAt(0);
        user(message); composer.setText(""); waiting();
        send.setImageResource(R.drawable.msg_close); send.setContentDescription(text("Остановить ответ", "Stop response"));
        final NebulaAiClient task = active = new NebulaAiClient();
        activeProvider = provider;
        worker = new Thread(() -> {
            try {
                final String result = task.generate(provider, endpoint, key, model, instruction, request);
                if (result.trim().isEmpty()) throw new IllegalStateException("EMPTY_RESPONSE");
                AndroidUtilities.runOnUIThread(() -> {
                    if (disposed || active != task) return;
                    finish(); completed++; conversation.add(message, result); answer(result);
                    NebulaAiChats.append(chatId, identity, message, result);
                    updateChatsLabel();
                    NebulaAiHistory.add(provider == NebulaAiClient.NANO ? "Gemini Nano" : model, message, result);
                });
            } catch (Exception e) {
                final String failure = failure(provider, e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (disposed || active != task) return;
                    finish(); completed++; answer(failure);
                    TextView retry = control(text("Изменить запрос", "Edit request")); messages.addView(retry);
                    retry.setOnClickListener(v -> { composer.setText(message); composer.setSelection(composer.length()); composer.requestFocus(); });
                });
            } finally {
                if (nanoInFlight == Thread.currentThread()) {
                    nanoInFlight = null;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!disposed && cancellationNotice != null) {
                            cancellationNotice.setText(text("Ответ остановлен. Можно отправить новый запрос.", "Response stopped. You can send another request."));
                            cancellationNotice = null;
                        }
                    });
                }
            }
        }, "NebulaAiChat");
        if (provider == NebulaAiClient.NANO) nanoInFlight = worker;
        worker.start();
    }
    private String failure(int provider, Exception e) {
        String value = e.getMessage() == null ? "" : e.getMessage();
        if (value.startsWith("GEMINI_NANO_DOWNLOAD_REQUIRED")) return text("Скачайте модель в настройках подключения.", "Download the model in connection settings.");
        if (value.startsWith("GEMINI_NANO_DOWNLOADING")) return text("Модель ещё скачивается. Статус — в настройках подключения.", "The model is downloading. Check connection settings for progress.");
        if ("EMPTY_RESPONSE".equals(value)) return text("Модель вернула пустой ответ. Попробуйте изменить запрос.", "The model returned an empty response. Try rephrasing your request.");
        if (provider == NebulaAiClient.NANO) return NebulaNanoAi.responseErrorText(e);
        return text("Не удалось получить ответ. Проверьте подключение, модель и лимиты провайдера.", "Could not get a response. Check the connection, model and provider limits.")
                + (value.matches("HTTP [0-9]{3}") ? " " + value : "");
    }
    private void stop() {
        if (active == null) return;
        NebulaAiClient cancelled = active;
        Thread running = worker;
        int provider = activeProvider;
        finish();
        cancelled.cancel();
        // AICore owns the in-flight inference. Interrupting Future.get and
        // closing its session immediately can tear it down while native work runs.
        if (provider != NebulaAiClient.NANO && running != null) running.interrupt();
        composer.requestFocus();
        boolean draining = provider == NebulaAiClient.NANO && running != null && running.isAlive();
        TextView stopped = label(draining
                ? text("Ответ скрыт. Модель завершает обработку; затем можно отправить новый запрос.", "Response hidden. The model is finishing; you can send a new request afterward.")
                : text("Ответ остановлен. Можно отправить новый запрос.", "Response stopped. You can send another request."), 14, theme.onSurfaceVariant());
        LayoutParams params = new LayoutParams(-1, -2); params.bottomMargin = dp(14); messages.addView(stopped, params);
        cancellationNotice = draining ? stopped : null;
    }
    public void dispose() { disposed = true; if (active != null) active.cancel(); if (worker != null && activeProvider != NebulaAiClient.NANO) worker.interrupt(); finish(); }
    private boolean animations() { return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled(); }
    private final class Pulse extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ValueAnimator animation; float phase;
        Pulse(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); update(); }
        @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
        @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused) update(); else stop(); }
        @Override protected void onVisibilityChanged(View view, int visibility) { super.onVisibilityChanged(view, visibility); if (isShown()) update(); else stop(); }
        void update() {
            if (!isAttachedToWindow() || !isShown() || !animations() || animation != null) return;
            animation = ValueAnimator.ofFloat(0, 1); animation.setDuration(1600); animation.setRepeatCount(ValueAnimator.INFINITE);
            animation.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); }); animation.start();
        }
        void stop() { ValueAnimator running = animation; animation = null; if (running != null) running.cancel(); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(), theme.primary(), 0xffbca5ff, Shader.TileMode.CLAMP));
            canvas.save(); canvas.rotate(phase * 90, cx, cy);
            float r = dp(11) * (1 + .14f * (float) Math.sin(phase * Math.PI * 2));
            Path path = new Path(); path.moveTo(cx, cy-r); path.quadTo(cx+2,cy-2,cx+r,cy); path.quadTo(cx+2,cy+2,cx,cy+r);
            path.quadTo(cx-2,cy+2,cx-r,cy); path.quadTo(cx-2,cy-2,cx,cy-r); path.close(); canvas.drawPath(path,paint); canvas.restore();
        }
    }
}
