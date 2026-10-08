package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import java.text.DateFormat;
import java.util.Date;

/** Manual update hub. Download/install consent and verification remain in the existing offer. */
public final class NebulaUpdateSettingsSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {
    private final Activity activity;
    private final NebulaTelegramUpdates updates;
    private final TextView lastCheck, status;
    private final NebulaRow automatic, beta, release;
    private final NebulaButton check;
    private final Runnable refresh = this::refresh;
    private boolean openOffer;

    public static void show(BaseFragment host) {
        Activity activity = host.getParentActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) return;
        if (host.getVisibleDialog() instanceof NebulaUpdateSettingsSheet) return;
        host.showDialog(new NebulaUpdateSettingsSheet(host));
    }
    private NebulaUpdateSettingsSheet(BaseFragment host) {
        super(host.getParentActivity(), false);
        activity = host.getParentActivity(); updates = NebulaTelegramUpdates.get(host.getCurrentAccount());
        NebulaTheme theme = NebulaTheme.of(activity);
        setBackgroundColor(theme.modalSurface()); fixNavigationBar(theme.modalSurface());
        setApplyTopPadding(false); setApplyBottomPadding(false); setCanDismissWithSwipe(false);
        LinearLayout body = column(activity); body.setPadding(dp(16), dp(12), dp(16), dp(16));
        View handle = new View(activity); GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(2)); shape.setColor(NebulaTheme.stateLayer(theme.onSurface(), .25f)); handle.setBackground(shape);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(34), dp(4)); hp.gravity = Gravity.CENTER_HORIZONTAL; hp.bottomMargin = dp(18); body.addView(handle, hp);
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new NebulaUpdateMascot(activity), new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout titles = column(activity); TextView title = label(activity, 23, theme.onSurface()); title.setTypeface(AndroidUtilities.bold()); title.setText(text("Обновления", "Updates")); titles.addView(title);
        lastCheck = label(activity, 13, theme.onSurfaceVariant()); lastCheck.setMaxLines(2); lastCheck.setPadding(0, dp(5), 0, 0); titles.addView(lastCheck);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.setMarginStart(dp(14)); header.addView(titles, tp); body.addView(header);
        NebulaCard information = new NebulaCard(activity);
        information.add(new NebulaRow(activity).icon(R.drawable.msg_info).title(text("Текущая версия", "Current version")).subtitle(NebulaTelegramUpdates.installedVersion() + " · " + text("сборка ", "build ") + NebulaTelegramUpdates.installedCode(), true));
        String abi = Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0];
        information.add(new NebulaRow(activity).icon(R.drawable.msg_settings).title(text("Тип сборки", "Build type")).subtitle((BuildConfig.DEBUG ? "Debug" : NebulaTelegramUpdates.installedVersion().contains("-beta") ? "Beta" : "Release") + " · " + abi, true));
        addCard(body, information, 20);
        NebulaCard preferences = new NebulaCard(activity);
        beta = new NebulaRow(activity).icon(R.drawable.msg_settings).title(text("Получать бета-версии", "Include beta releases")).trailing(NebulaRow.TRAIL_SWITCH).checked(updates.beta());
        beta.withClick(v -> { updates.setBeta(!updates.beta()); refresh(); }); preferences.add(beta);
        automatic = new NebulaRow(activity).icon(R.drawable.msg_download).title(text("Проверять при запуске", "Check on launch")).trailing(NebulaRow.TRAIL_SWITCH).checked(updates.automatic());
        automatic.withClick(v -> updates.setAutomatic(!updates.automatic())); preferences.add(automatic); addCard(body, preferences, 12);
        TextView explanation = label(activity, 12, theme.onSurfaceVariant()); explanation.setPadding(dp(12), dp(10), dp(12), 0);
        explanation.setText(text("Бета-версии отмечаются в канале отдельно. Загрузка и установка — только по нажатию.", "Beta releases are marked separately in the channel. Downloads and installation only start when you tap.")); body.addView(explanation);
        status = label(activity, 14, theme.onSurfaceVariant()); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); status.setPadding(dp(12), dp(12), dp(12), dp(4)); body.addView(status);
        NebulaCard offer = new NebulaCard(activity);
        release = new NebulaRow(activity).icon(R.drawable.msg_download).title(text("Обновление доступно", "Update available")).trailing(NebulaRow.TRAIL_CHEVRON);
        release.withClick(v -> { if (valid() && updates.available() && !updates.checking) { openOffer = true; dismiss(); } }); offer.add(release);
        addCard(body, offer, 8); release.setTag(offer);
        LinearLayout actions = new LinearLayout(activity); actions.setGravity(Gravity.CENTER_VERTICAL);
        check = new NebulaButton(activity, NebulaButton.STYLE_FILLED); check.setText(text("Проверить обновления", "Check for updates")); check.setSingleLine(false); check.setMinHeight(dp(52)); check.setPadding(dp(16), dp(12), dp(16), dp(12));
        check.setOnClickListener(v -> { if (valid()) updates.check(true, null); }); actions.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView channel = new ImageView(activity); channel.setScaleType(ImageView.ScaleType.CENTER); channel.setImageResource(R.drawable.msg_channel); channel.setColorFilter(theme.onPrimaryContainer()); channel.setBackground(new NebulaButton(activity, NebulaButton.STYLE_TONAL).getBackground()); channel.setFocusable(true); channel.setContentDescription(text("Канал обновлений NebulaGram", "NebulaGram update channel")); channel.setOnClickListener(v -> Browser.openUrl(activity, "https://t.me/" + NebulaRelease.CHANNEL));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(52), dp(52)); cp.setMarginStart(dp(10)); actions.addView(channel, cp); addCard(body, actions, 14);
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int width, int height) {
                int available = MeasureSpec.getMode(height) == MeasureSpec.UNSPECIFIED ? AndroidUtilities.displaySize.y : MeasureSpec.getSize(height);
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(Math.max(1, (int)(available * .88f)), MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false); scroll.addView(body); setCustomView(scroll); refresh();
    }
    private boolean valid() { return !activity.isFinishing() && !activity.isDestroyed() && updates.currentAccountSelected() && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter; }
    private void refresh() {
        if (!valid()) { if (isShowing()) dismiss(); return; }
        long date = updates.lastCheck();
        lastCheck.setText(date > 0 ? text("Последняя проверка: ", "Last checked: ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(date)) : text("Проверок ещё не было", "Not checked yet"));
        boolean busy = updates.checking || updates.verifying || updates.downloading();
        automatic.checked(updates.automatic()); beta.checked(updates.beta()); beta.setEnabled(!busy); beta.setAlpha(busy ? .5f : 1f); check.setEnabled(!busy);
        check.setText(updates.checking ? text("Проверяем…", "Checking…") : text("Проверить обновления", "Check for updates"));
        String message = updates.error != null ? updates.error : updates.checking ? text("Ищем подходящую сборку в канале", "Looking for a compatible channel build") : updates.verifying ? text("Проверяем подпись APK", "Verifying APK signature") : updates.downloading() ? text("Загрузка продолжается · ", "Download in progress · ") + Math.round(updates.progress() * 100) + "%" : updates.available() ? text("Новая версия готова к загрузке", "A new version is ready to download") : updates.release() != null ? text("Установлена актуальная версия", "You have the latest version") : date > 0 ? text("Подходящих обновлений пока нет", "No compatible updates yet") : "";
        status.setText(message); status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        ((View)release.getTag()).setVisibility(updates.available() ? View.VISIBLE : View.GONE);
        if (updates.available()) release.subtitle("NebulaGram " + updates.release().versionName + " · " + text("изменения и загрузка", "changes and download"), true);
    }
    @Override protected void onStart() { super.onStart(); updates.addListener(refresh); NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.activeAccountChanged); refresh(); }
    @Override protected void onStop() { updates.removeListener(refresh); NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.activeAccountChanged); super.onStop(); }
    @Override public void didReceivedNotification(int id, int account, Object... args) { refresh(); }
    @Override public void dismissInternal() { super.dismissInternal(); if (openOffer) { openOffer = false; if (valid()) updates.showOffer(activity); } }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
    private static LinearLayout column(Context context) { LinearLayout result = new LinearLayout(context); result.setOrientation(LinearLayout.VERTICAL); return result; }
    private static TextView label(Context c, int size, int color) { TextView result = new TextView(c); result.setTextSize(size); result.setTextColor(color); return result; }
    private static void addCard(LinearLayout body, View child, int top) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(top); body.addView(child, lp); }
}
