// 运行时适配层：把微信小游戏的全局 `wx` / canvas / image / 文件系统 / 触摸 / 网络
// 全部收敛到这里，用「结构化接口 + 边界处一次性 cast」隔离掉无 DOM 的原生运行时。
// 业务/游戏逻辑只 import 本文件导出的纯类型与函数，从而保持「无 any、无裸全局访问」。

export type CanvasTextAlign = 'left' | 'right' | 'center' | 'start' | 'end';
export type CanvasTextBaseline =
  | 'top'
  | 'hanging'
  | 'middle'
  | 'alphabetic'
  | 'ideographic'
  | 'bottom';

/** 小游戏 2D 上下文里本项目实际用到的子集（结构化类型，避免依赖 DOM lib）。 */
export interface Ctx2D {
  fillStyle: string;
  strokeStyle: string;
  lineWidth: number;
  globalAlpha: number;
  font: string;
  textAlign: CanvasTextAlign;
  textBaseline: CanvasTextBaseline;
  imageSmoothingEnabled: boolean;
  save(): void;
  restore(): void;
  scale(x: number, y: number): void;
  translate(x: number, y: number): void;
  clearRect(x: number, y: number, w: number, h: number): void;
  fillRect(x: number, y: number, w: number, h: number): void;
  strokeRect(x: number, y: number, w: number, h: number): void;
  beginPath(): void;
  closePath(): void;
  moveTo(x: number, y: number): void;
  lineTo(x: number, y: number): void;
  quadraticCurveTo(cpx: number, cpy: number, x: number, y: number): void;
  arc(x: number, y: number, r: number, start: number, end: number): void;
  fill(): void;
  stroke(): void;
  fillText(text: string, x: number, y: number): void;
  measureText(text: string): { width: number };
  drawImage(
    image: GameImage,
    sx: number,
    sy: number,
    sw: number,
    sh: number,
    dx: number,
    dy: number,
    dw: number,
    dh: number,
  ): void;
}

/** `wx.createImage()` 返回对象在本项目用到的子集。 */
export interface GameImage {
  src: string;
  width: number;
  height: number;
  onload: (() => void) | null;
  onerror: ((err: unknown) => void) | null;
}

/** `wx.createCanvas()` 返回对象在本项目用到的子集。 */
export interface GameCanvas {
  width: number;
  height: number;
  getContext(contextType: '2d'): Ctx2D;
}

export interface ScreenInfo {
  windowWidth: number;
  windowHeight: number;
  pixelRatio: number;
}

export interface GameTouch {
  identifier: number;
  clientX: number;
  clientY: number;
}

export interface GameTouchEvent {
  touches: GameTouch[];
  changedTouches: GameTouch[];
}

export type TouchHandler = (event: GameTouchEvent) => void;

export interface HttpResult {
  statusCode: number;
  data: unknown;
}

// ── 以下是与原生运行时交互的「唯一」边界，所有 cast 都封装在此 ──

export function getMainCanvas(): GameCanvas {
  // 小游戏首屏 canvas：第一次 createCanvas 即上屏画布。
  return wx.createCanvas() as unknown as GameCanvas;
}

export function get2dContext(canvas: GameCanvas): Ctx2D {
  return canvas.getContext('2d');
}

export function getScreenInfo(): ScreenInfo {
  const info = wx.getSystemInfoSync();
  return {
    windowWidth: info.windowWidth,
    windowHeight: info.windowHeight,
    pixelRatio: info.pixelRatio,
  };
}

export function createImage(): GameImage {
  return wx.createImage() as unknown as GameImage;
}

/** 读取随包发布的文本资源（如 Tiled JSON）。失败时抛错由上层捕获。 */
export function readTextFileSync(path: string): string {
  const fs = wx.getFileSystemManager();
  const content = fs.readFileSync(path, 'utf-8');
  return typeof content === 'string' ? content : String(content);
}

export function onTouchStart(handler: TouchHandler): void {
  wx.onTouchStart((res) => handler(res as unknown as GameTouchEvent));
}

export function onTouchMove(handler: TouchHandler): void {
  wx.onTouchMove((res) => handler(res as unknown as GameTouchEvent));
}

export function onTouchEnd(handler: TouchHandler): void {
  wx.onTouchEnd((res) => handler(res as unknown as GameTouchEvent));
}

export function onTouchCancel(handler: TouchHandler): void {
  wx.onTouchCancel((res) => handler(res as unknown as GameTouchEvent));
}

/** 读取本地存储的字符串（如登录态 token）。读取失败/缺失返回空串。 */
export function getStorageString(key: string): string {
  try {
    const value: unknown = wx.getStorageSync(key);
    return typeof value === 'string' ? value : '';
  } catch {
    return '';
  }
}

export interface HttpGetOptions {
  url: string;
  header?: Record<string, string>;
  timeoutMs?: number;
}

/** 极简 GET：成功 resolve HttpResult，任何失败 resolve null（绝不抛出，便于「非阻塞」用法）。 */
export function httpGetJson(options: HttpGetOptions): Promise<HttpResult | null> {
  return new Promise<HttpResult | null>((resolve) => {
    wx.request({
      url: options.url,
      method: 'GET',
      header: options.header ?? {},
      timeout: options.timeoutMs ?? 4000,
      success: (res) => {
        resolve({ statusCode: res.statusCode, data: res.data });
      },
      fail: () => resolve(null),
    });
  });
}

export interface HttpPostOptions {
  url: string;
  header?: Record<string, string>;
  body?: Record<string, unknown>;
  timeoutMs?: number;
}

/** 极简 POST(JSON)：成功 resolve HttpResult，任何失败 resolve null（绝不抛出，便于「非阻塞」用法）。 */
export function httpPostJson(options: HttpPostOptions): Promise<HttpResult | null> {
  return new Promise<HttpResult | null>((resolve) => {
    wx.request({
      url: options.url,
      method: 'POST',
      data: options.body ?? {},
      header: { 'content-type': 'application/json', ...(options.header ?? {}) },
      timeout: options.timeoutMs ?? 4000,
      success: (res) => {
        resolve({ statusCode: res.statusCode, data: res.data });
      },
      fail: () => resolve(null),
    });
  });
}

export interface NavigateMiniProgramOptions {
  appId: string;
  /** 目标页路径，留空打开首页。 */
  path?: string;
  /** 透传给目标小程序（其 onShow / getLaunchOptionsSync 可取）。 */
  extraData?: Record<string, unknown>;
  envVersion?: 'develop' | 'trial' | 'release';
}

/**
 * 跳转到另一个小程序（配套学习小程序）。成功 resolve true，失败/不支持 resolve false（绝不抛出）。
 * 调用方负责在 appId 为空时不要调用（见 config.companionEnabled）。
 */
export function navigateToMiniProgram(options: NavigateMiniProgramOptions): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    try {
      wx.navigateToMiniProgram({
        appId: options.appId,
        path: options.path,
        extraData: options.extraData,
        envVersion: options.envVersion,
        success: () => resolve(true),
        fail: () => resolve(false),
      });
    } catch {
      resolve(false);
    }
  });
}

export function requestFrame(callback: (timestampMs: number) => void): number {
  return requestAnimationFrame(callback);
}

export function cancelFrame(handle: number): void {
  cancelAnimationFrame(handle);
}

export function logInfo(message: string): void {
  console.log(message);
}

export function logError(message: string, error?: unknown): void {
  if (error === undefined) {
    console.error(message);
  } else {
    console.error(message, error);
  }
}
