package app.nebulagram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.AvatarDrawable;

/** Controls only the optional centred Liquid Glass layout of a normal chat. */
public final class NebulaChatStyle {
    private NebulaChatStyle() { }

    public static void header(ActionBar bar, boolean normalChat) {
        if (bar != null) {
            bar.setNebulaFloatingChatHeader(normalChat && NebulaAppearance.chatHeader());
        }
    }

    /**
     * Ставит на кнопку меню чата аватарку собеседника вместо трёх точек.
     *
     * <p>Убрать кнопку совсем нельзя: за ней все действия с чатом — звук,
     * очистка истории, поиск. Меняем только значок: точек в плавающей шапке
     * быть не должно, а меню остаётся там же, где его ищут.
     *
     * <p>Рисуем через AvatarDrawable, а не через настоящее фото: значок в
     * панели живёт в отрыве от жизненного цикла загрузки картинок, и
     * ImageReceiver здесь пришлось бы держать вручную.
     */
    public static void menuIcon(ActionBarMenuItem item, TLRPC.User user, TLRPC.Chat chat) {
        if (item == null || !NebulaAppearance.chatHeader()) {
            return;
        }
        try {
            AvatarDrawable avatar = new AvatarDrawable();
            if (user != null) {
                avatar.setInfo(user);
            } else if (chat != null) {
                avatar.setInfo(chat);
            } else {
                return;
            }
            avatar.setTextSize(AndroidUtilities.dp(9));
            item.setIcon(avatar);
        } catch (Throwable e) {
            // Значок — украшение: кнопка обязана остаться рабочей в любом случае.
            FileLog.e(e);
        }
    }
}
