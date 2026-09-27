package app.nebulagram.ui;

import android.content.Context;
import android.content.res.Configuration;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.LauncherIconController;
import java.util.Locale;

/** Launcher labels live in Android resources, outside Telegram's language pack. */
public final class NebulaLauncherIconLabels {
    private NebulaLauncherIconLabels() { }

    public static String title(Context context, LauncherIconController.LauncherIcon icon) {
        try {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            if (locale != null) configuration.setLocale(locale);
            return context.createConfigurationContext(configuration).getString(icon.title);
        } catch (Exception ignored) {
            return icon.name();
        }
    }
}
