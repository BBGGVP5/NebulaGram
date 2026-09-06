package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import java.text.DateFormat;
import java.util.Date;

/** Persistent Telegram downloads remain available when the screen is reopened. */
public final class NebulaUpdatesFragment extends BaseFragment {
    private NebulaTelegramUpdates updates;
    private TextView status, version;
    private NebulaChangelogView notes;
    private org.telegram.tgnet.TLRPC.Message shownPost;
    private ProgressBar progress;
    private NebulaButton check, action, retry;
    private NebulaCard releaseCard;
    private final Runnable refresh = this::refresh;

    @Override public View createView(Context c) {
        if (updates != null) updates.removeListener(refresh);
        shownPost = null;
        updates = NebulaTelegramUpdates.get(currentAccount);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text("Обновления NebulaGram", "NebulaGram updates"));
        NebulaTheme theme = NebulaTheme.of(c);
        actionBar.setBackgroundColor(theme.surface()); actionBar.setTitleColor(theme.onSurface()); actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        ScrollView scroll = new ScrollView(c); scroll.setFillViewport(true); scroll.setBackgroundColor(theme.surface());
        LinearLayout content = new LinearLayout(c); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), dp(10), dp(12), dp(32)); scroll.addView(content);
        NebulaCard installed = new NebulaCard(c);
        installed.add(new NebulaRow(c).icon(R.drawable.msg_info).title("NebulaGram " + NebulaTelegramUpdates.installedVersion())
                .subtitle(text("Установлена · сборка ", "Installed · build ") + NebulaTelegramUpdates.installedCode(), false));
        status = label(c); installed.add(status);
        check = button(c, installed, text("Проверить обновления", "Check for updates"), v -> updates.check(true, () -> {
            if (!isPaused() && getParentActivity() != null && updates.error == null && updates.available()) updates.showOffer(getParentActivity());
        }));
        content.addView(installed);
        content.addView(NebulaCard.header(c, text("Настройки", "Settings")));
        NebulaCard settings = new NebulaCard(c);
        NebulaRow automatic = new NebulaRow(c).icon(R.drawable.msg_download).title(text("Проверять автоматически", "Check automatically"))
                .subtitle(text("При открытии приложения. APK скачивается по нажатию.", "On opening the app. APK downloads start when you tap."), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(updates.automatic());
        automatic.withClick(v -> updates.setAutomatic(automatic.toggleChecked())); settings.add(automatic);
        settings.add(new NebulaRow(c).icon(R.drawable.msg_discussion).title(text("Канал обновлений", "Update channel"))
                .subtitle("@" + NebulaRelease.CHANNEL, false).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> Browser.openUrl(c, "https://t.me/" + NebulaRelease.CHANNEL)));
        content.addView(settings);
        releaseCard = new NebulaCard(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(16); content.addView(releaseCard, lp);
        version = label(c); version.setTextColor(theme.primary()); version.setTypeface(AndroidUtilities.bold()); releaseCard.add(version);
        notes = new NebulaChangelogView(c);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2); noteParams.setMargins(dp(16), dp(12), dp(16), dp(12)); releaseCard.addView(notes, noteParams);
        progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, dp(8)); bar.setMargins(dp(16), dp(8), dp(16), dp(8)); releaseCard.addView(progress, bar);
        action = button(c, releaseCard, "", v -> {
            if (updates.downloading()) updates.cancelDownload();
            else if (updates.downloaded()) updates.install(getParentActivity());
            else updates.download();
        });
        retry = button(c, releaseCard, text("Скачать заново", "Download again"), v -> updates.redownload());
        button(c, releaseCard, text("Открыть пост с изменениями", "Open release post"), v -> Browser.openUrl(c, updates.postUrl()));
        updates.addListener(refresh);
        fragmentView = scroll; refresh();
        updates.check(false, null);
        return fragmentView;
    }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
    private TextView label(Context c) {
        TextView view = new TextView(c); view.setTextSize(15); view.setTextColor(NebulaTheme.of(c).onSurfaceVariant()); view.setPadding(dp(16), dp(12), dp(16), dp(12)); return view;
    }
    private NebulaButton button(Context c, NebulaCard card, String title, View.OnClickListener click) {
        NebulaButton button = new NebulaButton(c, NebulaButton.STYLE_FILLED); button.setText(title); button.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48)); lp.setMargins(dp(16), dp(8), dp(16), dp(12)); card.addView(button, lp); return button;
    }
    private void refresh() {
        if (status == null) return;
        String value;
        if (updates.checking) value = text("Проверяем посты в канале…", "Checking channel posts…");
        else if (updates.verifying) value = text("Проверяем APK перед установкой…", "Verifying APK before installation…");
        else if (updates.error != null) value = updates.error;
        else if (updates.available()) value = text("Доступно обновление", "An update is available");
        else if (updates.release() != null) value = text("Установлена актуальная версия", "You have the latest version");
        else value = text("В канале пока нет подходящих сборок", "No compatible builds in the channel yet");
        if (updates.lastCheck() > 0 && !updates.checking) value += "\n" + text("Последняя проверка: ", "Last checked: ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(updates.lastCheck()));
        status.setText(value); check.setEnabled(!updates.checking && !updates.verifying && !updates.downloading());
        releaseCard.setVisibility(updates.available() ? View.VISIBLE : View.GONE);
        if (!updates.available()) return;
        NebulaRelease release = updates.release();
        version.setText("NebulaGram " + release.versionName + "\nTelegram " + release.telegramVersion + " · " + AndroidUtilities.formatFileSize(updates.document().size));
        if (shownPost != updates.post()) { notes.setPost(updates.post()); shownPost = updates.post(); }
        progress.setVisibility(updates.downloading() ? View.VISIBLE : View.GONE); progress.setProgress(Math.round(updates.progress() * 100));
        action.setEnabled(!updates.checking && !updates.verifying);
        action.setText(updates.verifying ? text("Проверка APK…", "Verifying APK…") : updates.downloading()
                ? text("Отменить загрузку · ", "Cancel download · ") + Math.round(updates.progress() * 100) + "%"
                : updates.downloaded() ? text("Установить обновление", "Install update") : text("Скачать обновление", "Download update"));
        retry.setVisibility(updates.downloaded() && updates.error != null ? View.VISIBLE : View.GONE);
        retry.setEnabled(!updates.verifying);
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onFragmentDestroy() { if (updates != null) updates.removeListener(refresh); super.onFragmentDestroy(); }
}
