package app.nebulagram.ui;

import org.telegram.messenger.R;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Как назвать состояние «подключаюсь через прокси».
 *
 * <p>Когда прокси — наш туннель, слово «прокси» ничего пользователю не
 * объясняет: он включал NebulaLink, а не прокси, и предложение «настроить
 * прокси» ведёт его в чужой экран со списком чужих серверов. Поэтому у своего
 * туннеля своё имя и своя ссылка, а у настоящего прокси всё остаётся как было.
 */
public final class NebulaConnection {
    private NebulaConnection() { }

    /** Идёт ли соединение через наш туннель прямо сейчас. */
    public static boolean throughLink() {
        try {
            return NebulaLink.isRoutingThroughTunnel();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Имя ресурса для заголовка-оверлея. {@code LocaleController.getString}
     * принимает пару «имя, идентификатор», поэтому нужны оба.
     */
    public static String connectingKey(boolean withDots) {
        if (throughLink()) {
            return withDots ? "NebulaConnectingLinkWithDots" : "NebulaConnectingLink";
        }
        return withDots ? "ConnectingToProxyWithDots" : "ConnectingToProxy";
    }

    /** Идентификатор того же ресурса. */
    public static int connectingId(boolean withDots) {
        if (throughLink()) {
            return withDots ? R.string.NebulaConnectingLinkWithDots : R.string.NebulaConnectingLink;
        }
        return withDots ? R.string.ConnectingToProxyWithDots : R.string.ConnectingToProxy;
    }

    /**
     * Нужна ли подпись «настроить прокси» под заголовком. У своего туннеля
     * настраивать в чужом экране нечего.
     */
    public static boolean offersProxySettings(int titleId) {
        return titleId == R.string.ConnectingToProxyWithDots && !throughLink();
    }
}
