// Canvas2D 像素渲染：瓦片层（按镜头裁剪）、NPC、玩家、HUD（交互横幅 + 方向键）。
// 全程关闭抗锯齿以保持像素清晰；世界坐标按 zoom 放大后绘制。
import type { Ctx2D, GameImage } from '../runtime/wx';
import type { Camera } from './Camera';
import type { Interactable, NpcSpawn, TileMap } from './TileMap';
import type { Player } from './Player';
import type { DialogView } from './dialogContent';
import {
  computeDialogLayout,
  computeInteractButton,
  type Direction,
  type DpadLayout,
  type Rect,
} from './layout';

/** 进入小镇后从 bootstrap 取得的 HUD 摘要（任一字段缺失即省略）。 */
export interface HudStatus {
  day: number | null;
  weather: string | null;
  weekday: string | null;
  questCount: number | null;
}

/** 靠近某对象但尚未打开对话时的提示。 */
export interface InteractPrompt {
  title: string;
  badge: string;
}

/** 一帧 HUD 所需的全部信息（屏幕坐标层）。 */
export interface HudFrame {
  dpad: DpadLayout;
  heldDir: Direction | null;
  interactLabel: string;
  interactActive: boolean;
  interactEnabled: boolean;
  status: HudStatus | null;
  prompt: InteractPrompt | null;
  dialog: DialogView | null;
}

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
    nearby: Interactable | null,
  ): void {
    const ctx = this.ctx;
    ctx.save();
    ctx.translate(-camera.x, -camera.y);
    this.drawTileLayers(map, tileset, camera);
    if (nearby) {
      this.drawNearbyHighlight(nearby);
    }
    this.drawNpcs(map.npcs, camera);
    this.drawPlayer(player);
    ctx.restore();
  }

  /** 在当前可交互对象的 footprint 上画一圈淡金色高亮，提示「这个可以交互」。 */
  private drawNearbyHighlight(item: Interactable): void {
    const ctx = this.ctx;
    const draw = this.drawTile;
    ctx.save();
    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(255,209,102,0.9)';
    ctx.fillStyle = 'rgba(255,209,102,0.14)';
    for (const tile of item.tiles) {
      const x = tile.x * draw + 2;
      const y = tile.y * draw + 2;
      const s = draw - 4;
      ctx.fillRect(x, y, s, s);
      ctx.strokeRect(x, y, s, s);
    }
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

  drawHud(frame: HudFrame): void {
    this.drawStatusBar(frame.status);
    if (frame.dialog) {
      this.drawDialog(frame.dialog);
    } else if (frame.prompt) {
      this.drawPrompt(frame.prompt);
    } else {
      this.drawHelp();
    }
    this.drawDpad(frame.dpad, frame.heldDir);
    this.drawInteractButton(frame.interactLabel, frame.interactActive, frame.interactEnabled);
  }

  private drawStatusBar(status: HudStatus | null): void {
    const ctx = this.ctx;
    const h = 26;
    const x = 12;
    const y = 14;

    const title = 'Pythontopia · 小镇';
    ctx.font = '13px sans-serif';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    const titleW = ctx.measureText(title).width + 20;
    this.chip(x, y, titleW, h);
    ctx.fillStyle = '#ffd166';
    ctx.fillText(title, x + 10, y + h / 2 + 1);

    const statusText = status ? this.statusText(status) : '';
    if (statusText) {
      ctx.font = '12px sans-serif';
      const w = ctx.measureText(statusText).width + 20;
      const y2 = y + h + 6;
      this.chip(x, y2, w, h);
      ctx.fillStyle = '#e9f0d9';
      ctx.fillText(statusText, x + 10, y2 + h / 2 + 1);
    }
  }

  private statusText(status: HudStatus): string {
    const parts: string[] = [];
    if (status.day !== null) {
      parts.push(`第 ${status.day} 天`);
    }
    if (status.weekday) {
      parts.push(weekdayLabel(status.weekday));
    }
    if (status.weather) {
      parts.push(weatherLabel(status.weather));
    }
    if (status.questCount !== null) {
      parts.push(`今日任务 ${status.questCount}`);
    }
    return parts.join(' · ');
  }

  private drawHelp(): void {
    const ctx = this.ctx;
    const text = '方向键 / 滑动移动 · 靠近建筑或 NPC 轻点交互';
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    const w = ctx.measureText(text).width + 24;
    const h = 26;
    const x = this.viewWidth / 2 - w / 2;
    const y = 70;
    roundRect(ctx, x, y, w, h, 8);
    ctx.fillStyle = 'rgba(20,16,12,0.5)';
    ctx.fill();
    ctx.fillStyle = '#f3ede1';
    ctx.fillText(text, this.viewWidth / 2, y + 7);
  }

  private drawPrompt(prompt: InteractPrompt): void {
    const ctx = this.ctx;
    const verb = prompt.badge === 'NPC' ? '交谈' : '查看';
    const text = `${prompt.badge} · ${prompt.title} — 轻点或按右下键${verb}`;
    ctx.font = '13px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const w = Math.min(this.viewWidth - 24, ctx.measureText(text).width + 28);
    const h = 30;
    const x = this.viewWidth / 2 - w / 2;
    const y = 66;
    roundRect(ctx, x, y, w, h, 9);
    ctx.fillStyle = 'rgba(24,18,12,0.78)';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#ffd166';
    roundRect(ctx, x, y, w, h, 9);
    ctx.stroke();
    ctx.fillStyle = '#fff8e1';
    ctx.fillText(text, this.viewWidth / 2, y + h / 2 + 1);
  }

  private drawDialog(view: DialogView): void {
    const ctx = this.ctx;
    const layout = computeDialogLayout(this.viewWidth, this.viewHeight);
    const { box, action } = layout;
    const pad = 16;
    const hasAction = view.deepLink !== null;

    roundRect(ctx, box.x, box.y, box.w, box.h, 14);
    ctx.fillStyle = 'rgba(24,18,12,0.9)';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#ffd166';
    roundRect(ctx, box.x, box.y, box.w, box.h, 14);
    ctx.stroke();

    const innerX = box.x + pad;
    // 徽标
    ctx.font = '11px sans-serif';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    const badgeW = ctx.measureText(view.badge).width + 14;
    roundRect(ctx, innerX, box.y + pad, badgeW, 18, 6);
    ctx.fillStyle = 'rgba(255,209,102,0.22)';
    ctx.fill();
    ctx.fillStyle = '#ffd166';
    ctx.fillText(view.badge, innerX + 7, box.y + pad + 10);

    // 标题
    ctx.textBaseline = 'top';
    ctx.fillStyle = '#fff8e1';
    ctx.font = '18px sans-serif';
    ctx.fillText(view.title, innerX, box.y + pad + 24);

    // 正文（按字符换行）
    const bodyTop = box.y + pad + 24 + 26;
    const bodyBottom = (hasAction ? action.y : box.y + box.h - 30) - 8;
    const lineH = 20;
    ctx.font = '14px sans-serif';
    ctx.fillStyle = '#e7ddca';
    const maxWidth = box.w - pad * 2;
    let lines = this.wrapText(view.body, maxWidth);
    const maxLines = Math.max(1, Math.floor((bodyBottom - bodyTop) / lineH));
    if (lines.length > maxLines) {
      lines = lines.slice(0, maxLines);
      const last = lines[maxLines - 1] ?? '';
      lines[maxLines - 1] = `${last.slice(0, Math.max(0, last.length - 1))}…`;
    }
    let cursorY = bodyTop;
    for (const line of lines) {
      ctx.fillText(line, innerX, cursorY);
      cursorY += lineH;
    }

    // 小字脚注（如「学习内容在配套学习小程序中」）
    if (view.footnote) {
      ctx.font = '11px sans-serif';
      ctx.fillStyle = 'rgba(255,248,225,0.55)';
      ctx.fillText(view.footnote, innerX, cursorY + 2);
    }

    // 深链动作按钮 + 关闭提示
    if (hasAction && view.deepLink) {
      roundRect(ctx, action.x, action.y, action.w, action.h, 10);
      ctx.fillStyle = 'rgba(255,209,102,0.92)';
      ctx.fill();
      ctx.fillStyle = '#3a2a12';
      ctx.font = '14px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(view.deepLink.label, action.x + action.w / 2, action.y + action.h / 2 + 1);
    }
    this.drawDialogHint(box, hasAction);
  }

  private drawDialogHint(box: Rect, hasAction: boolean): void {
    const ctx = this.ctx;
    const text = hasAction ? '轻点空白处关闭 · 右下键前往' : '轻点空白处或按右下键关闭';
    ctx.font = '11px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'bottom';
    ctx.fillStyle = 'rgba(255,248,225,0.5)';
    ctx.fillText(text, box.x + box.w / 2, box.y + box.h - 8);
  }

  private wrapText(text: string, maxWidth: number): string[] {
    const ctx = this.ctx;
    const lines: string[] = [];
    let current = '';
    for (const ch of text) {
      if (ch === '\n') {
        lines.push(current);
        current = '';
        continue;
      }
      const candidate = current + ch;
      if (current && ctx.measureText(candidate).width > maxWidth) {
        lines.push(current);
        current = ch;
      } else {
        current = candidate;
      }
    }
    if (current) {
      lines.push(current);
    }
    return lines;
  }

  private chip(x: number, y: number, w: number, h: number): void {
    const ctx = this.ctx;
    roundRect(ctx, x, y, w, h, 8);
    ctx.fillStyle = 'rgba(20,16,12,0.62)';
    ctx.fill();
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

  private drawInteractButton(label: string, active: boolean, enabled: boolean): void {
    const ctx = this.ctx;
    const rect = computeInteractButton(this.viewWidth, this.viewHeight);
    const r = Math.min(rect.w, rect.h) / 2;
    const cx = rect.x + rect.w / 2;
    const cy = rect.y + rect.h / 2;

    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.closePath();
    if (active) {
      ctx.fillStyle = 'rgba(255,209,102,0.92)';
    } else if (enabled) {
      ctx.fillStyle = 'rgba(231,111,81,0.85)';
    } else {
      ctx.fillStyle = 'rgba(20,16,12,0.4)';
    }
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(255,248,225,0.6)';
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.closePath();
    ctx.stroke();

    ctx.fillStyle = active ? '#3a2a12' : enabled ? '#fff8e1' : 'rgba(243,237,225,0.6)';
    ctx.font = `${Math.round(r * 0.5)}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, cx, cy + 1);
  }
}

function weatherLabel(code: string): string {
  switch (code.toLowerCase()) {
    case 'sunny':
    case 'sun':
      return '晴';
    case 'cloudy':
    case 'cloud':
      return '多云';
    case 'rain':
    case 'rainy':
      return '雨';
    case 'wind':
    case 'windy':
      return '风';
    case 'snow':
    case 'snowy':
      return '雪';
    case 'storm':
    case 'stormy':
      return '雷雨';
    default:
      return code;
  }
}

function weekdayLabel(code: string): string {
  switch (code.toLowerCase()) {
    case 'monday':
      return '周一';
    case 'tuesday':
      return '周二';
    case 'wednesday':
      return '周三';
    case 'thursday':
      return '周四';
    case 'friday':
      return '周五';
    case 'saturday':
      return '周六';
    case 'sunday':
      return '周日';
    default:
      return code;
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
