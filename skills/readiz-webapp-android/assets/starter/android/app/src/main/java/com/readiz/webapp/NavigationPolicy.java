package com.readiz.webapp;
import java.net.URI;
final class NavigationPolicy {
    static final String HOME = BuildConfig.HOME_URL;
    static URI parse(String url) {
        if (url == null) return null;
        try {
            URI u = URI.create(url);
            if (!"https".equals(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null
                    || (u.getPort() != -1 && u.getPort() != 443)) return null;
            String p = u.getPath();
            // Reject traversal, encoded separators and double decoding ambiguities.
            if (p == null || p.contains("..") || p.contains("\\") || p.contains("%")
                    || u.getRawPath().matches("(?i).*%(2f|5c).*")) return null;
            return u;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    static boolean internal(String url) {
        URI u = parse(url); if (u == null || u.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) return false;
        for (String allowed : BuildConfig.INTERNAL_URLS) {
            URI base = parse(allowed); if (base == null || !base.getHost().equalsIgnoreCase(u.getHost())) continue;
            String path = base.getPath();
            if (path.isEmpty() || path.equals("/") || u.getPath().equals(path) || u.getPath().startsWith(path + "/")) return true;
        }
        return false;
    }
    static boolean external(String url) { return parse(url) != null; }
}
