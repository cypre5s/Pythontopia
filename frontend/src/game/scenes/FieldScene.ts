import { router } from '@/app/router';
import { playBgm } from '@/utils/bgm';
import { createCropSprite, type CropSpriteHandle } from '@game/adapters/cropSprite';
import type { SceneLike } from '@game/adapters/sceneTypes';
import { useLearningStore } from '@stores/learning';

let cropHandles: CropSpriteHandle[] = [];
let tileSize = 32;

export const FieldScene = {
  key: 'FieldScene',
  preload(this: SceneLike) {
    // 静态资源由 BootScene 预加载，FieldScene 不重复 preload。
    void this;
  },
  create(this: SceneLike) {
    tileSize = 32;
    const width = 30;
    const height = 15;
    this.add
      .tileSprite(
        (width * tileSize) / 2,
        (height * tileSize) / 2,
        width * tileSize,
        height * tileSize,
        'tileset_farm',
      )
      .setAlpha(0.36)
      .setDepth(0);
    this.add
      .grid(
        (width * tileSize) / 2,
        (height * tileSize) / 2,
        width * tileSize,
        height * tileSize,
        tileSize,
        tileSize,
        0xb9d287,
        0.9,
        0x4f6c33,
        0.4,
      )
      .setDepth(1);
    this.add
      .text(8, 8, '知识田 · 65 KC × Python 语言基础', {
        color: '#1d1108',
        fontFamily: 'monospace',
        fontSize: '14px',
      })
      .setDepth(3);

    cropHandles = [];
    const learning = useLearningStore();
    void learning.loadField().then(() => {
      renderCrops(this);
    });
    this.cameras.main.setBounds(0, 0, width * tileSize, height * tileSize);
    this.physics?.world.setBounds(0, 0, width * tileSize, height * tileSize);
    playBgm('bgm_town_day');
  },
  update(this: SceneLike) {
    void this;
  },
};

function renderCrops(scene: SceneLike): void {
  cropHandles = [];
  const learning = useLearningStore();
  learning.field.crops.forEach((crop) => {
    const handle = createCropSprite(scene, {
      tileX: crop.tileX,
      tileY: crop.tileY,
      tileSize,
      status: crop.status,
      kcCode: crop.kcCode,
    });
    cropHandles.push(handle);
    bindClick(handle, crop.kcCode);
  });
}

function bindClick(handle: CropSpriteHandle, kcCode: string): void {
  const sprite = handle as unknown as {
    on?: (event: string, callback: () => void) => void;
  };
  if (typeof sprite.on === 'function') {
    sprite.on('pointerdown', () => {
      useLearningStore().selectKc(kcCode);
      void router.push({ path: '/field', query: { kc: kcCode } });
    });
  }
}
