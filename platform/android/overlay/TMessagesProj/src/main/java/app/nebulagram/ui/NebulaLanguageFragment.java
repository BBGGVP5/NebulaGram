package app.nebulagram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/** A Nebula-styled language picker that still uses Telegram's locale engine. */
public final class NebulaLanguageFragment extends BaseFragment
        implements NotificationCenter.NotificationCenterDelegate {

    private final ArrayList<LocaleController.LocaleInfo> languages = new ArrayList<>();
    private LinearLayout languageRows;
    private EditText searchField;

    @Override
    public boolean onFragmentCreate() {
        refreshLanguages();
        LocaleController.getInstance().loadRemoteLanguages(currentAccount, false);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
        languageRows = null;
        searchField = null;
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(NebulaText.text("Язык приложения", "App language"));
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(theme.surface());

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = label(context, NebulaText.text("Выбери язык", "Choose a language"),
                24, theme.onSurface(), true);
        content.addView(heading, matchWrap());
        TextView description = label(context,
                NebulaText.text("Язык интерфейса изменится сразу после выбора.",
                        "The interface language changes as soon as you choose one."),
                14, theme.onSurfaceVariant(), false);
        description.setLineSpacing(AndroidUtilities.dp(2), 1f);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = AndroidUtilities.dp(6);
        descriptionParams.bottomMargin = AndroidUtilities.dp(18);
        content.addView(description, descriptionParams);

        content.addView(NebulaCard.header(context, NebulaText.text("СЕЙЧАС ИСПОЛЬЗУЕТСЯ", "CURRENT LANGUAGE")));
        NebulaCard currentCard = new NebulaCard(context);
        LocaleController.LocaleInfo selected = LocaleController.getInstance().getCurrentLocaleInfo();
        currentCard.add(new NebulaRow(context).title(displayName(selected))
                .subtitle(englishName(selected), false));
        content.addView(currentCard);

        LinearLayout search = searchInput(context, theme);
        EditText filter = (EditText) search.getTag();
        searchField = filter;
        LinearLayout.LayoutParams filterParams = matchWrap();
        filterParams.topMargin = AndroidUtilities.dp(20);
        filterParams.bottomMargin = AndroidUtilities.dp(8);
        content.addView(search, filterParams);
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderLanguages();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        content.addView(NebulaCard.header(context, NebulaText.text("ВСЕ ЯЗЫКИ", "ALL LANGUAGES")));
        NebulaCard listCard = new NebulaCard(context);
        languageRows = new LinearLayout(context);
        languageRows.setOrientation(LinearLayout.VERTICAL);
        listCard.add(languageRows);
        content.addView(listCard);
        renderLanguages();
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }

    private LinearLayout searchInput(Context context, NebulaTheme theme) {
        LinearLayout field = new LinearLayout(context);
        field.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(AndroidUtilities.dp(16));
        background.setColor(theme.surfaceContainer());
        background.setStroke(AndroidUtilities.dp(1), theme.outline());
        field.setBackground(background);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_search);
        icon.setColorFilter(theme.onSurfaceVariant(), PorterDuff.Mode.SRC_IN);
        field.addView(icon, new LinearLayout.LayoutParams(AndroidUtilities.dp(22), AndroidUtilities.dp(22)));
        icon.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(22), AndroidUtilities.dp(22)));
        ((LinearLayout.LayoutParams) icon.getLayoutParams()).setMarginStart(AndroidUtilities.dp(14));
        ((LinearLayout.LayoutParams) icon.getLayoutParams()).setMarginEnd(AndroidUtilities.dp(8));

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTextColor(theme.onSurface());
        input.setHintTextColor(theme.onSurfaceVariant());
        input.setHint(NebulaText.text("Найти язык", "Search languages"));
        input.setPadding(0, AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setSelectAllOnFocus(false);
        field.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        field.setFocusableInTouchMode(false);
        field.setOnClickListener(v -> input.requestFocus());
        input.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable focusedBackground = new GradientDrawable();
            focusedBackground.setCornerRadius(AndroidUtilities.dp(16));
            focusedBackground.setColor(theme.surfaceContainer());
            focusedBackground.setStroke(AndroidUtilities.dp(focused ? 2 : 1),
                    focused ? theme.primary() : theme.outline());
            field.setBackground(focusedBackground);
        });
        field.setTag(input);
        return field;
    }

    private void renderLanguages() {
        if (languageRows == null) return;
        languageRows.removeAllViews();
        LocaleController.LocaleInfo current = LocaleController.getInstance().getCurrentLocaleInfo();
        String query = searchField == null || searchField.getText() == null
                ? "" : searchField.getText().toString().trim().toLowerCase(LocaleController.getInstance().getCurrentLocale());
        NebulaTheme theme = NebulaTheme.of(languageRows.getContext());
        int count = 0;
        for (LocaleController.LocaleInfo info : languages) {
            if (!matches(info, query)) continue;
            boolean selected = sameLanguage(info, current);
            NebulaRow row = new NebulaRow(languageRows.getContext())
                    .title(displayName(info))
                    .subtitle(englishName(info), false)
                    .selection(selected);
            if (selected) {
                row.badge("✓", theme.primary());
                GradientDrawable highlight = new GradientDrawable();
                highlight.setColor(NebulaTheme.stateLayer(theme.primary(), 0.10f));
                highlight.setCornerRadius(AndroidUtilities.dp(14));
                highlight.setStroke(AndroidUtilities.dp(1), NebulaTheme.stateLayer(theme.primary(), 0.42f));
                row.setBackground(new InsetDrawable(highlight,
                        AndroidUtilities.dp(5), AndroidUtilities.dp(3), AndroidUtilities.dp(5), AndroidUtilities.dp(3)));
                row.setContentDescription(displayName(info) + NebulaText.text(", выбрано", ", selected"));
            }
            row.setOnClickListener(v -> applyLanguage(info));
            languageRows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            count++;
        }
        if (count == 0) {
            TextView empty = label(languageRows.getContext(),
                    NebulaText.text("Ничего не найдено", "No languages found"),
                    14, theme.onSurfaceVariant(), false);
            empty.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(20),
                    AndroidUtilities.dp(16), AndroidUtilities.dp(20));
            languageRows.addView(empty, matchWrap());
        }
    }

    private boolean matches(LocaleController.LocaleInfo info, String query) {
        if (query.isEmpty()) return true;
        return contains(info.name, query) || contains(info.nameEnglish, query)
                || contains(info.shortName, query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(LocaleController.getInstance().getCurrentLocale()).contains(query);
    }

    private void refreshLanguages() {
        languages.clear();
        HashSet<String> seen = new HashSet<>();
        LocaleController controller = LocaleController.getInstance();
        for (LocaleController.LocaleInfo info : controller.languages) addLanguage(info, seen);
        for (LocaleController.LocaleInfo info : controller.unofficialLanguages) addLanguage(info, seen);
        LocaleController.LocaleInfo current = controller.getCurrentLocaleInfo();
        Collections.sort(languages, (first, second) -> {
            if (sameLanguage(first, current)) return sameLanguage(second, current) ? 0 : -1;
            if (sameLanguage(second, current)) return 1;
            if (first.serverIndex != second.serverIndex) return Integer.compare(first.serverIndex, second.serverIndex);
            return displayName(first).compareToIgnoreCase(displayName(second));
        });
    }

    private void addLanguage(LocaleController.LocaleInfo info, HashSet<String> seen) {
        if (info == null) return;
        String key = info.getKey();
        if (key == null) key = info.shortName;
        if (key == null || seen.add(key)) languages.add(info);
    }

    private boolean sameLanguage(LocaleController.LocaleInfo first, LocaleController.LocaleInfo second) {
        if (first == second) return true;
        return first != null && second != null && !TextUtils.isEmpty(first.shortName)
                && first.shortName.equalsIgnoreCase(second.shortName);
    }

    private String displayName(LocaleController.LocaleInfo info) {
        if (info == null) return NebulaText.text("Системный язык", "System language");
        if (!TextUtils.isEmpty(info.name)) return info.name;
        return TextUtils.isEmpty(info.shortName) ? "" : info.shortName;
    }

    private String englishName(LocaleController.LocaleInfo info) {
        if (info == null) return "";
        if (!TextUtils.isEmpty(info.nameEnglish) && !info.nameEnglish.equals(info.name)) {
            return info.nameEnglish + (TextUtils.isEmpty(info.shortName) ? "" : "  ·  " + info.shortName);
        }
        return TextUtils.isEmpty(info.shortName) ? "" : info.shortName;
    }

    private void applyLanguage(LocaleController.LocaleInfo info) {
        LocaleController.LocaleInfo previous = LocaleController.getInstance().getCurrentLocaleInfo();
        if (sameLanguage(info, previous)) return;

        try {
            AlertDialog progress = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progress.showDelayed(450);
            getMessagesController().getTranslateController().reset();
            int requestId = LocaleController.getInstance().applyLanguage(info, true, false, false,
                    true, currentAccount, () -> {
                        progress.dismiss();
                        AndroidUtilities.runOnUIThread(this::finishFragment, 10);
                    });
            if (requestId != 0) {
                progress.setOnCancelListener(dialog ->
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true));
            }

            String languageCode = info.pluralLangCode;
            String previousCode = previous == null ? null : previous.pluralLangCode;
            HashSet<String> selected = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
            HashSet<String> updated = new HashSet<>(selected);
            if (!TextUtils.isEmpty(previousCode) && selected.contains(previousCode)
                    && !TextUtils.equals(previousCode, languageCode)) {
                updated.remove(previousCode);
            }
            if (!TextUtils.isEmpty(languageCode) && !"null".equals(languageCode)) updated.add(languageCode);
            RestrictedLanguagesSelectActivity.updateRestrictedLanguages(updated, false);
            MessagesController.getInstance(currentAccount).getTranslateController().checkRestrictedLanguagesUpdate();
            MessagesController.getInstance(currentAccount).getTranslateController().cleanup();
            TranslateController.invalidateSuggestedLanguageCodes();
        } catch (Exception error) {
            org.telegram.messenger.FileLog.e(error);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack) {
            refreshLanguages();
            renderLanguages();
        }
    }

    private TextView label(Context context, CharSequence text, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
