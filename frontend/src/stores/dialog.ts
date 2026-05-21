import { defineStore } from 'pinia';
import { npcDialog } from '@/game/data/npcDialog';

export const useDialogStore = defineStore('dialog', {
  state: () => ({
    openNpcKey: null as string | null,
    lines: [] as string[],
  }),
  getters: {
    isOpen: (state): boolean => state.openNpcKey !== null,
  },
  actions: {
    open(npcKey: string): void {
      this.openNpcKey = npcKey;
      this.lines = npcDialog[npcKey] ?? ['[占位] 这位居民还没有准备好对话。'];
    },
    close(): void {
      this.openNpcKey = null;
      this.lines = [];
    },
  },
});
