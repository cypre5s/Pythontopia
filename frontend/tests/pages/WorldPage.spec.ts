import { flushPromises, shallowMount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import WorldPage from '@/pages/WorldPage.vue';

const mocks = vi.hoisted(() => ({
  loadMainline: vi.fn().mockResolvedValue(undefined),
  world: { session: {}, loadSession: vi.fn() },
  calendar: { today: { season: 'SPRING' } },
  gameSettings: {
    display: { fullscreen: false, showVignette: true, uiScale: 'medium', colorMode: 'default' },
    education: { focusMode: false, codeFontSize: 14 },
    updateDisplay: vi.fn(),
  },
}));

vi.mock('@stores/world', () => ({ useWorldStore: () => mocks.world }));
vi.mock('@stores/calendar', () => ({ useCalendarStore: () => mocks.calendar }));
vi.mock('@stores/gameSettings', () => ({ useGameSettingsStore: () => mocks.gameSettings }));
vi.mock('@stores/mainline', () => ({ useMainlineStore: () => ({ load: mocks.loadMainline }) }));
vi.mock('@game/adapters/dialog', () => ({ openNpcDialog: vi.fn() }));

describe('WorldPage', () => {
  beforeEach(() => {
    localStorage.clear();
    mocks.loadMainline.mockClear();
    mocks.world.loadSession.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads the authoritative mainline overview from one world-page owner', async () => {
    const wrapper = shallowMount(WorldPage);
    await flushPromises();

    expect(mocks.loadMainline).toHaveBeenCalledTimes(1);

    wrapper.unmount();
  });
});
