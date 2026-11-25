package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Built-in SVG viewer. The drawing is shown by a WebView with scripts, file and network access
 * off; any request the page still makes gets an empty reply. Pinch zooms up to 8×.
 */
public final class NebulaSvgViewerFragment extends BaseFragment {
    private static final int SHARE = 1, EXTERNAL = 2, BACKGROUND = 3;
    private final File file;
    private final String title;
    private final boolean restricted;
    private WebView web;
    private String svg;
    private boolean checkerboard;

    public NebulaSvgViewerFragment(File file, String title, boolean restricted) {
        this.file = file; this.title = title; this.restricted = restricted;
    }

    private static String t(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(title);
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(BACKGROUND, R.drawable.msg_photo_settings).setContentDescription(t("Фон прозрачности", "Transparency background"));
        if (!restricted) {
            menu.addItem(SHARE, R.drawable.msg_share).setContentDescription(t("Поделиться", "Share"));
            menu.addItem(EXTERNAL, R.drawable.msg_openin).setContentDescription(t("Открыть в другом приложении", "Open in another app"));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == BACKGROUND) { checkerboard = !checkerboard; load(); }
                else if (id == SHARE) NebulaPdfViewer.share(getParentActivity(), file, title, "image/svg+xml");
                else if (id == EXTERNAL) NebulaPdfViewer.openExternally(getParentActivity(), file, "image/svg+xml");
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(theme.surface());
        web = new WebView(context);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setBlockNetworkImage(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        web.setBackgroundColor(theme.surface());
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
        });
        root.addView(web, LayoutHelper.createFrame(-1, -1));
        try {
            svg = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception error) {
            FileLog.e(error);
            svg = "";
        }
        load();
        return fragmentView = root;
    }

    private void load() {
        if (web == null || svg == null) return;
        int background = NebulaTheme.of(web.getContext()).surface();
        web.loadDataWithBaseURL("about:blank", NebulaSvgViewer.page(svg, background, checkerboard), "text/html", "utf-8", null);
    }

    @Override public void onResume() {
        super.onResume();
        if (restricted && getParentActivity() != null) getParentActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (web != null) web.onResume();
    }

    @Override public void onPause() {
        super.onPause();
        if (restricted && getParentActivity() != null) getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (web != null) web.onPause();
    }

    @Override public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
    }
}
