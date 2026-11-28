package app.nebulagram.ui;

import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Stars.BotStarsActivity;
import java.util.function.Consumer;
import static app.nebulagram.ui.NebulaText.text;

/** Bot-only controls are shown only when the active account is the bot itself. */
public final class NebulaBotSettingsFragment extends BaseFragment {
    private final long botId, owner;
    private boolean destroyed, busy;
    private int request, generation;
    private LinearLayout body;
    private TextView status;
    private EditText language, name, about, description;
    private String loadedLanguage;
    public NebulaBotSettingsFragment(int account, long botId) { currentAccount = account; this.botId = botId; owner = UserConfig.getInstance(account).getClientUserId(); }
    private boolean botAccount() { TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser(); return user != null && user.bot && user.id == botId; }
    private boolean current(int token) { return !destroyed && generation == token && owner == UserConfig.getInstance(currentAccount).getClientUserId(); }
    private EditText field(Context c, String hint, int max) {
        EditText e = new EditText(c); e.setHint(hint); e.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); body.addView(e); return e;
    }
    private void row(Context c, String title, Runnable action) { body.addView(new NebulaRow(c).title(title).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> { if (!busy) action.run(); })); }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Настройки бота", "Bot settings"));
        body = NebulaFormUi.column(c); ScrollView scroll = new ScrollView(c); scroll.addView(body);
        language = field(c, text("Язык перевода: ru, en; пусто — основной", "Translation language: ru, en; empty for default"), 2); language.setSingleLine(true);
        name = field(c, text("Имя", "Name"), 64); about = field(c, text("Краткое описание", "About"), 120); description = field(c, text("Описание при запуске", "Description"), 512);
        row(c, text("Загрузить профиль для этого языка", "Load profile for this language"), this::loadProfile);
        row(c, text("Сохранить профиль / перевод", "Save profile / translation"), this::saveProfile);
        if (botAccount()) {
            row(c, text("Кнопка меню", "Menu button"), this::menu);
            row(c, text("Права по умолчанию в группах", "Default group admin rights"), () -> rights(false));
            row(c, text("Права по умолчанию в каналах", "Default channel admin rights"), () -> rights(true));
            row(c, text("Бизнес-подключение по ID", "Business connection by ID"), this::connection);
        }
        row(c, text("Звёзды", "Stars"), () -> {
            BotStarsActivity stars = new BotStarsActivity(currentAccount, BotStarsActivity.TYPE_STARS, botId); presentFragment(stars);
        });
        body.addView(NebulaFormUi.note(c, botAccount()
            ? text("Изменения применяются к текущему боту. Права по умолчанию не меняют права в существующих чатах.", "Changes apply to the current bot. Default rights do not change existing chats.")
            : text("Для управляемого бота доступны профиль, переводы и звёзды. Кнопка меню и права требуют входа аккаунтом бота.", "Managed bots support profiles, translations and stars. Menu buttons and default rights require a bot account.")));
        status = new TextView(c); status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)); body.addView(status);
        fragmentView = NebulaSettingsLayout.wrap(c, actionBar, scroll); loadProfile(); return fragmentView;
    }
    private void call(TLObject query, Consumer<TLObject> done) {
        if (busy || destroyed || owner == 0 || owner != UserConfig.getInstance(currentAccount).getClientUserId()) return;
        busy = true; final int token = ++generation; status.setText(text("Выполняется…", "Working…"));
        request = getConnectionsManager().sendRequest(query, (result, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!current(token)) return; busy = false; request = 0;
            if (error != null || result == null || result instanceof TLRPC.TL_boolFalse) { status.setText(text("Telegram отклонил запрос: ", "Telegram rejected the request: ") + (error == null ? "FAILED" : error.text)); return; }
            status.setText(text("Готово", "Done")); done.accept(result);
        }));
    }
    private String lang() { return language.getText().toString().trim().toLowerCase(java.util.Locale.ROOT); }
    private boolean validLanguage(String value) { return value.isEmpty() || value.matches("[a-z]{2}"); }
    private void loadProfile() {
        String code = lang(); if (!validLanguage(code)) { status.setText(text("Нужен код языка из двух букв", "Use a two-letter language code")); return; }
        NebulaBotApi.GetProfile query = new NebulaBotApi.GetProfile(); query.language = code;
        if (!botAccount()) query.bot = getMessagesController().getInputUser(botId);
        call(query, result -> {
            if (!(result instanceof NebulaBotApi.Profile)) return;
            NebulaBotApi.Profile profile = (NebulaBotApi.Profile) result;
            language.setText(code); loadedLanguage = code; name.setText(profile.name); about.setText(profile.about); description.setText(profile.description);
        });
    }
    private void saveProfile() {
        String code = lang(); if (!validLanguage(code) || loadedLanguage == null) { status.setText(text("Сначала загрузите профиль", "Load a profile first")); return; }
        TL_bots.setBotInfo q = new TL_bots.setBotInfo(); q.lang_code = code; q.name = name.getText().toString(); q.about = about.getText().toString(); q.description = description.getText().toString(); q.flags = 8 | 1 | 2;
        if (!botAccount()) { q.bot = getMessagesController().getInputUser(botId); q.flags |= 4; }
        showDialog(new NebulaDialog.Builder(getContext()).setTitle(text("Сохранить профиль?", "Save profile?"))
            .setMessage(text("Язык: ", "Language: ") + (code.isEmpty() ? text("основной", "default") : code))
            .setPositiveButton(text("Сохранить", "Save"), (d, w) -> call(q, result -> loadedLanguage = code)).setNegativeButton(text("Отмена", "Cancel"), null).create());
    }
    private void menu() {
        TL_bots.getBotMenuButton q = new TL_bots.getBotMenuButton(); q.user_id = new TLRPC.TL_inputUserEmpty();
        call(q, result -> {
            LinearLayout form = new LinearLayout(getContext()); form.setOrientation(LinearLayout.VERTICAL);
            EditText title = new EditText(getContext()), url = new EditText(getContext()); title.setHint(text("Название кнопки", "Button label")); url.setHint("https://…");
            title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)}); url.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
            if (result instanceof TL_bots.TL_botMenuButton) { title.setText(((TL_bots.TL_botMenuButton) result).text); url.setText(((TL_bots.TL_botMenuButton) result).url); }
            form.addView(title); form.addView(url);
            showDialog(new NebulaDialog.Builder(getContext()).setTitle(text("Кнопка меню", "Menu button")).setView(form)
                .setPositiveButton(text("Открывать Mini App", "Open Mini App"), (d, w) -> {
                    android.net.Uri uri = android.net.Uri.parse(url.getText().toString().trim());
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || title.getText().toString().trim().isEmpty()) { status.setText(text("Укажите название и HTTPS-адрес", "Enter a label and HTTPS URL")); return; }
                    TL_bots.TL_botMenuButton button = new TL_bots.TL_botMenuButton(); button.text = title.getText().toString().trim(); button.url = uri.toString(); setMenu(button);
                }).setNeutralButton(text("Команды", "Commands"), (d,w) -> setMenu(new TL_bots.TL_botMenuButtonCommands()))
                .setNegativeButton(text("По умолчанию", "Default"), (d,w) -> setMenu(new TL_bots.TL_botMenuButtonDefault())).create());
        });
    }
    private void setMenu(TL_bots.BotMenuButton button) { TL_bots.setBotMenuButton q = new TL_bots.setBotMenuButton(); q.user_id = new TLRPC.TL_inputUserEmpty(); q.button = button; call(q, result -> { }); }
    private void rights(boolean channel) {
        TLRPC.TL_users_getFullUser query = new TLRPC.TL_users_getFullUser(); query.id = new TLRPC.TL_inputUserSelf();
        call(query, response -> {
            if (!(response instanceof TLRPC.TL_users_userFull)) return;
            TLRPC.UserFull full = ((TLRPC.TL_users_userFull) response).full_user;
            TLRPC.TL_chatAdminRights existing = channel ? full.bot_broadcast_admin_rights : full.bot_group_admin_rights;
            TLRPC.TL_chatAdminRights copy = new TLRPC.TL_chatAdminRights();
            if (existing != null) {
                SerializedData out = new SerializedData(); existing.serializeToStream(out); SerializedData in = new SerializedData(out.toByteArray());
                copy = TLRPC.TL_chatAdminRights.TLdeserialize(in, in.readInt32(true), true); in.cleanup(); out.cleanup();
            }
            final TLRPC.TL_chatAdminRights value = copy;
            String[] fields = channel ? new String[]{"change_info","post_messages","edit_messages","delete_messages","invite_users","add_admins","post_stories","edit_stories","delete_stories","manage_direct_messages"}
                : new String[]{"change_info","delete_messages","ban_users","invite_users","pin_messages","manage_call","manage_topics","add_admins","anonymous"};
            String[] names = channel ? new String[]{text("Изменять профиль", "Change info"),text("Публиковать", "Post messages"),text("Редактировать", "Edit messages"),text("Удалять", "Delete messages"),text("Приглашать", "Invite users"),text("Назначать администраторов", "Add admins"),text("Публиковать истории", "Post stories"),text("Редактировать истории", "Edit stories"),text("Удалять истории", "Delete stories"),text("Личные сообщения", "Direct messages")}
                : new String[]{text("Изменять профиль", "Change info"),text("Удалять сообщения", "Delete messages"),text("Блокировать участников", "Ban users"),text("Приглашать", "Invite users"),text("Закреплять", "Pin messages"),text("Управлять звонками", "Manage calls"),text("Управлять темами", "Manage topics"),text("Назначать администраторов", "Add admins"),text("Анонимность", "Anonymous")};
            LinearLayout form = new LinearLayout(getContext()); form.setOrientation(LinearLayout.VERTICAL);
            for (int i=0;i<fields.length;i++) {
                final String field = fields[i];
                CheckBox check = new CheckBox(getContext()); check.setText(names[i]); check.setChecked(right(value, field));
                check.setOnCheckedChangeListener((button,on) -> setRight(value, field, on)); form.addView(check);
            }
            ScrollView scroll = new ScrollView(getContext()); scroll.addView(form);
            showDialog(new NebulaDialog.Builder(getContext()).setTitle(text("Запрашиваемые права", "Requested rights")).setView(scroll)
                .setPositiveButton(text("Сохранить", "Save"), (d,w) -> { NebulaBotApi.SetRights q = new NebulaBotApi.SetRights(); q.channel=channel; q.rights=value; call(q,result -> { }); })
                .setNegativeButton(text("Отмена", "Cancel"), null).create());
        });
    }
    private static boolean right(TLRPC.TL_chatAdminRights rights, String name) {
        switch (name) {
            case "change_info": return rights.change_info;
            case "post_messages": return rights.post_messages;
            case "edit_messages": return rights.edit_messages;
            case "delete_messages": return rights.delete_messages;
            case "invite_users": return rights.invite_users;
            case "add_admins": return rights.add_admins;
            case "post_stories": return rights.post_stories;
            case "edit_stories": return rights.edit_stories;
            case "delete_stories": return rights.delete_stories;
            case "manage_direct_messages": return rights.manage_direct_messages;
            case "ban_users": return rights.ban_users;
            case "pin_messages": return rights.pin_messages;
            case "manage_call": return rights.manage_call;
            case "manage_topics": return rights.manage_topics;
            case "anonymous": return rights.anonymous;
            default: return false;
        }
    }
    private static void setRight(TLRPC.TL_chatAdminRights rights, String name, boolean value) {
        switch (name) {
            case "change_info": rights.change_info = value; break;
            case "post_messages": rights.post_messages = value; break;
            case "edit_messages": rights.edit_messages = value; break;
            case "delete_messages": rights.delete_messages = value; break;
            case "invite_users": rights.invite_users = value; break;
            case "add_admins": rights.add_admins = value; break;
            case "post_stories": rights.post_stories = value; break;
            case "edit_stories": rights.edit_stories = value; break;
            case "delete_stories": rights.delete_stories = value; break;
            case "manage_direct_messages": rights.manage_direct_messages = value; break;
            case "ban_users": rights.ban_users = value; break;
            case "pin_messages": rights.pin_messages = value; break;
            case "manage_call": rights.manage_call = value; break;
            case "manage_topics": rights.manage_topics = value; break;
            case "anonymous": rights.anonymous = value; break;
        }
    }
    private void connection() {
        EditText id = new EditText(getContext()); id.setHint(text("ID бизнес-подключения", "Business connection ID")); id.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512)});
        showDialog(new NebulaDialog.Builder(getContext()).setTitle(text("Проверить бизнес-подключение", "Inspect business connection")).setView(id)
            .setPositiveButton(text("Проверить", "Inspect"), (d,w) -> {
                if (id.getText().toString().trim().isEmpty()) return;
                NebulaBotApi.GetConnection q = new NebulaBotApi.GetConnection(); q.id=id.getText().toString().trim();
                call(q, result -> {
                    NebulaBotApi.Connection connection=(NebulaBotApi.Connection)result;
                    String details=text("Пользователь: ", "User: ")+connection.user+"\n"+(connection.disabled?text("Отключено", "Disabled"):text("Активно", "Active"));
                    if (connection.rights!=null) details+="\n"+text("Ответы: ", "Replies: ")+connection.rights.reply+"\n"+text("Чтение: ", "Read: ")+connection.rights.read_messages;
                    showDialog(new NebulaDialog.Builder(getContext()).setTitle(text("Бизнес-подключение", "Business connection")).setMessage(details).setPositiveButton(text("Закрыть", "Close"),null).create());
                });
            }).setNegativeButton(text("Отмена", "Cancel"),null).create());
    }
    @Override public void onFragmentDestroy() { destroyed=true;generation++;if(request!=0)getConnectionsManager().cancelRequest(request,true);super.onFragmentDestroy(); }
}
