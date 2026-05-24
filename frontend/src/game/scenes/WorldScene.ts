// M2 重构原因：将 TownScene + FieldScene + BugMineScene 等多个独立 Phaser 实例
// 合并为单一 WorldScene，通过 sceneKey 切换 5 张大地图（town_hub / farm_north /
// mine_south / campus_east / port_west），消除多实例资源浪费与状态错乱。
//
// W2 集成：把 NpcFramework + InteractableSystem 真正接入 WorldScene：
//   - parseObjects 解析出来的 npc 列表统一注册到 NpcFramework，便于后续 patrol /
//     对话锚点 / faceTowards 等行为复用，而不是各场景再写一遍 dx/dy 巡逻；
//   - 同一份 npc 列表派生出 InteractableSystem 的 circle trigger，靠 hintRenderer
//     在屏幕底部气泡区显示 "按 E 与 {NPC 名} 对话"，actionPressed 边沿触发 dialog
//     store openWithTree；
//   - 额外注册一个 "公告板" trigger 作为 non-NPC trigger 示例（仍在 hub 喷泉东侧），
//     验证 InteractableSystem 不仅能服务 NPC；
//   - scene shutdown 走 attachLifecycle 兜底 tweens/time/input，并显式 clear
//     framework 实例，避免切场景留下幽灵 trigger。
//
// 隔离边界：WorldScene 属于 game adapter 层，允许直接 import phaser；业务层仍
// 只通过 SceneLike / PlayerSpriteHandle 等结构化接口与本文件交互。
import Phaser from 'phaser';
import { router } from '@/app/router';
import { playBgm, type BgmKey } from '@/composables/useAudio';
import type { Direction } from '@api/dto/Direction';
import { createDirectionController, type DirectionController } from '@game/adapters/input';
import { openNpcDialog } from '@game/adapters/dialog';
import { createPlayerSprite } from '@game/adapters/sprite';
import { drawNpcSprite, type NpcSpawn } from '@game/adapters/tilemap';
import type { PlayerSpriteHandle, SceneLike } from '@game/adapters/sceneTypes';
import { applyWeather, normalizeWeatherKind, type WeatherHandle, type WeatherKind } from '@game/adapters/weather';
import { applyTimeOfDay, deriveTimeOfDay } from '@game/adapters/daynight';
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
import {
  resolveSchedulePosition,
  getScheduleWaypoints,
  type NpcSchedule,
} from '@game/core/npcSchedule';
import { useWorldStore } from '@stores/world';

const TILE_SIZE = 16;
const RENDER_SCALE = 2;
const RENDER_TILE_SIZE = TILE_SIZE * RENDER_SCALE;
// R-13 §-1 续：CAMERA_ZOOM 2 → 1.35 让屏幕上每个 tile 视觉尺寸从 64px → ~43px，
// 解决玩家反馈"地上像素块太大"——同时 player sprite 仍清晰可辨（48×72 → 32×48），
// 可视范围相应扩大约 50%，更接近星露谷标准缩放。
const CAMERA_ZOOM = 1.35;
const CAMERA_LERP = 0.15;
const MOVE_COOLDOWN_MS = 130;
const RAIN_SLOWDOWN = 1.25;
const FADE_DURATION_MS = 280;

const SUPPORTED_MAP_KEYS: ReadonlySet<string> = new Set([
  'town_hub',
  'farm_north',
  'mine_south',
  'campus_east',
  'port_west',
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

/**
 * W2 集成：公告板 trigger 在 town_hub 中的中心 tile。
 * 喷泉位于 tile(24,19)-(25,20) 区域；公告板放在喷泉东侧两格，避开 spawn(25,20)。
 * 5 张地图 hub 默认 50×40，27,20 落在喷泉右下方且在玩家可达范围内。
 * 其他地图（farm/mine/campus/port）暂不挂公告板，留 TODO 给后续 wave。
 */
const BULLETIN_BOARD_TILE_X = 27;
const BULLETIN_BOARD_TILE_Y = 20;
/** Trigger 命中半径（以 tile 为单位）：1.4 ≈ 玩家走到相邻一圈即触发。 */
const TRIGGER_RADIUS_TILES = 1.4;
/** 公告板 trigger 跳转的目标路由。 */
const BULLETIN_BOARD_ROUTE = '/board';

/**
 * Phaser hint 气泡渲染器句柄：scene 端注入到 InteractableSystem，
 * 由 InteractableSystem 在 enter/exit/clear 时调用 show/hide。
 * destroy 在 shutdown 时由 scene 主动调用，回收 Phaser GameObject。
 */
interface PhaserHintRendererHandle {
  renderer: HintRenderer;
  destroy(): void;
}

interface WorldSceneState {
  mapKey: string;
  index: MapIndex | null;
  entry: MapIndexEntry | null;
  player: PlayerSpriteHandle | null;
  controller: DirectionController | null;
  lastMoveAt: number;
  actionWasDown: boolean;
  exits: ExitTile[];
  npcs: NpcSpawn[];
  width: number;
  height: number;
  renderTileSize: number;
  collisions: boolean[][];
  switching: boolean;
  weather: WeatherHandle | null;
  weatherKind: WeatherKind;
  // W2 集成：framework 实例在 create 中构造、shutdown 中 clear；testing 留 null
  // 默认值，避免 jsdom 单测里 update() 误触发空 framework 时抛 NPE。
  interactable: InteractableSystem | null;
  npcFramework: NpcFramework | null;
  hintRendererHandle: PhaserHintRendererHandle | null;
  lifecycleDetach: (() => void) | null;
  // npcFramework.update 需要 deltaMs。Phaser scene 的 this.time.now 是单调递增
  // 毫秒时钟，记录上一帧的值在本帧相减即可。0 表示首帧，跳过 NPC tick 防止
  // dt 巨大跳变。
  prevUpdateTimeMs: number;
}

const state: WorldSceneState = {
  mapKey: DEFAULT_MAP_KEY,
  index: null,
  entry: null,
  player: null,
  controller: null,
  lastMoveAt: 0,
  actionWasDown: false,
  exits: [],
  npcs: [],
  width: 0,
  height: 0,
  renderTileSize: RENDER_TILE_SIZE,
  collisions: [],
  switching: false,
  weather: null,
  weatherKind: 'sunny' as WeatherKind,
  interactable: null,
  npcFramework: null,
  hintRendererHandle: null,
  lifecycleDetach: null,
  prevUpdateTimeMs: 0,
};

export const WorldScene = {
  key: 'WorldScene',
  init(this: Phaser.Scene, data?: { mapKey?: string }): void {
    const candidate = typeof data?.mapKey === 'string' ? data.mapKey : state.mapKey;
    state.mapKey = SUPPORTED_MAP_KEYS.has(candidate) ? candidate : DEFAULT_MAP_KEY;
    state.switching = false;
    state.lastMoveAt = 0;
    state.actionWasDown = false;
    // 上一次 restart 残留的 weather 句柄要在 init 阶段一次性销毁，避免重复 setTint
    // 在 jsdom 单测下出现 cameras.main null 报错。
    if (state.weather) {
      state.weather.destroy();
      state.weather = null;
    }
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

    state.collisions = buildCollisions(map);

    const { spawnTile, exits, buildings, npcs } = parseObjects(map, state.entry?.spawn, index);
    state.exits = exits;
    state.npcs = npcs;

    const worldStore = useWorldStore();
    const persisted =
      worldStore.player && worldStore.player.mapKey === state.mapKey
        ? { tileX: worldStore.player.tileX, tileY: worldStore.player.tileY }
        : spawnTile;

    const player = createPlayerSprite(this, {
      tileX: persisted.tileX,
      tileY: persisted.tileY,
      direction: worldStore.player?.direction ?? 'DOWN',
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

    state.controller = createDirectionController(this);

    buildings.forEach((building) => {
      paintBuildingZone(phaserScene, building);
    });
    npcs.forEach((npc) => {
      drawNpcSprite(this, npc, RENDER_TILE_SIZE);
      paintNpcHotspot(phaserScene, npc);
    });

    // W2 集成：NpcFramework 持有逻辑层 NPC（巡逻/朝向/对话状态），
    // InteractableSystem 持有 trigger（distance + hint）。
    // 两者从相同的 npcs 列表派生，drawNpcSprite 维持视觉层（不接 patrol 移动，
    // 等 wave-3 把 Phaser sprite 与 NpcInstance.x/y 双向同步）。
    state.npcFramework = new NpcFramework(phaserScene);
    state.hintRendererHandle = createPhaserHintRenderer(phaserScene);
    state.interactable = new InteractableSystem(phaserScene, {
      hintRenderer: state.hintRendererHandle.renderer,
    });
    npcs.forEach((npc) => {
      state.npcFramework?.register(buildNpcDefinitionFromSpawn(npc, RENDER_TILE_SIZE));
      const trigger = buildNpcTrigger(npc, RENDER_TILE_SIZE, () => {
        // 占位 dialog text 仍走 dialog store openWithTree，自动 fetch
        // /data/npcs/{key}/{meta,dialog}.json，失败时 store 内部填 loadError。
        // NpcDialogService 接通时只需替换 dialog store 拉取实现。
        openNpcDialog(npc.npcKey);
        state.npcFramework?.startDialog(npc.npcKey);
        state.npcFramework?.faceTowards(
          npc.npcKey,
          player.tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2,
          player.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2,
        );
      });
      state.interactable?.register(trigger);
    });

    // A5: 异步加载 NPC 日程并更新位置（火后不管，不阻塞场景初始化）
    void applyNpcSchedules(npcs, state.npcFramework, state.mapKey, RENDER_TILE_SIZE);

    // town_hub 专属示例 trigger：公告板（非 NPC trigger，验证 InteractableSystem
    // 不只服务 NPC）。其他地图的 trigger（farm 浇水点 / mine 矿井入口等）留 TODO
    // 给后续 wave，等地图 JSON 加入 triggers object layer 后批量注册。
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
    }

    const worldWidth = state.width * RENDER_TILE_SIZE;
    const worldHeight = state.height * RENDER_TILE_SIZE;
    this.cameras.main.setBounds(0, 0, worldWidth, worldHeight);
    if (this.physics?.world?.setBounds) {
      this.physics.world.setBounds(0, 0, worldWidth, worldHeight);
    }
    this.cameras.main.startFollow(player, true, CAMERA_LERP, CAMERA_LERP);
    this.cameras.main.setZoom?.(CAMERA_ZOOM);
    phaserScene.cameras.main.fadeIn(FADE_DURATION_MS, 0, 0, 0);

    const bgm = pickBgm(state.entry?.bgm);
    playBgm(bgm);

    // W3 Polish：先应用 weather 粒子，再用 timeOfDay tint 覆盖颜色。两者顺序固定，
    // 不要互换，否则 setTint 会被 weather 反覆盖回去。
    const weatherKind = normalizeWeatherKind(worldStore.session?.weather);
    state.weatherKind = weatherKind;
    state.weather = applyWeather(this, weatherKind, worldWidth, worldHeight);
    const sessionForTime = worldStore.session;
    const timeOfDay = deriveTimeOfDay({
      inGameMinutes: sessionForTime ? sessionForTime.inGameMinutes : null,
    });
    applyTimeOfDay(this, timeOfDay);

    const onBeforeUnload = (): void => {
      void worldStore.flushPlayerUpdate();
    };
    window.addEventListener('beforeunload', onBeforeUnload);

    // W2 集成：attachLifecycle 兜底 tweens/time/input 清理，避免 HMR / restart
    // 残留 ticker；onShutdown 闭包负责 clear 业务 framework + 销毁 hint 渲染器。
    // 业务 state 字段（weather/player/...）仍在 shutdown handler 内 reset，便于
    // 单步可读。
    state.lifecycleDetach = attachLifecycle(phaserScene, {
      onShutdown: () => {
        state.interactable?.clear();
        state.interactable = null;
        state.npcFramework?.clear();
        state.npcFramework = null;
        state.hintRendererHandle?.destroy();
        state.hintRendererHandle = null;
        state.prevUpdateTimeMs = 0;
      },
    });

    this.events?.on('shutdown', () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      if (state.weather) {
        state.weather.destroy();
        state.weather = null;
      }
      state.player = null;
      state.controller = null;
      state.collisions = [];
      state.exits = [];
      state.npcs = [];
      state.lastMoveAt = 0;
      state.actionWasDown = false;
      // attachLifecycle 自己监听 shutdown 已经触发 onShutdown，这里仅 detach
      // 多余引用，避免 restart 时挂多份相同监听。
      if (state.lifecycleDetach) {
        state.lifecycleDetach();
        state.lifecycleDetach = null;
      }
    });
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

    const rawDirection = controller.getDirection();
    const effectiveCooldown = state.weatherKind === 'rainy' || state.weatherKind === 'stormy'
      ? MOVE_COOLDOWN_MS * RAIN_SLOWDOWN
      : MOVE_COOLDOWN_MS;
    if (rawDirection && this.time.now - state.lastMoveAt > effectiveCooldown) {
      state.lastMoveAt = this.time.now;
      const delta = directionDelta(rawDirection);
      const cardinal = toCardinal(rawDirection, player.direction);
      if (delta.dx === 0 && delta.dy === 0) {
        return;
      }
      // R-13 修复：星露谷式 turn-then-walk。如果当前 cardinal 不是玩家朝向，
      // 第一次按只转身不前进；下一次再按才真正走一格。
      // 这样玩家从「面朝下站立」按右键，先转脸朝右；再按右键，走一格。
      // 避免「按一下就强制蹭一步」的笨重感。
      if (player.direction !== cardinal) {
        player.moveToTile(player.tileX, player.tileY, cardinal);
        void useWorldStore().updatePlayerTile({
          tileX: player.tileX,
          tileY: player.tileY,
          direction: cardinal,
        });
        return;
      }
      const nextX = clamp(player.tileX + delta.dx, 0, state.width - 1);
      const nextY = clamp(player.tileY + delta.dy, 0, state.height - 1);
      if (!isBlocked(nextX, nextY)) {
        player.moveToTile(nextX, nextY, cardinal);
        void useWorldStore().updatePlayerTile({
          tileX: nextX,
          tileY: nextY,
          direction: cardinal,
        });
        const exit = findExit(nextX, nextY);
        if (exit) {
          triggerSceneTransition(this as unknown as Phaser.Scene, exit);
        }
      } else {
        player.moveToTile(player.tileX, player.tileY, cardinal);
      }
    }

    // W2 集成：framework 驱动。actionPressed 用边沿语义传入 InteractableSystem，
    // 由 trigger.onActivate 取代旧的"曼哈顿 <=1 找 NPC 调 openNpcDialog"。
    // npcFramework.update 接 deltaMs 推进 patrol；town_hub 当前 NPC 无 patrol
    // 数据，update() 实际是 no-op，未来 JSON 加 patrol 字段后立刻生效。
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
    }
    state.prevUpdateTimeMs = nowMs;

    const playerCenterX = player.tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
    const playerCenterY = player.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
    state.interactable?.update(playerCenterX, playerCenterY, actionPressedEdge);
  },
};

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

function parseObjects(
  map: Phaser.Tilemaps.Tilemap,
  fallbackSpawn: { tileX: number; tileY: number } | undefined,
  index: MapIndex | null,
): {
  spawnTile: { tileX: number; tileY: number };
  exits: ExitTile[];
  buildings: BuildingZone[];
  npcs: NpcSpawn[];
} {
  let spawnTile: { tileX: number; tileY: number } = fallbackSpawn ?? { tileX: 5, tileY: 5 };
  const exits: ExitTile[] = [];
  const buildings: BuildingZone[] = [];
  const npcs: NpcSpawn[] = [];

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
        // generate-maps.mjs 写出的字段是 `target`/`side`（4 tile 宽矩形,无 toTile*）;
        // 历史代码读的是 `toMap`/`toTileX`/`toTileY`。这里双向兼容,并优先读新字段。
        // 当只有 target 没有 toTile* 时,用目标地图 index.spawn 作为玩家落点 fallback。
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
        // 否则只命中左上角,其余 3 格直接穿过去毫无反应(W1 残留 bug)。
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
      if (type === 'npc') {
        const spriteValue = props['sprite'];
        // W2 集成：town_hub.json 用 "key" 字段（npc_mayor_py.properties[name=key]
        // value=mayor_py），保留向后兼容 "npcKey"。obj.name fallback 仅用于
        // 兜底，正式 NPC 必须显式声明 key 才能匹配 /data/npcs/<key>/。
        npcs.push({
          npcKey: String(props['key'] ?? props['npcKey'] ?? obj.name ?? 'npc'),
          label: String(props['label'] ?? obj.name ?? ''),
          tileX,
          tileY,
          sprite: typeof spriteValue === 'string' ? spriteValue : 'player_sara',
        });
      }
    });
  });

  return { spawnTile, exits, buildings, npcs };
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

function paintBuildingZone(phaserScene: Phaser.Scene, building: BuildingZone): void {
  const px = (building.tileX + building.width / 2) * RENDER_TILE_SIZE;
  const py = (building.tileY + building.height / 2) * RENDER_TILE_SIZE;
  const width = building.width * RENDER_TILE_SIZE;
  const height = building.height * RENDER_TILE_SIZE;
  const rect = phaserScene.add
    .rectangle(px, py, width, height, 0x000000, 0)
    .setStrokeStyle(2, 0x2f5a36, 0.4)
    .setInteractive({ useHandCursor: true })
    .setDepth(7);
  rect.on('pointerdown', () => {
    void router.push(building.route);
  });
  phaserScene.add
    .text(px, building.tileY * RENDER_TILE_SIZE - 4, building.label, {
      fontFamily: 'monospace',
      fontSize: '12px',
      color: '#fff8e1',
      backgroundColor: '#00000088',
      padding: { x: 4, y: 2 },
    })
    .setOrigin(0.5, 1)
    .setDepth(8);
}

function paintNpcHotspot(phaserScene: Phaser.Scene, npc: NpcSpawn): void {
  const cx = npc.tileX * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const cy = npc.tileY * RENDER_TILE_SIZE + RENDER_TILE_SIZE / 2;
  const marker = phaserScene.add
    .rectangle(cx, cy, RENDER_TILE_SIZE, RENDER_TILE_SIZE, 0xffffff, 0)
    .setInteractive({ useHandCursor: true })
    .setDepth(12);
  marker.on('pointerdown', () => openNpcDialog(npc.npcKey));
}

function isBlocked(tileX: number, tileY: number): boolean {
  if (tileX < 0 || tileY < 0 || tileX >= state.width || tileY >= state.height) {
    return true;
  }
  const row = state.collisions[tileY];
  return Boolean(row?.[tileX]);
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

export function toCardinal(dir: string, fallback: Direction): Direction {
  if (dir === 'UP' || dir === 'DOWN' || dir === 'LEFT' || dir === 'RIGHT') {
    return dir;
  }
  const tokens = dir.split('_');
  if (tokens.includes('LEFT')) return 'LEFT';
  if (tokens.includes('RIGHT')) return 'RIGHT';
  if (tokens.includes('UP')) return 'UP';
  if (tokens.includes('DOWN')) return 'DOWN';
  return fallback;
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
// W2 集成辅助导出：把 npc 列表转 NpcDefinition / ITrigger，便于在 jsdom
// 单测里 mock 调用而无需启动整个 Phaser scene。
// =====================================================================

/** Trigger id 前缀，业务侧调试时容易区分 npc trigger vs 其他类型。 */
export const NPC_TRIGGER_ID_PREFIX = 'npc:';

/** 公告板 trigger 固定 id，导出便于单测断言。 */
export const BULLETIN_BOARD_TRIGGER_ID = 'trigger:bulletin_board';

/**
 * 玩家走到 NPC 旁边时显示的 hint 文本，单独抽出便于本地化与单测。
 */
export function buildNpcHintText(npcLabel: string): string {
  // 故意保留中文以贴合 Beta 首批玩家（高校 Python 初学者）。
  return `按 E 与 ${npcLabel} 对话`;
}

/**
 * 由 tilemap parseObjects 返回的 NpcSpawn 推出 NpcFramework 注册用 NpcDefinition。
 *
 * A5 schedule 支持：
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
 * 构造公告板 trigger。当前仅 town_hub 注册，其他地图等后续 wave 接入 trigger
 * object layer 后批量生成。
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
    destroy?: () => void;
    visible?: boolean;
  };
  hintTextWithScroll.setScrollFactor?.(0, 0);
  hintTextWithScroll.setVisible?.(false);

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
      hintTextWithScroll.destroy?.();
    },
  };
}

/**
 * A5: 异步加载 NPC meta.json 获取 schedule，然后通过 NpcFramework.teleport
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
