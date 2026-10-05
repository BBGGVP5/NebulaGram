package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import java.util.ArrayList;
import java.util.UUID;

/** Explicit conversation roles, independent of the translation and transcription prompts. */
public final class NebulaAiRoles {
    private NebulaAiRoles() { }
    public static final class Role {
        public final String id, name, prompt, emoji;
        public final boolean preset;
        Role(String id, String name, String prompt, String emoji, boolean preset) { this.id=id;this.name=name;this.prompt=prompt;this.emoji=emoji;this.preset=preset; }
    }
    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_settings",0); }
    public static String selected() { return prefs().getString("selected_role", "assistant"); }
    public static ArrayList<Role> list() {
        ArrayList<Role> roles = new ArrayList<>();
        roles.add(new Role("assistant", "Assistant", "You are a helpful personal assistant. Reply clearly in the user's language.", "🤖", true));
        roles.add(new Role("summarizer", "Summarizer", "Summarize the supplied text or conversation accurately in the user's language. Preserve key facts, dates and decisions. Do not invent details.", "🔎", true));
        roles.add(new Role("proofreader", "Proofreader", "Proofread the supplied text while preserving the author's meaning, tone and language. Return the corrected text only.", "📝", true));
        try {
            JSONArray stored = new JSONArray(prefs().getString("roles", "[]"));
            for(int i=0;i<Math.min(40,stored.length());i++) {
                JSONObject row=stored.getJSONObject(i);String id=row.getString("id");
                if(!id.matches("[a-zA-Z0-9_-]{1,64}")||find(roles,id)!=null)continue;
                roles.add(new Role(id,row.getString("name"),row.getString("prompt"),row.optString("emoji","🤖"),false));
            }
        } catch(Exception ignored) { }
        return roles;
    }
    private static Role find(ArrayList<Role> roles,String id) {for(Role role:roles)if(role.id.equals(id))return role;return null;}
    public static Role current() {return find(list(),selected());}
    public static String prompt() {
        if(prefs().getString("selected_role", "").isEmpty())return "";
        Role role=current();return role==null?"":role.prompt;
    }
    public static void select(String id) {if(find(list(),id)!=null)prefs().edit().putString("selected_role",id).apply();}
    private static void write(ArrayList<Role> roles)throws Exception {
        JSONArray stored=new JSONArray();for(Role role:roles)if(!role.preset)stored.put(new JSONObject().put("id",role.id).put("name",role.name).put("prompt",role.prompt).put("emoji",role.emoji));
        prefs().edit().putString("roles",stored.toString()).apply();
    }
    public static Role save(String id,String name,String prompt,String emoji)throws Exception {
        if(name==null||name.trim().isEmpty()||name.length()>64||prompt==null||prompt.trim().isEmpty()||prompt.length()>20000||emoji==null||emoji.length()>64)throw new IllegalArgumentException("Invalid role");
        ArrayList<Role> roles=list();Role old=find(roles,id);
        if(old!=null&&old.preset)throw new IllegalArgumentException("Built-in role");
        if(id==null||id.isEmpty())id=UUID.randomUUID().toString();
        if(!id.matches("[a-zA-Z0-9_-]{1,64}")||old==null&&roles.size()>=43)throw new IllegalArgumentException("Invalid role");
        Role role=new Role(id,name.trim(),prompt.trim(),emoji.trim().isEmpty()?"🤖":emoji.trim(),false);
        if(old!=null)roles.remove(old);roles.add(role);write(roles);return role;
    }
    public static void delete(String id)throws Exception {
        ArrayList<Role> roles=list();Role role=find(roles,id);if(role==null||role.preset)return;
        roles.remove(role);write(roles);if(selected().equals(id))select("assistant");
    }
}
