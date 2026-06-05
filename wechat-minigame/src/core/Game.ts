// 游戏主控：建画布与上下文、加载资源、装配子系统、跑 requestAnimationFrame 主循环。
import {
  cancelFrame,
  get2dContext,
  getMainCanvas,
  getScreenInfo,
  logError,
  logInfo,
  requestFrame,
  type Ctx2D,
  type GameCanvas,
  type GameImage,
} from '../runtime/wx';
import { fetchBootstrapSpawn } from '../api';
import { loadImage, loadTownMapJson, MAP_PATH, TILESET_PATH } from './assets';
import { Camera } from './Camera';
import { Input } from './Input';
import { Player } from './Player';
import { Renderer } from './Renderer';
import { TileMap, type TilePoint } from './TileMap';

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

    void this.applyBackendSpawn();
    logInfo('[pythontopia] 小镇渲染端已启动');
  }

  stop(): void {
    this.running = false;
    if (this.rafHandle) {
      cancelFrame(this.rafHandle);
      this.rafHandle = 0;
    }
  }

  private async applyBackendSpawn(): Promise<void> {
    try {
      const spawn = await fetchBootstrapSpawn();
      if (!spawn || !this.map || !this.player) {
        return;
      }
      if (this.map.isBlocked(spawn.tileX, spawn.tileY)) {
        return;
      }
      this.player.placeAt(spawn.tileX, spawn.tileY);
      this.centerCameraOnPlayer();
      logInfo(`[pythontopia] 后端落点生效: (${spawn.tileX}, ${spawn.tileY})`);
    } catch (err) {
      logError('[pythontopia] bootstrap 落点获取失败（已忽略）', err);
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
  }

  private render(): void {
    const { map, player, input, camera, renderer, tileset } = this;
    if (!map || !player || !input || !camera || !renderer || !tileset) {
      return;
    }
    const banner = map.findInteractableNear(player.tileX, player.tileY);
    renderer.beginFrame();
    renderer.drawWorld(map, tileset, camera, player);
    renderer.drawHud(input.getDpad(), input.getHeldButton(), banner);
    renderer.endFrame();
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
