package app.nebulagram.ui;

import android.content.Context;
import android.os.Debug;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/** Visible-only memory sampling. Collection is best effort; it never deletes user data. */
public final class NebulaMemoryFragment extends BaseFragment {
    private NebulaRow used, maximum, nativeHeap;
    private boolean resumed;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (!resumed || used == null) return; update(); AndroidUtilities.runOnUIThread(this, 2000); }
    };
    private static String text(String ru, String en) { return NebulaText.text(ru, en); }
    private static String mb(long bytes) { return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1048576.0); }
    private void update() {
        Runtime runtime = Runtime.getRuntime(); long bytes = runtime.totalMemory() - runtime.freeMemory();
        used.value(mb(bytes) + " · " + Math.round(bytes * 100.0 / runtime.maxMemory()) + "%");
        maximum.value(mb(runtime.maxMemory())); nativeHeap.value(mb(Debug.getNativeHeapAllocatedSize()));
    }
    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(text("Память приложения", "App memory"));
        actionBar.setBackgroundColor(theme.surface()); actionBar.setTitleColor(theme.onSurface()); actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        ScrollView scroll = new ScrollView(context); scroll.setBackgroundColor(theme.surface());
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(24)); scroll.addView(body);
        NebulaCard card = new NebulaCard(context);
        used = new NebulaRow(context).title(text("Использовано Java heap", "Used Java heap")); card.add(used);
        maximum = new NebulaRow(context).title(text("Лимит Java heap", "Java heap limit")); card.add(maximum);
        nativeHeap = new NebulaRow(context).title(text("Нативная память", "Native heap")); card.add(nativeHeap); body.addView(card);
        body.addView(NebulaMenuFragment.placeholder(context, text("Экспериментальные показатели текущего процесса. Сборка мусора освобождает только объекты, которые больше не используются.", "Experimental current-process metrics. Garbage collection only frees unused objects.")));
        card = new NebulaCard(context); card.add(new NebulaRow(context).title(text("Запросить сборку мусора", "Request garbage collection")).withClick(v -> { Runtime.getRuntime().gc(); update(); })); body.addView(card);
        card = new NebulaCard(context);
        NebulaRow warning = new NebulaRow(context).title(text("Предупреждение о нехватке памяти", "Low-memory warning"))
                .subtitle(text("Когда Android сообщает о нехватке памяти приложению", "When Android reports memory pressure to the app"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaFeatureSettings.enabled("memory_warning"));
        warning.withClick(v -> NebulaFeatureSettings.set("memory_warning", warning.toggleChecked())); card.add(warning); body.addView(card);
        update(); return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll, -1);
    }
    @Override public void onResume() { super.onResume(); resumed = true; AndroidUtilities.cancelRunOnUIThread(refresh); refresh.run(); }
    @Override public void onPause() { resumed = false; AndroidUtilities.cancelRunOnUIThread(refresh); super.onPause(); }
    @Override public void onFragmentDestroy() { resumed = false; AndroidUtilities.cancelRunOnUIThread(refresh); super.onFragmentDestroy(); }
}
