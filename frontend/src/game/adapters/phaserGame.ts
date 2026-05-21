// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
import Phaser from 'phaser';

export type PhaserSceneConfig = Phaser.Types.Scenes.SettingsConfig;

export interface PhaserGameHandle {
  destroy(removeCanvas: boolean): void;
}

export function createPhaserGame(rootEl: HTMLElement, scenes: PhaserSceneConfig[]): PhaserGameHandle {
  return new Phaser.Game({
    type: Phaser.AUTO,
    parent: rootEl,
    width: 960,
    height: 640,
    backgroundColor: '#77a86f',
    pixelArt: true,
    roundPixels: true,
    scene: scenes,
    scale: {
      mode: Phaser.Scale.FIT,
      autoCenter: Phaser.Scale.CENTER_BOTH,
    },
  });
}
