import { defineStore } from 'pinia';
import { fetchWorldSession, patchPlayerState } from '@api/world';
import type { Direction } from '@api/dto/Direction';
import type { NpcStateDto } from '@api/dto/NpcStateDto';
import type { PlayerStateDto } from '@api/dto/PlayerStateDto';
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
