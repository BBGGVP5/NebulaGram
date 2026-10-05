package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import java.util.ArrayList;
import java.util.UUID;

/** Named connections. Profile JSON never contains API keys; the active legacy connection stays usable. */
public final class NebulaAiServices {
    private NebulaAiServices() { }
    public static final class Service {
        public final String id, name, endpoint, model;
        public final int provider;
        Service(String id, String name, int provider, String endpoint, String model) {
            this.id = id; this.name = name; this.provider = provider; this.endpoint = endpoint; this.model = model;
        }
        JSONObject json() throws Exception { return new JSONObject().put("id", id).put("name", name).put("provider", provider).put("endpoint", endpoint).put("model", model); }
    }
    private static SharedPreferences store() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_services", 0); }
    private static SharedPreferences active() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_settings", 0); }
    private static String scope(String id) {
        if (!id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid service");
        return "service-" + id;
    }
    public static String selected() { return active().getString("selected_service", ""); }
    public static String providerName(int provider) {
        switch (provider) {
            case NebulaAiClient.OPENAI: return "OpenAI";
            case NebulaAiClient.CLAUDE: return "Anthropic";
            case NebulaAiClient.GEMINI: return "Gemini";
            case NebulaAiClient.NANO: return "Gemini Nano";
            case NebulaAiClient.OPENROUTER: return "OpenRouter";
            case NebulaAiClient.PERPLEXITY: return "Perplexity";
            default: return NebulaText.text("Свой сервис", "Custom service");
        }
    }
    private static ArrayList<Service> read() {
        ArrayList<Service> result = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(store().getString("profiles", "[]"));
            for (int i = 0; i < Math.min(json.length(), 40); i++) {
                JSONObject row = json.getJSONObject(i); String id = row.getString("id"); scope(id);
                int provider = row.getInt("provider");
                if (provider < 0 || provider > NebulaAiClient.MAX_PROVIDER || find(result, id) != null) continue;
                result.add(new Service(id, row.getString("name"), provider, row.optString("endpoint"), row.optString("model")));
            }
        } catch (Exception ignored) { }
        return result;
    }
    private static void write(ArrayList<Service> profiles) throws Exception {
        JSONArray json = new JSONArray(); for (Service service : profiles) json.put(service.json());
        store().edit().putString("profiles", json.toString()).putBoolean("initialized", true).apply();
    }
    private static Service find(ArrayList<Service> profiles, String id) {
        for (Service service : profiles) if (service.id.equals(id)) return service;
        return null;
    }
    public static synchronized ArrayList<Service> list() {
        ArrayList<Service> profiles = read();
        if (store().getBoolean("initialized", false)) return profiles;
        SharedPreferences p = active(); int provider = p.getInt("provider", 0);
        profiles.add(new Service("nano", "Gemini Nano", NebulaAiClient.NANO, "", "nano-v3"));
        if (provider < 0 || provider > NebulaAiClient.MAX_PROVIDER) provider = 0;
        String selected = "nano";
        if (provider != NebulaAiClient.NANO) {
            selected = "legacy_" + provider;
            profiles.add(new Service(selected, providerName(provider), provider, p.getString("endpoint", "https://api.openai.com/v1"), p.getString("model_" + provider, "")));
        }
        try {
            if (provider != NebulaAiClient.NANO) NebulaAiSecrets.save(scope(selected), NebulaAiSecrets.read(provider));
            write(profiles); p.edit().putString("selected_service", selected).apply();
        } catch (Exception ignored) { /* Do not replace a still-usable legacy key after a Keystore failure. */ }
        return profiles;
    }
    public static Service find(String id) { return find(list(), id); }
    public static String key(String id) throws Exception { return NebulaAiSecrets.read(scope(id)); }
    public static synchronized Service save(String id, String name, int provider, String endpoint, String model, String key) throws Exception {
        if (name == null || name.trim().isEmpty() || name.length() > 64 || model == null || model.length() > 256 || endpoint == null || endpoint.length() > 1000
                || key == null || key.length() > 10000 || provider < 0 || provider > NebulaAiClient.MAX_PROVIDER) throw new IllegalArgumentException("Invalid service");
        if (provider != NebulaAiClient.NANO) NebulaAiClient.base(provider, endpoint);
        ArrayList<Service> profiles = list();
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        scope(id); Service prior = find(profiles, id);
        if (prior == null && profiles.size() >= 40) throw new IllegalArgumentException("Too many services");
        Service service = new Service(id, name.trim(), provider, endpoint.trim(), model.trim());
        if (provider != NebulaAiClient.NANO) NebulaAiSecrets.save(scope(id), key.trim());
        if (prior != null) profiles.remove(prior); profiles.add(service); write(profiles);
        if (selected().equals(id)) activate(service);
        return service;
    }
    /** Mirror explicit edits made in the existing advanced connection editor into its selected profile. */
    public static synchronized void capture() throws Exception {
        ArrayList<Service> profiles = list(); Service prior = find(profiles, selected()); SharedPreferences p = active();
        int provider = p.getInt("provider", 0);
        if (prior == null || prior.provider != provider) return;
        Service changed = new Service(prior.id, prior.name, provider, p.getString("endpoint", prior.endpoint), p.getString("model_" + provider, prior.model));
        String key = provider == NebulaAiClient.NANO ? "" : NebulaAiSecrets.read(provider);
        if (changed.endpoint.equals(prior.endpoint) && changed.model.equals(prior.model)
                && (provider == NebulaAiClient.NANO || key.equals(NebulaAiSecrets.read(scope(prior.id))))) return;
        if (provider != NebulaAiClient.NANO) NebulaAiSecrets.save(scope(prior.id), key);
        profiles.set(profiles.indexOf(prior), changed); write(profiles);
        p.edit().putInt("connection_revision", p.getInt("connection_revision", 0) + 1).apply();
    }
    private static void activate(Service service) throws Exception {
        String key = service.provider == NebulaAiClient.NANO ? "" : NebulaAiSecrets.read(scope(service.id));
        if (service.provider != NebulaAiClient.NANO) NebulaAiSecrets.save(service.provider, key);
        SharedPreferences p=active();
        p.edit().putInt("provider", service.provider).putString("model_" + service.provider, service.model)
                .putString("endpoint", service.endpoint).putString("selected_service", service.id).putBoolean("enabled", true)
                .putInt("connection_revision", p.getInt("connection_revision", 0) + 1).apply();
    }
    public static synchronized void select(String id) throws Exception {
        Service next = find(list(), id); if (next == null) throw new IllegalArgumentException("Unknown service");
        if (!id.equals(selected())) capture(); activate(next);
    }
    public static synchronized void delete(String id) throws Exception {
        if ("nano".equals(id)) throw new IllegalArgumentException("Built-in service");
        ArrayList<Service> profiles = list(); Service service = find(profiles, id); if (service == null) return;
        // Keep the profile recoverable if secure storage refuses a key deletion.
        if (selected().equals(id)) {
            if (service.provider != NebulaAiClient.NANO) NebulaAiSecrets.save(service.provider, "");
            active().edit().remove("selected_service").putBoolean("enabled", false).apply();
        }
        NebulaAiSecrets.save(scope(id), ""); profiles.remove(service); write(profiles);
    }
}
