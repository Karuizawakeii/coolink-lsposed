package io.github.coollink.restorer;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coolapk wrap / tracking URL restorer. Keep in sync with src/lib/unwrap.ts */
public final class LinkUnwrapper {
    private static final int MAX_DEPTH = 8;
    private static final Pattern URL_IN_TEXT = Pattern.compile(
            "https?:\\\\?/\\\\?/(?:www\\\\?\\.|m\\\\?\\.)?coolapk\\\\?\\.com\\\\?/[^\\s<>\"'\\]\\)]+",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> TRACKING = new HashSet<>(Arrays.asList(
            "sharekey", "shareuid", "sharefrom",
            "share_key", "share_uid", "share_from",
            "shareid", "sharefromid",
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"));

    private LinkUnwrapper() {}

    public static boolean needsRewrite(String text) {
        if (text == null || text.length() < 16) return false;
        return text.contains("coolapk.com/link")
                || text.contains("coolapk.com\\/link")
                || text.contains("shareKey=")
                || text.contains("shareUid=")
                || text.contains("shareFrom=")
                || text.contains("share_key=");
    }

    public static String unwrapUrl(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String current = unescapeJson(raw.trim());
        for (int i = 0; i < MAX_DEPTH; i++) {
            String next = unwrapOnce(current);
            if (next.equals(current)) return current;
            current = next;
        }
        return current;
    }

    public static String rewriteText(String text) {
        if (text == null || text.isEmpty() || !needsRewrite(text)) return text;
        Matcher matcher = URL_IN_TEXT.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            Punct split = stripPunct(match);
            String restored = unwrapUrl(split.url);
            matcher.appendReplacement(out, Matcher.quoteReplacement(restored + split.tail));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String unwrapOnce(String raw) {
        ParsedUrl parsed = ParsedUrl.parse(raw);
        if (parsed == null) return raw;
        if (isCoolapk(parsed.host) && "/link".equals(trimSlash(parsed.path))) {
            String target = parsed.queryValue("url");
            if (target == null) target = parsed.queryValue("u");
            if (target != null && !target.trim().isEmpty()) {
                String trimmed = target.trim();
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
                if (trimmed.startsWith("//")) return parsed.scheme + ":" + trimmed;
                try {
                    return URLDecoder.decode(trimmed, StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {
                    return trimmed;
                }
            }
        }
        return parsed.stripTracking();
    }

    private static boolean isCoolapk(String host) {
        String h = host.toLowerCase(Locale.US);
        return "coolapk.com".equals(h) || "www.coolapk.com".equals(h) || "m.coolapk.com".equals(h);
    }

    private static String trimSlash(String path) {
        if (path == null || path.isEmpty()) return "/";
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') end--;
        return path.substring(0, end);
    }

    private static String unescapeJson(String raw) {
        return raw.replace("\\/", "/");
    }

    private static Punct stripPunct(String url) {
        String u = url;
        StringBuilder tail = new StringBuilder();
        while (!u.isEmpty()) {
            char c = u.charAt(u.length() - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?') {
                tail.insert(0, c);
                u = u.substring(0, u.length() - 1);
            } else break;
        }
        return new Punct(u, tail.toString());
    }

    private static final class Punct {
        final String url;
        final String tail;
        Punct(String url, String tail) { this.url = url; this.tail = tail; }
    }

    static final class ParsedUrl {
        final String scheme;
        final String host;
        final String path;
        final String query;
        final String raw;

        private ParsedUrl(String scheme, String host, String path, String query, String raw) {
            this.scheme = scheme;
            this.host = host;
            this.path = path;
            this.query = query;
            this.raw = raw;
        }

        static ParsedUrl parse(String raw) {
            try {
                String s = unescapeJson(raw);
                int schemeEnd = s.indexOf("://");
                if (schemeEnd <= 0) return null;
                String scheme = s.substring(0, schemeEnd);
                int authStart = schemeEnd + 3;
                int pathStart = s.indexOf('/', authStart);
                int queryStart = s.indexOf('?', authStart);
                int hostEnd = pathStart >= 0 ? pathStart : (queryStart >= 0 ? queryStart : s.length());
                if (hostEnd <= authStart) return null;
                String host = s.substring(authStart, hostEnd);
                int colon = host.indexOf(':');
                if (colon > 0) host = host.substring(0, colon);
                String path = "/";
                if (pathStart >= 0) {
                    int pathEnd = queryStart >= 0 ? queryStart : s.length();
                    path = s.substring(pathStart, pathEnd);
                }
                String query = queryStart >= 0 ? s.substring(queryStart + 1) : "";
                return new ParsedUrl(scheme, host, path, query, s);
            } catch (Exception e) {
                return null;
            }
        }

        String queryValue(String key) {
            if (query == null || query.isEmpty()) return null;
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                String k = eq >= 0 ? part.substring(0, eq) : part;
                if (key.equalsIgnoreCase(k)) {
                    String v = eq >= 0 ? part.substring(eq + 1) : "";
                    try {
                        return URLDecoder.decode(v, StandardCharsets.UTF_8.name());
                    } catch (Exception e) {
                        return v;
                    }
                }
            }
            return null;
        }

        String stripTracking() {
            if (!isCoolapk(host) || query == null || query.isEmpty()) return raw;
            StringBuilder kept = new StringBuilder();
            for (String part : query.split("&")) {
                if (part.isEmpty()) continue;
                int eq = part.indexOf('=');
                String k = eq >= 0 ? part.substring(0, eq) : part;
                if (TRACKING.contains(k.toLowerCase(Locale.US))) continue;
                if (kept.length() > 0) kept.append('&');
                kept.append(part);
            }
            int q = raw.indexOf('?');
            String base = q >= 0 ? raw.substring(0, q) : raw;
            return kept.length() == 0 ? base : base + "?" + kept;
        }
    }
}
