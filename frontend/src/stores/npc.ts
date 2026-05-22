import { defineStore } from 'pinia';
import { fetchNpcState, giftNpc, talkToNpc } from '@api/worldNpc';
import type {
  NpcDialogStateDto,
  NpcGiftResponseDto,
  NpcTalkRequest,
} from '@api/dto/NpcDialogStateDto';

/**
 * NPC 对话/好感 store。
 *
 * 与 `useDialogStore` 的分工：
 *   - `useDialogStore`：单次对话窗口的 UI 状态（visible / tree / currentNode / 选项）；
 *   - `useNpcStore`：跨会话持久化的服务端状态（friendship / completedNodeIds / lastTalkedAt / flags），
 *     bootstrap 时下发，talk / gift 后由 store 写入；DialogModal 通过 `useNpcStore` 读取 friendship。
 *
 * 数据来源：
 *   - bootstrap：world store 的 `bootstrap()` 下发 npcStates，调用 `setBootstrap`；
 *   - lazy load：`loadState(npcKey)` 单独 GET /api/world/npcs/{key}/state（首次见到时）；
 *   - mutation：`talk(...)`、`gift(...)` 写后端并 merge 回本地 states 表。
 */
interface NpcState {
  states: Record<string, NpcDialogStateDto>;
  loading: boolean;
  error: string | null;
}

export const useNpcStore = defineStore('npc', {
  state: (): NpcState => ({
    states: {},
    loading: false,
    error: null,
  }),
  getters: {
    friendship(state) {
      return (npcKey: string): number => state.states[npcKey]?.friendship ?? 0;
    },
    isMet(state) {
      return (npcKey: string): boolean => npcKey in state.states;
    },
    getState(state) {
      return (npcKey: string): NpcDialogStateDto | null => state.states[npcKey] ?? null;
    },
  },
  actions: {
    async loadState(npcKey: string): Promise<NpcDialogStateDto | null> {
      this.loading = true;
      this.error = null;
      try {
        const result = await fetchNpcState(npcKey);
        this.states = { ...this.states, [npcKey]: result };
        return result;
      } catch (error) {
        this.error = error instanceof Error ? error.message : String(error);
        return null;
      } finally {
        this.loading = false;
      }
    },
    async talk(npcKey: string, payload: NpcTalkRequest): Promise<void> {
      const updated = await talkToNpc(npcKey, payload);
      const prev = this.states[npcKey];
      this.states = {
        ...this.states,
        [npcKey]: {
          npcKey: updated.npcKey,
          currentNodeId: updated.currentNodeId,
          completedNodeIds: updated.completedNodeIds,
          friendship: updated.friendship,
          lastTalkedAt: updated.lastTalkedAt,
          flags: prev?.flags ?? {},
        },
      };
    },
    async gift(
      npcKey: string,
      itemKey: string,
      qty: number,
    ): Promise<NpcGiftResponseDto> {
      const response = await giftNpc(npcKey, { itemKey, qty });
      const prev = this.states[npcKey];
      if (prev) {
        this.states = {
          ...this.states,
          [npcKey]: { ...prev, friendship: response.newFriendship },
        };
      }
      return response;
    },
    setBootstrap(states: NpcDialogStateDto[]): void {
      const next: Record<string, NpcDialogStateDto> = {};
      for (const entry of states) {
        next[entry.npcKey] = entry;
      }
      this.states = next;
    },
  },
});
