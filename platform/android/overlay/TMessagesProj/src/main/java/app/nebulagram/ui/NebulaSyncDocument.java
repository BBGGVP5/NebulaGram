package app.nebulagram.ui;

import org.json.JSONObject;
import java.util.*;

/** Portable presentation preferences only. Vector clocks preserve concurrent device edits. */
public final class NebulaSyncDocument {
    public static final String MARKER = "#NebulaGramSettingsV1\n";
    public final String device;
    public final SortedMap<String, Long> clock;
    public final SortedMap<String, Object> settings;

    public NebulaSyncDocument(String device, Map<String, Long> clock, Map<String, ?> settings) {
        if (device == null || !device.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("device");
        if (clock.size() > 24) throw new IllegalArgumentException("devices");
        this.device = device; this.clock = new TreeMap<>(); this.settings = new TreeMap<>();
        for (Map.Entry<String, Long> e : clock.entrySet()) {
            if (!e.getKey().matches("[a-f0-9-]{36}") || e.getValue() == null || e.getValue() < 1 || e.getValue() > 1000000000L)
                throw new IllegalArgumentException("clock");
            this.clock.put(e.getKey(), e.getValue());
        }
        if (!this.clock.containsKey(device)) throw new IllegalArgumentException("origin");
        for (Map.Entry<String, ?> e : settings.entrySet()) {
            Class<?> type = NebulaSettingsSchema.types.get(e.getKey());
            if (type == null) throw new IllegalArgumentException("unsupported preference");
            if (!type.isInstance(e.getValue()) || e.getValue() instanceof String && ((String)e.getValue()).length() > 1024)
                throw new IllegalArgumentException("value");
            this.settings.put(e.getKey(), e.getValue());
        }
    }
    public static NebulaSyncDocument parse(String text) throws Exception {
        if (text == null || text.length() > 4000 || !text.startsWith(MARKER)) throw new IllegalArgumentException("size/marker");
        JSONObject json = new JSONObject(text.substring(MARKER.length()));
        if (json.getInt("version") != 1) throw new IllegalArgumentException("version");
        JSONObject c = json.getJSONObject("clock"), s = json.getJSONObject("settings");
        Map<String, Long> clock = new TreeMap<>(); Map<String, Object> settings = new TreeMap<>();
        for (Iterator<String> it=c.keys();it.hasNext();) {
            String key=it.next(); Object n=c.get(key);
            if (!(n instanceof Integer) && !(n instanceof Long)) throw new IllegalArgumentException("counter");
            clock.put(key, ((Number)n).longValue());
        }
        for (Iterator<String> it=s.keys();it.hasNext();) { String key=it.next();settings.put(key,s.get(key)); }
        return new NebulaSyncDocument(json.getString("device"),clock,settings);
    }
    public String encode() throws Exception {
        String text = MARKER + new JSONObject().put("version",1).put("device",device)
                .put("clock",new JSONObject(clock)).put("settings",new JSONObject(settings));
        if (text.length() > 4000) throw new IllegalArgumentException("size");
        return text;
    }
    public boolean dominates(NebulaSyncDocument other) {
        boolean greater=false;
        Set<String> keys=new HashSet<>(clock.keySet()); keys.addAll(other.clock.keySet());
        for(String key:keys) {
            long a=clock.containsKey(key)?clock.get(key):0, b=other.clock.containsKey(key)?other.clock.get(key):0;
            if(a<b)return false; if(a>b)greater=true;
        }
        return greater;
    }
    public static Map<String, Long> mergedClock(Collection<NebulaSyncDocument> docs) {
        Map<String,Long> result=new TreeMap<>();
        for(NebulaSyncDocument d:docs) for(Map.Entry<String,Long> e:d.clock.entrySet())
            result.put(e.getKey(),Math.max(result.containsKey(e.getKey())?result.get(e.getKey()):0,e.getValue()));
        return result;
    }
    public static List<NebulaSyncDocument> heads(List<NebulaSyncDocument> docs) {
        List<NebulaSyncDocument> result=new ArrayList<>();
        for(NebulaSyncDocument d:docs) {
            boolean dominated=false;
            for(NebulaSyncDocument other:docs) if(other.dominates(d)){dominated=true;break;}
            if(!dominated)result.add(d);
        }
        return result;
    }
    public static SortedMap<String,Object> portable(Map<String,?> all) {
        SortedMap<String,Object> out=new TreeMap<>();
        for(String key:NebulaSettingsSchema.types.keySet()) if(all.containsKey(key))out.put(key,all.get(key));
        return out;
    }
}
