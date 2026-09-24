package app.nebulagram.ui;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/** Share navigation only: links cannot change settings or carry credentials. */
public final class NebulaSettingsLinks {
    private NebulaSettingsLinks() { }
    private static final java.util.Map<String, String> resources = new java.util.HashMap<>();
    private static String language;

    private static String resourceName(String title) {
        String current = java.util.Locale.getDefault().toLanguageTag() + LocaleController.getString(R.string.NebulaSettings);
        if (!current.equals(language)) {
            resources.clear(); language = current;
            for (java.lang.reflect.Field f : R.string.class.getFields()) {
                if (!f.getName().startsWith("Nebula")) continue;
                try { resources.put(LocaleController.getString(f.getInt(null)), f.getName()); }
                catch (Exception ignored) { }
            }
        }
        return resources.get(title);
    }

    public static void bind(View view, int section) {
        if (view instanceof NebulaRow) {
            NebulaRow row = (NebulaRow) view;
            row.setOnLongClickListener(v -> {
                String title = row.linkTitle();
                Uri.Builder link = new Uri.Builder().scheme("tg").authority("settings").appendPath("nebula")
                        .appendQueryParameter("section", Integer.toString(destination(section, title)));
                String resource = resourceName(title);
                if (resource != null) link.appendQueryParameter("key", resource);
                else link.appendQueryParameter("focus", title);
                AndroidUtilities.addToClipboard(link.build().toString());
                Toast.makeText(v.getContext(), NebulaText.text("Ссылка на настройку скопирована", "Setting link copied"), Toast.LENGTH_SHORT).show();
                return true;
            });
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) bind(group.getChildAt(i), section);
        }
    }

    private static int destination(int section, String title) {
        if (section == -1) {
            for (NebulaSettingsSearch.Entry entry : NebulaSettingsSearch.all())
                if (entry.title.equals(title)) return entry.section;
        }
        return section;
    }

    public static boolean isLink(String value) {
        if (value == null || value.length() > 2048) return false;
        Uri uri = Uri.parse(value);
        return "tg".equals(uri.getScheme()) && "settings".equals(uri.getHost()) && "/nebula".equals(uri.getPath());
    }

    public static boolean open(BaseFragment host, String value) {
        if (host == null || !isLink(value)) return false;
        try {
            Uri uri = Uri.parse(value);
            int section = Integer.parseInt(uri.getQueryParameter("section"));
            String focus = uri.getQueryParameter("focus");
            String key = uri.getQueryParameter("key");
            if (key != null && key.startsWith("Nebula") && key.length() < 120) {
                focus = LocaleController.getString(R.string.class.getField(key).getInt(null));
            }
            if (section >= 0 && section <= 9) host.presentFragment(new NebulaSectionFragment(section).focus(focus));
            else if (section == -1) host.presentFragment(new NebulaSettingsFragment());
            else if (section == -16) host.presentFragment(new NebulaTasksFragment());
            else if (section == -17) host.presentFragment(new NebulaMessageToolsFragment(null));
            else if (section == -18) host.presentFragment(new NebulaSyncFragment());
            else if (section == -19) host.presentFragment(new NebulaLockedChatsFragment());
            else if (section == -12) host.presentFragment(new NebulaAiFragment());
            else if (section == -10 || section == -11) host.presentFragment(new NebulaDesignFragment(section == -11));
            else if (section == -13) host.presentFragment(new NebulaUpdatesFragment());
            else if (section == -14) host.presentFragment(new NebulaMenuFragment(NebulaMenuFragment.SCREEN_ADVANCED));
            else if (section == -15) host.presentFragment(new NebulaPrivacyFragment());
            else return false;
            return true;
        } catch (Exception ignored) { return false; }
    }
}
