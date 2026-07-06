# Pythontopia Clients

`clients/` 收纳非 Web 客户端。Web 主链路仍在根目录的 `frontend/`、`backend/`、`services/`、`deploy/`。

## 分类

| 目录 | 内容 |
| --- | --- |
| `native/android/` | Kotlin + Jetpack Compose + LibGDX 原生 Android 客户端 |
| `native/windows/` | .NET 8 + WinUI 3 + MonoGame 原生 Windows 客户端 |
| `wrappers/android-webview/` | Android WebView 轻壳 |
| `wrappers/windows-electron/` | Electron 桌面轻壳 |
| `wechat/miniprogram/` | 原生微信小程序，学习陪伴端 |
| `wechat/minigame/` | 原生微信小游戏，像素小镇端 |

跨端 API 契约见 [`../docs/clients/api-contract.md`](../docs/clients/api-contract.md)。
