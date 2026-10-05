package app.nebulagram.ui;

import java.net.URI;
import java.util.*;

/** Immutable, indexed subset of ABP host/path and cosmetic rules. Unsupported syntax is ignored. */
public final class NebulaAdBlockRules {
    private static final class Rule {
        String path = "", domains = "", types = ""; boolean allow, third;
        boolean matches(String path, String page, String request, String type) {
            if (third && (request.equals(page) || request.endsWith("." + page) || page.endsWith("." + request))) return false;
            if (!types.isEmpty() && !Arrays.asList(types.split(",")).contains(type)) return false;
            if (!domains.isEmpty()) {
                boolean includes = false, included = false;
                for (String domain : domains.split("\\|")) {
                    boolean excluded = domain.startsWith("~"); String host = excluded ? domain.substring(1) : domain;
                    boolean match = page.equals(host) || page.endsWith("." + host);
                    if (excluded && match) return false;
                    if (!excluded) { includes = true; included |= match; }
                }
                if (includes && !included) return false;
            }
            return this.path.isEmpty() || path.startsWith(this.path);
        }
    }
    private final Map<String, List<Rule>> hosts;
    private final Map<String, List<String>> cosmetic;
    public final int networkCount, cosmeticCount;
    private NebulaAdBlockRules(Map<String, List<Rule>> hosts, Map<String, List<String>> cosmetic, int networkCount, int cosmeticCount) {
        this.hosts = hosts; this.cosmetic = cosmetic; this.networkCount = networkCount; this.cosmeticCount = cosmeticCount;
    }
    public static String host(String url) {
        try { String host = new URI(url).getHost(); return host == null ? "" : host.toLowerCase(Locale.ROOT); } catch (Exception ignored) { return ""; }
    }
    public static String domain(String value) {
        String host = value.trim().toLowerCase(Locale.ROOT);
        return host.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?") && !host.contains("..") && !host.contains(".-") && !host.contains("-.") ? host : "";
    }
    public static NebulaAdBlockRules parse(String text) {
        HashMap<String, List<Rule>> hosts = new HashMap<>(); HashMap<String, List<String>> cosmetic = new HashMap<>(); int networks = 0, selectors = 0;
        if (text.length() > 8_000_000) throw new IllegalArgumentException("Filter list too large");
        for (String line : text.split("\n")) {
            if (networks >= 50000 || selectors >= 5000) break;
            line = line.trim(); if (line.length() > 1024 || line.startsWith("!") || line.startsWith("[")) continue;
            int hash = line.indexOf("##");
            if (hash >= 0) {
                String domains = line.substring(0, hash), selector = line.substring(hash + 2);
                if (selector.length() > 240 || selector.isEmpty() || selector.contains("{") || selector.contains("}") || selector.contains("\\")
                        || selector.contains(":has(") || selector.contains(":style(") || selector.contains("url(") || selector.contains("@") || selector.contains("<") || selector.contains(">") || selector.contains("scriptlet")) continue;
                if (domains.contains("~")) continue;
                for (String scope : (domains.isEmpty() ? new String[]{""} : domains.split(","))) {
                    if (!scope.isEmpty() && domain(scope).isEmpty()) continue;
                    List<String> rules = cosmetic.computeIfAbsent(scope, k -> new ArrayList<>()); if (rules.size() < 128) { rules.add(selector); selectors++; }
                }
                continue;
            }
            Rule rule = new Rule(); rule.allow = line.startsWith("@@"); if (rule.allow) line = line.substring(2);
            if (!line.startsWith("||")) continue;
            String[] pieces = line.substring(2).split("\\$", -1); if (pieces.length > 2) continue;
            String location = pieces[0]; int split = location.indexOf('^'); if (split < 0) split = location.indexOf('/');
            String hostname = split < 0 ? location : location.substring(0, split); if (domain(hostname).isEmpty()) continue;
            if (split >= 0 && location.charAt(split) == '/') { rule.path = location.substring(split); if (rule.path.endsWith("*")) rule.path = rule.path.substring(0, rule.path.length() - 1); }
            else if (split >= 0 && !"^".equals(location.substring(split))) continue;
            if (rule.path.contains("*") || rule.path.contains("|") || rule.path.contains("^")) continue;
            boolean supported = true; ArrayList<String> types = new ArrayList<>();
            if (pieces.length == 2) for (String option : pieces[1].split(",")) {
                if ("third-party".equals(option)) rule.third = true;
                else if (option.startsWith("domain=")) rule.domains = option.substring(7);
                else if (Arrays.asList("script", "image", "stylesheet", "font", "media", "xmlhttprequest").contains(option)) types.add(option);
                else { supported = false; break; }
            }
            if (!supported) continue; rule.types = String.join(",", types);
            List<Rule> rules = hosts.computeIfAbsent(hostname, k -> new ArrayList<>()); if (rules.size() < 64) { rules.add(rule); networks++; }
        }
        hosts.replaceAll((k, v) -> Collections.unmodifiableList(v)); cosmetic.replaceAll((k, v) -> Collections.unmodifiableList(v));
        return new NebulaAdBlockRules(Collections.unmodifiableMap(hosts), Collections.unmodifiableMap(cosmetic), networks, selectors);
    }
    public boolean blocked(String url, String pageUrl, boolean mainFrame, boolean miniApp, Set<String> excluded) {
        if (mainFrame || miniApp || url.length() > 8192) return false;
        String request = host(url), page = host(pageUrl); if (request.isEmpty() || page.isEmpty() || excluded(page, excluded)) return false;
        String path; try { URI uri = new URI(url); path = uri.getRawPath(); if (path == null) path = "/"; } catch (Exception ignored) { return false; }
        String lower = path.toLowerCase(Locale.ROOT), type = lower.endsWith(".js") ? "script" : lower.endsWith(".css") ? "stylesheet"
                : lower.matches(".*\\.(png|jpe?g|gif|webp|svg|avif)$") ? "image" : lower.matches(".*\\.(woff2?|ttf|otf)$") ? "font" : lower.matches(".*\\.(mp4|webm|mp3|ogg)$") ? "media" : "other";
        boolean block = false;
        for (String scope = request; !scope.isEmpty();) {
            List<Rule> rules = hosts.get(scope);
            if (rules != null) for (Rule rule : rules) if (rule.matches(path, page, request, type)) { if (rule.allow) return false; block = true; }
            int dot = scope.indexOf('.'); scope = dot < 0 ? "" : scope.substring(dot + 1);
        }
        return block;
    }
    public static boolean excluded(String page, Set<String> excluded) { for (String host : excluded) if (page.equals(host) || page.endsWith("." + host)) return true; return false; }
    public String css(String pageUrl, Set<String> excluded) {
        String host = host(pageUrl); if (host.isEmpty() || excluded(host, excluded)) return "";
        LinkedHashSet<String> rules = new LinkedHashSet<>(); rules.addAll(cosmetic.getOrDefault("", Collections.emptyList()));
        for (String scope = host; !scope.isEmpty();) {
            rules.addAll(cosmetic.getOrDefault(scope, Collections.emptyList())); int dot = scope.indexOf('.'); scope = dot < 0 ? "" : scope.substring(dot + 1);
        }
        StringBuilder css = new StringBuilder(); int count = 0;
        for (String rule : rules) { if (++count > 128 || css.length() + rule.length() > 32000) break; css.append(rule).append("{display:none!important;}"); }
        return css.toString();
    }
}
