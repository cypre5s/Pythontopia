'use strict';

const { app, BrowserWindow, Menu, shell, session } = require('electron');
const path = require('node:path');
const fs = require('node:fs');

// 云端入口：前端用相对 API base（/api），因此加载此地址即「后端指向云服务器」。
// 可用环境变量覆盖（便于切换到带域名的 HTTPS 入口）。
const APP_URL = process.env.PYT_APP_URL || 'http://47.120.29.62/';
const APP_ORIGIN = (() => {
  try {
    return new URL(APP_URL).origin;
  } catch {
    return '';
  }
})();

const PARCHMENT = '#fef7e3';

/** @type {BrowserWindow | null} */
let mainWindow = null;

/** 加载失败时展示的内联错误页（带「重试」按钮，点回 APP_URL 重新加载）。 */
function errorPageUrl(message) {
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Pythontopia · 连接失败</title>
<style>
  :root { color-scheme: light; }
  * { box-sizing: border-box; }
  body { margin: 0; min-height: 100vh; display: grid; place-items: center;
    font-family: "Segoe UI", "Microsoft YaHei", sans-serif; background: ${PARCHMENT}; color: #2f2418; }
  .card { width: min(460px, 90vw); padding: 28px; text-align: center;
    border: 2px solid #5c4a32; border-radius: 12px; background: #fffdf8;
    box-shadow: 4px 4px 0 rgba(92,74,50,.18); }
  h1 { margin: 0 0 8px; font-size: 22px; }
  p { margin: 0 0 18px; line-height: 1.7; color: #6b5a45; word-break: break-all; }
  button { padding: 12px 28px; font-size: 16px; font-weight: 700; cursor: pointer;
    color: #2f2418; background: #f1c453; border: 2px solid #5c4a32; border-radius: 8px;
    box-shadow: 3px 3px 0 rgba(92,74,50,.25); }
  button:active { transform: translate(1px,1px); box-shadow: 2px 2px 0 rgba(92,74,50,.25); }
  code { background: rgba(241,196,83,.2); padding: 1px 6px; border-radius: 4px; }
</style></head>
<body><main class="card">
  <h1>无法连接到 Pythontopia</h1>
  <p>正在尝试加载 <code>${APP_URL}</code><br/>${message || ''}</p>
  <button onclick="location.href='${APP_URL}'">重试</button>
</main></body></html>`;
  return 'data:text/html;charset=utf-8,' + encodeURIComponent(html);
}

function buildMenu() {
  const isMac = process.platform === 'darwin';
  const template = [
    {
      label: 'Pythontopia',
      submenu: [
        { label: '重新加载', accelerator: 'CmdOrCtrl+R', click: () => mainWindow && mainWindow.loadURL(APP_URL) },
        { label: '强制重新加载', accelerator: 'CmdOrCtrl+Shift+R', click: () => mainWindow && mainWindow.webContents.reloadIgnoringCache() },
        { type: 'separator' },
        { role: 'quit', label: '退出' },
      ],
    },
    {
      label: '视图',
      submenu: [
        { role: 'zoomIn', label: '放大' },
        { role: 'zoomOut', label: '缩小' },
        { role: 'resetZoom', label: '重置缩放' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '全屏' },
        ...(app.isPackaged ? [] : [{ role: 'toggleDevTools', label: '开发者工具' }]),
      ],
    },
  ];
  if (isMac) template.unshift({ role: 'appMenu' });
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function createWindow() {
  // 运行时窗口/任务栏图标（exe 未内嵌图标时的品牌兜底）。
  const iconPath = path.join(__dirname, 'icon.png');
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: PARCHMENT,
    title: 'Pythontopia',
    autoHideMenuBar: false,
    ...(fs.existsSync(iconPath) ? { icon: iconPath } : {}),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false,
    },
  });

  // 麦克风（语音输入）等权限：仅放行同源页面的必要权限。
  session.defaultSession.setPermissionRequestHandler((wc, permission, callback) => {
    const allowed = ['media', 'clipboard-read', 'clipboard-sanitized-write', 'fullscreen', 'pointerLock'];
    callback(allowed.includes(permission));
  });

  // window.open / target=_blank：同源在应用内开新窗，跨源交给系统浏览器。
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    try {
      if (new URL(url).origin === APP_ORIGIN) return { action: 'allow' };
    } catch { /* 非法 url 直接拒绝 */ }
    if (/^https?:/i.test(url)) void shell.openExternal(url);
    return { action: 'deny' };
  });

  // 主窗口导航：仅允许停留在同源，跨源链接外开，避免应用被导航走。
  mainWindow.webContents.on('will-navigate', (event, url) => {
    try {
      if (new URL(url).origin !== APP_ORIGIN) {
        event.preventDefault();
        if (/^https?:/i.test(url)) void shell.openExternal(url);
      }
    } catch { /* 忽略 */ }
  });

  // 加载失败（主框架、非用户中断）→ 展示重试页。
  mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL, isMainFrame) => {
    if (!isMainFrame || errorCode === -3) return; // -3: ERR_ABORTED
    void mainWindow.loadURL(errorPageUrl(`错误：${errorDescription}（${errorCode}）`));
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  void mainWindow.loadURL(APP_URL);
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(() => {
    buildMenu();
    createWindow();
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });
}
