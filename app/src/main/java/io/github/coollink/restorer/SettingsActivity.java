package io.github.coollink.restorer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0c0f0d"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = text("链净 CoolLink", 22, Color.parseColor("#e8eee9"), true);
        TextView sub = text("酷安帖子 / 评论区短链还原", 14, Color.parseColor("#8b968e"), false);
        sub.setPadding(0, dp(6), 0, dp(20));

        root.addView(title);
        root.addView(sub);
        root.addView(text("安装", 16, Color.parseColor("#1faa6c"), true));
        root.addView(text(
                "1. 用 Android Studio 打开本模块工程并编译安装。\n"
                        + "2. 在 LSPosed 中启用「链净」，作用域只勾选酷安。\n"
                        + "3. 强制停止酷安后重新打开。\n"
                        + "4. 点开帖子或评论里的外链，应直接进入原址。",
                14, Color.parseColor("#e8eee9"), false));

        TextView hooks = text("挂钩点", 16, Color.parseColor("#1faa6c"), true);
        hooks.setPadding(0, dp(20), 0, 0);
        root.addView(hooks);
        root.addView(text(
                "采用“最后一刻还原”，尽量减少对酷安内部数据的干扰：\n"
                        + "· URLSpan 点击链接\n"
                        + "· Activity Intent 跳转\n"
                        + "· WebView URL\n"
                        + "· 剪贴板 / 分享文本\n"
                        + "· 支持多层 URL 编码",
                14, Color.parseColor("#e8eee9"), false));

        TextView note = text("说明", 16, Color.parseColor("#1faa6c"), true);
        note.setPadding(0, dp(20), 0, 0);
        root.addView(note);
        root.addView(text(
                "本模块只改写客户端看到的链接，不会请求酷安接口，也不会写入酷安数据目录。\n"
                        + "包名 com.coolapk.market · 模块 io.github.coollink.restorer",
                14, Color.parseColor("#e8eee9"), false));

        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setLineSpacing(dp(3), 1.15f);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
