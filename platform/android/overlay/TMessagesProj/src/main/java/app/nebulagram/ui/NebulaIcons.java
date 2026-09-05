package app.nebulagram.ui;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
/** Original iOS-inspired controls. Emoji, stickers and message status assets remain native. */
public final class NebulaIcons {
    private NebulaIcons() { }
    private static final android.util.SparseIntArray ICONS = new android.util.SparseIntArray();
    static {
        ICONS.put(R.drawable.ic_ab_back, R.drawable.nebula_cupertino_back);
        ICONS.put(R.drawable.msg_discussion, R.drawable.nebula_cupertino_chat);
        ICONS.put(R.drawable.msg_discuss, R.drawable.nebula_cupertino_chat);
        ICONS.put(R.drawable.msg_msgbubble, R.drawable.nebula_cupertino_chat);
        ICONS.put(R.drawable.settings_chat, R.drawable.nebula_cupertino_chat);
        ICONS.put(R.drawable.msg_saved, R.drawable.nebula_cupertino_bookmark);
        ICONS.put(R.drawable.files_folder, R.drawable.nebula_cupertino_folder);
        ICONS.put(R.drawable.msg_folders, R.drawable.nebula_cupertino_folder);
        ICONS.put(R.drawable.msg_search, R.drawable.nebula_cupertino_search);
        ICONS.put(R.drawable.msg_openprofile, R.drawable.nebula_cupertino_person);
        ICONS.put(R.drawable.msg_contacts, R.drawable.nebula_cupertino_person);
        ICONS.put(R.drawable.msg_settings, R.drawable.nebula_cupertino_gear);
        ICONS.put(R.drawable.msg_notifications, R.drawable.nebula_cupertino_bell);
        ICONS.put(R.drawable.msg_secret, R.drawable.nebula_cupertino_lock);
        ICONS.put(R.drawable.msg_permissions, R.drawable.nebula_cupertino_lock);
        ICONS.put(R.drawable.msg_calls, R.drawable.nebula_cupertino_phone);
        ICONS.put(R.drawable.msg_videocall, R.drawable.nebula_cupertino_video);
        ICONS.put(R.drawable.input_mic, R.drawable.nebula_cupertino_mic);
        ICONS.put(R.drawable.input_attach, R.drawable.nebula_cupertino_attach);
        ICONS.put(R.drawable.msg_gallery, R.drawable.nebula_cupertino_photo);
        ICONS.put(R.drawable.msg_photos, R.drawable.nebula_cupertino_photo);
        ICONS.put(R.drawable.msg_camera, R.drawable.nebula_cupertino_camera);
        ICONS.put(R.drawable.camera, R.drawable.nebula_cupertino_camera);
        ICONS.put(R.drawable.msg_archive, R.drawable.nebula_cupertino_archive);
        ICONS.put(R.drawable.msg_forward, R.drawable.nebula_cupertino_forward);
        ICONS.put(R.drawable.msg_copy, R.drawable.nebula_cupertino_copy);
        ICONS.put(R.drawable.msg_delete, R.drawable.nebula_cupertino_trash);
        ICONS.put(R.drawable.msg_edit, R.drawable.nebula_cupertino_edit);
        ICONS.put(R.drawable.msg_share, R.drawable.nebula_cupertino_share);
        ICONS.put(R.drawable.msg_customize, R.drawable.nebula_cupertino_sliders);
        ICONS.put(R.drawable.msg_photo_settings, R.drawable.nebula_cupertino_sliders);
        ICONS.put(R.drawable.msg_language, R.drawable.nebula_cupertino_globe);
        ICONS.put(R.drawable.msg_info, R.drawable.nebula_cupertino_info);
        ICONS.put(R.drawable.msg_add, R.drawable.nebula_cupertino_add);
        ICONS.put(R.drawable.msg_list, R.drawable.nebula_cupertino_list);
        ICONS.put(R.drawable.msg_download, R.drawable.nebula_cupertino_download);
        ICONS.put(R.drawable.msg_link, R.drawable.nebula_cupertino_link);
    }
    static {
        ICONS.put(R.drawable.menu_reply, R.drawable.nebula_cupertino_reply);
        ICONS.put(R.drawable.msg_sendfile, R.drawable.nebula_cupertino_file);
        ICONS.put(R.drawable.msg_settings_old, R.drawable.nebula_cupertino_gear);
        ICONS.put(R.drawable.settings_account, R.drawable.nebula_cupertino_person);
        ICONS.put(R.drawable.settings_data, R.drawable.nebula_cupertino_download);
        ICONS.put(R.drawable.settings_devices, R.drawable.nebula_cupertino_devices);
        ICONS.put(R.drawable.settings_folders, R.drawable.nebula_cupertino_folder);
        ICONS.put(R.drawable.settings_language, R.drawable.nebula_cupertino_globe);
        ICONS.put(R.drawable.settings_privacy, R.drawable.nebula_cupertino_lock);
        ICONS.put(R.drawable.settings_sounds, R.drawable.nebula_cupertino_bell);
        ICONS.put(R.drawable.msg_callback, R.drawable.nebula_cupertino_phone);
        ICONS.put(R.drawable.profile_phone, R.drawable.nebula_cupertino_phone);
        ICONS.put(R.drawable.profile_video, R.drawable.nebula_cupertino_video);
        ICONS.put(R.drawable.msg_shareout, R.drawable.nebula_cupertino_share);
        ICONS.put(R.drawable.msg_archive_archive, R.drawable.nebula_cupertino_archive);
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
