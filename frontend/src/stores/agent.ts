import { defineStore } from 'pinia';

export const useAgentStore = defineStore('agent', {
  state: () => ({
    runs: [] as string[],
    activeRun: null as string | null,
    logs: [] as string[],
  }),
});
