import { defineStore } from 'pinia';
import {
  fetchCalendarEvents,
  fetchCalendarToday,
  markCalendarEventProcessed,
} from '@api/worldCalendar';
import type { WorldCalendarDto, WorldSeason } from '@api/dto/WorldCalendarDto';
import type { WorldEventDto } from '@api/dto/WorldEventDto';

/**
 * 世界日历 store：当前日期 / 季节 / 天气 + 待处理事件队列。
 *
 * 数据来源：
 *   - bootstrap：world store 的 `bootstrap()` 下发 calendar + events，调用 `setBootstrap`；
 *   - lazy reload：`load()` 并行 GET today + events；
 *   - sleep：world store 的 `sleep()` action 收到推进后的 calendar，再调 `setBootstrap` 覆盖；
 *   - mutation：`markEventProcessed(id)` POST events/{id}/process 把单个事件标记为已处理。
 *
 * `unprocessedEvents` 用于 WorldScene 在 sleep 之后弹"昨夜发生的事"。
 */
interface CalendarState {
  today: WorldCalendarDto | null;
  events: WorldEventDto[];
  loading: boolean;
  error: string | null;
}

export const useCalendarStore = defineStore('calendar', {
  state: (): CalendarState => ({
    today: null,
    events: [],
    loading: false,
    error: null,
  }),
  getters: {
    unprocessedEvents(state): WorldEventDto[] {
      return state.events.filter((event) => !event.isProcessed);
    },
    isSeason(state) {
      return (season: WorldSeason): boolean => state.today?.season === season;
    },
    dayIndex(state): number {
      return state.today?.dayIndex ?? 0;
    },
  },
  actions: {
    async load(): Promise<void> {
      this.loading = true;
      this.error = null;
      try {
        const [today, events] = await Promise.all([
          fetchCalendarToday(),
          fetchCalendarEvents(),
        ]);
        this.today = today;
        this.events = events;
      } catch (error) {
        this.error = error instanceof Error ? error.message : String(error);
      } finally {
        this.loading = false;
      }
    },
    async markEventProcessed(eventId: number): Promise<void> {
      const updated = await markCalendarEventProcessed(eventId);
      const idx = this.events.findIndex((event) => event.id === updated.id);
      if (idx >= 0) {
        const next = [...this.events];
        next[idx] = updated;
        this.events = next;
      }
    },
    setBootstrap(today: WorldCalendarDto, events: WorldEventDto[] = []): void {
      this.today = today;
      this.events = events;
    },
    /**
     * 增量追加新事件（sleep 推进后若后端只返回 newEvents 没返回 calendar 时使用）。
     * 按 id 去重，避免同一事件被重复入队。
     */
    pushEvents(events: WorldEventDto[]): void {
      if (!events.length) return;
      const known = new Set(this.events.map((e) => e.id));
      const fresh = events.filter((e) => !known.has(e.id));
      if (fresh.length) {
        this.events = [...this.events, ...fresh];
      }
    },
  },
});
