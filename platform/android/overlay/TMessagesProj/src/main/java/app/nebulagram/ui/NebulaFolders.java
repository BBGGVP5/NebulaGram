package app.nebulagram.ui;

import org.telegram.messenger.FileLog;

import java.io.File;

/**
 * Куда клиент складывает сохранённое: «NebulaGram», а не «Telegram».
 *
 * <p>Имена каталогов у Telegram записаны строками в полутора десятках мест —
 * «Telegram», «Telegram Images», «Telegram Video» и так далее. Здесь они
 * переименованы в одном месте, а вызывающий передаёт прежнее имя: подстановка
 * получается механической, и ни одна строка апстрима не переписана по смыслу.
 *
 * <p>Старый каталог не бросаем. Если он уже есть, а нового ещё нет, каталог
 * переименовывается: иначе прежние сохранения остались бы в одной папке, новые
 * легли бы в другую, и пользователь искал бы файлы в двух местах. Переименование
 * в пределах одного родителя — операция файловой системы, ничего не копируется.
 */
public final class NebulaFolders {
    /** Имя, под которым клиент виден в «Загрузках» и в галерее. */
    public static final String NAME = "NebulaGram";
    private static final String LEGACY = "Telegram";

    private NebulaFolders() { }

    /** Наше имя для каталога, который у Telegram назывался {@code legacy}. */
    public static String name(String legacy) {
        return legacy != null && legacy.startsWith(LEGACY)
                ? NAME + legacy.substring(LEGACY.length())
                : legacy;
    }

    /**
     * Каталог с нашим именем внутри {@code parent}.
     *
     * <p>Относительный родитель — это не место на диске, а путь для MediaStore
     * ({@code RELATIVE_PATH}); там переносить нечего, и файловую систему мы не
     * трогаем.
     */
    public static File dir(File parent, String legacy) {
        File target = new File(parent, name(legacy));
        if (parent == null || !parent.isAbsolute()) {
            return target;
        }
        migrate(new File(parent, legacy), target);
        return target;
    }

    /** То же для родителя, заданного строкой. */
    public static File dir(String parent, String legacy) {
        return dir(new File(parent), legacy);
    }

    private static void migrate(File from, File to) {
        if (to.exists() || !from.exists() || !from.isDirectory()) {
            return;
        }
        try {
            if (!from.renameTo(to)) {
                // Не беда: каталог просто заведётся заново под новым именем,
                // а прежние файлы останутся лежать там, где лежали.
                FileLog.d("NebulaGram: could not rename " + from + " to " + to);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
