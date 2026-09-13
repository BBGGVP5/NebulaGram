package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BottomSheet;
import java.text.DateFormat;
import java.util.Date;

/** Presentation only: all transfers and signature verification stay in the updater. */
public final class NebulaUpdateSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {
    private final Activity activity;
    private final NebulaTelegramUpdates updates;
    private final int offeredCode;
    private final long offeredDocument;
    private final TextView status;
    private final ProgressBar progress;
    private final NebulaButton action, retry;
    private final Runnable refresh = this::refresh;

    public NebulaUpdateSheet(Activity activity, NebulaTelegramUpdates updates) {
        super(activity, false);
        this.activity = activity; this.updates = updates;
        NebulaRelease release = updates.release();
        offeredCode = release.versionCode; offeredDocument = updates.document().id;
        NebulaTheme theme = NebulaTheme.of(activity);
        setBackgroundColor(theme.surface()); fixNavigationBar(theme.surface());
        setApplyTopPadding(false); setApplyBottomPadding(false);
        // The body owns scroll gestures; Back/outside/Later still dismiss the sheet.
        setCanDismissWithSwipe(false);

        LinearLayout body = column(activity); body.setPadding(dp(16), dp(12), dp(16), dp(4));
        View handle = new View(activity);
        GradientDrawable handleShape = new GradientDrawable(); handleShape.setColor(theme.onSurfaceVariant()); handleShape.setCornerRadius(dp(2));
        handle.setBackground(handleShape); handle.setAlpha(.35f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(34), dp(4)); hp.gravity = Gravity.CENTER_HORIZONTAL; hp.bottomMargin = dp(16);
        body.addView(handle, hp);

        LinearLayout hero = column(activity); hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{theme.primaryContainer(), theme.surfaceContainer()});
        background.setCornerRadius(dp(24)); background.setStroke(dp(1), NebulaTheme.stateLayer(theme.primary(), .18f));
        hero.setBackground(background);
        LinearLayout titleRow = new LinearLayout(activity); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        NebulaUpdateMascot mascot = new NebulaUpdateMascot(activity);
        titleRow.addView(mascot, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout titles = column(activity);
        titles.addView(label(activity, text("ОБНОВЛЕНИЕ ГОТОВО", "UPDATE AVAILABLE"), 11, theme.primary(), true));
        TextView title = label(activity, text("Новая версия\nNebulaGram", "A new\nNebulaGram"), 24, theme.onSurface(), true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1,-2); titleParams.topMargin = dp(6); titles.addView(title,titleParams);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(0,-2,1f); titlesParams.setMarginStart(dp(16)); titleRow.addView(titles,titlesParams);
        hero.addView(titleRow);
        TextView version = label(activity, NebulaTelegramUpdates.installedVersion() + "  →  " + release.versionName, 16, theme.onSurface(), true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1,-2); vp.topMargin = dp(18); hero.addView(version,vp);
        String detail = "Telegram " + release.telegramVersion + " · " + AndroidUtilities.formatFileSize(updates.document().size);
        if (updates.post() != null && updates.post().date > 0) detail += "\n" + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(updates.post().date * 1000L));
        TextView details = label(activity, detail, 13, theme.onSurfaceVariant(), false);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1,-2); dp.topMargin = dp(6); hero.addView(details,dp);
        body.addView(hero, new LinearLayout.LayoutParams(-1,-2));

        TextView heading = label(activity,text("Что нового", "What's new"),18,theme.onSurface(),true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1,-2); np.setMargins(dp(8),dp(22),dp(8),dp(12)); body.addView(heading,np);
        NebulaChangelogView notes = new NebulaChangelogView(activity); notes.setPost(updates.post()); notes.setLineSpacing(dp(4),1f);
        notes.setPadding(dp(8),0,dp(8),dp(16)); body.addView(notes,new LinearLayout.LayoutParams(-1,-2));
        status = label(activity,"",13,theme.onSurfaceVariant(),false);
        status.setPadding(dp(8),dp(8),dp(8),dp(8)); body.addView(status);
        progress = new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(theme.primary()));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,dp(4)); pp.setMargins(dp(8),dp(4),dp(8),dp(12)); body.addView(progress,pp);
        retry = button(activity,text("Скачать заново", "Download again"),false);
        retry.setOnClickListener(v -> { if (valid()) updates.redownload(); else dismiss(); }); body.addView(retry,new LinearLayout.LayoutParams(-1,-2));

        ScrollView scroll = new ScrollView(activity); scroll.setVerticalScrollBarEnabled(false); scroll.addView(body);
        LinearLayout footer = column(activity); footer.setPadding(dp(20),dp(12),dp(20),dp(8));
        action = button(activity,"",true); action.setOnClickListener(v -> performAction()); footer.addView(action,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout secondary = new LinearLayout(activity);
        NebulaButton post = button(activity,text("Пост релиза", "Release post"),false);
        final String offeredPost = updates.postUrl();
        post.setOnClickListener(v -> { dismiss(); Browser.openUrl(activity,offeredPost); });
        secondary.addView(post,new LinearLayout.LayoutParams(0,-2,1f));
        NebulaButton later = button(activity,text("Напомнить завтра", "Remind tomorrow"),false);
        later.setContentDescription(text("Напомнить при открытии приложения через сутки", "Remind when opening the app after 24 hours"));
        later.setOnClickListener(v -> { if (valid()) updates.markPrompted(); dismiss(); }); secondary.addView(later,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(6); footer.addView(secondary,sp);
        setCustomView(new SheetContent(activity,scroll,footer));
        refresh();
    }

    private boolean valid() {
        return !activity.isFinishing() && !activity.isDestroyed() && updates.currentAccountSelected()
                && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter
                && updates.available() && updates.release().versionCode == offeredCode
                && updates.document() != null && updates.document().id == offeredDocument;
    }
    private void performAction() {
        if (!valid()) { dismiss(); return; }
        if (updates.checking || updates.verifying) return;
        if (updates.downloading()) updates.cancelDownload();
        else if (updates.downloaded()) updates.install(activity);
        else updates.download();
        refresh();
    }
    private void refresh() {
        if (!valid()) { if (isShowing()) dismiss(); return; }
        boolean loading = updates.downloading();
        int percent = Math.max(0,Math.min(100,Math.round(updates.progress()*100)));
        status.setText(updates.error != null ? updates.error : updates.verifying
                ? text("Проверяем подпись и версию APK…", "Checking APK signature and version…")
                : loading ? text("Можно закрыть окно — загрузка продолжится.", "You can close this window. The download will continue.")
                : updates.downloaded() ? text("Файл загружен. Установка — только с вашего подтверждения.", "Downloaded. Installation requires your confirmation.")
                : text("Перед установкой проверим подпись APK.", "The APK signature will be checked before installation."));
        status.setTextColor(updates.error != null ? 0xFFE06A79 : NebulaTheme.of(activity).onSurfaceVariant());
        progress.setVisibility(loading || updates.verifying ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(updates.verifying); progress.setProgress(percent);
        action.setEnabled(!updates.checking && !updates.verifying);
        action.setText(updates.verifying ? text("Проверяем APK…", "Verifying APK…")
                : loading ? text("Отменить загрузку · ", "Cancel download · ") + percent + "%"
                : updates.downloaded() ? text("Установить обновление", "Install update")
                : text("Скачать · ", "Download · ") + AndroidUtilities.formatFileSize(updates.document().size));
        retry.setVisibility(updates.error != null && updates.downloaded() ? View.VISIBLE : View.GONE);
        retry.setEnabled(!updates.verifying && !updates.checking);
    }
    @Override protected void onStart() {
        super.onStart(); updates.addListener(refresh);
        NotificationCenter.getGlobalInstance().addObserver(this,NotificationCenter.activeAccountChanged); refresh();
    }
    @Override protected void onStop() {
        updates.removeListener(refresh);
        NotificationCenter.getGlobalInstance().removeObserver(this,NotificationCenter.activeAccountChanged); super.onStop();
    }
    @Override public void didReceivedNotification(int id,int account,Object... args) { refresh(); }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
    private static LinearLayout column(Context context) { LinearLayout v=new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private static TextView label(Context c,String text,int size,int color,boolean bold) {
        TextView v=new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(AndroidUtilities.bold()); return v;
    }
    private static NebulaButton button(Context c,String title,boolean primary) {
        NebulaButton b=new NebulaButton(c,primary?NebulaButton.STYLE_FILLED:NebulaButton.STYLE_TEXT);
        b.setSingleLine(false); b.setMaxLines(3); b.setTextSize(primary?15:13); b.setText(title);
        b.setPadding(dp(12),dp(10),dp(12),dp(10)); return b;
    }
    /** Measure the footer first; only the release body scrolls on small screens. */
    private static final class SheetContent extends LinearLayout {
        private final ScrollView body; private final LinearLayout footer;
        private boolean footerInBody;
        SheetContent(Context c,ScrollView body,LinearLayout footer) {
            super(c); setOrientation(VERTICAL); this.body=body; this.footer=footer;
            addView(body,new LayoutParams(-1,-2)); addView(footer,new LayoutParams(-1,-2));
        }
        @Override protected void onMeasure(int width,int height) {
            int available=MeasureSpec.getSize(height);
            if (MeasureSpec.getMode(height)==MeasureSpec.UNSPECIFIED) available=AndroidUtilities.displaySize.y;
            int limit=Math.max(1,(int)(available*.9f));
            footer.measure(width,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int remaining=Math.max(0,limit-footer.getMeasuredHeight());
            // Landscape / very large fonts: let the entire sheet scroll rather
            // than clipping the primary action or leaving an unusable text slit.
            boolean compact=remaining<dp(96);
            if (compact != footerInBody) {
                LinearLayout content=(LinearLayout)body.getChildAt(0);
                if (compact) { removeView(footer); content.addView(footer,new LayoutParams(-1,-2)); }
                else { content.removeView(footer); addView(footer,new LayoutParams(-1,-2)); }
                footerInBody=compact;
            }
            body.measure(width,MeasureSpec.makeMeasureSpec(compact?limit:remaining,MeasureSpec.AT_MOST));
            body.getLayoutParams().height=body.getMeasuredHeight();
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }
    }
}
