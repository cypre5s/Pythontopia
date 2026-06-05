// Canvas2D 像素渲染：瓦片层（按镜头裁剪）、NPC、玩家、HUD（交互横幅 + 方向键）。
// 全程关闭抗锯齿以保持像素清晰；世界坐标按 zoom 放大后绘制。
import type { Ctx2D, GameImage } from '../runtime/wx';
import type { Camera } from './Camera';
import type { Interactable, NpcSpawn, TileMap } from './TileMap';
import type { Player } from './Player';
import type { Direction, DpadLayout, Rect } from './layout';

const NPC_PALETTE: ReadonlyArray<string> = [
  '#e76f51',
  '#2a9d8f',
  '#e9c46a',
  '#8d6cab',
  '#4d96d9',
  '#c84b6b',
  '#5aa469',
  '#d98c5f',
];

const PLAYER_BODY = '#3a86ff';
const PLAYER_SKIN = '#ffd9a0';
const PLAYER_HAIR = '#5a3a29';

export class Renderer {
  constructor(
    private readonly ctx: Ctx2D,
    private viewWidth: number,
    private viewHeight: number,
    private readonly pixelRatio: number,
    private readonly drawTile: number,
  ) {}

  resize(viewWidth: number, viewHeight: number): void {
    this.viewWidth = viewWidth;
    this.viewHeight = viewHeight;
  }

  beginFrame(): void {
    const ctx = this.ctx;
    ctx.save();
    ctx.scale(this.pixelRatio, this.pixelRatio);
    ctx.imageSmoothingEnabled = false;
    ctx.fillStyle = '#7fb069';
    ctx.fillRect(0, 0, this.viewWidth, this.viewHeight);
  }

  endFrame(): void {
    this.ctx.restore();
  }

  drawWorld(
    map: TileMap,
    tileset: GameImage,
    camera: Camera,
    player: Player,
  ): void {
    const ctx = this.ctx;
    ctx.save();
    ctx.translate(-camera.x, -camera.y);
    this.drawTileLayers(map, tileset, camera);
    this.drawNpcs(map.npcs, camera);
    this.drawPlayer(player);
    ctx.restore();
  }

  private drawTileLayers(map: TileMap, tileset: GameImage, camera: Camera): void {
    const ctx = this.ctx;
    const draw = this.drawTile;
    const columns = map.tileset.columns;
    const srcW = map.tileset.tileWidth;
    const srcH = map.tileset.tileHeight;

    const startCol = Math.max(0, Math.floor(camera.x / draw) - 1);
    const endCol = Math.min(map.width - 1, Math.floor((camera.x + this.viewWidth) / draw) + 1);
    const startRow = Math.max(0, Math.floor(camera.y / draw) - 1);
    const endRow = Math.min(map.height - 1, Math.floor((camera.y + this.viewHeight) / draw) + 1);

    for (const layer of map.layers) {
      for (let row = startRow; row <= endRow; row += 1) {
        for (let col = startCol; col <= endCol; col += 1) {
          const gid = layer.data[row * layer.width + col];
          if (gid === undefined || gid === 0) {
            continue;
          }
          const frame = map.frameForGid(gid);
          if (frame < 0) {
            continue;
          }
          const sx = (frame % columns) * srcW;
          const sy = Math.floor(frame / columns) * srcH;
          ctx.drawImage(tileset, sx, sy, srcW, srcH, col * draw, row * draw, draw, draw);
        }
      }
    }
  }

  private drawNpcs(npcs: ReadonlyArray<NpcSpawn>, camera: Camera): void {
    const draw = this.drawTile;
    for (const npc of npcs) {
      const cx = (npc.tileX + 0.5) * draw;
      const footY = (npc.tileY + 1) * draw;
      if (!this.isVisible(cx, footY, camera, draw * 3)) {
        continue;
      }
      this.drawAvatar(cx, footY, colorFor(npc.sprite + npc.key), '#3b2a1a', '#ffd9a0', 0);
      this.drawHintBubble(cx, footY - draw * 1.7);
      this.drawNameTag(npc.label, cx, footY - draw * 1.85);
    }
  }

  private drawPlayer(player: Player): void {
    const draw = this.drawTile;
    const { fx, fy } = player.fractionalTile();
    const cx = (fx + 0.5) * draw;
    const footY = (fy + 1) * draw + player.bobOffset();
    this.drawAvatar(cx, footY, PLAYER_BODY, PLAYER_HAIR, PLAYER_SKIN, 0);
    this.drawFacing(cx, footY, player.facing);
  }

  // 方块小人：阴影 + 身体 + 头 + 头发，纯矩形保持像素味。
  private drawAvatar(
    cx: number,
    footY: number,
    body: string,
    hair: string,
    skin: string,
    bodyInset: number,
  ): void {
    const ctx = this.ctx;
    const unit = this.drawTile / 16;
    const bodyW = this.drawTile * 0.62 - bodyInset;
    const bodyH = this.drawTile * 0.62;
    const headSize = this.drawTile * 0.5;
    const top = footY - bodyH - headSize;

    ctx.save();
    ctx.translate(cx, footY - 1);
    ctx.scale(1, 0.5);
    ctx.fillStyle = 'rgba(0,0,0,0.28)';
    ctx.beginPath();
    ctx.arc(0, 0, this.drawTile * 0.34, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    ctx.fillStyle = body;
    ctx.fillRect(Math.round(cx - bodyW / 2), Math.round(footY - bodyH), Math.round(bodyW), Math.round(bodyH));
    ctx.fillStyle = 'rgba(0,0,0,0.18)';
    ctx.fillRect(Math.round(cx - bodyW / 2), Math.round(footY - bodyH), Math.round(bodyW), Math.round(2 * unit));

    ctx.fillStyle = skin;
    ctx.fillRect(Math.round(cx - headSize / 2), Math.round(top), Math.round(headSize), Math.round(headSize));
    ctx.fillStyle = hair;
    ctx.fillRect(Math.round(cx - headSize / 2), Math.round(top), Math.round(headSize), Math.round(headSize * 0.34));
  }

  private drawFacing(cx: number, footY: number, facing: Direction): void {
    const ctx = this.ctx;
    const unit = this.drawTile / 16;
    const headSize = this.drawTile * 0.5;
    const bodyH = this.drawTile * 0.62;
    const eyeY = footY - bodyH - headSize + headSize * 0.55;
    const eye = Math.max(2, Math.round(2 * unit));
    ctx.fillStyle = '#23314a';
    if (facing === 'left') {
      ctx.fillRect(Math.round(cx - headSize * 0.32), Math.round(eyeY), eye, eye);
    } else if (facing === 'right') {
      ctx.fillRect(Math.round(cx + headSize * 0.32 - eye), Math.round(eyeY), eye, eye);
    } else if (facing === 'up') {
      // 背对镜头：不画眼睛。
    } else {
      ctx.fillRect(Math.round(cx - headSize * 0.28), Math.round(eyeY), eye, eye);
      ctx.fillRect(Math.round(cx + headSize * 0.28 - eye), Math.round(eyeY), eye, eye);
    }
  }

  private drawHintBubble(cx: number, y: number): void {
    const ctx = this.ctx;
    const w = this.drawTile * 0.42;
    const h = this.drawTile * 0.5;
    roundRect(ctx, cx - w / 2, y - h, w, h, Math.min(6, w * 0.3));
    ctx.fillStyle = '#ffd166';
    ctx.fill();
    ctx.fillStyle = '#4d3613';
    ctx.font = `${Math.round(this.drawTile * 0.4)}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('!', cx, y - h * 0.52);
  }

  private drawNameTag(text: string, cx: number, baselineY: number): void {
    const ctx = this.ctx;
    const fontPx = Math.max(9, Math.round(this.drawTile * 0.3));
    ctx.font = `${fontPx}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'bottom';
    const padX = 4;
    const w = ctx.measureText(text).width + padX * 2;
    const h = fontPx + 6;
    roundRect(ctx, cx - w / 2, baselineY - h, w, h, 3);
    ctx.fillStyle = 'rgba(20,16,12,0.7)';
    ctx.fill();
    ctx.fillStyle = '#fff8e1';
    ctx.fillText(text, cx, baselineY - 3);
  }

  private isVisible(worldX: number, worldY: number, camera: Camera, margin: number): boolean {
    return (
      worldX >= camera.x - margin &&
      worldX <= camera.x + this.viewWidth + margin &&
      worldY >= camera.y - margin &&
      worldY <= camera.y + this.viewHeight + margin
    );
  }

  // ── HUD（屏幕坐标，不随镜头平移）──

  drawHud(dpad: DpadLayout, held: Direction | null, banner: Interactable | null): void {
    this.drawTitleChip();
    if (banner) {
      this.drawBanner(banner);
    } else {
      this.drawHelp();
    }
    this.drawDpad(dpad, held);
  }

  private drawTitleChip(): void {
    const ctx = this.ctx;
    const text = 'Pythontopia · 小镇';
    const fontPx = 13;
    ctx.font = `${fontPx}px sans-serif`;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    const w = ctx.measureText(text).width + 20;
    const h = 26;
    const x = 12;
    const y = 14;
    roundRect(ctx, x, y, w, h, 8);
    ctx.fillStyle = 'rgba(20,16,12,0.62)';
    ctx.fill();
    ctx.fillStyle = '#ffd166';
    ctx.fillText(text, x + 10, y + h / 2 + 1);
  }

  private drawHelp(): void {
    const ctx = this.ctx;
    const text = '方向键或滑动屏幕移动 · 靠近建筑/NPC 查看信息';
    const fontPx = 12;
    ctx.font = `${fontPx}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    const w = ctx.measureText(text).width + 24;
    const h = 26;
    const x = this.viewWidth / 2 - w / 2;
    const y = 52;
    roundRect(ctx, x, y, w, h, 8);
    ctx.fillStyle = 'rgba(20,16,12,0.5)';
    ctx.fill();
    ctx.fillStyle = '#f3ede1';
    ctx.fillText(text, this.viewWidth / 2, y + 7);
  }

  private drawBanner(item: Interactable): void {
    const ctx = this.ctx;
    const titleFont = 18;
    const subFont = 12;
    const kindLabel = item.kind === 'npc' ? 'NPC' : item.kind === 'landmark' ? '地标' : '建筑';
    const subtitle = item.subtitle ? `${kindLabel} · ${item.subtitle}` : kindLabel;

    ctx.font = `${titleFont}px sans-serif`;
    const titleW = ctx.measureText(item.title).width;
    ctx.font = `${subFont}px sans-serif`;
    const subW = ctx.measureText(subtitle).width;

    const contentW = Math.max(titleW, subW);
    const w = Math.min(this.viewWidth - 24, contentW + 32);
    const h = 58;
    const x = this.viewWidth / 2 - w / 2;
    const y = 44;

    roundRect(ctx, x, y, w, h, 12);
    ctx.fillStyle = 'rgba(24,18,12,0.82)';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#ffd166';
    roundRect(ctx, x, y, w, h, 12);
    ctx.stroke();

    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = '#fff8e1';
    ctx.font = `${titleFont}px sans-serif`;
    ctx.fillText(item.title, this.viewWidth / 2, y + 22);
    ctx.fillStyle = '#ffd166';
    ctx.font = `${subFont}px sans-serif`;
    ctx.fillText(subtitle, this.viewWidth / 2, y + 42);
  }

  private drawDpad(dpad: DpadLayout, held: Direction | null): void {
    this.drawDpadButton(dpad.up, '▲', held === 'up');
    this.drawDpadButton(dpad.down, '▼', held === 'down');
    this.drawDpadButton(dpad.left, '◀', held === 'left');
    this.drawDpadButton(dpad.right, '▶', held === 'right');
  }

  private drawDpadButton(rect: Rect, glyph: string, active: boolean): void {
    const ctx = this.ctx;
    roundRect(ctx, rect.x, rect.y, rect.w, rect.h, 10);
    ctx.fillStyle = active ? 'rgba(255,209,102,0.85)' : 'rgba(20,16,12,0.45)';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(255,248,225,0.55)';
    roundRect(ctx, rect.x, rect.y, rect.w, rect.h, 10);
    ctx.stroke();
    ctx.fillStyle = active ? '#3a2a12' : '#f3ede1';
    ctx.font = `${Math.round(rect.h * 0.42)}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(glyph, rect.x + rect.w / 2, rect.y + rect.h / 2 + 1);
  }
}

function colorFor(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  const index = Math.abs(hash) % NPC_PALETTE.length;
  return NPC_PALETTE[index] ?? PLAYER_BODY;
}

function roundRect(ctx: Ctx2D, x: number, y: number, w: number, h: number, r: number): void {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.lineTo(x + w - radius, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
  ctx.lineTo(x + w, y + h - radius);
  ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
  ctx.lineTo(x + radius, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
}
