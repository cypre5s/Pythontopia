import { defineStore } from 'pinia';
import { fetchWorldSession, patchPlayerState } from '@api/world';
import { fetchGameBootstrap, postWorldSleep } from '@api/game';
import { useCalendarStore } from '@stores/calendar';
import { useInventoryStore } from '@stores/inventory';
import { useLearningStore } from '@stores/learning';
import { useNpcStore } from '@stores/npc';
import { useSkillStore } from '@stores/skill';
import { useToastStore } from '@stores/toast';
import { playChime } from '@/composables/useStardewFx';
import type { Direction } from '@api/dto/Direction';
import type { GameBootstrapResponseDto } from '@api/dto/GameBootstrapResponseDto';
import type { NpcStateDto } from '@api/dto/NpcStateDto';
import type { PlayerCurrencyDto } from '@api/dto/PlayerCurrencyDto';
import type { PlayerStateDto } from '@api/dto/PlayerStateDto';
import type { SaveSlotDto, SaveSlotInventoryItem } from '@api/dto/SaveSlotDto';
import type { WorldSessionDto } from '@api/dto/WorldSessionDto';

export type { Direction };

interface PendingPlayerUpdate {
  tileX: number;
  tileY: number;
  direction: Direction;
}

let throttleTimer: ReturnType<typeof setTimeout> | null = null;
let lastSentAt = 0;
let pendingUpdate: PendingPlayerUpdate | null = null;

const DEFAULT_SCENE_NAME = 'WorldScene';

export const useWorldStore = defineStore('world', {
  state: () => ({
    session: null as WorldSessionDto | null,
    player: null as PlayerStateDto | null,
    npcStates: [] as NpcStateDto[],
    currency: null as PlayerCurrencyDto | null,
    activeSlot: null as SaveSlotDto | null,
    sceneName: DEFAULT_SCENE_NAME as string,
    unlockedBuildings: [] as string[],
    accumulatedPlaySeconds: 0 as number,
    inventoryItems: [] as SaveSlotInventoryItem[],
    bootstrapped: false,
    loading: false,
    // R-13 §-1 C-FastTravel：Workshop 顶部 5 传送 chip 跳 /world 时把目标 mapKey
    // 暂存在 store；WorldCanvas mount + bootstrap 之后会读取并强制 BootScene 起到
    // 该地图，避免 BootScene 始终硬编码 town_hub。
    pendingTravelMap: null as string | null,
    pendingTravelTile: null as { x: number; y: number } | null,
  }),
  getters: {
    /**
     * `hasUnlocked('mine_entry')` 用于地图入口门禁判定。读自 W3 save_slot.unlocked_buildings，
     * 不读 InventoryStore，避免与背包语义混淆。
     */
    hasUnlocked(state) {
      return (buildingKey: string): boolean => state.unlockedBuildings.includes(buildingKey);
    },
  },
  actions: {
    async loadSession(): Promise<void> {
      this.loading = true;
      try {
        const response = await fetchWorldSession();
        this.session = response;
        this.player = response.player;
        this.npcStates = response.npcs;
      } finally {
        this.loading = false;
      }
    },
    /**
     * 在跳路由到 /world 之前调用，告诉 WorldCanvas / BootScene 应该启动到哪张地图，
     * 而不是按 server bootstrap 返回的 player.mapKey。`tile` 可选，未提供时落到地图
     * 默认 spawn。设置后第一次被 WorldCanvas 消费就清空。
     */
    setPendingTravel(mapKey: string, tile?: { x: number; y: number }): void {
      this.pendingTravelMap = mapKey;
      this.pendingTravelTile = tile ?? null;
    },
    consumePendingTravel(): { mapKey: string; tile: { x: number; y: number } | null } | null {
      if (!this.pendingTravelMap) return null;
      const next = { mapKey: this.pendingTravelMap, tile: this.pendingTravelTile };
      this.pendingTravelMap = null;
      this.pendingTravelTile = null;
      return next;
    },
    /**
     * 一次性拉全量并分发到各子 store，由 WorldCanvas 在挂载时调用。
     *
     * 与 W3-CTRL POST /api/game/bootstrap 对齐：
     *   - world + player：写到 world store 自身
     *   - crops / mastery / profile / path / quests：通过 learning.setBootstrapData 分发
     *   - inventory / skills(+catalog) / calendar(+events) / npcStates：分别下发到各 store
     *   - currency / activeSlot：保存在 world store（简单标量，不另起 store）
     *
     * 失败时不抛错（保留 bootstrapped=false），允许 UI 显示降级欢迎页，由 toast 提示原因。
     */
    async bootstrap(): Promise<GameBootstrapResponseDto | null> {
      this.loading = true;
      try {
        const data = await fetchGameBootstrap();
        this.applyBootstrap(data);
        this.bootstrapped = true;
        return data;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        useToastStore().show(`世界加载失败：${message}`, 'error');
        return null;
      } finally {
        this.loading = false;
      }
    },
    applyBootstrap(data: GameBootstrapResponseDto): void {
      this.session = data.world;
      this.player = data.player;
      this.npcStates = data.world.npcs;
      this.currency = data.currency;
      this.activeSlot = data.activeSlot;
      if (data.activeSlot) {
        this.syncFromSlot(data.activeSlot);
      } else {
        this.resetSlotState();
      }

      useLearningStore().setBootstrapData({
        crops: data.crops,
        quests: data.quests,
        mastery: data.mastery,
        profile: data.profile,
        path: data.path,
      });
      useInventoryStore().setBootstrapItems(data.inventory);
      useSkillStore().setBootstrap(data.skillsCatalog ?? [], data.skills);
      // R-13：后端实际字段是 pendingEvents（GameBootstrapResponse.java），
      // events 是旧前端别名；兼容两边别名，确保拿得到事件。
      useCalendarStore().setBootstrap(data.calendar, data.events ?? data.pendingEvents ?? []);
      useNpcStore().setBootstrap(data.npcStates ?? []);
    },
    /**
     * 把后端最新的 SaveSlotDto 同步到 world store 的 RPG 扩展字段上。WorldScene 切换 / 解锁建筑 /
     * 累计时长 / 背包快照 都从这里读，避免 component 各自 patch state。
     */
    syncFromSlot(slot: SaveSlotDto): void {
      this.activeSlot = slot;
      this.sceneName = slot.sceneName || DEFAULT_SCENE_NAME;
      this.unlockedBuildings = [...slot.unlockedBuildings];
      this.accumulatedPlaySeconds = slot.accumulatedPlaySeconds;
      this.inventoryItems = slot.inventoryItems.map((item) => ({ ...item }));
    },
    /**
     * 推进一天：触发 mastery decay / 作物枯萎 / NPC 周计划重置。
     *
     * 与 W3-CTRL POST /api/world/sleep 对齐：
     *   - 后端原子推进 world.dayIndex 并写 world_event 表
     *   - 返回 { world: <patch>, calendar, events? }，由 world store merge 进 session
     *   - UI 通过 toast 提示，详细变化由 WorldScene 自行读取 calendar.unprocessedEvents 弹板
     *
     * 调用方负责后续刷新 crops / mastery（通常通过 learning.refreshAfterEvent）。
     */
    async sleep(): Promise<void> {
      const response = await postWorldSleep();
      if (this.session) {
        this.session = { ...this.session, ...response.world };
      }
      // R-13：后端实际字段是 newEvents（WorldAdvanceResponse.java）；calendar 是
      // 旧前端兼容字段，新版后端不再返回时就用现有 calendar 兜底。
      const events = response.events ?? response.newEvents ?? [];
      const calendar = response.calendar;
      if (calendar) {
        useCalendarStore().setBootstrap(calendar, events);
      } else if (events.length > 0) {
        useCalendarStore().pushEvents(events);
      }
      useToastStore().show(`第 ${response.world.dayIndex} 天醒来`, 'info');
      // R-13：推一天给 dawn chime，星露谷感拉满
      try {
        playChime('dawn');
      } catch {
        /* tone.js 未解锁时静默 */
      }
    },
    async updatePlayerTile(update: PendingPlayerUpdate): Promise<void> {
      pendingUpdate = update;
      this.player = {
        playerStateId: this.player?.playerStateId ?? 0,
        avatarKey: this.player?.avatarKey ?? 'student_default',
        mapKey: this.player?.mapKey ?? 'town_main',
        ...update,
      };
      const elapsed = Date.now() - lastSentAt;
      if (elapsed >= 500) {
        await this.flushPlayerUpdate();
        return;
      }
      if (throttleTimer === null) {
        throttleTimer = setTimeout(() => {
          throttleTimer = null;
          void this.flushPlayerUpdate();
        }, 500 - elapsed);
      }
    },
    async flushPlayerUpdate(): Promise<void> {
      if (!pendingUpdate) {
        return;
      }
      const update = pendingUpdate;
      pendingUpdate = null;
      lastSentAt = Date.now();
      const player = await patchPlayerState(update);
      this.player = player;
    },
    async refreshWeather(): Promise<void> {
      await this.loadSession();
    },
    resetSlotState(): void {
      this.sceneName = DEFAULT_SCENE_NAME;
      this.unlockedBuildings = [];
      this.accumulatedPlaySeconds = 0;
      this.inventoryItems = [];
    },
  },
});
