// 隔离原因：BitmapFont .fnt 暂未生成，先用 web font CSS 注入承接像素字体，让 Phaser Text 直接复用。
// fontFamily 通过 PIXEL_FONT_FAMILY 暴露，业务层只需引用常量，不需要关心 woff2 路径。

const FONT_INJECTED_FLAG = '__pythontopia_pixel_font_injected__';

export const PIXEL_FONT_FAMILY = 'PythontopiaPixel, "Courier New", monospace';

interface InjectedWindow {
  [FONT_INJECTED_FLAG]?: boolean;
}

export function ensurePixelFontLoaded(): Promise<void> {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return Promise.resolve();
  }
  const injectedWindow = window as unknown as InjectedWindow;
  // 注入 @font-face 只做一次（幂等）；等待字体就绪每次都执行，
  // 这样调用方（如 BootScene.create）可以 await 到真正可用的中文像素字体，
  // 避免字体加载完成前用回退字体测量中文宽度，导致 NPC 名牌/气泡背景偏小、文字溢出边框。
  if (!injectedWindow[FONT_INJECTED_FLAG]) {
    const style = document.createElement('style');
    style.textContent = `
@font-face {
  font-family: 'PythontopiaPixel';
  src: url('/assets/pixel/fonts/ark-pixel-12px-mono-zh_cn.woff2') format('woff2');
  font-display: block;
}
@font-face {
  font-family: 'PythontopiaPixel';
  src: url('/assets/pixel/fonts/ark-pixel-12px-mono-latin.woff2') format('woff2');
  unicode-range: U+0000-007F;
  font-display: block;
}`;
    document.head.appendChild(style);
    injectedWindow[FONT_INJECTED_FLAG] = true;
  }
  if ('fonts' in document) {
    // 同时预热名牌(11px)与正文(12px)两档字号，确保两者测宽都使用真实字形。
    return Promise.all([
      document.fonts.load('11px PythontopiaPixel'),
      document.fonts.load('12px PythontopiaPixel'),
    ])
      .then(() => undefined)
      .catch(() => undefined);
  }
  return Promise.resolve();
}
