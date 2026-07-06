# Pythontopia 安卓端（WebView 外壳）

一个**单模块、零三方依赖的 WebView 外壳**：全屏加载云端像素学习小镇
（默认 `http://47.120.29.62/`）。前端使用相对 API base（`/api`），因此
「后端自动指向云服务器」——外壳只负责把官网装进一个原生 App 壳，
不打包前端资源、不复用 `:app` 多模块原生工程（Compose/LibGDX）。

## 能力

- 全屏 `WebView`，JS / DOM Storage / 媒体自动播放已开启。
- 语音输入：`getUserMedia` 录音权限按需申请并授予。
- `<input type=file>` 文件选择、跨站链接外跳系统浏览器、返回键回退历史。
- 顶部加载进度条；断网时显示「重试」错误页。
- 明文(HTTP)流量仅对 ECS 主机放行（`res/xml/network_security_config.xml`）。

## 构建

```bash
# SDK 路径在 local.properties（sdk.dir=/home/cypress/Android）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

无正式 keystore 时回退 debug 签名，演示期可直接安装。
产物对应 ECS download-gate 的下载位置 `/opt/pythontopia/downloads/pythontopia.apk`。

## 切换入口地址

修改 `app/src/main/kotlin/com/pythontopia/webview/MainActivity.kt` 中的
`APP_URL` / `APP_HOST`，并同步 `network_security_config.xml`（若改为 HTTPS 域名可移除明文放行）。
