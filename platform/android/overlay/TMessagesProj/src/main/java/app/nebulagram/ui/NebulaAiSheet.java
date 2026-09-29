package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;

/** Opt-in home shortcut: the native glass camera button opens a transient conversation. */
public final class NebulaAiSheet {
    private NebulaAiSheet() { }
    public static boolean homeEnabled() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_settings", 0).getBoolean("home_shortcut", false);
    }
    public static void show(BaseFragment host) {
        Context c = host.getParentActivity(); if (c == null) return;
        NebulaTheme theme = NebulaTheme.of(c);
        int surface = theme.modalSurface();
        BottomSheet sheet = new BottomSheet.Builder(c, true, surface).create();
        sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        sheet.setApplyTopPadding(false); sheet.setApplyBottomPadding(false); sheet.setCanDismissWithSwipe(false);
        LinearLayout root = new LinearLayout(c) {
            @Override protected void onMeasure(int w, int h) {
                int available = MeasureSpec.getSize(h);
                int desired = (int) (getResources().getDisplayMetrics().heightPixels * .84f);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(available > 0 ? Math.min(desired, available) : desired, MeasureSpec.EXACTLY));
            }
        };
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable(); background.setColor(surface); background.setCornerRadius(dp(28));
        root.setBackground(background); root.setClipToOutline(true);
        LinearLayout header = new LinearLayout(c); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = button(c, "Nebula AI", theme.onSurface()); title.setTextSize(20); title.setTypeface(AndroidUtilities.bold());
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = button(c, NebulaText.text("Настройки", "Settings"), theme.primary()); header.addView(settings);
        TextView close = button(c, "×", theme.onSurface()); close.setTextSize(26); close.setContentDescription(NebulaText.text("Закрыть", "Close"));
        close.setOnClickListener(v -> sheet.dismiss()); header.addView(close); root.addView(header);
        Runnable setup = () -> { sheet.dismiss(); host.presentFragment(new NebulaAiFragment().openConnection()); };
        settings.setOnClickListener(v -> setup.run());
        NebulaAiChatView chat = new NebulaAiChatView(c, "", setup);
        chat.setBackgroundColor(surface);
        root.addView(chat, new LinearLayout.LayoutParams(-1, 0, 1));
        sheet.setCustomView(root); sheet.setOnDismissListener((Runnable) chat::dispose);
        host.showDialog(sheet);
        if (sheet.getWindow() != null) sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private static TextView button(Context c, String text, int color) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(13); v.setTextColor(color);
        v.setGravity(Gravity.CENTER); v.setMinWidth(dp(48)); v.setMinHeight(dp(48)); v.setPadding(dp(8), 0, dp(8), 0); return v;
    }
    private static int dp(int n) { return AndroidUtilities.dp(n); }
}
