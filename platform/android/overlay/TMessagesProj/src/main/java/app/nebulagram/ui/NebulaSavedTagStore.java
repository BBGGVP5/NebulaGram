package app.nebulagram.ui;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Pure local label metadata. No message text, media, reactions or server attributes. */
public final class NebulaSavedTagStore {
    public static final int MAX_LABELS=64, MAX_MESSAGES=3000, MAX_BYTES=256000;
    public static final class Label { public final String id,name; Label(String id,String name){this.id=id;this.name=name;} }
    private final LinkedHashMap<String,String> labels=new LinkedHashMap<>();
    private final TreeMap<Integer,LinkedHashSet<String>> messages=new TreeMap<>();
    public static String scope(long user){if(user<=0)throw new IllegalArgumentException("Account required");return "saved-labels-v1-"+user;}
    public static String name(String value) throws IOException {
        if(value==null)throw new IOException("Label name required");value=Normalizer.normalize(value.trim(),Normalizer.Form.NFC);
        if(value.isEmpty()||value.codePointCount(0,value.length())>32||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Label name must contain 1–32 characters");return value;
    }
    public static NebulaSavedTagStore decode(String raw) throws Exception {
        NebulaSavedTagStore store=new NebulaSavedTagStore();if(raw==null||raw.isEmpty())return store;
        if(raw.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Label metadata too large");
        JSONObject root=new JSONObject(raw);JSONArray labels=root.getJSONArray("labels");JSONObject links=root.getJSONObject("messages");
        if(labels.length()>MAX_LABELS||links.length()>MAX_MESSAGES)throw new IOException("Label limits exceeded");
        HashSet<String> names=new HashSet<>();
        for(int i=0;i<labels.length();i++){JSONObject l=labels.getJSONObject(i);String id=l.getString("id"),name=name(l.getString("name"));if(!id.matches("[a-zA-Z0-9-]{1,64}")||store.labels.containsKey(id)||!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Invalid duplicate label");store.labels.put(id,name);}
        Iterator<String> keys=links.keys();while(keys.hasNext()){String key=keys.next();int message=Integer.parseInt(key);if(message<=0||!key.equals(Integer.toString(message)))throw new IOException("Invalid message ID");JSONArray ids=links.getJSONArray(key);if(ids.length()==0||ids.length()>8)throw new IOException("Invalid label list");LinkedHashSet<String> set=new LinkedHashSet<>();for(int i=0;i<ids.length();i++){String id=ids.getString(i);if(!store.labels.containsKey(id)||!set.add(id))throw new IOException("Invalid label reference");}store.messages.put(message,set);}
        return store;
    }
    public String encode() throws Exception {
        JSONArray tags=new JSONArray();for(Map.Entry<String,String> l:labels.entrySet())tags.put(new JSONObject().put("id",l.getKey()).put("name",l.getValue()));
        JSONObject links=new JSONObject();for(Map.Entry<Integer,LinkedHashSet<String>> m:messages.entrySet())links.put(Integer.toString(m.getKey()),new JSONArray(m.getValue()));
        String raw=new JSONObject().put("labels",tags).put("messages",links).toString();if(raw.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Label metadata too large");return raw;
    }
    public List<Label> labels(){ArrayList<Label> result=new ArrayList<>();for(Map.Entry<String,String> l:labels.entrySet())result.add(new Label(l.getKey(),l.getValue()));return result;}
    public String create(String value) throws Exception {value=name(value);for(String existing:labels.values())if(existing.equalsIgnoreCase(value))throw new IOException("This label already exists");if(labels.size()>=MAX_LABELS)throw new IOException("Maximum 64 labels");String id=UUID.randomUUID().toString();labels.put(id,value);return id;}
    public void rename(String id,String value) throws Exception {value=name(value);if(!labels.containsKey(id))throw new IOException("Label missing");for(Map.Entry<String,String> existing:labels.entrySet())if(!existing.getKey().equals(id)&&existing.getValue().equalsIgnoreCase(value))throw new IOException("This label already exists");labels.put(id,value);}
    public void remove(String id){labels.remove(id);Iterator<Map.Entry<Integer,LinkedHashSet<String>>> iterator=messages.entrySet().iterator();while(iterator.hasNext()){Set<String> set=iterator.next().getValue();set.remove(id);if(set.isEmpty())iterator.remove();}}
    public void toggle(int message,String id) throws Exception {if(message<=0||!labels.containsKey(id))throw new IOException("Invalid label target");LinkedHashSet<String> set=messages.get(message);if(set!=null&&set.contains(id)){set.remove(id);if(set.isEmpty())messages.remove(message);return;}if(set==null){if(messages.size()>=MAX_MESSAGES)throw new IOException("Maximum 3000 labelled messages");set=new LinkedHashSet<>();messages.put(message,set);}if(set.size()>=8)throw new IOException("Maximum 8 labels per message");set.add(id);}
    public boolean assigned(int message,String id){return messages.containsKey(message)&&messages.get(message).contains(id);}
    public List<Integer> messages(String id){ArrayList<Integer> result=new ArrayList<>();for(Map.Entry<Integer,LinkedHashSet<String>> m:messages.descendingMap().entrySet())if(m.getValue().contains(id))result.add(m.getKey());return result;}
}
