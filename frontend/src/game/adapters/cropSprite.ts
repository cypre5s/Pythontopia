// 隔离原因：作物贴图与帧切换是 Phaser 渲染，业务层必须通过该 adapter 调用。
import type { CropStatus } from '@api/dto/CropTileDto';
import type { ImageLike, SceneLike, TextLike } from './sceneTypes';

export interface CropSpriteSpec {
  tileX: number;
  tileY: number;
  tileSize: number;
  status: CropStatus;
  kcCode: string;
  label?: string;
}

export interface CropSpriteHandle {
  status: CropStatus;
  kcCode: string;
  tileX: number;
  tileY: number;
  setStatus(status: CropStatus): void;
}

const STATUS_FRAME: Record<CropStatus, { fill: number; outline: number; alpha: number; symbol: string }> = {
  LOCKED: { fill: 0x4f4434, outline: 0x322a1f, alpha: 0.55, symbol: '·' },
  SEED: { fill: 0x6a5b3d, outline: 0x322a1f, alpha: 0.95, symbol: '·' },
  SPROUT: { fill: 0xa3c47b, outline: 0x4c6a39, alpha: 1, symbol: '∴' },
  MATURE: { fill: 0xf4c95d, outline: 0x6e4f1d, alpha: 1, symbol: '✿' },
  WITHERED: { fill: 0x8a6e4a, outline: 0x3d2c19, alpha: 0.7, symbol: '×' },
};

export function createCropSprite(scene: SceneLike, spec: CropSpriteSpec): CropSpriteHandle {
  const frame = STATUS_FRAME[spec.status];
  const x = spec.tileX * spec.tileSize + spec.tileSize / 2;
  const y = spec.tileY * spec.tileSize + spec.tileSize / 2;
  const body = scene.add
    .rectangle(x, y, spec.tileSize - 8, spec.tileSize - 8, frame.fill, frame.alpha)
    .setStrokeStyle(2, frame.outline)
    .setInteractive()
    .setDepth(8);
  const label: TextLike = scene.add
    .text(x, y, frame.symbol, { color: '#1d1108', fontFamily: 'monospace', fontSize: '16px' })
    .setOrigin(0.5)
    .setDepth(9);
  const handle: CropSpriteHandle = {
    status: spec.status,
    kcCode: spec.kcCode,
    tileX: spec.tileX,
    tileY: spec.tileY,
    setStatus(next: CropStatus): void {
      this.status = next;
      const nextFrame = STATUS_FRAME[next];
      applyFrame(body as unknown as Record<string, unknown>, nextFrame);
      const labelHandle = label as unknown as { setText?: (value: string) => void };
      if (labelHandle.setText) {
        labelHandle.setText(nextFrame.symbol);
      }
    },
  };
  return handle;
}

function applyFrame(body: Record<string, unknown>, frame: { fill: number; outline: number; alpha: number }): void {
  const target = body as unknown as {
    fillColor?: number;
    setStrokeStyle?: (width: number, color: number) => unknown;
    setAlpha?: (alpha: number) => unknown;
  };
  target.fillColor = frame.fill;
  target.setStrokeStyle?.(2, frame.outline);
  target.setAlpha?.(frame.alpha);
}

export function setCropSpriteImage(image: ImageLike, status: CropStatus): void {
  const frame = STATUS_FRAME[status];
  image.setAlpha(frame.alpha);
  image.setScale(status === 'MATURE' ? 1.2 : 1);
}
