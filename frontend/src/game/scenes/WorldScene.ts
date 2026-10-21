// WorldScene：R-14 世界收缩后仅 town_hub 一张实景地图（外围图与 mine_south 风景图已移除）。
// 保留 sceneKey 切换 / 场景过渡机制以备将来扩展，但当前单 hub 下无跨图传送。
//
// NpcFramework + InteractableSystem 接入：
//   - parseObjects 解析出的 npc 列表注册到 NpcFramework（patrol / 对话锚点 /
//     faceTowards），并派生 InteractableSystem 的 circle trigger，由 hintRenderer
//     在屏幕底部显示「按 E 与 {NPC 名} 对话」，actionPressed 边沿触发 dialog store；
//   - 另注册一个「公告板」非 NPC trigger；
//   - scene shutdown 走 attachLifecycle 兜底 tweens/time/input，并 clear framework
//     实例，避免切场景留下幽灵 trigger。
//
// 隔离边界：WorldScene 属于 game adapter 层，允许直接 import phaser；业务层仍
// 只通过 SceneLike / PlayerSpriteHandle 等结构化接口与本文件交互。
import Phaser from 'phaser';
import { router } from '@/app/router';
import { perfMonitor } from '@/services/perfMonitor';
import { playBgm, playSfx, type BgmKey } from '@/composables/useAudio';
import { Haptics } from '@game/adapters/haptics';
import type { CardinalDirection, Direction } from '@api/dto/Direction';
import {
  createDirectionController,
  toServerDirection,
  type DirectionController,
  type ExtendedDirection,
} from '@game/adapters/input';
import { openNpcDialog } from '@game/adapters/dialog';
import { createPlayerSprite } from '@game/adapters/sprite';
import { cardinalize, type CharacterAction } from '@game/adapters/lpcAnimations';
import { drawNpcSprite, type NpcSpawn, type NpcSpriteController } from '@game/adapters/tilemap';
import type { PlayerSpriteHandle, SceneLike } from '@game/adapters/sceneTypes';
import { applyWeather, normalizeWeatherKind, type WeatherHandle, type WeatherKind } from '@game/adapters/weather';
import { applyTimeOfDay, deriveTimeOfDay } from '@game/adapters/daynight';
import { applyMood, type MoodHandle, type MoodKind } from '@game/adapters/mood';
import {
  InteractableSystem,
  type HintRenderer,
  type ITrigger,
} from '@game/core/InteractableSystem';
import {
  NpcFramework,
  defaultPortraitKey,
  type NpcDefinition,
  type NpcWaypoint,
} from '@game/core/NpcFramework';
import { attachLifecycle } from '@game/core/SceneLifecycle';
import { resolveLowObstacleJump } from '@game/core/JumpSystem';
import { findTilePath, type WorldTile } from '@game/core/WorldPathfinding';
import {
  resolveSchedulePosition,
  getScheduleWaypoints,
  type NpcSchedule,
} from '@game/core/npcSchedule';
import { useWorldStore } from '@stores/world';
import { useMoodStore } from '@stores/mood';

const TILE_SIZE = 16;
const RENDER_SCALE = 1;
const RENDER_TILE_SIZE = TILE_SIZE * RENDER_SCALE;
// CAMERA_ZOOM：基准缩放，配合整数倍像素缩放避免子像素接缝。
const CAMERA_ZOOM = 1.5;
// 全屏适配 cover 缩放上限：地图小于视口时放大铺满，但不超过此值，避免像素插值毛刺
// （>3.0 时单屏 < 10 tile 且毛刺暴露）。
const CAMERA_ZOOM_MAX = 3;
const CAMERA_LERP = 0.15;
const MOVE_COOLDOWN_MS = 130;
// 奔跑时缩短逐格移动冷却（手感更快），与 run 动画帧率配合。
const RUN_COOLDOWN_FACTOR = 0.62;

// 每张地图按主题映射一个「工具动作」（按 C 触发）：hub 庆祝挥手。
// R-14：mine_south 风景图已删（Bug 矿洞改 /bug-mine 页面），未列地图按默认 celebrate 兜底。
const MAP_TOOL_ACTION: Readonly<Record<string, CharacterAction>> = {
  town_hub: 'celebrate',
};
function toolActionForMap(mapKey: string): CharacterAction {
  return MAP_TOOL_ACTION[mapKey] ?? 'celebrate';
}
// 转身→起步的短延迟：转身只占这一小段（而非整段移动冷却），让「按住转个方向就走」
// 跟手，同时仍保留「点一下=转身、按住=走起来」的星露谷手感。
const TURN_DELAY_MS = 70;
const RAIN_SLOWDOWN = 1.25;
const FADE_DURATION_MS = 280;
// 起跳 → 落地音的间隔，约等于 sprite.ts 的 JUMP_DURATION_MS(260) 的落地时刻。
const LANDING_SFX_DELAY_MS = 220;

// ── 人物↔NPC AABB 碰撞 + 顿帧(hit-stop) ──
// 玩家与 NPC 各视作以贴图中心为原点的正方形包围盒；候选目标格中心与各 NPC 像素中心
// 做轴对齐包围盒(AABB)重叠判定，重叠即视为不可进入（NPC 阻挡）。0.8 tile 的盒边长保证
// 相邻格可贴边站立、同格/半格内相撞。
const PLAYER_COLLIDE_BOX = RENDER_TILE_SIZE * 0.8;
const NPC_COLLIDE_BOX = RENDER_TILE_SIZE * 0.8;
// 撞到 NPC 时的顿帧(hit-stop)：仅做「短暂冻结 + 极轻撞击音」，不做镜头抖动。
// （Phaser camera.shake 的 intensity 会乘以整个视口尺寸再乘相机缩放，日常走位撞 NPC 时
//  极易整屏大幅震动且不适，故移除。）freeze 取 light 档 ≈3 帧(50ms：业界 20–50ms / 1–3 帧，
//  远低于 300ms 发涩上限)；REFRACTORY 不应期避免持续顶着 NPC 反复触发。
const HITSTOP_MS = 50;
const HITSTOP_REFRACTORY_MS = 320;

// ── 脚步反馈 ── 每走一格落一团淡出尘土（减弱动效时跳过），隔步播一声极轻脚步音并随机化
// 音高（避免机械重复，game feel 经验）。dust 深度落在玩家贴图(≈14)之下，贴地不挡身体。
const FOOTSTEP_VOLUME = 0.06;
const FOOTSTEP_DETUNE_RANGE = 600;
const DUST_DEPTH = 13.5;

// ── 活的小镇 ── NPC 在玩家此半径内会转头看玩家（巡逻/游走/待机呼吸已由 NpcFramework + 贴图实现）。
const LOOK_AT_RANGE_PX = RENDER_TILE_SIZE * 1.8;

// R-14 方案A：仅中央小镇 town_hub 为实景地图（mine_south 风景图已删，Bug 矿洞改 /bug-mine 页面）。
const SUPPORTED_MAP_KEYS: ReadonlySet<string> = new Set([
  'town_hub',
]);
const DEFAULT_MAP_KEY = 'town_hub';

const VALID_BGM_KEYS: ReadonlySet<string> = new Set<string>([
  'bgm_town_day',
  'bgm_town_eve',
  'bgm_bug_mine',
  'bgm_quest',
]);
const DEFAULT_BGM: BgmKey = 'bgm_town_day';

export interface MapIndexEntry {
  key: string;
  file?: string;
  label?: string;
  width?: number;
  height?: number;
  bgm?: string;
  tileset?: string;
  spawn?: { tileX: number; tileY: number };
  exits?: string[];
}

export interface MapIndex {
  maps: MapIndexEntry[];
}

interface ExitTile {
  toMap: string;
  toTileX: number;
  toTileY: number;
  tileX: number;
  tileY: number;
}

interface BuildingZone {
  route: string;
  label: string;
  tileX: number;
  tileY: number;
  width: number;
  height: number;
}

interface LowObstacleZone {
  tileX: number;
  tileY: number;
  width: number;
  height: number;
  sprite: string | null;
  frame: number | null;
}

/**
 * 地标（`type='landmark'`，如 town_hub 的镜月喷泉）：不参与碰撞、不可交互，
 * 只在瓦片实景上补一块名牌，让「地图 JSON 里写了名字」这件事在画面上有落点。
 */
interface LandmarkZone {
  label: string;
  tileX: number;
  tileY: number;
  width: number;
  height: number;
}

/**
 * 公告板 trigger 在 town_hub 中的中心 tile。
 * 喷泉位于 tile(24,19)-(25,20) 区域；公告板放在喷泉东侧两格，避开 spawn(25,20)。
 * hub 默认 50×40，27,20 落在喷泉右下方且在玩家可达范围内。
 */
const BULLETIN_BOARD_TILE_X = 27;
const BULLETIN_BOARD_TILE_Y = 20;
/** Trigger 命中半径（以 tile 为单位）：1.4 ≈ 玩家走到相邻一圈即触发。 */
const TRIGGER_RADIUS_TILES = 1.4;
/** 公告板 trigger 跳转的目标路由。 */
const BULLETIN_BOARD_ROUTE = '/board';

/**
 * 「冒险地图」告示牌 trigger 在 town_hub 中的中心 tile。
 * 与公告板（喷泉东侧两格 27,20）对称放在喷泉西侧两格 22,20，作为 R-15 主线唯一入口告示牌。
 */
const ADVENTURE_MAP_TILE_X = 22;
const ADVENTURE_MAP_TILE_Y = 20;
/** 「冒险地图」告示牌 trigger 跳转的目标路由（R-15 主线地图）。 */
const ADVENTURE_MAP_ROUTE = '/mainline-map';

/**
 * Phaser hint 气泡渲染器句柄：scene 端注入到 InteractableSystem，
 * 由 InteractableSystem 在 enter/exit/clear 时调用 show/hide。
 * destroy 在 shutdown 时由 scene 主动调用，回收 Phaser GameObject。
 */
interface PhaserHintRendererHandle {
  renderer: HintRenderer;
  destroy(): void;
}

/** NPC 可点击热点句柄（只暴露 update() 需要的最小接口，避免耦合 Phaser 类型）。 */
interface NpcHotspotHandle {
  setPosition?(x: number, y: number): unknown;
  destroy?(): void;
}

interface WorldSceneState {
  mapKey: string;
  index: MapIndex | null;
  entry: MapIndexEntry | null;
  player: PlayerSpriteHandle | null;
  controller: DirectionController | null;
  clickPath: WorldTile[];
  clickTarget: WorldTile | null;
  groundClickHandler: ((pointer: Phaser.Input.Pointer, currentlyOver?: Phaser.GameObjects.GameObject[]) => void) | null;
  lastMoveAt: number;
  actionWasDown: boolean;
  jumpWasDown: boolean;
  toolWasDown: boolean;
  // 顿帧(hit-stop)：this.time.now < hitStopUntil 期间冻结 update 推进；
  // lastHitStopAt 用于不应期节流，避免持续顶着 NPC 连续触发。
  hitStopUntil: number;
  lastHitStopAt: number;
  exits: ExitTile[];
  npcs: NpcSpawn[];
  width: number;
  height: number;
  renderTileSize: number;
  collisions: boolean[][];
  lowObstacles: boolean[][];
  switching: boolean;
  weather: WeatherHandle | null;
  weatherKind: WeatherKind;
  // 氛围叠加层句柄，与 weather 同生命周期（create 应用、init/shutdown 销毁）。
  mood: MoodHandle | null;
  // framework 实例在 create 中构造、shutdown 中 clear；默认 null，
  // 避免 jsdom 单测里 update() 误触发空 framework 时抛 NPE。
  interactable: InteractableSystem | null;
  npcFramework: NpcFramework | null;
  // 每个 NPC 的动画控制器，按 npcKey 索引；update() 每帧用
  // NpcFramework 实例位置同步贴图 + 行走/待机/工作手势动画。
  npcSprites: Map<string, NpcSpriteController>;
  // 每个 NPC 的「按 E 对话」trigger 引用：wander 时每帧把 trigger 圆心同步到 NPC 当前
  // 位置，让交互区跟随移动的 NPC（InteractableSystem 每帧读 shape，直接改 shape 即可）。
  npcTriggers: Map<string, ITrigger>;
  // 每个 NPC 的可点击热点（点一下开对话）：与 trigger 一样在 update 的同步循环里
  // 跟随 NPC 当前位置，wander / 日程 teleport 后仍然对得上。
  npcHotspots: Map<string, NpcHotspotHandle>;
  hintRendererHandle: PhaserHintRendererHandle | null;
  lifecycleDetach: (() => void) | null;
  // npcFramework.update 需要 deltaMs。Phaser scene 的 this.time.now 是单调递增
  // 毫秒时钟，记录上一帧的值在本帧相减即可。0 表示首帧，跳过 NPC tick 防止
  // dt 巨大跳变。
  prevUpdateTimeMs: number;
  // 「冒险地图」告示牌上的持久引导标记（「!」+ 标签）显示对象句柄：场景销毁时清理。
  // R-15 退役旧 mainQuest 后，标记不再按 activeQuest 动态重绘，改为单一静态引导。
  questMarkerObjects: Phaser.GameObjects.GameObject[];
}

const state: WorldSceneState = {
  mapKey: DEFAULT_MAP_KEY,
  index: null,
  entry: null,
  player: null,
  controller: null,
  clickPath: [],
  clickTarget: null,
  groundClickHandler: null,
  lastMoveAt: 0,
  actionWasDown: false,
  jumpWasDown: false,
  toolWasDown: false,
  hitStopUntil: 0,
  lastHitStopAt: 0,
  exits: [],
  npcs: [],
  width: 0,
  height: 0,
  renderTileSize: RENDER_TILE_SIZE,
  collisions: [],
  lowObstacles: [],
  switching: false,
  weather: null,
  weatherKind: 'sunny' as WeatherKind,
  mood: null,
  interactable: null,
  npcFramework: null,
  npcSprites: new Map(),
  npcTriggers: new Map(),
  npcHotspots: new Map(),
  hintRendererHandle: null,
  lifecycleDetach: null,
  prevUpdateTimeMs: 0,
  questMarkerObjects: [],
};

export const WorldScene = {
  key: 'WorldScene',
  init(this: Phaser.Scene, data?: { mapKey?: string }): void {
    const candidate = typeof data?.mapKey === 'string' ? data.mapKey : state.mapKey;
    state.mapKey = SUPPORTED_MAP_KEYS.has(candidate) ? candidate : DEFAULT_MAP_KEY;
    state.switching = false;
    state.lastMoveAt = 0;
    state.clickPath = [];
    state.clickTarget = null;
    state.actionWasDown = false;
    state.hitStopUntil = 0;
    state.lastHitStopAt = 0;
    // 上一次 restart 残留的 weather 句柄要在 init 阶段一次性销毁，避免重复 setTint
    // 在 jsdom 单测下出现 cameras.main null 报错。
    if (state.weather) {
      state.weather.destroy();
      state.weather = null;
    }
    // 上一次 restart 残留的 mood 句柄同样在 init 阶段销毁，保持与 weather 一致。
    if (state.mood) {
      state.mood.destroy();
      state.mood = null;
    }
    // 地图加载/切换耗时埋点：init → 首个 update（create 完成后第一帧）≈ 一次完整地图构建。
    // 可选链兜底 stub 场景（单测只传 cache/load，无 events），不影响既有断言。
    const perfLabel = `map_load:${state.mapKey}`;
    perfMonitor.start(perfLabel);
    const sceneEvents = (this as unknown as {
      events?: { once?: (event: string, cb: () => void) => void };
    }).events;
    sceneEvents?.once?.('update', () => {
      perfMonitor.end(perfLabel);
    });
  },
  preload(this: Phaser.Scene): void {
    if (!this.cache.json.exists('map_index')) {
      this.load.json('map_index', '/assets/maps/index.json');
    }
    const mapAssetKey = `map_${state.mapKey}`;
    if (!this.cache.tilemap.exists(mapAssetKey)) {
      this.load.tilemapTiledJSON(mapAssetKey, `/assets/maps/${state.mapKey}.json`);
    }
  },
  create(this: SceneLike): void {
    const phaserScene = this as unknown as Phaser.Scene;
    state.exits = [];
    state.npcs = [];
    state.collisions = [];
    state.lowObstacles = [];
    state.clickPath = [];
    state.clickTarget = null;

    const index = phaserScene.cache.json.exists('map_index')
      ? (phaserScene.cache.json.get('map_index') as MapIndex)
      : null;
    state.index = index;
    state.entry = index?.maps?.find((m) => m.key === state.mapKey) ?? null;

    const mapAssetKey = `map_${state.mapKey}`;
    if (!phaserScene.cache.tilemap.exists(mapAssetKey)) {
      drawFallbackMessage(phaserScene, `地图加载失败：${state.mapKey}`);
      return;
    }

    const map = phaserScene.make.tilemap({ key: mapAssetKey });
    const tilesetKey = state.entry?.tileset ?? 'kenney_tiny_town';
    const tileset = map.addTilesetImage(tilesetKey, tilesetKey, TILE_SIZE, TILE_SIZE, 0, 0);
    if (!tileset) {
      drawFallbackMessage(phaserScene, `Tileset 缺失：${tilesetKey}`);
      return;
    }

    state.width = map.width;
    state.height = map.height;

    const groundLayer = map.createLayer('ground', tileset, 0, 0);
    groundLayer?.setScale(RENDER_SCALE).setDepth(0);
    const buildingsLayer = map.createLayer('buildings', tileset, 0, 0);
    buildingsLayer?.setScale(RENDER_SCALE).setDepth(2);
    const decorationLayer = map.createLayer('decoration', tileset, 0, 0);
    decorationLayer?.setScale(RENDER_SCALE).setDepth(4);

    const { spawnTile, exits, buildings, npcs, lowObstacles, landmarks } = parseObjects(
      map,
      state.entry?.spawn,
      index,
    );
    state.exits = exits;
    state.npcs = npcs;
    state.lowObstacles = buildLowObstacleGrid(map.width, map.height, lowObstacles);
    state.collisions = mergeCollisionGrids(buildCollisions(map), state.lowObstacles);

    const worldStore = useWorldStore();
    const savedTile =
      worldStore.player && worldStore.player.mapKey === state.mapKey
        ? { tileX: worldStore.player.tileX, tileY: worldStore.player.tileY }
        : spawnTile;
    const recoveredBlockedSave = isBlocked(savedTile.tileX, savedTile.tileY);
    const persisted = recoveredBlockedSave ? spawnTile : savedTile;
    const playerDirection = cardinalize(worldStore.player?.direction ?? 'DOWN');

    const player = createPlayerSprite(this, {
      tileX: persisted.tileX,
      tileY: persisted.tileY,
      // 存档 DTO 的 direction 类型上仍允许 8 向（历史数据 / 其它客户端可能写入）；
      // 世界层只认 4 向，回读时先 cardinalize，避免把对角朝向塞进 sprite 后
      // 落到 LPC 表里找不到对应行。
      direction: playerDirection,
      tileSize: RENDER_TILE_SIZE,
    });
    state.player = player;

    if (worldStore.player) {
      worldStore.player = {
        ...worldStore.player,
        mapKey: state.mapKey,
        tileX: player.tileX,
        tileY: player.tileY,
      };
    }
    if (recoveredBlockedSave) {
      persistPlayerTile(player.tileX, player.tileY, playerDirection);
    }

    state.controller = createDirectionController(this);
    const canvas = phaserScene.game.canvas;
    if (canvas) {
      canvas.tabIndex = 0;
      canvas.focus({ preventScroll: true });
    }
    state.groundClickHandler = (pointer, currentlyOver = []) => {
      if (pointer.button !== 0) return;
      // Clicks on NPC/building hotspots keep their existing interaction behavior and cancel walking.
      if (currentlyOver.length > 0) {
        state.clickPath = [];
        state.clickTarget = null;
        return;
      }
      const target = {
        x: Math.floor(pointer.worldX / RENDER_TILE_SIZE),
        y: Math.floor(pointer.worldY / RENDER_TILE_SIZE),
      };
      const path = findTilePath({
        start: { x: player.tileX, y: player.tileY },
        target,
        width: state.width,
        height: state.height,
        isBlocked: (x, y) => isBlocked(x, y) || npcOverlapsTile(x, y),
      });
      state.clickPath = path ?? [];
      state.clickTarget = path?.length ? target : null;
      canvas?.focus({ preventScroll: true });
    };
    phaserScene.input.on('pointerdown', state.groundClickHandler);

    buildings.forEach((building) => {
      paintBuildingZone(phaserScene, building);
    });
    lowObstacles.forEach((obstacle) => {
      paintLowObstacleSprite(phaserScene, obstacle);
    });
    landmarks.forEach((landmark) => {
      paintLandmarkLabel(phaserScene, landmark);
    });
    state.npcSprites.clear();
    state.npcHotspots.clear();
    const npcWorkAction = toolActionForMap(state.mapKey);
    npcs.forEach((npc) => {
      const controller = drawNpcSprite(this, npc, RENDER_TILE_SIZE, { workAction: npcWorkAction });
      state.npcSprites.set(npc.npcKey, controller);
      state.npcHotspots.set(npc.npcKey, paintNpcHotspot(phaserScene, npc));
    });

    // NpcFramework 持有逻辑层 NPC（巡逻/朝向/对话状态），InteractableSystem 持有
    // trigger（distance + hint）；两者从相同的 npcs 列表派生，drawNpcSprite 维持视觉层。
    state.npcFramework = new NpcFramework(phaserScene);
    // wander 避障：把 tile 级碰撞判定（含越界）暴露给 NpcFramework（世界像素坐标 → tile）。
    state.npcFramework.setCollisionChecker((px, py) => {
      const tx = Math.floor(px / RENDER_TILE_SIZE);
      const ty = Math.floor(py / RENDER_TILE_SIZE);
      if (isBlocked(tx, ty)) {
        return true;
      }
      // 游走避让玩家：玩家所在格视为占用，NPC 不会走到玩家身上（与人物↔NPC AABB 碰撞互补）。
      const p = state.player;
      return p !== null && p.tileX === tx && p.tileY === ty;
    });
    state.npcTriggers.clear();
    state.hintRendererHandle = createPhaserHintRenderer(phaserScene);
    state.interactable = new InteractableSystem(phaserScene, {
      hintRenderer: state.hintRendererHandle.renderer,
    });
    npcs.forEach((npc) => {
      state.npcFramework?.register(buildNpcDefinitionFromSpawn(npc, RENDER_TILE_SIZE));
      const trigger = buildNpcTrigger(npc, RENDER_TILE_SIZE, () => {
        // dialog store openWithTree 自动 fetch /data/npcs/{key}/{meta,dialog}.json，
        // 失败时 store 内部填 loadError。
        Haptics.tap();
        openNpcDialog(npc.npcKey);
        state.npcFramework?.startDialog(npc.npcKey);
        state.npcFramework?.faceTowards(
          npc.npcKey,
          player.tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2,
          player.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2,
        );
        // 对话开始：NPC 朝向玩家并做一次点头/施法手势。
        state.npcSprites.get(npc.npcKey)?.playGesture('talk');
      });
      state.npcTriggers.set(npc.npcKey, trigger);
      state.interactable?.register(trigger);
    });

    // 异步加载 NPC 日程并更新位置（火后不管，不阻塞场景初始化）
    void applyNpcSchedules(npcs, state.npcFramework, state.mapKey, RENDER_TILE_SIZE);

    // 仅 town_hub 注册公告板 trigger（非 NPC trigger 示例）。
    if (state.mapKey === DEFAULT_MAP_KEY) {
      state.interactable.register(
        buildBulletinBoardTrigger({
          tileX: BULLETIN_BOARD_TILE_X,
          tileY: BULLETIN_BOARD_TILE_Y,
          tileSize: RENDER_TILE_SIZE,
          onActivate: () => {
            void router.push(BULLETIN_BOARD_ROUTE);
          },
        }),
      );
      // R-15 主线唯一入口：「冒险地图」告示牌 → /mainline-map（与 HUD 按钮 / 公告板·班级任务并列）。
      state.interactable.register(
        buildAdventureMapTrigger({
          tileX: ADVENTURE_MAP_TILE_X,
          tileY: ADVENTURE_MAP_TILE_Y,
          tileSize: RENDER_TILE_SIZE,
          onActivate: () => {
            void router.push(ADVENTURE_MAP_ROUTE);
          },
        }),
      );
    }

    // 建筑入口键盘链路：建筑体被碰撞层挡住，玩家站到门口一圈内按 E 即可进入，
    // 与鼠标点击整屋等效——键盘玩家不再依赖 NPC 对话 route 兜底
    // （/profile-room、/board 等恰好没有对话兜底）。注册顺序在 NPC trigger 之后，
    // 玩家同时贴近 NPC 和建筑时优先提示 NPC 对话。
    buildings.forEach((building) => {
      state.interactable?.register(
        buildBuildingTrigger(building, RENDER_TILE_SIZE, () => {
          void router.push(building.route);
        }),
      );
    });

    const worldWidth = state.width * RENDER_TILE_SIZE;
    const worldHeight = state.height * RENDER_TILE_SIZE;
    this.cameras.main.setBounds(0, 0, worldWidth, worldHeight);
    if (this.physics?.world?.setBounds) {
      this.physics.world.setBounds(0, 0, worldWidth, worldHeight);
    }
    this.cameras.main.startFollow(player, true, CAMERA_LERP, CAMERA_LERP);
    // 全屏适配：固定 zoom 在「地图比视口小」（宽屏 / 小图）时会露出背景色。改用 cover
    // 缩放——取「基准 zoom」与「恰好覆盖视口所需 zoom」的较大值（封顶 CAMERA_ZOOM_MAX），
    // 并随画布 resize 重算、scene shutdown 解绑，保证世界始终铺满视口。
    const applyCoverZoom = (): void => {
      const viewW = phaserScene.scale?.width ?? 0;
      const viewH = phaserScene.scale?.height ?? 0;
      const zoom =
        worldWidth > 0 && worldHeight > 0 && viewW > 0 && viewH > 0
          ? Math.min(
              CAMERA_ZOOM_MAX,
              Math.max(CAMERA_ZOOM, viewW / worldWidth, viewH / worldHeight),
            )
          : CAMERA_ZOOM;
      this.cameras.main.setZoom?.(zoom);
    };
    applyCoverZoom();
    if (typeof phaserScene.scale?.on === 'function') {
      // 解绑放在下面的 teardown 里（同时覆盖 shutdown 与 destroy），
      // 单挂 once('shutdown') 在整 Game 销毁路径上永远不会触发。
      phaserScene.scale.on('resize', applyCoverZoom);
    }
    phaserScene.cameras.main.fadeIn(FADE_DURATION_MS, 0, 0, 0);

    const bgm = pickBgm(state.entry?.bgm);
    playBgm(bgm);

    // 先应用 weather 粒子，再用 timeOfDay tint 覆盖颜色。两者顺序固定，
    // 不要互换，否则 setTint 会被 weather 反覆盖回去。
    const weatherKind = normalizeWeatherKind(worldStore.session?.weather);
    state.weatherKind = weatherKind;
    state.weather = applyWeather(this, weatherKind, worldWidth, worldHeight);
    const sessionForTime = worldStore.session;
    const timeOfDay = deriveTimeOfDay({
      inGameMinutes: sessionForTime ? sessionForTime.inGameMinutes : null,
    });
    applyTimeOfDay(this, timeOfDay);

    // 在 weather/time 之后叠加「氛围层」（背景级、不抢 tint）。初始从 mood store
    // 读当前 mood；之后 mood store refresh / 开关变更派发的 window CustomEvent 实时重应用
    // （先销毁旧句柄再重建）；当前服务端仅返回中性氛围兼容值。
    const applyMoodKind = (kind: MoodKind): void => {
      if (state.mood) {
        state.mood.destroy();
        state.mood = null;
      }
      state.mood = applyMood(this, kind, worldWidth, worldHeight);
    };
    try {
      applyMoodKind(useMoodStore().mood);
    } catch {
      // mood store 不可用（极端测试场景）时跳过初始氛围，不阻断场景构建。
    }
    const onMoodChanged = (event: Event): void => {
      const detail = (event as CustomEvent<{ mood?: MoodKind }>).detail;
      if (detail?.mood) {
        applyMoodKind(detail.mood);
      }
    };
    window.addEventListener('pythontopia:mood-changed', onMoodChanged);

    // R-15：单一引导——在 town_hub「冒险地图」告示牌上画一次持久「!」标记，指引玩家去
    // /mainline-map（主线 = 后端权威 mainline 域）。不再按旧 mainQuest activeQuest 动态重绘。
    paintAdventureMapMarker(phaserScene, state.mapKey, RENDER_TILE_SIZE);

    const onBeforeUnload = (): void => {
      void worldStore.flushPlayerUpdate();
    };
    window.addEventListener('beforeunload', onBeforeUnload);

    // attachLifecycle 兜底 tweens/time/input 清理，避免 HMR / restart 残留 ticker；
    // onShutdown 闭包负责 clear 业务 framework + 销毁 hint 渲染器。业务 state 字段
    // （weather/player/...）仍在 shutdown handler 内 reset，便于单步可读。
    state.lifecycleDetach = attachLifecycle(phaserScene, {
      onShutdown: () => {
        state.interactable?.clear();
        state.interactable = null;
        state.npcFramework?.clear();
        state.npcFramework = null;
        state.npcSprites.forEach((controller) => controller.destroy());
        state.npcSprites.clear();
        state.npcTriggers.clear();
        // 热点是场景自己 add 的 GameObject，显式 destroy 后清引用（与 npcSprites 一致）。
        state.npcHotspots.forEach((hotspot) => hotspot.destroy?.());
        state.npcHotspots.clear();
        state.hintRendererHandle?.destroy();
        state.hintRendererHandle = null;
        state.prevUpdateTimeMs = 0;
      },
    });

    // 同一个 teardown 同时挂 'shutdown' 与 'destroy'：Phaser 销毁整个 Game 时
    // （destroyPhaserGame → SceneManager.destroy → Systems.destroy）只 emit DESTROY
    // **不** emit SHUTDOWN，只挂 shutdown 会让下面这两个 window 监听器随每次
    // /world ↔ /code-theater 往返累积一份，永不回收。torndown 标志保证两个事件
    // 只跑一次，并在跑完把自己从两个事件上摘掉（restart 时不叠加多份监听）。
    const sceneEventsForTeardown = this.events;
    let torndown = false;
    const teardown = (): void => {
      if (torndown) {
        return;
      }
      torndown = true;
      sceneEventsForTeardown?.off?.('shutdown', teardown);
      sceneEventsForTeardown?.off?.('destroy', teardown);
      phaserScene.scale?.off?.('resize', applyCoverZoom);
      window.removeEventListener('beforeunload', onBeforeUnload);
      window.removeEventListener('pythontopia:mood-changed', onMoodChanged);
      if (state.groundClickHandler) {
        phaserScene.input.off('pointerdown', state.groundClickHandler);
        state.groundClickHandler = null;
      }
      // 显示对象随场景销毁，这里只清引用，避免 restart 时对旧对象重复 destroy。
      state.questMarkerObjects = [];
      if (state.weather) {
        state.weather.destroy();
        state.weather = null;
      }
      // 与 weather 同步销毁氛围层句柄。
      if (state.mood) {
        state.mood.destroy();
        state.mood = null;
      }
      state.player = null;
      state.controller = null;
      state.clickPath = [];
      state.clickTarget = null;
      state.collisions = [];
      state.lowObstacles = [];
      state.exits = [];
      state.npcs = [];
      state.lastMoveAt = 0;
      state.actionWasDown = false;
      state.jumpWasDown = false;
      state.toolWasDown = false;
      // attachLifecycle 自己监听 shutdown/destroy 已经触发 onShutdown，这里仅 detach
      // 多余引用，避免 restart 时挂多份相同监听。
      if (state.lifecycleDetach) {
        state.lifecycleDetach();
        state.lifecycleDetach = null;
      }
    };
    this.events?.on('shutdown', teardown);
    this.events?.on('destroy', teardown);
  },
  update(this: SceneLike): void {
    if (state.switching) {
      return;
    }
    const player = state.player;
    const controller = state.controller;
    if (!player || !controller) {
      return;
    }

    // 顿帧(hit-stop)：撞到 NPC 后短暂冻结整帧推进（玩家移动 + NPC 巡逻/贴图同步），
    // 强化「撞上去」的打击反馈。冻结期保持 prevUpdateTimeMs 跟随，解冻后 NPC dt 不跳变。
    if (this.time.now < state.hitStopUntil) {
      state.prevUpdateTimeMs = this.time.now;
      return;
    }

    let rawDirection = controller.getDirection();
    if (rawDirection) {
      state.clickPath = [];
      state.clickTarget = null;
    } else if (state.clickTarget) {
      while (state.clickPath[0]?.x === player.tileX && state.clickPath[0]?.y === player.tileY) {
        state.clickPath.shift();
      }
      if (player.tileX === state.clickTarget.x && player.tileY === state.clickTarget.y) {
        state.clickPath = [];
        state.clickTarget = null;
      } else {
        const next = state.clickPath[0];
        rawDirection = next ? directionToTile(next, player.tileX, player.tileY) : null;
      }
    }
    const jumpIsDown = controller.isJumpPressed();
    const jumpPressedEdge = jumpIsDown && !state.jumpWasDown;
    state.jumpWasDown = jumpIsDown;
    if (jumpPressedEdge && !player.isJumping()) {
      state.clickPath = [];
      state.clickTarget = null;
      const facing = toCardinal(player.direction, 'DOWN');
      const jump = resolveLowObstacleJump({
        tileX: player.tileX,
        tileY: player.tileY,
        direction: facing,
        width: state.width,
        height: state.height,
        collisions: state.collisions,
        lowObstacles: state.lowObstacles,
      });
      if (jump.kind === 'jump') {
        player.jumpToTile(jump.landing.tileX, jump.landing.tileY, jump.direction);
        playSfx('sfx_jump', { volume: 0.42 });
        scheduleLandingSfx(this, 0.36);
        persistPlayerTile(jump.landing.tileX, jump.landing.tileY, jump.direction);
        const exit = findExit(jump.landing.tileX, jump.landing.tileY);
        if (exit) {
          triggerSceneTransition(this as unknown as Phaser.Scene, exit);
        }
      } else {
        player.jumpToTile(player.tileX, player.tileY, facing);
        playSfx('sfx_jump', { volume: 0.18 });
        scheduleLandingSfx(this, 0.18);
      }
      return;
    }
    if (player.isJumping()) {
      return;
    }

    // C 键使用工具——按地图主题播放浇水/挖矿/钓鱼/施法/庆祝动画（定身播放）。
    const toolIsDown = controller.isToolPressed();
    const toolPressedEdge = toolIsDown && !state.toolWasDown;
    state.toolWasDown = toolIsDown;
    if (toolPressedEdge && !player.isBusy()) {
      player.playAction(toolActionForMap(state.mapKey));
      persistPlayerTile(player.tileX, player.tileY, toCardinal(player.direction, 'DOWN'));
    }

    const running = controller.isRunPressed();
    const baseCooldown = state.weatherKind === 'rainy' || state.weatherKind === 'stormy'
      ? MOVE_COOLDOWN_MS * RAIN_SLOWDOWN
      : MOVE_COOLDOWN_MS;
    // 奔跑缩短逐格冷却；动作播放（isBusy）期间暂停移动，让一次性动画播完。
    const effectiveCooldown = running ? baseCooldown * RUN_COOLDOWN_FACTOR : baseCooldown;
    if (rawDirection && !player.isBusy() && this.time.now - state.lastMoveAt > effectiveCooldown) {
      state.lastMoveAt = this.time.now;
      const { cardinal, dx, dy } = resolveMoveStep(rawDirection, player.direction);
      if (dx === 0 && dy === 0) {
        return;
      }
      // 星露谷式 turn-then-walk：当前 cardinal 不是玩家朝向时，第一次按只转身不前进，
      // 下一次再按才真正走一格，避免「按一下就强制蹭一步」的笨重感。
      if (shouldTurnBeforeMove(player.direction, rawDirection)) {
        player.faceDirection(cardinal);
        // 转身只占用较短的 turn 延迟，而非整段移动冷却：下一帧只要再过 TURN_DELAY_MS
        // 就能起步，避免「转完身要干等一个完整冷却才走」的卡顿。
        state.lastMoveAt = this.time.now - (effectiveCooldown - TURN_DELAY_MS);
        persistPlayerTile(player.tileX, player.tileY, cardinal);
        return;
      }
      const nextX = clamp(player.tileX + dx, 0, state.width - 1);
      const nextY = clamp(player.tileY + dy, 0, state.height - 1);
      const blockedByTile = isBlocked(nextX, nextY);
      // 人物↔NPC AABB 碰撞：地形可走但目标格被 NPC 包围盒占据时同样不可进入。
      const blockedByNpc = !blockedByTile && npcOverlapsTile(nextX, nextY);
      if (!blockedByTile && !blockedByNpc) {
        player.moveToTile(nextX, nextY, cardinal, { running, durationMs: effectiveCooldown });
        spawnFootstepFx(this, player);
        persistPlayerTile(nextX, nextY, cardinal);
        const exit = findExit(nextX, nextY);
        if (exit) {
          triggerSceneTransition(this as unknown as Phaser.Scene, exit);
        }
      } else {
        // 撞墙/撞 NPC 都只原地转向不前进；撞到 NPC 追加顿帧反馈。
        player.moveToTile(player.tileX, player.tileY, cardinal);
        if (blockedByNpc) {
          triggerHitStop(this);
        }
        if (state.clickTarget) {
          const path = findTilePath({
            start: { x: player.tileX, y: player.tileY },
            target: state.clickTarget,
            width: state.width,
            height: state.height,
            isBlocked: (x, y) => isBlocked(x, y) || npcOverlapsTile(x, y),
          });
          state.clickPath = path ?? [];
          if (path === null || path.length === 0) state.clickTarget = null;
        }
      }
    }

    // actionPressed 用边沿语义传入 InteractableSystem，由 trigger.onActivate 处理交互。
    // npcFramework.update 接 deltaMs 推进 patrol；town_hub 当前 NPC 无 patrol 数据，
    // update() 实际是 no-op。
    const actionIsDown = controller.isActionPressed();
    const actionPressedEdge = actionIsDown && !state.actionWasDown;
    state.actionWasDown = actionIsDown;

    const nowMs = this.time.now;
    if (state.prevUpdateTimeMs > 0) {
      const deltaMs = nowMs - state.prevUpdateTimeMs;
      // 浏览器切后台 / debugger 暂停可能让 deltaMs 几十秒。clamp 上限 200ms
      // 避免 NPC 一次跳超过半屏；clamp 下限 0 防 time.now 偶发抖动出现负数。
      const safeDelta = Math.max(0, Math.min(200, deltaMs));
      state.npcFramework?.update(safeDelta);
      // 用逻辑层实例位置驱动 NPC 贴图（巡逻走动 / 站桩待机 / 工作手势）。
      if (state.npcFramework) {
        const framework = state.npcFramework;
        for (const instance of framework.getAll()) {
          // 活的小镇：玩家走近时 NPC 转头看玩家（未在对话中才生效，避免与对话朝向打架）。
          if (!instance.inDialog) {
            const lookDx = player.x - instance.x;
            const lookDy = player.y - instance.y;
            if (lookDx * lookDx + lookDy * lookDy <= LOOK_AT_RANGE_PX * LOOK_AT_RANGE_PX) {
              framework.faceTowards(instance.id, player.x, player.y);
            }
          }
          state.npcSprites.get(instance.id)?.sync(instance, safeDelta);
          // wander 时把「按 E」trigger 圆心同步到 NPC 当前位置，交互区随之移动。
          const trig = state.npcTriggers.get(instance.id);
          if (trig && trig.shape.type === 'circle') {
            trig.shape.x = instance.x;
            trig.shape.y = instance.y;
          }
          // 鼠标点击热点同理跟随，否则 wander / 日程 teleport 后热点还钉在出生格。
          const hotspot = state.npcHotspots.get(instance.id);
          if (hotspot) {
            syncNpcHotspot(hotspot, instance.x, instance.y);
          }
        }
      }
    }
    state.prevUpdateTimeMs = nowMs;

    const playerCenterX = player.tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
    const playerCenterY = player.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
    state.interactable?.update(playerCenterX, playerCenterY, actionPressedEdge);
  },
};

/**
 * 写回后端玩家格位的唯一出口。
 *
 * 项目约束：后端 `PlayerStatePatch.direction` 只接受 4 向（DTO 类型上仍是 8 向，
 * 兼容历史 / 其它客户端数据），所以每一次写都必须先过 `toServerDirection` 降级。
 * 四条写路径（跳跃落地 / 工具动作 / 转身 / 走一格）统一走这个函数，新增写路径也
 * 不可能忘记降级 —— 这是「写后端降 4 向」约束在前端的落点。
 */
export function persistPlayerTile(tileX: number, tileY: number, direction: Direction): void {
  void useWorldStore().updatePlayerTile({
    tileX,
    tileY,
    direction: toServerDirection(direction),
  });
}

/**
 * 跳跃落地音：用 scene 自己的 time.delayedCall 而非 window.setTimeout。
 * 原生定时器不在 SceneLifecycle 的 `time.removeAllEvents()` 管辖内，落地前离场
 * 会在已销毁的场景上放音（Phaser sound 已随 Game 一起销毁 → 抛异常 / 幽灵音）。
 */
function scheduleLandingSfx(scene: SceneLike, volume: number): void {
  scene.time.delayedCall?.(LANDING_SFX_DELAY_MS, () => playSfx('sfx_land', { volume }));
}

function triggerSceneTransition(phaserScene: Phaser.Scene, exit: ExitTile): void {
  if (!SUPPORTED_MAP_KEYS.has(exit.toMap)) {
    return;
  }
  state.switching = true;
  phaserScene.cameras.main.fadeOut(FADE_DURATION_MS, 0, 0, 0);
  phaserScene.cameras.main.once('camerafadeoutcomplete', () => {
    const worldStore = useWorldStore();
    if (worldStore.player) {
      worldStore.player = {
        ...worldStore.player,
        mapKey: exit.toMap,
        tileX: exit.toTileX,
        tileY: exit.toTileY,
      };
    }
    phaserScene.scene.restart({ mapKey: exit.toMap });
  });
}

function findExit(tileX: number, tileY: number): ExitTile | null {
  return state.exits.find((exit) => exit.tileX === tileX && exit.tileY === tileY) ?? null;
}

function buildCollisions(map: Phaser.Tilemaps.Tilemap): boolean[][] {
  const grid = Array.from({ length: map.height }, () =>
    Array.from({ length: map.width }, () => false),
  );
  // 'collision' 是 maps/README 里约定的专用碰撞层名，但 scripts/generate-maps.mjs
  // 至今不产出该层（town_hub.json 只有 ground/buildings/decoration/objects），
  // 所以实际起作用的只有 'buildings'。保留 'collision' 作前向兼容：生成器补上后
  // 这里无需改动即可生效。
  const layerNames = ['collision', 'buildings'];
  layerNames.forEach((name) => {
    const layer = map.getLayer(name);
    const tilemapLayer = layer?.tilemapLayer;
    if (!tilemapLayer) {
      return;
    }
    for (let y = 0; y < map.height; y += 1) {
      for (let x = 0; x < map.width; x += 1) {
        const tile = tilemapLayer.getTileAt(x, y, false);
        if (tile && tile.index >= 0) {
          const row = grid[y];
          if (row) {
            row[x] = true;
          }
        }
      }
    }
  });
  return grid;
}

function buildLowObstacleGrid(width: number, height: number, zones: readonly LowObstacleZone[]): boolean[][] {
  const grid = Array.from({ length: height }, () => Array.from({ length: width }, () => false));
  zones.forEach((zone) => {
    for (let dy = 0; dy < zone.height; dy += 1) {
      for (let dx = 0; dx < zone.width; dx += 1) {
        const tileX = zone.tileX + dx;
        const tileY = zone.tileY + dy;
        const row = grid[tileY];
        if (row && tileX >= 0 && tileX < width) {
          row[tileX] = true;
        }
      }
    }
  });
  return grid;
}

function mergeCollisionGrids(collisions: boolean[][], lowObstacles: boolean[][]): boolean[][] {
  return collisions.map((row, y) =>
    row.map((blocked, x) => blocked || lowObstacles[y]?.[x] === true),
  );
}

export function parseObjects(
  map: Phaser.Tilemaps.Tilemap,
  fallbackSpawn: { tileX: number; tileY: number } | undefined,
  index: MapIndex | null,
): {
  spawnTile: { tileX: number; tileY: number };
  exits: ExitTile[];
  buildings: BuildingZone[];
  npcs: NpcSpawn[];
  lowObstacles: LowObstacleZone[];
  landmarks: LandmarkZone[];
} {
  let spawnTile: { tileX: number; tileY: number } = fallbackSpawn ?? { tileX: 5, tileY: 5 };
  const exits: ExitTile[] = [];
  const buildings: BuildingZone[] = [];
  const npcs: NpcSpawn[] = [];
  const lowObstacles: LowObstacleZone[] = [];
  const landmarks: LandmarkZone[] = [];

  map.objects.forEach((group) => {
    group.objects.forEach((obj) => {
      const type = String(obj.type ?? '');
      const tileX = Math.floor((obj.x ?? 0) / TILE_SIZE);
      const tileY = Math.floor((obj.y ?? 0) / TILE_SIZE);
      const props = collectProperties(obj.properties);
      if (type === 'spawn') {
        spawnTile = { tileX, tileY };
        return;
      }
      if (type === 'exit') {
        // generate-maps.mjs 写出 `target`/`side`（4 tile 宽矩形,无 toTile*）;同时兼容旧字段
        // `toMap`/`toTileX`/`toTileY`,优先读新字段。只有 target 时用目标地图 index.spawn 作落点 fallback。
        const target = String(props['target'] ?? props['toMap'] ?? '');
        if (!target) return;
        const targetEntry = index?.maps?.find((m) => m.key === target) ?? null;
        const toTileX = Number(
          props['toTileX'] ?? targetEntry?.spawn?.tileX ?? 5,
        );
        const toTileY = Number(
          props['toTileY'] ?? targetEntry?.spawn?.tileY ?? 5,
        );
        // 展开 4 tile 宽/高的 exit 区域,让玩家走到任意一格都触发跳转;
        // 否则只命中左上角,其余格直接穿过去毫无反应。
        const widthTiles = Math.max(
          1,
          Math.round((Number(obj.width ?? TILE_SIZE)) / TILE_SIZE),
        );
        const heightTiles = Math.max(
          1,
          Math.round((Number(obj.height ?? TILE_SIZE)) / TILE_SIZE),
        );
        for (let dx = 0; dx < widthTiles; dx += 1) {
          for (let dy = 0; dy < heightTiles; dy += 1) {
            exits.push({
              tileX: tileX + dx,
              tileY: tileY + dy,
              toMap: target,
              toTileX,
              toTileY,
            });
          }
        }
        return;
      }
      if (type === 'building') {
        buildings.push({
          route: String(props['route'] ?? '/'),
          label: String(props['label'] ?? obj.name ?? ''),
          tileX,
          tileY,
          width: Math.max(1, Math.floor((obj.width ?? TILE_SIZE) / TILE_SIZE)),
          height: Math.max(1, Math.floor((obj.height ?? TILE_SIZE) / TILE_SIZE)),
        });
        return;
      }
      if (type === 'low_obstacle') {
        const frameValue = props['frame'];
        lowObstacles.push({
          tileX,
          tileY,
          width: Math.max(1, Math.floor((obj.width ?? TILE_SIZE) / TILE_SIZE)),
          height: Math.max(1, Math.floor((obj.height ?? TILE_SIZE) / TILE_SIZE)),
          sprite: typeof props['sprite'] === 'string' ? props['sprite'] : null,
          frame: typeof frameValue === 'number' ? frameValue : null,
        });
        return;
      }
      if (type === 'npc') {
        const spriteValue = props['sprite'];
        // town_hub.json 用 "key" 字段（npc_mayor_py.properties[name=key]
        // value=mayor_py），保留向后兼容 "npcKey"。obj.name fallback 仅用于
        // 兜底，正式 NPC 必须显式声明 key 才能匹配 /data/npcs/<key>/。
        npcs.push({
          npcKey: String(props['key'] ?? props['npcKey'] ?? obj.name ?? 'npc'),
          label: String(props['label'] ?? obj.name ?? ''),
          tileX,
          tileY,
          sprite: typeof spriteValue === 'string' ? spriteValue : 'player_sara',
        });
        return;
      }
      if (type === 'landmark') {
        landmarks.push({
          label: String(props['label'] ?? obj.name ?? ''),
          tileX,
          tileY,
          width: Math.max(1, Math.floor((obj.width ?? TILE_SIZE) / TILE_SIZE)),
          height: Math.max(1, Math.floor((obj.height ?? TILE_SIZE) / TILE_SIZE)),
        });
        return;
      }
      // 兜底分支：地图 JSON 写了、代码没实现的对象类型此前被静默丢弃
      //（landmark 喷泉就这么消失了半年）。这里打一次 warn 让缺口可见。
      warnUnknownObjectType(type, obj.name);
    });
  });

  return { spawnTile, exits, buildings, npcs, lowObstacles, landmarks };
}

// 每种未知类型只 warn 一次：parseObjects 每次 create 都跑一遍，不去重会刷屏。
const warnedObjectTypes = new Set<string>();

function warnUnknownObjectType(type: string, name: unknown): void {
  const key = type || '(empty)';
  if (warnedObjectTypes.has(key)) {
    return;
  }
  warnedObjectTypes.add(key);
  if (typeof console !== 'undefined' && typeof console.warn === 'function') {
    console.warn(
      `[WorldScene] parseObjects: 未处理的地图对象 type="${key}"（如 "${String(name ?? '')}"），已忽略。`
      + ' 要么在 parseObjects 里补一个分支，要么把该类型从地图生成器中删掉。',
    );
  }
}

/** 仅供测试：清空「未知对象类型」的 warn 去重集合。 */
export function __resetUnknownObjectTypeWarnings(): void {
  warnedObjectTypes.clear();
}

function collectProperties(props: unknown): Record<string, unknown> {
  if (!Array.isArray(props)) {
    return {};
  }
  const entries = (props as Array<{ name?: unknown; value?: unknown }>).filter(
    (p): p is { name: string; value: unknown } => typeof p.name === 'string',
  );
  return Object.fromEntries(entries.map((p) => [p.name, p.value]));
}

// 用 LPC 茅草屋整屋 sprite 替换方块拼接建筑。屋子底沿对齐建筑区位底部、
// 屋顶向上延伸（星露谷式），整屋遮住底下旧的瓦片建筑；点击进入对应房间。
const COTTAGE_VARIANTS: ReadonlyArray<string> = ['cottage_tan', 'cottage_cream', 'cottage_stone'];
const COTTAGE_FALLBACK_W = 200;

function cottageVariantFor(route: string): string {
  let hash = 0;
  for (let i = 0; i < route.length; i += 1) hash = (hash + route.charCodeAt(i)) % COTTAGE_VARIANTS.length;
  return COTTAGE_VARIANTS[hash] ?? COTTAGE_VARIANTS[0] ?? 'cottage_tan';
}

function paintBuildingZone(phaserScene: Phaser.Scene, building: BuildingZone): void {
  const px = (building.tileX + building.width / 2) * RENDER_TILE_SIZE;
  const footprintW = building.width * RENDER_TILE_SIZE;
  const bottomY = (building.tileY + building.height) * RENDER_TILE_SIZE;
  // 屋子略宽于地基以盖住旧瓦片边缘；底沿坐在地基底部，屋顶往上探出。
  const targetW = footprintW * 1.2;

  const textures = (phaserScene as unknown as { textures?: { exists?: (k: string) => boolean } }).textures;
  const variant = cottageVariantFor(building.route);
  const useCottage = textures?.exists?.(variant) === true;

  let interactive: Phaser.GameObjects.GameObject;
  if (useCottage) {
    const cottage = phaserScene.add.image(px, bottomY, variant).setOrigin(0.5, 1).setDepth(6);
    // 按整屋实际像素宽缩放（不同变体宽度不一），底沿对齐地基底部。
    const nativeW = cottage.width || COTTAGE_FALLBACK_W;
    cottage.setScale(targetW / nativeW).setInteractive({ useHandCursor: true });
    interactive = cottage;
  } else {
    // 兜底（纹理缺失，如测试桩）：保留透明可点击矩形。
    const height = building.height * RENDER_TILE_SIZE;
    interactive = phaserScene.add
      .rectangle(px, building.tileY * RENDER_TILE_SIZE + height / 2, footprintW, height, 0x000000, 0)
      .setInteractive({ useHandCursor: true })
      .setDepth(6);
  }
  interactive.on('pointerdown', () => {
    void router.push(building.route);
  });

  const label = phaserScene.add
    .text(px, bottomY - building.height * RENDER_TILE_SIZE - 8, building.label, {
      fontFamily: 'monospace',
      fontSize: '12px',
      color: '#fff8e1',
      backgroundColor: '#1c1810dd',
      padding: { x: 6, y: 3 },
    })
    .setOrigin(0.5, 1)
    .setDepth(8)
    .setVisible(false);
  interactive.on('pointerover', () => label.setVisible(true));
  interactive.on('pointerout', () => label.setVisible(false));
}

/**
 * 画 NPC 的可点击热点并返回句柄。
 *
 * 名牌已由 NpcSpriteController 常驻显示在头顶上方；此处只保留覆盖整个 NPC 立绘
 * 高度的可点击热点（点一下直接开对话），不再重复画名牌。热点比单格高，覆盖
 * ULPC 立绘（约 2.5 tile 高）。
 *
 * 返回句柄由 update() 每帧同步到 NPC 实时位置 —— NPC 默认 wander 2 tile、按日程
 * 还会异步 teleport，热点钉死在出生格的话，点 NPC 点不着、点空地反而开对话。
 */
function paintNpcHotspot(phaserScene: Phaser.Scene, npc: NpcSpawn): NpcHotspotHandle {
  const cx = npc.tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const cy = npc.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const marker = phaserScene.add
    .rectangle(cx, cy - RENDER_TILE_SIZE, RENDER_TILE_SIZE * 1.2, RENDER_TILE_SIZE * 3, 0xffffff, 0)
    .setInteractive({ useHandCursor: true })
    .setDepth(12);
  marker.on('pointerdown', () => openNpcDialog(npc.npcKey));
  return marker as unknown as NpcHotspotHandle;
}

/** 把热点同步到 NPC 当前像素中心（热点中心比 NPC 中心高一格，与创建时一致）。 */
function syncNpcHotspot(handle: NpcHotspotHandle, npcX: number, npcY: number): void {
  handle.setPosition?.(npcX, npcY - RENDER_TILE_SIZE);
}

/**
 * 地标名牌：贴在地标区位正上方的一行小字（如「镜月喷泉」）。
 * 地标本体的像素画面来自 decoration 瓦片层，这里只补名字，不加碰撞、不加交互。
 */
function paintLandmarkLabel(phaserScene: Phaser.Scene, landmark: LandmarkZone): void {
  if (!landmark.label) {
    return;
  }
  try {
    const cx = (landmark.tileX + landmark.width / 2) * RENDER_TILE_SIZE;
    const topY = landmark.tileY * RENDER_TILE_SIZE - 4;
    phaserScene.add
      .text(cx, topY, landmark.label, {
        fontFamily: 'monospace',
        fontSize: '10px',
        color: '#fff8e1',
        backgroundColor: '#2b3a2199',
        padding: { x: 4, y: 2 },
      })
      .setOrigin(0.5, 1)
      .setDepth(8);
  } catch {
    // jsdom / 无渲染环境（测试桩）忽略。
  }
}

function paintLowObstacleSprite(phaserScene: Phaser.Scene, obstacle: LowObstacleZone): void {
  if (!obstacle.sprite || obstacle.frame === null) {
    return;
  }
  for (let dy = 0; dy < obstacle.height; dy += 1) {
    for (let dx = 0; dx < obstacle.width; dx += 1) {
      phaserScene.add
        .sprite(
          (obstacle.tileX + dx) * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2,
          (obstacle.tileY + dy) * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2,
          obstacle.sprite,
          obstacle.frame,
        )
        .setScale(RENDER_SCALE)
        .setDepth(5);
    }
  }
}

/** 销毁当前主线标记（连带 tween），供重绘 / 任务完成清除复用。 */
function clearQuestMarkers(phaserScene: Phaser.Scene): void {
  state.questMarkerObjects.forEach((obj) => {
    try {
      phaserScene.tweens?.killTweensOf?.(obj);
      obj.destroy();
    } catch {
      // 场景切换竞态下对象可能已被 Phaser 回收，忽略。
    }
  });
  state.questMarkerObjects = [];
}

function paintAdventureMapMarker(
  phaserScene: Phaser.Scene,
  mapKey: string,
  tileSize: number,
): void {
  try {
    // 先清旧标记再画：场景 restart 不叠加多份。
    clearQuestMarkers(phaserScene);
    if (mapKey !== DEFAULT_MAP_KEY) return;
    const cx = ADVENTURE_MAP_TILE_X * tileSize + tileSize / 2;
    const topY = ADVENTURE_MAP_TILE_Y * tileSize - 4;
    // 持久「!」+「冒险地图」标签：单一引导玩家走到告示牌按 E 进 /mainline-map（主线后端权威）。
    const excl = phaserScene.add
      .text(cx, topY, '!', {
        fontFamily: 'monospace',
        fontSize: '16px',
        color: '#ffd166',
        fontStyle: 'bold',
      })
      .setOrigin(0.5, 1)
      .setDepth(15);
    phaserScene.tweens.add({
      targets: excl,
      y: topY - 6,
      duration: 800,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.easeInOut',
    });
    const label = phaserScene.add
      .text(cx, ADVENTURE_MAP_TILE_Y * tileSize + tileSize * 0.6, '冒险地图', {
        fontFamily: 'monospace',
        fontSize: '10px',
        color: '#fff8e1',
        backgroundColor: '#4d3613',
        padding: { x: 4, y: 2 },
      })
      .setOrigin(0.5, 0)
      .setDepth(15);
    state.questMarkerObjects.push(excl, label);
  } catch {
    // jsdom / 测试环境无 phaser add，静默跳过。
  }
}

function isBlocked(tileX: number, tileY: number): boolean {
  if (tileX < 0 || tileY < 0 || tileX >= state.width || tileY >= state.height) {
    return true;
  }
  const row = state.collisions[tileY];
  return Boolean(row?.[tileX]);
}

function directionToTile(target: WorldTile, fromX: number, fromY: number): ExtendedDirection | null {
  const dx = target.x - fromX;
  const dy = target.y - fromY;
  if (dx === 0 && dy === -1) return 'UP';
  if (dx === 0 && dy === 1) return 'DOWN';
  if (dx === -1 && dy === 0) return 'LEFT';
  if (dx === 1 && dy === 0) return 'RIGHT';
  return null;
}

/**
 * 人物↔NPC 的 AABB 碰撞：候选目标格中心与各 NPC 实时像素中心做轴对齐包围盒重叠判定。
 *
 * 两个包围盒（玩家 / NPC，均以中心为原点的正方形）在 x、y 轴投影都重叠才算碰撞：
 * 即 |dx| < (boxP+boxN)/2 且 |dy| < (boxP+boxN)/2。相邻格中心相距一个 tile
 * (16) 大于阈值(12.8)，故玩家可贴边站立；同格或半格内的 NPC（含游走中的 NPC）则阻挡。
 */
function npcOverlapsTile(tileX: number, tileY: number): boolean {
  const framework = state.npcFramework;
  if (!framework) {
    return false;
  }
  const centerX = tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const centerY = tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const overlapHalf = (PLAYER_COLLIDE_BOX + NPC_COLLIDE_BOX) / 2;
  for (const npc of framework.getAll()) {
    if (Math.abs(centerX - npc.x) < overlapHalf && Math.abs(centerY - npc.y) < overlapHalf) {
      return true;
    }
  }
  return false;
}

/**
 * 触发顿帧(hit-stop)：设置短暂冻结窗口 + 极轻撞击音，强化「撞上 NPC」的反馈，不做镜头抖动。
 * 以不应期(REFRACTORY)节流：玩家持续顶着 NPC 时只偶发再触发，不会连续顿帧。
 */
function triggerHitStop(scene: SceneLike): void {
  const now = scene.time.now;
  if (now - state.lastHitStopAt < HITSTOP_REFRACTORY_MS) {
    return;
  }
  state.lastHitStopAt = now;
  state.hitStopUntil = now + HITSTOP_MS;
  Haptics.bump();
  playSfx('sfx_land', { volume: 0.2 });
}

/** 是否启用减弱动效（无障碍）：true 时跳过尘土等纯装饰动效。 */
function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

let footstepParity = 0;

/**
 * 玩家走一格的脚步反馈：每步落一团快速淡出的尘土（减弱动效时跳过），隔步播一声极轻脚步音
 * 并随机化音高（避免机械重复）。尘土深度落在玩家贴图之下，贴地不挡身体。
 */
function spawnFootstepFx(scene: SceneLike, player: PlayerSpriteHandle): void {
  footstepParity = (footstepParity + 1) % 2;
  if (footstepParity === 0) {
    const detune = Math.round((Math.random() * 2 - 1) * FOOTSTEP_DETUNE_RANGE);
    playSfx('sfx_land', { volume: FOOTSTEP_VOLUME, detune });
  }
  if (prefersReducedMotion()) {
    return;
  }
  try {
    const phaserScene = scene as unknown as Phaser.Scene;
    const dust = phaserScene.add.ellipse(
      player.x,
      player.y + RENDER_TILE_SIZE * 0.42,
      RENDER_TILE_SIZE * 0.5,
      RENDER_TILE_SIZE * 0.26,
      0xcdbb94,
      0.5,
    );
    dust.setDepth(DUST_DEPTH);
    phaserScene.tweens.add({
      targets: dust,
      alpha: 0,
      scaleX: 1.9,
      scaleY: 1.9,
      duration: 280,
      ease: 'Quad.easeOut',
      onComplete: () => dust.destroy(),
    });
  } catch {
    // 测试 / 无渲染环境（jsdom）忽略。
  }
}

export function directionDelta(dir: string): { dx: number; dy: number } {
  if (!dir) {
    return { dx: 0, dy: 0 };
  }
  const tokens = dir.split('_');
  let dx = 0;
  let dy = 0;
  tokens.forEach((token) => {
    if (token === 'LEFT') dx -= 1;
    else if (token === 'RIGHT') dx += 1;
    else if (token === 'UP') dy -= 1;
    else if (token === 'DOWN') dy += 1;
  });
  return { dx, dy };
}

/**
 * 把任意方向串收敛到 4 向。
 *
 * 返回类型与 fallback 参数都收窄成 CardinalDirection：旧签名 `(dir, fallback: Direction)
 * => Direction` 的末条 `return fallback` 会把 8 向对角值原样吐回，编译器完全兜不住
 * 「世界层只跑 4 向」的约束。收窄后，任何试图把对角值当朝向传下去的调用点都会编译失败。
 */
export function toCardinal(dir: string, fallback: CardinalDirection): CardinalDirection {
  if (dir === 'UP' || dir === 'DOWN' || dir === 'LEFT' || dir === 'RIGHT') {
    return dir;
  }
  const tokens = dir.split('_');
  // 方向惯性（docs/stardew/03-character-walk-anim-spec §3.2 防颤动）：斜向输入包含
  // 当前朝向轴时延续当前朝向。W+A 松紧交替（UP ↔ UP_LEFT）不再翻转朝向，
  // 也不再触发多余的 turn-then-walk 停顿。
  if (tokens.includes(fallback)) return fallback;
  if (tokens.includes('LEFT')) return 'LEFT';
  if (tokens.includes('RIGHT')) return 'RIGHT';
  if (tokens.includes('UP')) return 'UP';
  if (tokens.includes('DOWN')) return 'DOWN';
  return fallback;
}

/**
 * 由「本帧输入方向 + 当前朝向」解出这一步的朝向与格位增量。
 *
 * 关键约定：位移取 cardinal（4 向）的 delta，而不是 rawDirection（8 向）的。原因有二：
 *   1. 速度一致 —— 对角 delta 是 (±1,±1)，同一个 MOVE_COOLDOWN_MS 内走的却是 √2 个
 *      tile 的距离，斜走凭空快 1.41×（直线 123 px/s → 对角 174 px/s，叠 Shift 达 280）；
 *   2. 不穿墙角 —— 对角移动只判目标格，两个正交邻格都是墙时照样能斜穿过去
 *      （town_hub 主街栅栏 (26,31)→(25,32)、工坊墙角 (39,27)→(40,28) 均可复现）。
 * cardinal 本来就是驱动贴图朝向与存档方向的那个值，位移跟它走，三者不再分叉。
 * 8 向输入仍保留其唯一有用的语义：toCardinal 的方向惯性（W+A 松紧交替不翻朝向）。
 *
 * 后置条件：|dx| + |dy| ≤ 1，永远不会出现对角步。
 */
export function resolveMoveStep(
  rawDirection: string,
  facing: Direction,
): { cardinal: CardinalDirection; dx: number; dy: number } {
  const cardinal = toCardinal(rawDirection, cardinalize(facing));
  const { dx, dy } = directionDelta(cardinal);
  return { cardinal, dx, dy };
}

export function shouldTurnBeforeMove(currentDirection: Direction, requestedDirection: string): boolean {
  const facing = cardinalize(currentDirection);
  // currentDirection 若是（历史存档带进来的）对角值，cardinalize 后必然 !== 原值，
  // 于是先转身把朝向掰正，再走 —— 正是想要的兜底。
  return currentDirection !== toCardinal(requestedDirection, facing);
}

function clamp(value: number, lower: number, upper: number): number {
  return Math.max(lower, Math.min(upper, value));
}

function pickBgm(candidate: string | undefined): BgmKey {
  if (candidate && VALID_BGM_KEYS.has(candidate)) {
    return candidate as BgmKey;
  }
  return DEFAULT_BGM;
}

function drawFallbackMessage(phaserScene: Phaser.Scene, text: string): void {
  phaserScene.add
    .text(phaserScene.scale.width / 2, phaserScene.scale.height / 2, text, {
      color: '#fff8e1',
      fontFamily: 'monospace',
      fontSize: '18px',
      backgroundColor: '#3b2411',
      padding: { x: 12, y: 8 },
    })
    .setOrigin(0.5)
    .setDepth(100);
}

// =====================================================================
// 辅助导出：把 npc 列表转 NpcDefinition / ITrigger，便于在 jsdom 单测里
// mock 调用而无需启动整个 Phaser scene。
// =====================================================================

/** Trigger id 前缀，业务侧调试时容易区分 npc trigger vs 其他类型。 */
export const NPC_TRIGGER_ID_PREFIX = 'npc:';
export const BUILDING_TRIGGER_ID_PREFIX = 'building:';

/** 公告板 trigger 固定 id，导出便于单测断言。 */
export const BULLETIN_BOARD_TRIGGER_ID = 'trigger:bulletin_board';

/** 「冒险地图」告示牌 trigger 固定 id，导出便于单测断言。 */
export const ADVENTURE_MAP_TRIGGER_ID = 'trigger:adventure_map';

/**
 * 玩家走到 NPC 旁边时显示的 hint 文本，单独抽出便于本地化与单测。
 */
export function buildNpcHintText(npcLabel: string): string {
  // 故意保留中文以贴合首批玩家（高校 Python 初学者）。
  return `按 E 与 ${npcLabel} 对话`;
}

/**
 * 由 tilemap parseObjects 返回的 NpcSpawn 推出 NpcFramework 注册用 NpcDefinition。
 *
 * schedule 支持：
 *   - 如果 NPC 有 schedule 数据 + 当前有日历信息，
 *     使用 resolveSchedulePosition 计算当前应在的位置；
 *   - 同一地图内多个时段位置自动生成巡逻 waypoints。
 */
export function buildNpcDefinitionFromSpawn(
  npc: NpcSpawn,
  tileSize: number,
  scheduleCtx?: { schedule?: NpcSchedule; weekday: string; timeOfDay: string; mapKey: string },
): NpcDefinition {
  let spawnX = npc.tileX * tileSize + tileSize / 2;
  let spawnY = npc.tileY * tileSize + tileSize / 2;
  let patrol: NpcWaypoint[] | undefined;

  if (scheduleCtx?.schedule) {
    const resolved = resolveSchedulePosition(
      scheduleCtx.schedule,
      scheduleCtx.weekday,
      scheduleCtx.timeOfDay,
    );
    if (resolved && resolved.map === scheduleCtx.mapKey) {
      spawnX = resolved.tileX * tileSize + tileSize / 2;
      spawnY = resolved.tileY * tileSize + tileSize / 2;
    }

    const waypoints = getScheduleWaypoints(
      scheduleCtx.schedule,
      scheduleCtx.weekday,
      scheduleCtx.mapKey,
    );
    if (waypoints.length >= 2) {
      patrol = waypoints.map((wp) => ({
        x: wp.tileX * tileSize + tileSize / 2,
        y: wp.tileY * tileSize + tileSize / 2,
        dwellMs: 8000,
      }));
    }
  }

  return {
    id: npc.npcKey,
    displayName: npc.label || npc.npcKey,
    portraitKey: defaultPortraitKey(npc.npcKey),
    spawnX,
    spawnY,
    patrol,
    // 无 patrol（无日程巡逻）的 NPC 自由游走，半径 2 tile，避免「呆在原地」（R-14 需求8b）；
    // wander 时 WorldScene 每帧同步对话 trigger 圆心，保证「按 E」交互不脱节。
    wander: patrol ? undefined : { radiusPx: tileSize * 2 },
  };
}

/**
 * 由 NpcSpawn 构造 InteractableSystem 用的 trigger。
 *
 * - id 用 `npc:<npcKey>` 前缀避免和未来的 trigger object layer 撞 id；
 * - 形状采用 circle，半径 = TRIGGER_RADIUS_TILES × tileSize，玩家走到相邻一圈
 *   即进入命中；
 * - hint 走 buildNpcHintText(label) 拼接，UI 复用 hintRenderer。
 */
export function buildNpcTrigger(
  npc: NpcSpawn,
  tileSize: number,
  onActivate: () => void,
): ITrigger {
  const cx = npc.tileX * tileSize + tileSize / 2;
  const cy = npc.tileY * tileSize + tileSize / 2;
  return {
    id: `${NPC_TRIGGER_ID_PREFIX}${npc.npcKey}`,
    shape: {
      type: 'circle',
      x: cx,
      y: cy,
      radius: tileSize * TRIGGER_RADIUS_TILES,
    },
    hint: buildNpcHintText(npc.label || npc.npcKey),
    onActivate,
  };
}

/**
 * 由 BuildingZone 构造建筑入口 trigger（E 键进入，与鼠标点击等效）。
 *
 * - id 用 `building:<route>` 前缀，route 在同图内唯一；
 * - 形状取建筑地基矩形向四周外扩 1 tile：建筑本体在碰撞层内不可站立，
 *   外扩一圈即「门口」，玩家贴墙任意一侧都能按 E 进入。
 */
export function buildBuildingTrigger(
  building: { route: string; label: string; tileX: number; tileY: number; width: number; height: number },
  tileSize: number,
  onActivate: () => void,
): ITrigger {
  return {
    id: `${BUILDING_TRIGGER_ID_PREFIX}${building.route}`,
    shape: {
      type: 'rect',
      x: (building.tileX - 1) * tileSize,
      y: (building.tileY - 1) * tileSize,
      width: (building.width + 2) * tileSize,
      height: (building.height + 2) * tileSize,
    },
    hint: `按 E 进入${building.label || '建筑'}`,
    onActivate,
  };
}

/**
 * 构造公告板 trigger（当前仅 town_hub 注册）。
 */
export function buildBulletinBoardTrigger(opts: {
  tileX: number;
  tileY: number;
  tileSize: number;
  onActivate: () => void;
}): ITrigger {
  const cx = opts.tileX * opts.tileSize + opts.tileSize / 2;
  const cy = opts.tileY * opts.tileSize + opts.tileSize / 2;
  return {
    id: BULLETIN_BOARD_TRIGGER_ID,
    shape: {
      type: 'circle',
      x: cx,
      y: cy,
      radius: opts.tileSize * TRIGGER_RADIUS_TILES,
    },
    hint: '按 E 查看公告板',
    onActivate: opts.onActivate,
  };
}

/**
 * 构造「冒险地图」告示牌 trigger（当前仅 town_hub 注册）→ /mainline-map。
 * R-15 主线退役旧 mainQuest 后，世界内对主线的唯一引导入口。
 */
export function buildAdventureMapTrigger(opts: {
  tileX: number;
  tileY: number;
  tileSize: number;
  onActivate: () => void;
}): ITrigger {
  const cx = opts.tileX * opts.tileSize + opts.tileSize / 2;
  const cy = opts.tileY * opts.tileSize + opts.tileSize / 2;
  return {
    id: ADVENTURE_MAP_TRIGGER_ID,
    shape: {
      type: 'circle',
      x: cx,
      y: cy,
      radius: opts.tileSize * TRIGGER_RADIUS_TILES,
    },
    hint: '按 E 打开冒险地图',
    onActivate: opts.onActivate,
  };
}

/**
 * 创建一个用 Phaser text + bg rectangle 实现的 hint 气泡渲染器。
 *
 * 设计取舍：
 * - 用 scrollFactor=0 让气泡常驻镜头底部居中，避免被相机跟随玩家时遮挡操作；
 * - depth=200 高于 weather / time-of-day tint（depth≈100），保证可见；
 * - show/hide 用 visible 属性切换，不在每次 show 时重建 GameObject 防 GC 抖动；
 * - destroy 在 scene shutdown 时调用，回收 GameObject 引用，避免 HMR 后留多份。
 */
function createPhaserHintRenderer(
  phaserScene: Phaser.Scene,
): PhaserHintRendererHandle {
  const cameraWidth = phaserScene.cameras?.main?.width ?? 960;
  const cameraHeight = phaserScene.cameras?.main?.height ?? 640;
  const hintText = phaserScene.add
    .text(cameraWidth / 2, cameraHeight - 36, '', {
      fontFamily: 'monospace',
      fontSize: '14px',
      color: '#fff8e1',
      backgroundColor: '#4d3613',
      padding: { x: 10, y: 4 },
    })
    .setOrigin(0.5, 0.5)
    .setDepth(200);

  // scrollFactor=0 -> 跟相机一起平移（即不随世界滚动），保持屏幕底部位置。
  const hintTextWithScroll = hintText as unknown as {
    setScrollFactor?: (x: number, y?: number) => unknown;
    setVisible?: (v: boolean) => unknown;
    setPosition?: (x: number, y: number) => unknown;
    destroy?: () => void;
    visible?: boolean;
  };
  hintTextWithScroll.setScrollFactor?.(0, 0);
  hintTextWithScroll.setVisible?.(false);

  const onResize = (gameSize: { width: number; height: number }): void => {
    hintTextWithScroll.setPosition?.(gameSize.width / 2, gameSize.height - 36);
  };
  if (typeof phaserScene.scale?.on === 'function') {
    phaserScene.scale.on('resize', onResize);
  }

  let destroyed = false;
  return {
    renderer: {
      show(text: string): void {
        if (destroyed) {
          return;
        }
        hintText.setText(text);
        if (typeof hintTextWithScroll.setVisible === 'function') {
          hintTextWithScroll.setVisible(true);
        } else {
          hintTextWithScroll.visible = true;
        }
      },
      hide(): void {
        if (destroyed) {
          return;
        }
        if (typeof hintTextWithScroll.setVisible === 'function') {
          hintTextWithScroll.setVisible(false);
        } else {
          hintTextWithScroll.visible = false;
        }
      },
    },
    destroy(): void {
      if (destroyed) {
        return;
      }
      destroyed = true;
      if (typeof phaserScene.scale?.off === 'function') {
        phaserScene.scale.off('resize', onResize);
      }
      hintTextWithScroll.destroy?.();
    },
  };
}

/**
 * 异步加载 NPC meta.json 获取 schedule，再通过 NpcFramework.teleport
 * 把 NPC 移动到当前日程位置。
 */
async function applyNpcSchedules(
  npcs: readonly NpcSpawn[],
  framework: NpcFramework | null,
  mapKey: string,
  tileSize: number,
): Promise<void> {
  if (!framework || npcs.length === 0) return;

  const calendarStore = await import('@stores/calendar').then((m) => m.useCalendarStore());
  const today = calendarStore.today;
  const weekday = today?.weekday ?? new Date().toLocaleDateString('en-US', { weekday: 'long' }).toLowerCase();
  const timeOfDay = today?.timeOfDay ?? '10:00';

  const fetches = npcs.map(async (npc) => {
    try {
      const res = await fetch(`/data/npcs/${npc.npcKey}/meta.json`, { headers: { Accept: 'application/json' } });
      if (!res.ok) return;
      const meta = await res.json() as { schedule?: NpcSchedule };
      if (!meta.schedule) return;

      const position = resolveSchedulePosition(meta.schedule, weekday, timeOfDay);
      if (!position || position.map !== mapKey) return;

      const waypoints = getScheduleWaypoints(meta.schedule, weekday, mapKey);
      const patrol = waypoints.length >= 2
        ? waypoints.map((wp) => ({
            x: wp.tileX * tileSize + tileSize / 2,
            y: wp.tileY * tileSize + tileSize / 2,
            dwellMs: 8000,
          }))
        : undefined;

      framework.teleport(
        npc.npcKey,
        position.tileX * tileSize + tileSize / 2,
        position.tileY * tileSize + tileSize / 2,
        patrol,
      );
    } catch {
      // NPC 没有 meta.json 或加载失败，保持默认位置
    }
  });

  const BATCH_SIZE = 5;
  for (let i = 0; i < fetches.length; i += BATCH_SIZE) {
    await Promise.allSettled(fetches.slice(i, i + BATCH_SIZE));
  }
}
