package app.nebulagram.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.BaseInputConnection;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.concurrent.*;

/** Latest-draft-only translation with reversible automatic insertion. */
public final class NebulaDraftTranslation {
    private static final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());
    private final BaseFragment host;
    private final EditText editor;
    private final View anchor;
    private final java.util.function.Consumer<CharSequence> apply;
    private Runnable pending;
    private NebulaTranslationClient client;
    private PopupWindow preview;
    private LinearLayout panel;
    private TextView title, detail, restore;
    private ProgressBar progress;
    private final NebulaDraftRequestGate gate = new NebulaDraftRequestGate();
    private final NebulaDraftOriginal original = new NebulaDraftOriginal();
    private long activeDialog;
    private int activeAccount;
    private long activeUser;
    private String identityPrefix = "", appliedPrefix = "", shownSource = "";
    private CharSequence shownAnswer;
    private boolean replacing;
    private String dismissedField;
    private final java.util.LinkedHashMap<String,CharSequence> cache = new java.util.LinkedHashMap<String,CharSequence>(16,.75f,true) {
        protected boolean removeEldestEntry(java.util.Map.Entry<String,CharSequence> entry) { return size() > 16; }
    };
    public NebulaDraftTranslation(BaseFragment host, EditText editor, View anchor, java.util.function.Consumer<CharSequence> apply) {
        this.host = host; this.editor = editor; this.anchor = anchor; this.apply = apply;
    }
    public void changed(int account, long dialog, boolean allowed) {
        if (replacing) return;
        long user = NebulaTasks.user(account);
        if (activeDialog != dialog || activeAccount != account || activeUser != user) { original.clear(); cache.clear(); appliedPrefix = ""; dismissedField = null; stop(); }
        activeDialog = dialog; activeAccount = account; activeUser = user;
        CharSequence source = NebulaRichText.snapshot(editor.getText());
        if (source.toString().equals(dismissedField)) return;
        dismissedField = null;
        if (source.length() == 0) original.clear();
        String language = NebulaTranslationSettings.draftLanguage(account, dialog);
        String connectionIdentity = NebulaTranslationSettings.translationIdentity();
        identityPrefix = connectionIdentity + ":" + user + ":" + dialog + ":" + language + ":";
        if (!allowed || !NebulaTranslationSettings.draft(account, dialog) || !NebulaTranslationSettings.translationAvailable()
            || user <= 0 || DialogObject.isEncryptedDialog(dialog) || dialog == 0 || source.toString().trim().isEmpty() || source.length() > 12000) { stop(); return; }
        if (original.isDisplayed(source) && identityPrefix.equals(appliedPrefix)) {
            show(source, source, null, false); return;
        }
        final String identity = identityPrefix + NebulaRichText.key(account, source);
        final long request = gate.begin(identity);
        if (request == 0) return;
        cancelTransport();
        final CharSequence input = original.source(source);
        final org.telegram.tgnet.TLRPC.TL_textWithEntities richInput = NebulaRichText.capture(account, input);
        final String cacheKey = identityPrefix + NebulaRichText.key(richInput);
        CharSequence cached = cache.get(cacheKey);
        if (cached != null) { complete(request, cached, source, input, null); return; }
        pending = new Runnable() {
            @Override public void run() {
                if (!gate.accepts(request)) return;
                // Leave an IME's live composing region and unfinished suggestion intact.
                if (composing()) { AndroidUtilities.runOnUIThread(this, 60); return; }
                pending = null;
                NebulaTranslationClient connection = client = new NebulaTranslationClient();
                show(null, source, null, true);
                worker.execute(() -> {
                    org.telegram.tgnet.TLRPC.TL_textWithEntities result = null; String failure = null;
                    try {
                        if (!gate.accepts(request) || !connectionIdentity.equals(NebulaTranslationSettings.translationIdentity())) return;
                        result = NebulaRichText.translate(connection, richInput, language, true, phase -> AndroidUtilities.runOnUIThread(() -> {
                            if (gate.accepts(request)) show(null, source, phase, true);
                        }));
                    } catch (Exception error) { failure = NebulaTranslationClient.errorText(error); }
                    final org.telegram.tgnet.TLRPC.TL_textWithEntities translated = result; final String errorText = failure;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!gate.accepts(request) || user != NebulaTasks.user(account) || !connectionIdentity.equals(NebulaTranslationSettings.translationIdentity())
                            || !NebulaRichText.key(account, source).equals(NebulaRichText.key(account, editor.getText())) || !NebulaTranslationSettings.draft(account, dialog)
                            || !language.equals(NebulaTranslationSettings.draftLanguage(account, dialog)) || !NebulaTranslationSettings.translationAvailable()) return;
                        client = null;
                        CharSequence answer = translated == null ? null : NebulaRichText.render(translated, editor.getPaint().getFontMetricsInt());
                        if (answer != null && answer.length() > 0) cache.put(cacheKey, answer);
                        complete(request, answer, source, input, errorText);
                    });
                });
            }
        };
        AndroidUtilities.runOnUIThread(pending, NebulaTranslationSettings.delay(account, dialog));
    }
    private boolean composing() { return BaseInputConnection.getComposingSpanStart(editor.getText()) >= 0; }
    private void complete(long request, CharSequence answer, CharSequence source, CharSequence input, String error) {
        if (!gate.accepts(request) || !NebulaRichText.key(activeAccount, source).equals(NebulaRichText.key(activeAccount, editor.getText()))) return;
        if (answer != null && !answer.toString().trim().isEmpty() && NebulaTranslationSettings.automatic(activeAccount, activeDialog)) {
            if (composing()) {
                pending = () -> complete(request, answer, source, input, error);
                AndroidUtilities.runOnUIThread(pending, 60); return;
            }
            if (!NebulaRichText.key(activeAccount, answer).equals(NebulaRichText.key(activeAccount, source))) {
                original.replaced(input, answer); appliedPrefix = identityPrefix;
                replace(answer); gate.suppress(identityPrefix + NebulaRichText.key(activeAccount, answer));
            }
            show(answer, answer, null, false);
        } else show(answer, source, error, false);
    }
    private void replace(CharSequence value) {
        int start = editor.getSelectionStart(), end = editor.getSelectionEnd(), length = editor.length();
        replacing = true;
        try {
            apply.accept(value);
            if (start == end && end == length) editor.setSelection(editor.length());
            else if (start >= 0 && end >= 0) editor.setSelection(Math.min(start, editor.length()), Math.min(end, editor.length()));
        } finally { replacing = false; }
    }
    private void useOrRestore() {
        CharSequence field = NebulaRichText.snapshot(editor.getText());
        if (shownAnswer != null && !NebulaRichText.key(activeAccount, shownAnswer).equals(NebulaRichText.key(activeAccount, field)) && shownSource.equals(field.toString())) {
            CharSequence value = shownAnswer; original.replaced(original.source(field), value); appliedPrefix = identityPrefix;
            cancelTransport(); gate.suppress(identityPrefix + NebulaRichText.key(activeAccount, value)); replace(value); show(value, value, null, false);
        } else if (original.hasOriginal()) {
            CharSequence value = original.restore(field); stop(); gate.suppress(identityPrefix + NebulaRichText.key(activeAccount, value)); original.clear(); appliedPrefix = ""; replace(value);
        }
    }
    private TextView action(String text, NebulaTheme theme) {
        TextView view = new TextView(anchor.getContext()); view.setText(text); view.setTextSize(14);
        view.setGravity(Gravity.CENTER); view.setTextColor(theme.primary()); view.setMinHeight(dp(44));
        view.setPadding(dp(12), 0, dp(12), 0); view.setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(theme.outline(), 1));
        return view;
    }
    private void createPanel(NebulaTheme theme) {
        panel = new LinearLayout(anchor.getContext()); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(12), dp(2), dp(4), dp(10));
        LinearLayout row = new LinearLayout(anchor.getContext()); row.setGravity(Gravity.CENTER_VERTICAL); panel.addView(row);
        progress = new ProgressBar(anchor.getContext()); progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(theme.primary()));
        row.addView(progress, new LinearLayout.LayoutParams(dp(18), dp(18)));
        title = action("", theme); title.setGravity(Gravity.CENTER_VERTICAL); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        title.setOnClickListener(v -> NebulaTranslationSettings.choose(host, NebulaTranslationSettings.draftLanguage(activeAccount, activeDialog), language -> {
            NebulaTranslationSettings.prefs(activeAccount).edit().putString("draft_language_" + activeDialog, language).apply();
            changed(activeAccount, activeDialog, true);
        }));
        title.setOnLongClickListener(v -> { stop(); host.presentFragment(new NebulaTranslationFragment(activeAccount, activeDialog)); return true; });
        restore = action("", theme); row.addView(restore, new LinearLayout.LayoutParams(-2, dp(44)));
        restore.setOnClickListener(v -> useOrRestore());
        TextView close = action("×", theme); close.setTextSize(24); close.setContentDescription(NebulaText.text("Закрыть перевод", "Close translation"));
        row.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        close.setOnClickListener(v -> { CharSequence field = NebulaRichText.snapshot(editor.getText()); dismissedField = field.toString(); stop(); gate.suppress(identityPrefix + NebulaRichText.key(activeAccount, field)); });
        detail = new TextView(anchor.getContext()); detail.setTextSize(14); detail.setTextColor(theme.onSurfaceVariant());
        detail.setMaxLines(2); detail.setEllipsize(TextUtils.TruncateAt.END); detail.setPadding(dp(2), 0, dp(10), 0); panel.addView(detail);
    }
    private void show(CharSequence answer, CharSequence source, String error, boolean loading) {
        if (!anchor.isAttachedToWindow() || host.getParentActivity() == null) return;
        NebulaTheme theme = NebulaTheme.of(anchor.getContext());
        if (panel == null) createPanel(theme);
        shownSource = source.toString(); shownAnswer = answer;
        title.setText("Nebula AI · " + NebulaTranslationSettings.draftLanguage(activeAccount, activeDialog).toUpperCase(java.util.Locale.ROOT));
        title.setContentDescription(NebulaText.text("Язык перевода. Удерживайте для настроек", "Translation language. Hold for settings"));
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        boolean canApply = !loading && answer != null && !NebulaRichText.key(activeAccount, answer).equals(NebulaRichText.key(activeAccount, source));
        restore.setText(canApply ? NebulaText.text("Применить", "Apply") : NebulaText.text("Оригинал", "Original"));
        restore.setVisibility(original.hasOriginal() || canApply ? View.VISIBLE : View.GONE);
        detail.setText(error != null ? error : loading ? NebulaText.text("Переводим…", "Translating…")
            : canApply ? answer : original.hasOriginal() ? original.restore(source) : answer != null ? answer : source);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{theme.modalSurface(), theme.surfaceContainer()});
        background.setCornerRadius(dp(20)); background.setStroke(dp(1), theme.outline()); panel.setBackground(background);
        int width = anchor.getWidth() - dp(16); if (width <= 0) return;
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = panel.getMeasuredHeight(), y = -anchor.getHeight() - height - dp(4);
        if (preview == null) {
            preview = new PopupWindow(panel, width, height, false); preview.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            preview.setElevation(dp(6)); preview.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); preview.setClippingEnabled(true);
            preview.showAsDropDown(anchor, dp(8), y);
            if (!NebulaGlass.reduced()) { panel.setAlpha(0); panel.setTranslationY(dp(3)); panel.animate().alpha(1).translationY(0).setDuration(120).start(); }
        } else preview.update(anchor, dp(8), y, width, height);
    }
    public void stop() {
        gate.cancel(); cancelTransport();
        if (preview != null) { preview.dismiss(); preview = null; }
        panel = null;
    }
    private void cancelTransport() {
        if (pending != null) { AndroidUtilities.cancelRunOnUIThread(pending); pending = null; }
        if (client != null) { client.cancel(); client = null; }
    }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
}
