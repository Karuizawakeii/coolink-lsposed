# 链净 CoolLink

酷安帖子 / 评论区短链还原 LSPosed 模块

## 功能

自动将酷安中的 `coolapk.com/link?url=...` 短链还原为真实目标地址，支持：

- 点击跳转（startActivity / WebView）
- 文本展示（URLSpan）
- 复制分享（剪贴板 / Intent EXTRA_TEXT）

## 版本

### v2.0.0（当前）
- 迁移至 libxposed Modern API 102
- 使用 `XposedModule` + `hook().intercept()` 链式 API
- 启用 `PROTECTIVE` 异常模式增强稳定性
- 精简 hook 策略，仅挂钩最终 URL/点击边界

### v1.0.0
- 基于 Xposed API 82 的初始版本
- 支持 OkHttp / Gson / JSONObject / TextView / URLSpan / WebView / 剪贴板

## 安装

1. 下载 `releases/CoolLink-2.0.0-api102-arm64.apk` 并安装
2. 在 LSPosed 中启用「链净」，作用域只勾选酷安
3. 强制停止酷安后重新打开

## 构建

```bash
./gradlew assembleRelease
```

- minSdk 26 / targetSdk 34
- 依赖 libxposed API 102.0.0
