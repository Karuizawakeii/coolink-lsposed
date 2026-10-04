package io.github.coollink.restorer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

/**
 * Generic Android deep-link router.
 *
 * CoolLink does not maintain a package/domain allow-list. For an http/https
 * URL it removes an explicit browser/Custom Tab target and lets Android's
 * normal ACTION_VIEW/App Links resolution choose the application that owns
 * the URL. If no application owns the URL, the normal browser remains the
 * fallback.
 */
public final class SupportedAppRouter {

    private SupportedAppRouter() {}

    /**
     * Returns an implicit ACTION_VIEW intent for web URLs so Android can
     * resolve the URL to an installed App Link handler.
     *
     * For non-web intents the original intent is returned unchanged.
     */
    public static Intent route(Context context, Intent original) {
        if (context == null || original == null) return original;
        if (!Intent.ACTION_VIEW.equals(original.getAction())) return original;

        Uri uri = original.getData();
        if (!isWebUri(uri)) return original;

        // Coolapk may have explicitly targeted a browser or a Custom Tab.
        // Remove that target and let Android resolve the actual URL again.
        // Verified App Links can then win; otherwise Android falls back to the
        // user's normal browser/default handler.
        if (original.getComponent() == null && original.getPackage() == null) {
            return original;
        }

        Intent routed = new Intent(original);
        routed.setComponent(null);
        routed.setPackage(null);
        routed.setSelector(null);
        return routed;
    }

    /**
     * Launches a web URL through Android's normal ACTION_VIEW resolution.
     * Returns true only when CoolLink actually started another activity.
     */
    public static boolean openFromContext(Context context, String rawUrl) {
        if (context == null || rawUrl == null || rawUrl.trim().isEmpty()) return false;

        final Uri uri;
        try {
            uri = Uri.parse(rawUrl.trim());
        } catch (Throwable ignored) {
            return false;
        }
        if (!isWebUri(uri)) return false;

        Intent original = new Intent(Intent.ACTION_VIEW, uri);
        Intent routed = route(context, original);

        // A plain implicit ACTION_VIEW is exactly what we want here. It lets
        // Android choose a verified App Link handler when one exists and a
        // browser otherwise. Do not pre-resolve and hard-code package names.
        try {
            if (!(context instanceof Activity)) {
                routed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(routed);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Launches a web URL from a WebView context using normal App Link routing. */
    public static boolean openFromWebView(WebView webView, String rawUrl) {
        return webView != null && openFromContext(webView.getContext(), rawUrl);
    }

    private static boolean isWebUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
