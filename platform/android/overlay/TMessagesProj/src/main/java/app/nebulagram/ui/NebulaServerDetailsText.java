package app.nebulagram.ui;
import org.json.JSONObject;

final class NebulaServerDetailsText {
    static String describe(JSONObject data) {
        StringBuilder text = new StringBuilder(NebulaText.text("Провайдер и оплата", "Provider and payment"));
        if (data == null) return text.toString();
        for (String key : new String[]{"provider", "plan", "amount", "currency", "due_date"}) {
            String value = data.optString(key, "");
            if (!value.isEmpty()) text.append(" · ").append(value);
        }
        return text.toString();
    }
}
