# 链净 CoolLink

针对 `com.coolapk.market` 的 libxposed Modern API 102 模块。

## 功能

将酷安帖子/评论中的：

`https://www.coolapk.com/link?url=https%3A%2F%2Fexample.com%2Fabc`

还原为：

`https://example.com/abc`

支持：

- URLSpan 点击
- Activity Intent 跳转
- WebView loadUrl
- 剪贴板
- 分享文本中的链接
- 多层 URL 编码（最多 8 层）
- `url=` / `u=` 参数

## 设计原则

只在链接即将被打开、复制或分享的最后阶段处理，不 Hook OkHttp、Gson、JSONObject、TextView.setText 等高频数据/UI API，避免无意义扫描和破坏 Spanned。

## API

- libxposed Modern API `102.0.0`
- `minSdk 26`
- Java 17
- 作用域：`com.coolapk.market`

API 102 模块入口：`META-INF/xposed/java_init.list`

模块配置：`META-INF/xposed/module.prop`

作用域：`META-INF/xposed/scope.list`

## v2.2.0

- 新增通用第三方应用链接直开
- 支持根据 Android 系统的 Intent / App Links 自动匹配对应应用
- 酷安中的网页链接在存在对应应用时，可直接跳转到对应应用
- 支持常见的第三方应用链接，例如 Pixiv、哔哩哔哩、YouTube、GitHub 等，具体取决于设备上已安装应用及其 App Links 配置
- 没有对应应用时自动回退到浏览器
- 优化 Custom Tabs 和浏览器显式目标处理
- 基于 LSPosed Modern Xposed API 102

## 安装

1. 安装 APK
2. 在 LSPosed 中启用 CoolLink
3. 作用域仅勾选「酷安」
4. 重启酷安

## 构建

```bash
./gradlew assembleRelease
```
