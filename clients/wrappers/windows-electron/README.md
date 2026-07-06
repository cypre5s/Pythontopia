# Pythontopia 桌面端（Electron 外壳）

一个**轻量 Electron 外壳**：启动后用 Chromium 内核加载云端像素学习小镇
（默认 `http://47.120.29.62/`）。前端使用相对 API base（`/api`），因此
「后端自动指向云服务器」——外壳本身不含任何业务代码、不打包前端资源。

## 为什么是外壳而非打包前端

- 前端/后端/判题都跑在阿里云 ECS，外壳只需把官网装进一个原生窗口。
- 始终与线上一致，无需随前端发版重新打包。
- 与「基于 WebView 的安卓端」同一思路，双端体验一致。

## 运行 / 构建

```bash
npm install
npm start                 # 本地预览（加载云端站点）
npm run dist              # 产出 dist/pythontopia-windows.exe 与 .zip（Windows x64）
```

在非 Windows（Linux/WSL）上构建 Windows 目标需要 `wine`（electron-builder 用其运行 rcedit/NSIS）。
国内网络建议设置镜像加速：

```bash
export ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
export ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/
```

## 切换入口地址

```bash
PYT_APP_URL=https://your-domain/ npm start
```

产物 `pythontopia-windows.exe`（NSIS 安装版）与 `pythontopia-windows.zip`（免装版）
对应 ECS download-gate 的下载位置 `/opt/pythontopia/downloads/`。
