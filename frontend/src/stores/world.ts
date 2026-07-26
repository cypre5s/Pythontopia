import { defineStore } from 'pinia';
import { fetchWorldSession, patchPlayerState } from '@api/world';
import { fetchGameBootstrap, postWorldSleep } from '@api/game';
import { useCalendarStore } from '@stores/calendar';
import { useLearningStore } from '@stores/learning';
import { useNpcStore } from '@stores/npc';
import { useSkillStore } from '@stores/skill';
import { useHudStore } from '@stores/hud';
import { useToastStore } from '@stores/toast';
import { playChime } from '@/composables/useStardewFx';
import type { Direction } from '@api/dto/Direction';
import type { GameBootstrapResponseDto } from '@api/dto/GameBootstrapResponseDto';
import type { MasterySnapshotDto } from '@api/dto/MasterySnapshotDto';
import type { NpcStateDto } from '@api/dto/NpcStateDto';
import type { PlayerStateDto } from '@api/dto/PlayerStateDto';
import type { SaveSlotDto } from '@api/dto/SaveSlotDto';
import type { WorldSessionDto } from '@api/dto/WorldSessionDto';

export type { Direction };

interface PendingPlayerUpdate {
  tileX: number;
  tileY: number;
  direction: Direction;
}

/**
 * KC 掌握度快照（masteryScore ∈ [0,1]，后端 MasteryEvaluator 钳制）折算成 HUD 需要的
 * 0–100 百分比平均值。空快照返回 0（HUD 进度条显示空条）。
 */
export function masteryAvgPercent(snapshot: readonly MasterySnapshotDto[]): number {
  if (snapshot.length === 0) {
    return 0;
  }
  const total = snapshot.reduce((sum, entry) => sum + (Number.isFinite(entry.masteryScore) ? entry.masteryScore : 0), 0);
  const avg = total / snapshot.length;
  return Math.round(Math.max(0, Math.min(1, avg)) * 100);
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
    activeSlot: null as SaveSlotDto | null,
    sceneName: DEFAULT_SCENE_NAME as string,
    accumulatedPlaySeconds: 0 as number,
    bootstrapped: false,
    loading: false,
    // Workshop 顶部传送 chip 跳 /world 时把目标 mapKey
    // 暂存在 store；WorldCanvas mount + bootstrap 之后会读取并强制 BootScene 起到
    // 该地图，避免 BootScene 始终硬编码 town_hub。
    pendingTravelMap: null as string | null,
    pendingTravelTile: null as { x: number; y: number } | null,
  }),
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
     * 与 POST /api/game/bootstrap 对齐：
     *   - world + player：写到 world store 自身
     *   - mastery / profile / path / quests：通过 learning.setBootstrapData 分发
     *   - skills(+catalog) / calendar(+events) / npcStates：分别下发到各 store
     *   - activeSlot：保存在 world store（不另起 store）
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
      this.activeSlot = data.activeSlot;
      if (data.activeSlot) {
        this.syncFromSlot(data.activeSlot);
      } else {
        this.resetSlotState();
      }

      useLearningStore().setBootstrapData({
        quests: data.quests,
        mastery: data.mastery,
        profile: data.profile,
        path: data.path,
      });
      useSkillStore().setBootstrap(data.skillsCatalog ?? [], data.skills);
      // 后端实际字段是 pendingEvents（GameBootstrapResponse.java），
      // events 是旧前端别名；兼容两边别名，确保拿得到事件。
      useCalendarStore().setBootstrap(data.calendar, data.events ?? data.pendingEvents ?? []);
      useNpcStore().setBootstrap(data.npcStates ?? []);

      useHudStore().syncMeta({
        dayIndex: data.world.dayIndex ?? 1,
        weather: data.world.weather ?? 'SUNNY',
        // HUD 掌握度条是 0–100 的百分比；mastery 快照是 0–1 标度，这里统一折算，
        // 否则 HudShell 的进度条永远停在 0%。
        masteryAvg: masteryAvgPercent(data.mastery ?? []),
      });
    },
    /**
     * 把后端最新的 SaveSlotDto 同步到 world store 的 RPG 扩展字段上。WorldScene 切换 /
     * 累计时长 都从这里读，避免 component 各自 patch state。
     * （R-14 收缩后无背包/建筑解锁玩法，slot 的 inventoryItems/unlockedBuildings
     * 仅是后端 NOT NULL 契约字段，前端不再落 store。）
     */
    syncFromSlot(slot: SaveSlotDto): void {
      this.activeSlot = slot;
      this.sceneName = slot.sceneName || DEFAULT_SCENE_NAME;
      this.accumulatedPlaySeconds = slot.accumulatedPlaySeconds;
      // R-15：旧 mainQuest 存档快照已作废——主线进度改由后端权威 mainline 域（按 user_id 独立表），
      // 不再回灌 snapshot.mainQuest。slot 的 RPG 扩展字段仍透传到 store（见上）。
    },
    /**
     * 推进一天：触发 mastery decay / NPC 周计划重置。
     *
     * 与 POST /api/world/sleep 对齐：
     *   - 后端原子推进 world.dayIndex 并写 world_event 表
     *   - 返回 { world: <patch>, calendar, events? }，由 world store merge 进 session
     *   - UI 通过 toast 提示，详细变化由 WorldScene 自行读取 calendar.unprocessedEvents 弹板
     *
     * 掌握度衰减是**后端**语义（MasteryEvaluator 读时按天衰减并钳在 [0,1]），推进一天后本
     * store 只负责重新拉取服务端权威快照，绝不在前端伪造衰减。
     */
    async sleep(): Promise<void> {
      const response = await postWorldSleep();
      if (this.session) {
        this.session = { ...this.session, ...response.world };
      }
      // 后端实际字段是 newEvents（WorldAdvanceResponse.java）；calendar 是
      // 旧前端兼容字段，新版后端不再返回时就用现有 calendar 兜底。
      const events = response.events ?? response.newEvents ?? [];
      const calendar = response.calendar;
      if (calendar) {
        useCalendarStore().setBootstrap(calendar, events);
      } else if (events.length > 0) {
        useCalendarStore().pushEvents(events);
      }
      useToastStore().show(`第 ${response.world.dayIndex} 天醒来`, 'info');

      const hud = useHudStore();
      hud.syncMeta({
        dayIndex: response.world.dayIndex,
        weather: response.world.weather ?? 'SUNNY',
      });

      // 推进一天后重新取服务端掌握度（含后端的遗忘衰减），并刷新 HUD 掌握度条。
      // 拉取失败不阻塞「醒来」流程：下次进入学习页 / bootstrap 会重新拉。
      try {
        const learningStore = useLearningStore();
        await learningStore.loadField();
        hud.syncMeta({ masteryAvg: masteryAvgPercent(learningStore.field.masterySnapshot) });
      } catch {
        /* 掌握度刷新失败静默降级，保留上一份快照 */
      }

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
        mapKey: this.player?.mapKey ?? 'town_hub',
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
      const currentMapKey = this.player?.mapKey;
      const player = await patchPlayerState(update);
      this.player = currentMapKey ? { ...player, mapKey: currentMapKey } : player;
    },
    async refreshWeather(): Promise<void> {
      await this.loadSession();
    },
    resetSlotState(): void {
      this.sceneName = DEFAULT_SCENE_NAME;
      this.accumulatedPlaySeconds = 0;
    },
  },
});
