package app.nebulagram.ui;

import android.content.Context;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.BaseFragment;

/** Music player and document viewer. Each network lookup is a separate opt-in naming its service. */
public final class NebulaMediaFeaturesFragment extends BaseFragment {
    private static String t(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public android.view.View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, t("Музыка и документы", "Music and documents"));
        LinearLayout column = NebulaFormUi.column(c);

        NebulaCard player = new NebulaCard(c);
        player.add(toggle(c, t("Плеер Material 3 Expressive", "Material 3 Expressive player"),
            t("Большая обложка, цвета из обложки, волновая шкала", "Large cover, colours from the artwork, wavy seek bar"),
            "expressive_player", NebulaMediaPreferences.expressivePlayer()));
        player.add(toggle(c, t("Текст песен", "Lyrics"),
            t("Синхронный текст с lrclib.net", "Synced lyrics from lrclib.net"),
            "player_lyrics", NebulaMediaPreferences.lyrics()));
        player.add(toggle(c, t("Обложки Apple Music", "Apple Music covers"),
            t("Обложки высокого качества из iTunes", "High-resolution artwork from iTunes"),
            "player_apple_covers", NebulaMediaPreferences.appleCovers()));
        player.add(new NebulaRow(c).title(t("Очистить кэш текстов и обложек", "Clear lyrics and cover cache"))
            .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
                NebulaMusicService.clearCache();
                android.widget.Toast.makeText(c, t("Кэш очищен", "Cache cleared"), android.widget.Toast.LENGTH_SHORT).show();
            }));
        NebulaFormUi.group(column, t("Музыкальный плеер", "Music player"), player);
        column.addView(NebulaFormUi.note(c, t(
            "Текст песен и обложки выключены по умолчанию. Если включить их, исполнитель, название и длительность трека отправляются на lrclib.net (тексты) и itunes.apple.com (обложки). Ответы хранятся в кэше на устройстве. Живые (анимированные) обложки Apple Music требуют ключа разработчика Apple, поэтому показывается статичная обложка в высоком качестве.",
            "Lyrics and covers are off by default. When enabled, the track's artist, title and length are sent to lrclib.net (lyrics) and itunes.apple.com (covers). Replies are cached on the device. Animated Apple Music artwork needs an Apple developer key, so the high-resolution static cover is shown instead.")));

        NebulaCard documents = new NebulaCard(c);
        documents.add(toggle(c, t("Встроенный просмотр PDF", "Built-in PDF viewer"),
            t("Открывать PDF из чатов в Nebula · выключите, чтобы открывать в другом приложении", "Open PDFs from chats in Nebula · turn off to use another app"),
            "pdf_viewer", NebulaMediaPreferences.pdfViewer()));
        NebulaFormUi.group(column, t("Документы", "Documents"), documents);
        column.addView(NebulaFormUi.note(c, t(
            "PDF открывается без интернета. Кнопки в шапке просмотра позволяют поделиться файлом или открыть его в другом приложении. В чатах с запретом пересылки эти кнопки скрыты.",
            "PDFs open offline. Buttons in the viewer's header share the file or open it in another app. In chats that restrict forwarding these buttons are hidden.")));
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, column));
    }

    private NebulaRow toggle(Context c, String title, String subtitle, String key, boolean value) {
        return new NebulaRow(c).title(title).subtitle(subtitle, false).trailing(NebulaRow.TRAIL_SWITCH).checked(value)
            .withClick(v -> NebulaMediaPreferences.set(key, ((NebulaRow) v).toggleChecked()));
    }
}
