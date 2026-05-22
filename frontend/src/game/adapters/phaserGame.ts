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
  gameInstance = new Phaser.Game({
    type: Phaser.AUTO,
    parent: rootEl,
    width: 960,
    height: 640,
    backgroundColor: '#77a86f',
    pixelArt: true,
    roundPixels: true,
    scene: scenes,
    physics: {
      default: 'arcade',
      arcade: { gravity: { x: 0, y: 0 }, debug: false },
    },
    scale: {
      mode: Phaser.Scale.FIT,
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
