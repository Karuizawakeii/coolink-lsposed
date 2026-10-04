package io.github.coollink.restorer;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Coolapk's link wrapper.
 *
 * Example:
 * https://www.coolapk.com/link?url=https%3A%2F%2Falist.1mxy.cn%2F139
 * -> https://alist.1mxy.cn/139
 */
public final class LinkUnwrapper {
    private static final int MAX_DEPTH = 8;

    // Matches both normal URLs and JSON-style escaped slashes.
    private static final Pattern URL_IN_TEXT = Pattern.compile(
            "https?:\\\\?/\\\\?/(?:www\\\\?\\.|m\\\\?\\.)?coolapk\\\\?\\.com\\\\?/link(?:\\?[^\\s<>\"'\\]\\)}]+)?",
            Pattern.CASE_INSENSITIVE);

    private LinkUnwrapper() {}

    /** Fast pre-check used by clipboard/share text paths. */
    public static boolean needsRewrite(String text) {
        if (text == null || text.length() < 16) return false;
        String lower = text.toLowerCase(Locale.US);
        return lower.contains("coolapk.com/link")
                || lower.contains("coolapk.com\\/link");
    }

    /** Unwrap only Coolapk /link URLs. Normal Coolapk URLs are left unchanged. */
    public static String unwrapUrl(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        String current = unescapeJson(raw.trim());
        for (int i = 0; i < MAX_DEPTH; i++) {
            String next = unwrapOnce(current);
            if (next.equals(current)) return current;
            current = unescapeJson(next.trim());
        }
        return current;
    }

    /** Restore all Coolapk /link URLs embedded in a text string. */
    public static String rewriteText(String text) {
        if (text == null || text.isEmpty() || !needsRewrite(text)) return text;

        Matcher matcher = URL_IN_TEXT.matcher(text);
        StringBuffer out = new StringBuffer(text.length());

        while (matcher.find()) {
            String match = matcher.group();
            Punct split = stripPunctuation(match);
            String restored = unwrapUrl(split.url);
            matcher.appendReplacement(
                    out,
                    Matcher.quoteReplacement(restored + split.tail));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String unwrapOnce(String raw) {
        ParsedUrl parsed = ParsedUrl.parse(raw);
        if (parsed == null || !isCoolapk(parsed.host)) return raw;
        if (!"/link".equals(trimSlash(parsed.path))) return raw;

        String target = parsed.queryValue("url");
        if (target == null) target = parsed.queryValue("u");
        if (target == null || target.trim().isEmpty()) return raw;

        String value = target.trim();
        if (value.startsWith("//")) return parsed.scheme + ":" + value;
        if (value.startsWith("http://") || value.startsWith("https://")) return value;

        // Handles double/triple URL encoding without making the normal case
        // depend on an extra network request.
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value;
        }
    }

    private static boolean isCoolapk(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        return "coolapk.com".equals(h)
                || "www.coolapk.com".equals(h)
                || "m.coolapk.com".equals(h);
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

    private static Punct stripPunctuation(String url) {
        String u = url;
        StringBuilder tail = new StringBuilder();

        while (!u.isEmpty()) {
            char c = u.charAt(u.length() - 1);
            if (isTrailingPunctuation(c)) {
                tail.insert(0, c);
                u = u.substring(0, u.length() - 1);
            } else {
                break;
            }
        }
        return new Punct(u, tail.toString());
    }

    private static boolean isTrailingPunctuation(char c) {
        return c == '.' || c == ',' || c == ';' || c == ':' || c == '!'
                || c == '?' || c == ')' || c == ']' || c == '}'
                || c == '，' || c == '。' || c == '；' || c == '：'
                || c == '！' || c == '？' || c == '）' || c == '】' || c == '》';
    }

    private static final class Punct {
        final String url;
        final String tail;

        Punct(String url, String tail) {
            this.url = url;
            this.tail = tail;
        }
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
                int authorityStart = schemeEnd + 3;
                int pathStart = s.indexOf('/', authorityStart);
                int queryStart = s.indexOf('?', authorityStart);
                int fragmentStart = s.indexOf('#', authorityStart);

                int hostEnd = firstPositive(pathStart, queryStart, fragmentStart, s.length());
                if (hostEnd <= authorityStart) return null;

                String host = s.substring(authorityStart, hostEnd);
                int colon = host.indexOf(':');
                if (colon > 0) host = host.substring(0, colon);

                String path = "/";
                if (pathStart >= 0 && pathStart < hostEnd + 1) {
                    int pathEnd = firstPositiveAfter(queryStart, fragmentStart, s.length(), pathStart);
                    path = s.substring(pathStart, pathEnd);
                }

                String query = "";
                if (queryStart >= 0) {
                    int queryEnd = fragmentStart >= 0 && fragmentStart > queryStart
                            ? fragmentStart : s.length();
                    query = s.substring(queryStart + 1, queryEnd);
                }

                return new ParsedUrl(scheme, host, path, query, s);
            } catch (Throwable ignored) {
                return null;
            }
        }

        String queryValue(String key) {
            if (query == null || query.isEmpty()) return null;

            for (String part : query.split("&", -1)) {
                int eq = part.indexOf('=');
                String k = eq >= 0 ? part.substring(0, eq) : part;
                if (!key.equalsIgnoreCase(k)) continue;

                String value = eq >= 0 ? part.substring(eq + 1) : "";
                try {
                    return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {
                    return value;
                }
            }
            return null;
        }
    }

    private static int firstPositive(int a, int b, int c, int fallback) {
        int result = fallback;
        if (a >= 0 && a < result) result = a;
        if (b >= 0 && b < result) result = b;
        if (c >= 0 && c < result) result = c;
        return result;
    }

    private static int firstPositiveAfter(int a, int b, int fallback, int after) {
        int result = fallback;
        if (a > after && a < result) result = a;
        if (b > after && b < result) result = b;
        return result;
    }
}
