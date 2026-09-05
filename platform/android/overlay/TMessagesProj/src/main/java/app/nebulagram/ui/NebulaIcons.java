package app.nebulagram.ui;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
/** iOS-inspired interface glyphs; artwork, emoji and the NebulaGram mark remain original. */
public final class NebulaIcons {
    private NebulaIcons() { }
    private static final android.util.SparseIntArray ICONS = new android.util.SparseIntArray();
    static {
        ICONS.put(R.drawable.msg_settings, R.drawable.nebula_settings_mark);
        ICONS.put(R.drawable.ic_ab_back, R.drawable.nebula_ios_back);
        ICONS.put(R.drawable.msg_search, R.drawable.nebula_ios_search);
        ICONS.put(R.drawable.msg_input_attach2, R.drawable.nebula_ios_attach);
        ICONS.put(R.drawable.input_mic, R.drawable.nebula_ios_mic);
        ICONS.put(R.drawable.msg_voice_unmuted, R.drawable.nebula_ios_mic);
        ICONS.put(R.drawable.msg_emoji_smiles, R.drawable.nebula_ios_smile);
        ICONS.put(R.drawable.msg_emoji_recent, R.drawable.nebula_ios_smile);
        ICONS.put(R.drawable.msg_discussion, R.drawable.nebula_ios_chat);
        ICONS.put(R.drawable.msg_message, R.drawable.nebula_ios_chat);
        ICONS.put(R.drawable.settings_chat, R.drawable.nebula_ios_chat);
        ICONS.put(R.drawable.msg_openprofile, R.drawable.nebula_ios_profile);
        ICONS.put(R.drawable.msg_contacts, R.drawable.nebula_ios_profile);
        ICONS.put(R.drawable.settings_account, R.drawable.nebula_ios_profile);
        ICONS.put(R.drawable.msg_calls, R.drawable.nebula_ios_phone);
        ICONS.put(R.drawable.msg_calls_regular, R.drawable.nebula_ios_phone);
        ICONS.put(R.drawable.msg_callback, R.drawable.nebula_ios_phone);
        ICONS.put(R.drawable.profile_phone, R.drawable.nebula_ios_phone);
        ICONS.put(R.drawable.msg_folders, R.drawable.nebula_ios_folder);
        ICONS.put(R.drawable.files_folder, R.drawable.nebula_ios_folder);
        ICONS.put(R.drawable.settings_folders, R.drawable.nebula_ios_folder);
        ICONS.put(R.drawable.msg_saved, R.drawable.nebula_ios_saved);
        ICONS.put(R.drawable.msg_archive, R.drawable.nebula_ios_archive);
        ICONS.put(R.drawable.msg_archive_archive, R.drawable.nebula_ios_archive);
        ICONS.put(R.drawable.msg_add, R.drawable.nebula_ios_add);
        ICONS.put(R.drawable.msg_addbot, R.drawable.nebula_ios_add);
        ICONS.put(R.drawable.msg_addcontact, R.drawable.nebula_ios_add);
        ICONS.put(R.drawable.msg_filled_plus, R.drawable.nebula_ios_add);
        ICONS.put(R.drawable.msg_delete, R.drawable.nebula_ios_delete);
        ICONS.put(R.drawable.msg_delete_auto, R.drawable.nebula_ios_delete);
        ICONS.put(R.drawable.msg_clear_recent, R.drawable.nebula_ios_delete);
        ICONS.put(R.drawable.msg_clearcache, R.drawable.nebula_ios_delete);
        ICONS.put(R.drawable.msg_clear, R.drawable.nebula_ios_delete);
        ICONS.put(R.drawable.msg_copy, R.drawable.nebula_ios_copy);
        ICONS.put(R.drawable.ic_ab_reply, R.drawable.nebula_ios_reply);
        ICONS.put(R.drawable.msg_reply_small, R.drawable.nebula_ios_reply);
        ICONS.put(R.drawable.msg_forward, R.drawable.nebula_ios_forward);
        ICONS.put(R.drawable.msg_share, R.drawable.nebula_ios_forward);
        ICONS.put(R.drawable.msg_shareout, R.drawable.nebula_ios_forward);
        ICONS.put(R.drawable.msg_link, R.drawable.nebula_ios_link);
        ICONS.put(R.drawable.msg_link2, R.drawable.nebula_ios_link);
        ICONS.put(R.drawable.msg_download, R.drawable.nebula_ios_download);
        ICONS.put(R.drawable.menu_download_round, R.drawable.nebula_ios_download);
        ICONS.put(R.drawable.settings_data, R.drawable.nebula_ios_download);
        ICONS.put(R.drawable.msg_notifications, R.drawable.nebula_ios_bell);
        ICONS.put(R.drawable.msg_unmute, R.drawable.nebula_ios_bell);
        ICONS.put(R.drawable.settings_sounds, R.drawable.nebula_ios_bell);
        ICONS.put(R.drawable.msg_mute, R.drawable.nebula_ios_mute);
        ICONS.put(R.drawable.msg_edit, R.drawable.nebula_ios_edit);
        ICONS.put(R.drawable.msg_camera, R.drawable.nebula_ios_camera);
        ICONS.put(R.drawable.camera, R.drawable.nebula_ios_camera);
        ICONS.put(R.drawable.msg_gallery, R.drawable.nebula_ios_photo);
        ICONS.put(R.drawable.msg_photos, R.drawable.nebula_ios_photo);
        ICONS.put(R.drawable.msg_video, R.drawable.nebula_ios_video);
        ICONS.put(R.drawable.input_video, R.drawable.nebula_ios_video);
        ICONS.put(R.drawable.msg_secret, R.drawable.nebula_ios_lock);
        ICONS.put(R.drawable.settings_privacy, R.drawable.nebula_ios_lock);
        ICONS.put(R.drawable.msg_language, R.drawable.nebula_ios_globe);
        ICONS.put(R.drawable.settings_language, R.drawable.nebula_ios_globe);
        ICONS.put(R.drawable.settings_devices, R.drawable.nebula_ios_device);
        ICONS.put(R.drawable.msg_customize, R.drawable.nebula_ios_sliders);
        ICONS.put(R.drawable.msg_photo_settings, R.drawable.nebula_ios_sliders);
        ICONS.put(R.drawable.msg_actions, R.drawable.nebula_ios_sliders);
        ICONS.put(R.drawable.msg_download_settings, R.drawable.nebula_ios_sliders);
        ICONS.put(R.drawable.msg_list, R.drawable.nebula_ios_list);
        ICONS.put(R.drawable.msg_close, R.drawable.nebula_ios_close);
        ICONS.put(R.drawable.msg_cancel, R.drawable.nebula_ios_close);
        ICONS.put(R.drawable.msg_clear_input, R.drawable.nebula_ios_close);
        ICONS.put(R.drawable.msg_text_check, R.drawable.nebula_ios_check);
        ICONS.put(R.drawable.msg_check_s, R.drawable.nebula_ios_check);
        ICONS.put(R.drawable.msg_gift_premium, R.drawable.nebula_ios_gift);
        ICONS.put(R.drawable.msg_fave, R.drawable.nebula_ios_star);
        ICONS.put(R.drawable.msg_premium_liststar, R.drawable.nebula_ios_star);
        ICONS.put(R.drawable.settings_premium, R.drawable.nebula_ios_star);
        ICONS.put(R.drawable.msg_info, R.drawable.nebula_ios_info);
        ICONS.put(R.drawable.settings_power, R.drawable.nebula_ios_power);
        ICONS.put(R.drawable.input_keyboard, R.drawable.nebula_ios_keyboard);
        ICONS.put(R.drawable.msg_sticker, R.drawable.nebula_ios_sticker);
    }
    private static Boolean selected;
    public static boolean enabled() {
        if (selected == null && ApplicationLoader.applicationContext != null) {
            selected = ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).getBoolean("ios_icons", true);
        }
        return selected == null || selected;
    }
    public static void setEnabled(boolean value) {
        selected = value;
        ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).edit().putBoolean("ios_icons", value).apply();
    }
    public static int resource(int original) {
        if (!enabled()) return original;
        return ICONS.get(original, original);
    }
}
