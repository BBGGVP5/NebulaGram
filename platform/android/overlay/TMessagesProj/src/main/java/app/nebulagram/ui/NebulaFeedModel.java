package app.nebulagram.ui;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Лента выбранных каналов: какие каналы в неё входят и по каким вкладкам они
 * разложены. Хранится только на устройстве.
 *
 * <p>Чистая Java без Android: проверка собирает её отдельно и гоняет порядок,
 * скрытие, удаление канала и формат хранения.
 */
public final class NebulaFeedModel {
    public static final class Category {
        public final long id;
        public String emoji, name;
        public boolean hidden;
        public final LinkedHashSet<Long> channels = new LinkedHashSet<>();

        public Category(long id, String emoji, String name) {
            this.id = id;
            this.emoji = emoji == null ? "" : emoji;
            this.name = name == null ? "" : name;
        }
    }

    /** Пост ленты: достаточно знать канал, номер и время. */
    public interface Post {
        long channel();
        int id();
        int date();
    }

    public final LinkedHashSet<Long> channels = new LinkedHashSet<>();
    public final ArrayList<Category> categories = new ArrayList<>();
    private long nextId = 1;

    public Category create(String emoji, String name) {
        Category category = new Category(nextId++, emoji, name);
        categories.add(category);
        return category;
    }

    public Category find(long id) {
        for (Category category : categories) if (category.id == id) return category;
        return null;
    }

    public void remove(long id) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == id) {
                categories.remove(i);
                return;
            }
        }
    }

    public void move(int from, int to) {
        if (from < 0 || to < 0 || from >= categories.size() || to >= categories.size() || from == to) return;
        Category moving = categories.remove(from);
        categories.add(to, moving);
    }

    /** Канал вышел из ленты — он уходит и из всех вкладок, иначе вкладка показала бы чужое. */
    public void setChannel(long channel, boolean included) {
        if (included) {
            channels.add(channel);
            return;
        }
        channels.remove(channel);
        for (Category category : categories) category.channels.remove(channel);
    }

    /** Вкладки над лентой, по порядку, без скрытых глазком. */
    public List<Category> tabs() {
        ArrayList<Category> tabs = new ArrayList<>();
        for (Category category : categories) if (!category.hidden) tabs.add(category);
        return tabs;
    }

    /** Каналы вкладки; null — вкладка «Все». Только те, что ещё в ленте. */
    public List<Long> channelsOf(Category category) {
        ArrayList<Long> result = new ArrayList<>();
        if (category == null) {
            result.addAll(channels);
        } else {
            for (Long channel : category.channels) if (channels.contains(channel)) result.add(channel);
        }
        return result;
    }

    /** Новые сверху; одинаковые посты из разных загрузок — один раз. */
    public static <T extends Post> List<T> newestFirst(List<T> posts, int limit) {
        ArrayList<T> sorted = new ArrayList<>(posts);
        Collections.sort(sorted, (a, b) -> {
            if (a.date() != b.date()) return a.date() > b.date() ? -1 : 1;
            if (a.channel() != b.channel()) return a.channel() < b.channel() ? -1 : 1;
            return Integer.compare(b.id(), a.id());
        });
        ArrayList<T> unique = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (T post : sorted) {
            if (!seen.add(post.channel() + ":" + post.id())) continue;
            unique.add(post);
            if (unique.size() >= limit) break;
        }
        return unique;
    }

    // --- хранение -----------------------------------------------------------

    public String serialize() {
        StringBuilder out = new StringBuilder("v1\n");
        out.append("n|").append(nextId).append('\n');
        out.append("c|").append(join(channels)).append('\n');
        for (Category category : categories) {
            out.append("k|").append(category.id).append('|').append(category.hidden ? 1 : 0)
                    .append('|').append(encode(category.emoji)).append('|').append(encode(category.name))
                    .append('|').append(join(category.channels)).append('\n');
        }
        return out.toString();
    }

    public static NebulaFeedModel parse(String value) {
        NebulaFeedModel model = new NebulaFeedModel();
        if (value == null || !value.startsWith("v1\n")) return model;
        for (String line : value.split("\n")) {
            String[] parts = line.split("\\|", -1);
            try {
                if (parts[0].equals("n") && parts.length >= 2) {
                    model.nextId = Math.max(model.nextId, Long.parseLong(parts[1]));
                } else if (parts[0].equals("c") && parts.length >= 2) {
                    split(parts[1], model.channels);
                } else if (parts[0].equals("k") && parts.length >= 6) {
                    Category category = new Category(Long.parseLong(parts[1]), decode(parts[3]), decode(parts[4]));
                    category.hidden = "1".equals(parts[2]);
                    split(parts[5], category.channels);
                    if (model.find(category.id) != null) continue;
                    model.categories.add(category);
                    model.nextId = Math.max(model.nextId, category.id + 1);
                }
            } catch (RuntimeException ignored) {
                // Повреждённая строка теряется одна, остальная лента остаётся.
            }
        }
        return model;
    }

    private static String join(Iterable<Long> values) {
        StringBuilder out = new StringBuilder();
        for (Long value : values) {
            if (out.length() > 0) out.append(',');
            out.append(value);
        }
        return out.toString();
    }

    private static void split(String value, LinkedHashSet<Long> into) {
        if (value.isEmpty()) return;
        for (String part : value.split(",")) {
            try {
                into.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) { }
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return "";
        }
    }
}
