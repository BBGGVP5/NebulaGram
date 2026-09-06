package app.nebulagram.ui;

import org.telegram.messenger.LocaleController;

public final class NebulaText {
    private NebulaText() { }
    public static String text(String ru, String en) {
        return LocaleController.getInstance().getCurrentLocale().getLanguage().equals("ru") ? ru : en;
    }
}
