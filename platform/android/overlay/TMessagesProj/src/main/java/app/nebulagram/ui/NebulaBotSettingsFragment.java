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
    private NebulaSettingsHero hero;
    private NebulaRow languageRow;
    private EditText name, about, description;
    private String languageCode = "", loadedLanguage;
    private static final String[] LANGUAGES = {"", "ru", "en", "uk", "be", "kk", "uz", "de", "es", "fr", "it", "pt", "tr", "ar", "fa", "id", "ja", "ko", "zh"};
    public NebulaBotSettingsFragment(int account, long botId) { currentAccount = account; this.botId = botId; owner = UserConfig.getInstance(account).getClientUserId(); }
    private boolean botAccount() { TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser(); return user != null && user.bot && user.id == botId; }
    private boolean current(int token) { return !destroyed && generation == token && owner == UserConfig.getInstance(currentAccount).getClientUserId(); }
    private void status(String value) { if (hero != null) hero.setStatus(value); }
    private static String languageTitle(String code) {
        if (code.isEmpty()) return text("Основной", "Default");
        java.util.Locale locale = new java.util.Locale(code);
        String title = locale.getDisplayLanguage(java.util.Locale.getDefault());
        return title.isEmpty() ? code : Character.toUpperCase(title.charAt(0)) + title.substring(1) + " · " + code;
    }
    private EditText field(NebulaCard card, String hint, int lines, int max) {
        EditText e = NebulaFormUi.cardField(card.getContext(), hint, lines, max); card.add(e); return e;
    }
    private NebulaRow action(int icon, String title, String subtitle, Runnable run) {
        return NebulaFormUi.action(getContext(), icon, title, subtitle, v -> { if (!busy) run.run(); });
    }
    /**
     * Шапка с аватаркой и именем бота, ниже карточки: язык профиля, сам
     * профиль с кнопкой сохранения, управление и звёзды. Ход запросов виден в
     * строке состояния шапки, а не отдельной надписью внизу.
     */
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Настройки бота", "Bot settings"));
        body = NebulaFormUi.column(c); ScrollView scroll = new ScrollView(c); scroll.addView(body);
        TLRPC.User bot = getMessagesController().getUser(botId);
        String username = bot == null ? null : UserObject.getPublicUsername(bot);
        body.addView(new NebulaAccountRow(c, currentAccount, bot, bot == null ? text("Бот", "Bot") : UserObject.getUserName(bot),
                username == null || username.isEmpty() ? "" : "@" + username, false, false));
        hero = new NebulaSettingsHero(c, R.drawable.msg_bots, text("Профиль бота", "Bot profile"),
                text("Имя, краткое и полное описание — для каждого языка отдельно.",
                        "Name, about and description, separately for each language."));
        body.addView(hero);

        NebulaCard languageCard = new NebulaCard(c);
        languageRow = new NebulaRow(c).icon(R.drawable.msg_language).title(text("Язык профиля", "Profile language"))
                .value(languageTitle(languageCode)).trailing(NebulaRow.TRAIL_CHEVRON);
        languageRow.setOnClickListener(v -> chooseLanguage());
        languageCard.add(languageRow);
        NebulaFormUi.group(body, text("Перевод", "Translation"), languageCard);

        NebulaCard profile = new NebulaCard(c);
        name = field(profile, text("Имя", "Name"), 1, 64);
        about = field(profile, text("Краткое описание", "About"), 2, 120);
        description = field(profile, text("Описание при запуске", "Description shown on start"), 3, 512);
        NebulaFormUi.group(body, text("Профиль", "Profile"), profile);
        body.addView(NebulaFormUi.primary(c, text("Сохранить", "Save"), v -> { if (!busy) saveProfile(); }));

        NebulaCard manage = new NebulaCard(c);
        if (botAccount()) {
            manage.add(action(R.drawable.msg_list, text("Кнопка меню", "Menu button"), text("Mini App или команды", "Mini App or commands"), this::menu));
            manage.add(action(R.drawable.msg_groups, text("Права в группах", "Group admin rights"), text("Запрашиваются при добавлении", "Requested when added"), () -> rights(false)));
            manage.add(action(R.drawable.msg_channel, text("Права в каналах", "Channel admin rights"), text("Запрашиваются при добавлении", "Requested when added"), () -> rights(true)));
            manage.add(action(R.drawable.msg_link2, text("Бизнес-подключение", "Business connection"), text("Проверить по ID", "Inspect by ID"), this::connection));
        }
        manage.add(action(R.drawable.settings_stars, text("Звёзды", "Stars"), text("Баланс и вывод", "Balance and withdrawal"), () -> {
            BotStarsActivity stars = new BotStarsActivity(currentAccount, BotStarsActivity.TYPE_STARS, botId); presentFragment(stars);
        }));
        NebulaFormUi.group(body, text("Управление", "Management"), manage);
        body.addView(NebulaFormUi.note(c, botAccount()
            ? text("Изменения применяются к текущему боту. Права по умолчанию не меняют права в существующих чатах.", "Changes apply to the current bot. Default rights do not change existing chats.")
            : text("Кнопка меню и права по умолчанию настраиваются, только когда вы вошли аккаунтом бота.", "Menu buttons and default rights are available only when signed in as the bot.")));
        fragmentView = NebulaSettingsLayout.wrap(c, actionBar, scroll); loadProfile(); return fragmentView;
    }
    private void chooseLanguage() {
        CharSequence[] titles = new CharSequence[LANGUAGES.length];
        int selected = 0;
        for (int i = 0; i < LANGUAGES.length; i++) { titles[i] = languageTitle(LANGUAGES[i]); if (LANGUAGES[i].equals(languageCode)) selected = i; }
        showDialog(new NebulaDialog.Builder(getContext()).setTitle(text("Язык профиля", "Profile language"))
            .setSelectedIndex(selected).setItems(titles, (d, which) -> {
                languageCode = LANGUAGES[which]; languageRow.value(languageTitle(languageCode)); loadProfile();
            }).create());
    }
    private void call(TLObject query, Consumer<TLObject> done) {
        if (busy || destroyed || owner == 0 || owner != UserConfig.getInstance(currentAccount).getClientUserId()) return;
        busy = true; final int token = ++generation; status(text("Загрузка…", "Loading…"));
        request = getConnectionsManager().sendRequest(query, (result, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!current(token)) return; busy = false; request = 0;
            if (error != null || result == null || result instanceof TLRPC.TL_boolFalse) { status(text("Telegram отклонил запрос: ", "Telegram rejected the request: ") + (error == null ? "FAILED" : error.text)); return; }
            status(text("Готово", "Done")); done.accept(result);
        }));
    }
    private String lang() { return languageCode; }
    private boolean validLanguage(String value) { return value.isEmpty() || value.matches("[a-z]{2}"); }
    private void loadProfile() {
        String code = lang(); if (!validLanguage(code)) { status(text("Нужен код языка из двух букв", "Use a two-letter language code")); return; }
        NebulaBotApi.GetProfile query = new NebulaBotApi.GetProfile(); query.language = code;
        if (!botAccount()) query.bot = getMessagesController().getInputUser(botId);
        call(query, result -> {
            if (!(result instanceof NebulaBotApi.Profile)) return;
            NebulaBotApi.Profile profile = (NebulaBotApi.Profile) result;
            loadedLanguage = code; name.setText(profile.name); about.setText(profile.about); description.setText(profile.description);
        });
    }
    private void saveProfile() {
        String code = lang(); if (!validLanguage(code) || loadedLanguage == null) { status(text("Профиль ещё загружается", "The profile is still loading")); return; }
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
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || title.getText().toString().trim().isEmpty()) { status(text("Укажите название и HTTPS-адрес", "Enter a label and HTTPS URL")); return; }
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
