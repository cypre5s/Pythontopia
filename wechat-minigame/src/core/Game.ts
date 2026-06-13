// 游戏主控：建画布与上下文、加载资源、装配子系统、跑 requestAnimationFrame 主循环。
// 同时编排「交互（NPC/建筑对话）」「bootstrap HUD」「跳转配套学习小程序」三条新链路。
import {
  cancelFrame,
  get2dContext,
  getMainCanvas,
  getScreenInfo,
  logError,
  logInfo,
  navigateToMiniProgram,
  requestFrame,
  type Ctx2D,
  type GameCanvas,
  type GameImage,
} from '../runtime/wx';
import { COMPANION, companionEnabled } from '../config';
import { fetchBootstrap, fetchMainlineSummary, fetchNpcGreeting } from '../api';
import { loadImage, loadTownMapJson, MAP_PATH, TILESET_PATH } from './assets';
import { Camera } from './Camera';
import { Dialog } from './Dialog';
import { buildDialogView, loadNpcLineBook, NpcLineBook } from './dialogContent';
import { Input, type InputAction } from './Input';
import { computeDialogLayout, rectContains } from './layout';
import { Player } from './Player';
import { Renderer, type HudFrame, type HudStatus, type InteractPrompt } from './Renderer';
import { TileMap, type Interactable, type TilePoint } from './TileMap';

const SOURCE_TILE = 16;
const FALLBACK_SPAWN: TilePoint = { tileX: 25, tileY: 20 };
const MAX_FRAME_DELTA_MS = 50;

export class Game {
  private readonly canvas: GameCanvas;
  private readonly ctx: Ctx2D;
  private readonly viewWidth: number;
  private readonly viewHeight: number;
  private readonly pixelRatio: number;
  private readonly drawTile: number;

  private map: TileMap | null = null;
  private tileset: GameImage | null = null;
  private player: Player | null = null;
  private camera: Camera | null = null;
  private input: Input | null = null;
  private renderer: Renderer | null = null;

  private readonly dialog = new Dialog();
  private lineBook: NpcLineBook = new NpcLineBook(new Map());
  private nearby: Interactable | null = null;
  private hudStatus: HudStatus | null = null;

  private rafHandle = 0;
  private lastTimestamp = 0;
  private running = false;

  constructor() {
    this.canvas = getMainCanvas();
    const screen = getScreenInfo();
    this.viewWidth = screen.windowWidth;
    this.viewHeight = screen.windowHeight;
    this.pixelRatio = screen.pixelRatio;
    this.canvas.width = Math.floor(this.viewWidth * this.pixelRatio);
    this.canvas.height = Math.floor(this.viewHeight * this.pixelRatio);
    this.ctx = get2dContext(this.canvas);

    const zoom = clampInt(
      Math.round(Math.min(this.viewWidth, this.viewHeight) / (SOURCE_TILE * 8)),
      2,
      4,
    );
    this.drawTile = SOURCE_TILE * zoom;
  }

  async start(): Promise<void> {
    this.lineBook = loadNpcLineBook();
    const rawMap = loadTownMapJson(MAP_PATH);
    this.tileset = await loadImage(TILESET_PATH);

    const map = new TileMap(rawMap, FALLBACK_SPAWN);
    this.map = map;

    const spawn = nearestWalkable(map, map.spawn);
    const player = new Player(spawn.tileX, spawn.tileY);
    this.player = player;

    const worldWidth = map.width * this.drawTile;
    const worldHeight = map.height * this.drawTile;
    this.camera = new Camera(this.viewWidth, this.viewHeight, worldWidth, worldHeight);
    this.input = new Input(this.viewWidth, this.viewHeight);
    this.renderer = new Renderer(
      this.ctx,
      this.viewWidth,
      this.viewHeight,
      this.pixelRatio,
      this.drawTile,
    );

    this.centerCameraOnPlayer();
    this.running = true;
    const loop = (timestamp: number): void => this.frame(timestamp, loop);
    this.rafHandle = requestFrame(loop);

    void this.applyBootstrap();
    void this.applyMainline();
    logInfo('[pythontopia] 小镇渲染端已启动');
  }

  stop(): void {
    this.running = false;
    if (this.rafHandle) {
      cancelFrame(this.rafHandle);
      this.rafHandle = 0;
    }
  }

  /** 后端 bootstrap（非阻塞）：成功则覆盖落点并点亮 HUD；失败安静回退。 */
  private async applyBootstrap(): Promise<void> {
    try {
      const result = await fetchBootstrap();
      if (!result) {
        return;
      }
      if (result.hud) {
        this.patchHud({
          day: result.hud.dayIndex,
          weather: result.hud.weather,
          weekday: result.hud.weekday,
          questCount: result.hud.questCount,
        });
      }
      const spawn = result.spawn;
      if (spawn && this.map && this.player && !this.map.isBlocked(spawn.tileX, spawn.tileY)) {
        this.player.placeAt(spawn.tileX, spawn.tileY);
        this.centerCameraOnPlayer();
        logInfo(`[pythontopia] 后端落点生效: (${spawn.tileX}, ${spawn.tileY})`);
      }
    } catch (err) {
      logError('[pythontopia] bootstrap 获取失败（已忽略）', err);
    }
  }

  /** 合并式更新 HUD 状态（bootstrap 与 mainline 两条链路各更新一部分字段）。 */
  private patchHud(patch: Partial<HudStatus>): void {
    const base: HudStatus = this.hudStatus ?? {
      day: null,
      weather: null,
      weekday: null,
      questCount: null,
      stars: null,
    };
    this.hudStatus = { ...base, ...patch };
  }

  /** 主线总星（非阻塞）：成功则点亮 HUD「⭐ N 星」；失败安静忽略。 */
  private async applyMainline(): Promise<void> {
    try {
      const summary = await fetchMainlineSummary();
      if (summary) {
        this.patchHud({ stars: summary.totalStars });
      }
    } catch (err) {
      logError('[pythontopia] 主线进度获取失败（已忽略）', err);
    }
  }

  private frame(timestamp: number, loop: (t: number) => void): void {
    if (!this.running) {
      return;
    }
    const delta =
      this.lastTimestamp === 0
        ? 16
        : Math.min(MAX_FRAME_DELTA_MS, timestamp - this.lastTimestamp);
    this.lastTimestamp = timestamp;

    this.update(delta);
    this.render();

    this.rafHandle = requestFrame(loop);
  }

  private update(deltaMs: number): void {
    const { map, player, input } = this;
    if (!map || !player || !input) {
      return;
    }
    player.update(deltaMs, input.getDirection(), map);
    this.centerCameraOnPlayer();

    this.nearby = map.findInteractableNear(player.tileX, player.tileY);
    const openTarget = this.dialog.currentTarget();
    if (
      this.dialog.isOpen() &&
      openTarget &&
      !map.isInteractableAdjacent(openTarget, player.tileX, player.tileY)
    ) {
      this.dialog.close();
    }

    for (const action of input.consumeActions()) {
      this.handleAction(action);
    }
  }

  private handleAction(action: InputAction): void {
    if (this.dialog.isOpen()) {
      if (action.type === 'tap') {
        if (this.dialog.hasDeepLink() && this.tapHitsDeepLink(action.x, action.y)) {
          this.triggerDeepLink();
        } else {
          this.dialog.close();
        }
        return;
      }
      // 交互键：有深链则前往，否则关闭。
      if (this.dialog.hasDeepLink()) {
        this.triggerDeepLink();
      } else {
        this.dialog.close();
      }
      return;
    }

    if (this.nearby) {
      const target = this.nearby;
      this.dialog.open(buildDialogView(target, this.lineBook), target);
      if (target.kind === 'npc' && target.npcKey) {
        void this.loadGreeting(target.npcKey);
      }
    }
  }

  /** 拉取 NPC 个性化寒暄（非阻塞）：成功且仍是同一 NPC 时回填到对话框；失败安静忽略。 */
  private async loadGreeting(npcKey: string): Promise<void> {
    try {
      const result = await fetchNpcGreeting(npcKey);
      if (result && result.available && !result.degraded && result.greeting.trim().length > 0) {
        this.dialog.applyNpcGreeting(npcKey, result.greeting);
      }
    } catch (err) {
      logError('[pythontopia] NPC 寒暄获取失败（已忽略）', err);
    }
  }

  private tapHitsDeepLink(x: number, y: number): boolean {
    const { action } = computeDialogLayout(this.viewWidth, this.viewHeight);
    return rectContains(action, x, y);
  }

  private triggerDeepLink(): void {
    const deepLink = this.dialog.deepLink();
    this.dialog.close();
    if (!deepLink) {
      return;
    }
    if (!companionEnabled()) {
      logInfo(`[pythontopia] 配套学习小程序未配置，跳过跳转：${deepLink.route}`);
      return;
    }
    void navigateToMiniProgram({
      appId: COMPANION.appId,
      path: COMPANION.path || undefined,
      envVersion: COMPANION.envVersion,
      extraData: { route: deepLink.route, from: 'minigame_town' },
    }).then((ok) => {
      if (!ok) {
        logError(`[pythontopia] 跳转学习小程序失败：${deepLink.route}`);
      }
    });
  }

  private render(): void {
    const { map, player, input, camera, renderer, tileset } = this;
    if (!map || !player || !input || !camera || !renderer || !tileset) {
      return;
    }
    const highlight = this.dialog.isOpen() ? this.dialog.currentTarget() : this.nearby;
    renderer.beginFrame();
    renderer.drawWorld(map, tileset, camera, player, highlight);
    renderer.drawHud(this.buildHudFrame(input));
    renderer.endFrame();
  }

  private buildHudFrame(input: Input): HudFrame {
    const dialog = this.dialog.currentView();
    const prompt: InteractPrompt | null =
      !dialog && this.nearby
        ? { title: this.nearby.title, badge: badgeFor(this.nearby) }
        : null;
    return {
      dpad: input.getDpad(),
      heldDir: input.getHeldButton(),
      interactLabel: this.interactLabel(),
      interactActive: input.getInteractHeld(),
      interactEnabled: dialog !== null || this.nearby !== null,
      status: this.hudStatus,
      prompt,
      dialog,
    };
  }

  private interactLabel(): string {
    const dialog = this.dialog.currentView();
    if (dialog) {
      return dialog.deepLink ? '前往' : '关闭';
    }
    if (!this.nearby) {
      return '·';
    }
    return this.nearby.kind === 'npc' ? '对话' : '查看';
  }

  private centerCameraOnPlayer(): void {
    const { player, camera } = this;
    if (!player || !camera) {
      return;
    }
    const { fx, fy } = player.fractionalTile();
    camera.follow((fx + 0.5) * this.drawTile, (fy + 0.5) * this.drawTile);
  }
}

function badgeFor(item: Interactable): string {
  if (item.kind === 'npc') {
    return 'NPC';
  }
  if (item.kind === 'landmark') {
    return '地标';
  }
  return '建筑';
}

function clampInt(value: number, min: number, max: number): number {
  if (value < min) {
    return min;
  }
  if (value > max) {
    return max;
  }
  return value;
}

/** 出生点若被占用，做螺旋式就近搜索找最近可通行格。 */
function nearestWalkable(map: TileMap, start: TilePoint): TilePoint {
  if (!map.isBlocked(start.tileX, start.tileY)) {
    return start;
  }
  for (let radius = 1; radius < Math.max(map.width, map.height); radius += 1) {
    for (let dy = -radius; dy <= radius; dy += 1) {
      for (let dx = -radius; dx <= radius; dx += 1) {
        if (Math.max(Math.abs(dx), Math.abs(dy)) !== radius) {
          continue;
        }
        const tileX = start.tileX + dx;
        const tileY = start.tileY + dy;
        if (!map.isBlocked(tileX, tileY)) {
          return { tileX, tileY };
        }
      }
    }
  }
  return start;
}
