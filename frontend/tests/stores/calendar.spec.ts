import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCalendarStore } from '@stores/calendar';
import type { WorldCalendarDto } from '@/api/dto/WorldCalendarDto';
import type { WorldEventDto } from '@/api/dto/WorldEventDto';

const mocks = vi.hoisted(() => ({
  fetchCalendarToday: vi.fn(),
  fetchCalendarEvents: vi.fn(),
  markCalendarEventProcessed: vi.fn(),
}));

vi.mock('@/api/worldCalendar', () => mocks);

function makeToday(): WorldCalendarDto {
  return {
    dayIndex: 7,
    weekday: 'MONDAY',
    monthIndex: 1,
    season: 'spring',
    timeOfDay: 'MORNING',
    weather: 'SUNNY',
  };
}

function makeEvent(id: number, processed = false): WorldEventDto {
  return {
    id,
    dateIso: '2026-05-22',
    eventType: 'MASTERY_DECAY',
    payload: { kcCode: 'PY43-KC-001' },
    isProcessed: processed,
    createdAt: '2026-05-22T08:00:00Z',
  };
}

describe('calendar store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchCalendarToday.mockReset();
    mocks.fetchCalendarEvents.mockReset();
    mocks.markCalendarEventProcessed.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('load() parallel fetches today + events', async () => {
    mocks.fetchCalendarToday.mockResolvedValue(makeToday());
    mocks.fetchCalendarEvents.mockResolvedValue([makeEvent(1), makeEvent(2, true)]);
    const store = useCalendarStore();
    await store.load();
    expect(mocks.fetchCalendarToday).toHaveBeenCalledTimes(1);
    expect(mocks.fetchCalendarEvents).toHaveBeenCalledTimes(1);
    expect(store.today?.dayIndex).toBe(7);
    expect(store.events).toHaveLength(2);
    expect(store.unprocessedEvents).toHaveLength(1);
    expect(store.isSeason('spring')).toBe(true);
    expect(store.isSeason('winter')).toBe(false);
    expect(store.dayIndex).toBe(7);
  });

  it('load() captures error gracefully', async () => {
    mocks.fetchCalendarToday.mockRejectedValue(new Error('offline'));
    mocks.fetchCalendarEvents.mockResolvedValue([]);
    const store = useCalendarStore();
    await store.load();
    expect(store.error).toBe('offline');
    expect(store.loading).toBe(false);
  });

  it('markEventProcessed() POST /process and replaces local entry', async () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday(), [makeEvent(1), makeEvent(2)]);
    mocks.markCalendarEventProcessed.mockResolvedValue(makeEvent(1, true));
    await store.markEventProcessed(1);
    expect(mocks.markCalendarEventProcessed).toHaveBeenCalledWith(1);
    expect(store.unprocessedEvents.map((e) => e.id)).toEqual([2]);
  });

  it('setBootstrap with empty events defaults to []', () => {
    const store = useCalendarStore();
    store.setBootstrap(makeToday());
    expect(store.events).toEqual([]);
    expect(store.today?.season).toBe('spring');
  });
});
