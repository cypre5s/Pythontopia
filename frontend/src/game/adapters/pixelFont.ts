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
  if (injectedWindow[FONT_INJECTED_FLAG]) {
    return Promise.resolve();
  }
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
  if ('fonts' in document) {
    return document.fonts
      .load('12px PythontopiaPixel')
      .then(() => undefined)
      .catch(() => undefined);
  }
  return Promise.resolve();
}
