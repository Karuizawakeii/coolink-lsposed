# 链净 CoolLink

酷安帖子 / 评论区短链还原 LSPosed 模块

## 功能

自动将酷安中的 `coolapk.com/link?url=...` 短链还原为真实目标地址，支持：

- 信息流 / 评论 JSON（OkHttp / Gson / JSONObject）
- 文本展示（TextView / URLSpan）
- 点击跳转（startActivity / WebView）
- 复制分享（剪贴板 / Intent EXTRA_TEXT）

## 安装

1. 下载 [releases/CoolLink-1.0.0-arm64.apk](releases/CoolLink-1.0.0-arm64.apk) 并安装
2. 在 LSPosed 中启用「链净」，作用域只勾选酷安
3. 强制停止酷安后重新打开
4. 点开帖子或评论里的外链，应直接进入原址

## 构建

```bash
./gradlew assembleRelease
```

- minSdk 26 / targetSdk 34
- 依赖 Xposed API 82
