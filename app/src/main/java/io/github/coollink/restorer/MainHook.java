package io.github.coollink.restorer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.webkit.WebView;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.reflect.Type;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Version-stable Coolapk link restorer.
 * Hooks Android / OkHttp / Gson / JSON — not obfuscated Coolapk class names —
 * so posts and comments keep working across Coolapk updates.
 */
public class MainHook implements IXposedHookLoadPackage {
    static final String TAG = "CoolLink";
    static final String COOLAPK = "com.coolapk.market";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!COOLAPK.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + ": in " + lpparam.packageName + " " + lpparam.appInfo);
        hookIntents();
        hookUrlSpan();
        hookTextView();
        hookWebView();
        hookClipboard();
        hookJson();
        hookOkHttp(lpparam.classLoader);
        hookGson(lpparam.classLoader);
    }

    private void hookIntents() {
        XC_MethodHook rewrite = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                for (Object arg : param.args) {
                    if (arg instanceof Intent) rewriteIntent((Intent) arg);
                }
            }
        };
        tryHook(Activity.class, "startActivity", rewrite, Intent.class);
        tryHook(Activity.class, "startActivity", rewrite, Intent.class, Bundle.class);
        tryHook(Activity.class, "startActivityForResult", rewrite, Intent.class, int.class);
        tryHook(Activity.class, "startActivityForResult", rewrite, Intent.class, int.class, Bundle.class);
    }

    private void rewriteIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null) {
            String raw = data.toString();
            String restored = LinkUnwrapper.unwrapUrl(raw);
            if (!raw.equals(restored)) {
                intent.setData(Uri.parse(restored));
                XposedBridge.log(TAG + ": intent " + raw + " -> " + restored);
            }
        }
        String extra = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (extra != null && LinkUnwrapper.needsRewrite(extra)) {
            intent.putExtra(Intent.EXTRA_TEXT, LinkUnwrapper.rewriteText(extra));
        }
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (subject != null && LinkUnwrapper.needsRewrite(subject)) {
            intent.putExtra(Intent.EXTRA_SUBJECT, LinkUnwrapper.rewriteText(subject));
        }
    }

    private void hookUrlSpan() {
        try {
            XposedHelpers.findAndHookMethod(URLSpan.class, "getURL", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object result = param.getResult();
                    if (!(result instanceof String)) return;
                    String url = (String) result;
                    String restored = LinkUnwrapper.unwrapUrl(url);
                    if (!url.equals(restored)) param.setResult(restored);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": URLSpan " + t);
        }
    }

    private void hookTextView() {
        XC_MethodHook rewrite = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object cs = param.args[0];
                if (!(cs instanceof CharSequence)) return;
                CharSequence text = (CharSequence) cs;
                if (!LinkUnwrapper.needsRewrite(text.toString())) return;
                param.args[0] = rewriteCharSequence(text);
            }
        };
        tryHook(TextView.class, "setText", rewrite, CharSequence.class);
        tryHook(TextView.class, "setText", rewrite, CharSequence.class, TextView.BufferType.class);
    }

    private CharSequence rewriteCharSequence(CharSequence text) {
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            SpannableStringBuilder builder = new SpannableStringBuilder(LinkUnwrapper.rewriteText(spanned.toString()));
            URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
            // Rebuild URL spans against restored text — cheapest correct path:
            // copy non-URL spans is unnecessary; Coolapk linkifies on the fly.
            if (spans.length == 0) return builder;
            String restored = builder.toString();
            SpannableStringBuilder out = new SpannableStringBuilder(restored);
            // Re-apply URLSpans by unwrapping original hrefs if they still occur.
            for (URLSpan span : spans) {
                String href = LinkUnwrapper.unwrapUrl(span.getURL());
                int start = restored.indexOf(href);
                if (start >= 0) {
                    out.setSpan(new URLSpan(href), start, start + href.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return out;
        }
        return LinkUnwrapper.rewriteText(text.toString());
    }

    private void hookWebView() {
        XC_MethodHook rewrite = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof String)) return;
                String url = (String) param.args[0];
                String restored = LinkUnwrapper.unwrapUrl(url);
                if (!url.equals(restored)) param.args[0] = restored;
            }
        };
        tryHook(WebView.class, "loadUrl", rewrite, String.class);
        tryHook(WebView.class, "loadUrl", rewrite, String.class, java.util.Map.class);
    }

    private void hookClipboard() {
        try {
            XposedHelpers.findAndHookMethod(ClipboardManager.class, "setPrimaryClip", ClipData.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ClipData clip = (ClipData) param.args[0];
                    if (clip == null || clip.getItemCount() == 0) return;
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text == null) return;
                    String raw = text.toString();
                    if (!LinkUnwrapper.needsRewrite(raw)) return;
                    param.args[0] = ClipData.newPlainText(clip.getDescription().getLabel(), LinkUnwrapper.rewriteText(raw));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clipboard " + t);
        }
    }

    private void hookJson() {
        try {
            XposedHelpers.findAndHookConstructor(JSONObject.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof String)) return;
                    String raw = (String) param.args[0];
                    if (LinkUnwrapper.needsRewrite(raw)) {
                        param.args[0] = LinkUnwrapper.rewriteText(raw);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": JSONObject " + t);
        }
    }

    private void hookOkHttp(ClassLoader cl) {
        try {
            Class<?> body = XposedHelpers.findClass("okhttp3.ResponseBody", cl);
            XposedHelpers.findAndHookMethod(body, "string", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object result = param.getResult();
                    if (!(result instanceof String)) return;
                    String s = (String) result;
                    if (LinkUnwrapper.needsRewrite(s)) {
                        param.setResult(LinkUnwrapper.rewriteText(s));
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": okhttp3 " + t);
        }
    }

    private void hookGson(ClassLoader cl) {
        try {
            Class<?> gson = XposedHelpers.findClass("com.google.gson.Gson", cl);
            XC_MethodHook before = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof String)) return;
                    String raw = (String) param.args[0];
                    if (LinkUnwrapper.needsRewrite(raw)) {
                        param.args[0] = LinkUnwrapper.rewriteText(raw);
                    }
                }
            };
            tryHook(gson, "fromJson", before, String.class, Class.class);
            tryHook(gson, "fromJson", before, String.class, Type.class);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": gson " + t);
        }
    }

    private void tryHook(Class<?> cls, String method, XC_MethodHook hook, Class<?>... params) {
        try {
            XposedHelpers.findAndHookMethod(cls, method, concat(params, hook));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": skip " + cls.getName() + "#" + method + " " + t.getMessage());
        }
    }

    private Object[] concat(Class<?>[] params, XC_MethodHook hook) {
        Object[] all = new Object[params.length + 1];
        System.arraycopy(params, 0, all, 0, params.length);
        all[params.length] = hook;
        return all;
    }
}
