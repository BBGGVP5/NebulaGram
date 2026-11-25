package app.nebulagram.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;

/** "Export data", as in Telegram Desktop: what to export, a size limit for files and how to save. */
public final class NebulaExportFragment extends BaseFragment {
    private static final NebulaExportOptions options = new NebulaExportOptions();
    private final boolean[] expanded = new boolean[4];
    private LinearLayout column;

    private static String t(String ru, String en) { return NebulaText.text(ru, en); }

    private static String[] titles(int section) {
        switch (section) {
            case 0: return new String[]{t("Информация профиля", "Profile information"), t("Контакты", "Contacts"), t("Активные сеансы", "Active sessions")};
            case 1: return new String[]{t("Личные чаты", "Personal chats"), t("Боты", "Bots"), t("Избранное", "Saved Messages"),
                    t("Частные группы", "Private groups"), t("Публичные группы", "Public groups"), t("Каналы", "Channels")};
            case 3: return new String[]{t("Фото профиля", "Profile photos"), t("Наборы стикеров", "Sticker sets"), t("Истории в профиле", "Profile stories")};
            default: return new String[]{t("Фото", "Photos"), t("Видео", "Videos"), t("Голосовые", "Voice messages"), t("Кружки", "Video messages"),
                    t("Стикеры", "Stickers"), "GIF", t("Файлы", "Files")};
        }
    }

    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, t("Экспорт данных", "Export data"));
        column = NebulaFormUi.column(c);
        rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, column));
    }

    private void rebuild() {
        Context c = column.getContext();
        column.removeAllViews();
        column.addView(NebulaFormUi.note(c, t("Сохраните копию данных Telegram на это устройство: информацию аккаунта, чаты и медиа.",
                "Save a copy of your Telegram data to this device: account information, chats and media.")));
        NebulaExport running = NebulaExport.current();
        if (running != null && !running.state.finished) {
            NebulaCard card = new NebulaCard(c);
            card.add(new NebulaRow(c).title(t("Экспорт идёт", "Export in progress")).value(running.state.percent + "%").trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v -> progress(running)));
            column.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }

        NebulaCard what = new NebulaCard(c);
        String[] sections = {t("Аккаунт", "Account"), t("Чаты", "Chats"), t("Медиа", "Media"), t("Другое", "Other")};
        for (int s = 0; s < sections.length; s++) {
            final int section = s;
            int total = NebulaExportOptions.SECTIONS[s].length;
            NebulaRow head = new NebulaRow(c).title(sections[s] + "  " + options.selected(s) + "/" + total + (expanded[s] ? "  ˄" : "  ˅"))
                    .trailing(NebulaRow.TRAIL_SWITCH).checked(options.any(s));
            head.setOnTouchListener((v, e) -> {
                // The left part opens the list, the switch on the right turns the whole section on or off.
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (e.getX() < v.getWidth() - AndroidUtilities.dp(80)) { expanded[section] = !expanded[section]; }
                    else options.setAll(section, !options.any(section));
                    rebuild();
                }
                return true;
            });
            what.add(head);
            if (expanded[s]) {
                String[] items = titles(s);
                for (int i = 0; i < items.length; i++) {
                    final int item = NebulaExportOptions.SECTIONS[s][i];
                    NebulaRow row = new NebulaRow(c).title("     " + items[i]).trailing(NebulaRow.TRAIL_SWITCH).checked(options.has(item));
                    row.withClick(v -> { options.set(item, row.toggleChecked()); rebuild(); });
                    what.add(row);
                }
            }
        }
        NebulaFormUi.group(column, t("Что экспортировать", "What to export"), what);
        column.addView(NebulaFormUi.note(c, t("Секретные чаты хранятся только на ваших устройствах и не экспортируются.",
                "Secret chats are stored only on your devices and can't be exported.")));

        NebulaCard limit = new NebulaCard(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        FrameLayout labels = new FrameLayout(c);
        NebulaTheme theme = NebulaTheme.of(c);
        TextView min = label(c, "1 MB", theme.onSurfaceVariant()), max = label(c, "4 GB", theme.onSurfaceVariant()),
                value = label(c, NebulaExportOptions.size(options.sizeLimit), theme.primary());
        labels.addView(min, new FrameLayout.LayoutParams(-2, -2, Gravity.START));
        labels.addView(value, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        labels.addView(max, new FrameLayout.LayoutParams(-2, -2, Gravity.END));
        box.addView(labels, new LinearLayout.LayoutParams(-1, -2));
        SeekBar seek = new SeekBar(c);
        seek.setMax(1000);
        seek.setProgress(Math.round(NebulaExportOptions.position(options.sizeLimit) * 1000));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                options.sizeLimit = NebulaExportOptions.rounded(NebulaExportOptions.limit(progress / 1000f));
                value.setText(NebulaExportOptions.size(options.sizeLimit));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        box.addView(seek, new LinearLayout.LayoutParams(-1, -2));
        limit.add(box);
        NebulaFormUi.group(column, t("Лимит размера", "Size limit"), limit);
        column.addView(NebulaFormUi.note(c, t("Файлы больше лимита пропускаются, вместо них в экспорте остаётся пометка.",
                "Larger files are skipped. The export keeps a note about them in place of the file.")));

        NebulaCard saving = new NebulaCard(c);
        String[] formats = {t("HTML для чтения", "Human-readable HTML"), t("JSON как в Telegram Desktop", "Desktop-compatible JSON"), t("HTML и JSON", "HTML and JSON")};
        saving.add(new NebulaRow(c).title(t("Формат", "Format")).value(formats[options.format]).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(t("Формат", "Format")).setSelectedIndex(options.format)
                        .setItems(formats, (d, which) -> { options.format = which; rebuild(); }).create())));
        String[] periods = {t("Всё время", "All time"), t("Последний год", "Last year"), t("Последний месяц", "Last month"), t("Последняя неделя", "Last week")};
        saving.add(new NebulaRow(c).title(t("Период", "Period")).value(periods[options.period]).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(t("Период", "Period")).setSelectedIndex(options.period)
                        .setItems(periods, (d, which) -> { options.period = which; rebuild(); }).create())));
        saving.add(new NebulaRow(c).title(t("Папка", "Folder")).value(t("Загрузки/NebulaGram", "Downloads/NebulaGram")));
        NebulaFormUi.group(column, t("Сохранение", "Saving"), saving);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52));
        params.topMargin = AndroidUtilities.dp(16);
        column.addView(NebulaFormUi.primary(c, t("Экспортировать", "Export"), v -> start()), params);
    }

    private static TextView label(Context c, String text, int color) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        return view;
    }

    private void start() {
        Activity activity = getParentActivity();
        if (activity == null) return;
        if (!options.valid()) {
            Toast.makeText(activity, t("Выберите, что экспортировать", "Choose what to export"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            Toast.makeText(activity, t("Разрешите доступ к памяти и нажмите ещё раз", "Allow storage access and tap again"), Toast.LENGTH_SHORT).show();
            return;
        }
        NebulaExport export = NebulaExport.start(currentAccount, copy(options));
        progress(export);
        rebuild();
    }

    private static NebulaExportOptions copy(NebulaExportOptions source) {
        NebulaExportOptions out = new NebulaExportOptions();
        for (int[] section : NebulaExportOptions.SECTIONS) for (int item : section) out.set(item, source.has(item));
        out.sizeLimit = source.sizeLimit; out.format = source.format; out.period = source.period;
        return out;
    }

    /** "0% · Chats 1 of 78", the current chat and file, Continue in background and Stop export. */
    private void progress(NebulaExport export) {
        Activity activity = getParentActivity();
        if (activity == null) return;
        NebulaTheme theme = NebulaTheme.of(activity);
        BottomSheet sheet = new BottomSheet(activity, false, getResourceProvider());
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        TextView percent = label(activity, "0%", theme.onSurface());
        percent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        percent.setTypeface(AndroidUtilities.bold());
        TextView step = label(activity, "", theme.onSurfaceVariant());
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        TextView chat = label(activity, "", theme.onSurface());
        chat.setTypeface(AndroidUtilities.bold());
        TextView messages = label(activity, "", theme.onSurfaceVariant());
        TextView file = label(activity, "", theme.onSurfaceVariant());
        file.setSingleLine(true);
        file.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        root.addView(percent);
        root.addView(step);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(16));
        barParams.topMargin = AndroidUtilities.dp(10);
        root.addView(bar, barParams);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(AndroidUtilities.dp(24));
        shape.setColor(theme.surfaceContainer());
        card.setBackground(shape);
        card.addView(chat); card.addView(messages); card.addView(file);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = AndroidUtilities.dp(14);
        root.addView(card, cardParams);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52));
        buttonParams.topMargin = AndroidUtilities.dp(16);
        root.addView(NebulaFormUi.primary(activity, t("Продолжить в фоне", "Continue in Background"), v -> sheet.dismiss()), buttonParams);
        TextView stop = label(activity, t("Остановить экспорт", "Stop Export"), theme.onSurface());
        stop.setGravity(Gravity.CENTER);
        stop.setTypeface(AndroidUtilities.bold());
        stop.setOnClickListener(v -> { export.stop(); sheet.dismiss(); });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48));
        stopParams.topMargin = AndroidUtilities.dp(6);
        root.addView(stop, stopParams);
        sheet.setCustomView(root);
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            NebulaExport.State s = export.state;
            percent.setText(s.percent + "%");
            bar.setProgress(s.percent);
            if (s.finished) {
                step.setText(s.error.isEmpty() && !s.stopped ? t("Готово · ", "Done · ") + s.folder : s.step);
                stop.setText(t("Закрыть", "Close"));
                stop.setOnClickListener(v -> sheet.dismiss());
            } else {
                step.setText(s.chatCount > 0 ? s.step + " · " + s.chatIndex + t(" из ", " of ") + s.chatCount : s.step);
            }
            chat.setText(s.chat);
            messages.setText(s.messageCount > 0 ? s.message + t(" из ", " of ") + s.messageCount : "");
            file.setText(s.file.isEmpty() ? "" : s.file + "  " + NebulaExportOptions.size(s.fileDone) + t(" из ", " of ") + NebulaExportOptions.size(s.fileTotal));
            if (!s.finished && sheet.isShowing()) AndroidUtilities.runOnUIThread(tick[0], 300);
        };
        sheet.setOnDismissListener(d -> { AndroidUtilities.cancelRunOnUIThread(tick[0]); if (column != null) rebuild(); });
        showDialog(sheet);
        tick[0].run();
    }
}
