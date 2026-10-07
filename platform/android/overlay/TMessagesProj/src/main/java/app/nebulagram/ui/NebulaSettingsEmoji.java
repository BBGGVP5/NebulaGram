package app.nebulagram.ui;

import org.telegram.messenger.R;

/** Settings use emoji; messenger icon packs remain available for native controls. */
final class NebulaSettingsEmoji {
    private NebulaSettingsEmoji(){}
    static String forIcon(int resource){
        // Match source resources too: a selected messenger icon pack can remap them.
        if(resource==R.drawable.msg_customize||resource==R.drawable.nebula_settings_appearance)return "🎨";
        if(resource==R.drawable.msg_settings||resource==R.drawable.nebula_settings_general)return "⚙️";
        if(resource==R.drawable.msg_link||resource==R.drawable.nebula_settings_link)return "🔗";
        if(resource==R.drawable.msg_info||resource==R.drawable.nebula_settings_about)return "ℹ️";
        if(resource==R.drawable.msg_saved||resource==R.drawable.nebula_settings_sync)return "🔄";
        if(resource==R.drawable.msg_openprofile||resource==R.drawable.nebula_settings_profile)return "👤";
        if(resource==R.drawable.msg_calendar||resource==R.drawable.nebula_settings_tasks)return "✅";
        if(resource==R.drawable.msg_emoji_smiles||resource==R.drawable.nebula_settings_ai)return "🤖";
        if(resource==R.drawable.msg_folders||resource==R.drawable.files_folder||resource==R.drawable.nebula_settings_folder)return "🗂";
        if(resource==R.drawable.msg_secret||resource==R.drawable.nebula_settings_privacy||resource==R.drawable.nebula_settings_chat_lock)return "🔐";
        if(resource==R.drawable.msg_discussion||resource==R.drawable.msg_discuss||resource==R.drawable.nebula_settings_chat)return "💬";
        if(resource==R.drawable.menu_reply||resource==R.drawable.nebula_settings_messages)return "✉️";
        if(resource==R.drawable.msg_language||resource==R.drawable.nebula_settings_language)return "🌐";
        if(resource==R.drawable.nebula_settings_app_icon)return "🚀";
        if(resource==R.drawable.nebula_settings_text_tools)return "🧰";
        if(resource==R.drawable.msg_notifications||resource==R.drawable.nebula_settings_notifications)return "🔔";
        if(resource==R.drawable.nebula_link_shield)return "🔗";
        if(resource==R.drawable.msg_speed)return "🚀";
        if(resource==R.drawable.msg_retry||resource==R.drawable.msg_reset)return "🔄";
        if(resource==R.drawable.msg_download)return "📥";
        if(resource==R.drawable.msg_edit)return "📝";
        if(resource==R.drawable.msg_delete||resource==R.drawable.delete)return "🗑️";
        if(resource==R.drawable.msg_stats)return "📊";
        if(resource==R.drawable.msg_list)return "📋";
        if(resource==R.drawable.msg_search)return "🔎";
        if(resource==R.drawable.msg_permissions)return "🔐";
        if(resource==R.drawable.msg_copy)return "📋";
        if(resource==R.drawable.msg_recent)return "🕓";
        if(resource==R.drawable.msg_photo_settings)return "📷";
        if(resource==R.drawable.msg_link2)return "🔗";
        if(resource==R.drawable.msg_videocall)return "📹";
        if(resource==R.drawable.nebula_settings_support)return "💝";
        if(resource==R.drawable.nebula_ai_spark)return "🤖";
        if(resource==R.drawable.msg_translate)return "🌐";
        if(resource==R.drawable.nebula_cupertino_photo)return "🖼️";
        if(resource==R.drawable.nebula_cupertino_person)return "👤";
        if(resource==R.drawable.nebula_cupertino_list)return "📋";
        if(resource==R.drawable.nebula_cupertino_sliders)return "🎚️";
        if(resource==R.drawable.nebula_cupertino_edit)return "📝";
        if(resource==R.drawable.nebula_cupertino_bell)return "🔔";
        if(resource==R.drawable.nebula_cupertino_gear)return "⚙️";
        if(resource==R.drawable.nebula_cupertino_lock)return "🔐";
        if(resource==R.drawable.nebula_cupertino_folder)return "🗂️";
        return null;
    }
}
