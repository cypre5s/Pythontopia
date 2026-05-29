// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 单例化原因：GameCanvas / FieldCanvas 等多个挂载点可能同时 mount，必须保证全局只有一个 Phaser.Game 实例。
import Phaser from 'phaser';

export type PhaserSceneConfig = Phaser.Types.Scenes.SettingsConfig;

let gameInstance: Phaser.Game | null = null;

export interface PhaserGameHandle {
  destroy(removeCanvas: boolean): void;
}

export function createPhaserGame(rootEl: HTMLElement, scenes: PhaserSceneConfig[]): PhaserGameHandle {
  if (gameInstance) {
    return { destroy: (removeCanvas) => destroyPhaserGame(removeCanvas) };
  }
  // R-12 fullscreen 沉浸：用 RESIZE 模式让 canvas 永远等于父容器尺寸（WorldCanvas
  // 的 ResizeObserver 会 scale.resize 推送视口变化）。pixelArt + roundPixels 保证
  // 整数倍像素缩放，配合 WorldScene 的 RENDER_SCALE × CAMERA_ZOOM 共同决定 tile
  // 显示尺寸。
  // 初始尺寸用当前 rootEl 的真实 boundingClientRect，避免老的 960×640 黑边一闪。
  const rect = rootEl.getBoundingClientRect();
  const initWidth = Math.max(320, Math.floor(rect.width || window.innerWidth));
  const initHeight = Math.max(240, Math.floor(rect.height || window.innerHeight));
  gameInstance = new Phaser.Game({
    type: Phaser.AUTO,
    parent: rootEl,
    width: initWidth,
    height: initHeight,
    backgroundColor: '#1c2b18',
    pixelArt: true,
    roundPixels: true,
    scene: scenes,
    physics: {
      default: 'arcade',
      arcade: { gravity: { x: 0, y: 0 }, debug: false },
    },
    scale: {
      // RESIZE：canvas 跟随父容器尺寸（WorldCanvas ResizeObserver 触发 scale.resize），
      // 不再做信箱式裁切；camera 仍然居中跟随 player，所以画面焦点不会漂移。
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH,
    },
  });
  return { destroy: destroyPhaserGame };
}

export function getPhaserGame(): Phaser.Game | null {
  return gameInstance;
}

function destroyPhaserGame(removeCanvas = true): void {
  if (gameInstance) {
    gameInstance.destroy(removeCanvas);
    gameInstance = null;
  }
}
