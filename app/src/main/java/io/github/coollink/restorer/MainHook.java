package io.github.coollink.restorer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.text.style.URLSpan;

import java.lang.reflect.Method;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * CoolLink using libxposed Modern API 102.
 *
 * The module intentionally hooks only the final URL/click boundaries instead
 * of scanning Coolapk's OkHttp/Gson/UI data. This keeps the target process
 * untouched until a link is actually opened, copied or shared.
 */
public final class MainHook extends XposedModule {
    private static final String TAG = "CoolLink";
    private static final String COOLAPK = "com.coolapk.market";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded process=" + param.getProcessName()
                + " framework=" + getFrameworkName()
                + " api=" + getApiVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!COOLAPK.equals(param.getPackageName())) return;

        try {
            hookUrlSpan();
            hookIntents();
            hookWebView();
            hookClipboard();
            log(Log.INFO, TAG, "hooks installed for " + COOLAPK);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook installation failed", t);
        }
    }

    private void hookUrlSpan() throws NoSuchMethodException {
        Method method = URLSpan.class.getDeclaredMethod("getURL");
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof String)) return result;

                    String raw = (String) result;
                    String restored = LinkUnwrapper.unwrapUrl(raw);
                    if (!raw.equals(restored)) {
                        log(Log.DEBUG, TAG, "URLSpan restored");
                        return restored;
                    }
                    return result;
                });
    }

    private void hookIntents() throws NoSuchMethodException {
        hookActivity("startActivity", new Class<?>[]{Intent.class});
        hookActivity("startActivity", new Class<?>[]{Intent.class, Bundle.class});
        hookActivity("startActivityForResult", new Class<?>[]{Intent.class, int.class});
        hookActivity("startActivityForResult", new Class<?>[]{Intent.class, int.class, Bundle.class});
    }

    private void hookActivity(String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        Method method = Activity.class.getDeclaredMethod(name, parameterTypes);
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length > 0 && args[0] instanceof Intent) {
                        args[0] = rewriteIntent((Intent) args[0]);
                    }
                    return chain.proceed(args);
                });
    }

    private Intent rewriteIntent(Intent intent) {
        if (intent == null) return null;

        Uri data = intent.getData();
        if (data != null) {
            String raw = data.toString();
            String restored = LinkUnwrapper.unwrapUrl(raw);
            if (!raw.equals(restored)) {
                intent.setData(Uri.parse(restored));
                log(Log.DEBUG, TAG, "Intent data restored");
            }
        }

        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text != null && LinkUnwrapper.needsRewrite(text)) {
            String restored = LinkUnwrapper.rewriteText(text);
            if (!text.equals(restored)) intent.putExtra(Intent.EXTRA_TEXT, restored);
        }

        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (subject != null && LinkUnwrapper.needsRewrite(subject)) {
            String restored = LinkUnwrapper.rewriteText(subject);
            if (!subject.equals(restored)) intent.putExtra(Intent.EXTRA_SUBJECT, restored);
        }

        return intent;
    }

    private void hookWebView() throws NoSuchMethodException {
        Method oneArg = WebView.class.getDeclaredMethod("loadUrl", String.class);
        hook(oneArg)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String raw = (String) chain.getArg(0);
                    String restored = LinkUnwrapper.unwrapUrl(raw);
                    return chain.proceed(new Object[]{restored});
                });

        Method twoArg = WebView.class.getDeclaredMethod("loadUrl", String.class, Map.class);
        hook(twoArg)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String raw = (String) chain.getArg(0);
                    String restored = LinkUnwrapper.unwrapUrl(raw);
                    return chain.proceed(new Object[]{restored, chain.getArg(1)});
                });
    }

    private void hookClipboard() throws NoSuchMethodException {
        Method method = ClipboardManager.class.getDeclaredMethod("setPrimaryClip", ClipData.class);
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ClipData clip = (ClipData) chain.getArg(0);
                    if (clip == null || clip.getItemCount() == 0) {
                        return chain.proceed();
                    }

                    CharSequence text = clip.getItemAt(0).getText();
                    if (text == null) return chain.proceed();

                    String raw = text.toString();
                    if (!LinkUnwrapper.needsRewrite(raw)) {
                        return chain.proceed();
                    }

                    String restored = LinkUnwrapper.rewriteText(raw);
                    if (raw.equals(restored)) return chain.proceed();

                    ClipData replacement = ClipData.newPlainText(
                            clip.getDescription().getLabel(), restored);
                    return chain.proceed(new Object[]{replacement});
                });
    }
}
