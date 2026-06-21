# 原生客户端发布物（静态托管目录）

本目录由前端静态服务（Vite/Nginx）以 `/downloads/` 路径直出，供「操作指南 → 多端下载」页的下载按钮链接。

发布时把构建好的原生客户端安装包放到这里（文件名需与 `frontend/src/content/guideSections.ts` 中
`clients` 主题的 `download.href` 一致）：

| 文件名 | 平台 | 产物来源 |
|---|---|---|
| `pythontopia.apk` | Android（minSdk 26 / Android 8.0+） | `android/` Gradle 构建的 release/debug APK |
| `pythontopia-windows.exe` | Windows 10/11 64 位 | `windows/` .NET 8 + WinUI 3 打包安装器 |
| `pythontopia-windows.zip` | Windows 10/11 64 位 | `windows/` 免安装压缩包 |

说明：
- 二进制产物**不入 git 仓库**（体积大、与代码分层授权）；本目录只保留本说明文件占位。
- 文件未放置前，下载按钮会指向 404；放置后即可直接下载，无需改代码。
- 资产授权遵循 `LICENSE-ART.md`；分发安装包前确认像素素材合规。
