import { defineStore } from 'pinia';
import { fetchWorldSession, patchPlayerState } from '@api/world';
import { fetchGameBootstrap, postWorldSleep } from '@api/game';
import { useCalendarStore } from '@stores/calendar';
import { useInventoryStore } from '@stores/inventory';
import { useLearningStore } from '@stores/learning';
import { useNpcStore } from '@stores/npc';
import { useSkillStore } from '@stores/skill';
import { useToastStore } from '@stores/toast';
import type { Direction } from '@api/dto/Direction';
import type { GameBootstrapResponseDto } from '@api/dto/GameBootstrapResponseDto';
import type { NpcStateDto } from '@api/dto/NpcStateDto';
import type { PlayerCurrencyDto } from '@api/dto/PlayerCurrencyDto';
import type { PlayerStateDto } from '@api/dto/PlayerStateDto';
import type { SaveSlotDto } from '@api/dto/SaveSlotDto';
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

export const useWorldStore = defineStore('world', {
  state: () => ({
    session: null as WorldSessionDto | null,
    player: null as PlayerStateDto | null,
    npcStates: [] as NpcStateDto[],
    currency: null as PlayerCurrencyDto | null,
    activeSlot: null as SaveSlotDto | null,
    bootstrapped: false,
    loading: false,
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

      useLearningStore().setBootstrapData({
        crops: data.crops,
        quests: data.quests,
        mastery: data.mastery,
        profile: data.profile,
        path: data.path,
      });
      useInventoryStore().setBootstrapItems(data.inventory);
      useSkillStore().setBootstrap(data.skillsCatalog ?? [], data.skills);
      useCalendarStore().setBootstrap(data.calendar, data.events ?? []);
      useNpcStore().setBootstrap(data.npcStates ?? []);
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
      useCalendarStore().setBootstrap(response.calendar, response.events ?? []);
      useToastStore().show(`第 ${response.world.dayIndex} 天醒来`, 'info');
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
  },
});
