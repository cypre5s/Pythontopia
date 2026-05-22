import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FieldPage from '@/pages/FieldPage.vue';
import { useLearningStore } from '@stores/learning';
import type { CropTileDto } from '@/api/dto/CropTileDto';

const sfxMocks = vi.hoisted(() => ({
  playSfx: vi.fn(),
}));

vi.mock('@/utils/sfx', () => sfxMocks);
vi.mock('@/app/AppLayout.vue', () => ({
  default: { template: '<main><slot /></main>' },
}));
vi.mock('@/components/game/FieldCanvas.vue', () => ({
  default: { template: '<div data-testid="field-canvas" />' },
}));
vi.mock('@/components/field/KcDetailPanel.vue', () => ({
  default: { props: ['kcCode'], template: '<aside data-testid="kc-detail">{{ kcCode }}</aside>' },
}));

function crop(kcCode: string): CropTileDto {
  return {
    tileX: 1,
    tileY: 1,
    kcCode,
    status: 'SEED',
    unlocked: true,
    masteryScore: 0,
    growthStage: 0,
  };
}

async function mountFieldPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/field', component: { template: '<div />' } },
      { path: '/profile-room', component: { template: '<div />' } },
    ],
  });
  await router.push('/field');

  const learning = useLearningStore();
  learning.profile = {
    profileId: 1,
    userId: 1,
    packId: 43,
    major: null,
    narrativeSummary: null,
  };
  learning.field.crops = [crop('PY43-KC-034'), crop('PY43-KC-031'), crop('PY43-KC-001')];
  vi.spyOn(learning, 'loadField').mockResolvedValue();

  const wrapper = mount(FieldPage, {
    global: {
      plugins: [pinia, router],
    },
  });
  await flushPromises();
  return { wrapper, learning, router };
}

describe('FieldPage chapter navigation', () => {
  beforeEach(() => {
    sfxMocks.playSfx.mockReset();
  });

  it('renders chapter icons and selects the first KC in the clicked chapter', async () => {
    const { wrapper, learning, router } = await mountFieldPage();

    const chapterButtons = wrapper.findAll('[data-testid^="chapter-nav-"]');
    expect(chapterButtons).toHaveLength(7);
    expect(wrapper.find('[data-testid="chapter-nav-control_flow_if_else"] img').attributes('src')).toBe(
      '/assets/pixel/icons_kc/control_flow_if_else/delapouite__choice.png',
    );

    await wrapper.find('[data-testid="chapter-nav-list_tuple_set_dict"]').trigger('click');
    await flushPromises();

    expect(learning.field.activeKcCode).toBe('PY43-KC-031');
    expect(router.currentRoute.value.query.kc).toBe('PY43-KC-031');
  });

  it('plays throttled hover sfx for chapter controls', async () => {
    const { wrapper } = await mountFieldPage();

    await wrapper.find('[data-testid="chapter-nav-chapter_basics"]').trigger('pointerenter');

    expect(sfxMocks.playSfx).toHaveBeenCalledWith('hover');
  });
});
