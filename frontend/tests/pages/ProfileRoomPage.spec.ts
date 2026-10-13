import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import ProfileRoomPage from '@/pages/ProfileRoomPage.vue';
import { fetchLearnerTwinState } from '@/api/twin';
import { useWorkshopUiStore } from '@/stores/workshopUi';
import type { LearnerTwinState } from '@/api/twin';

vi.mock('@/api/twin', () => ({
  fetchLearnerTwinState: vi.fn(),
  fetchWeeklyReport: vi.fn(),
}));

const unassessedState: LearnerTwinState = {
  assessedKcCount: 0,
  totalKcCount: 65,
  masteredKcCount: 0,
  growthStage: '初识小镇',
  kcs: [{
    kcCode: 'PY43-KC-001',
    kcName: '变量',
    chapter: 1,
    chapterName: '第一章',
    assessed: false,
    masteryScore: null,
    confidence: null,
    reviewDue: false,
    dueAt: null,
    lastPracticedAt: null,
    consecutiveFailures: 0,
  }],
  nextSteps: [{
    kcCode: 'PY43-KC-001',
    kcName: '变量',
    chapter: 1,
    chapterName: '第一章',
    difficulty: 1,
    action: 'PRACTICE',
    reason: '尚未评估，先从基础题建立证据',
    remediationRequired: false,
  }],
};

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/profile-room', component: ProfileRoomPage },
      { path: '/profile', component: { template: '<div />' } },
      { path: '/workshop', component: { template: '<div />' } },
      { path: '/review-hall', component: { template: '<div />' } },
      { path: '/bug-mine', component: { template: '<div />' } },
      { path: '/problems', component: { template: '<div />' } },
      { path: '/expedition', component: { template: '<div />' } },
    ],
  });
}

describe('ProfileRoomPage Learner Twin', () => {
  it('shows unassessed points without presenting default scores as results', async () => {
    setActivePinia(createPinia());
    vi.mocked(fetchLearnerTwinState).mockResolvedValue(unassessedState);
    const router = makeRouter();
    await router.push('/profile-room');
    const wrapper = mount(ProfileRoomPage, {
      global: {
        plugins: [router],
        stubs: { AppLayout: { template: '<div><slot /></div>' }, ProfileInterviewPanel: true },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('我的学习分身');
    expect(wrapper.text()).toContain('还没有经判题确认的学习证据');
    expect(wrapper.text()).toContain('尚未评估，先从基础题建立证据');
    expect(wrapper.text()).not.toContain('掌握 0%');
  });

  it('routes the main and distinct alternative recommendations to matching learning actions', async () => {
    setActivePinia(createPinia());
    vi.mocked(fetchLearnerTwinState).mockResolvedValue({
      ...unassessedState,
      kcs: [{ ...unassessedState.kcs[0], assessed: true, masteryScore: 0.42, reviewDue: true }],
      nextSteps: [
        { ...unassessedState.nextSteps[0], action: 'REVIEW', reason: 'FSRS-lite 复习已到期' },
        {
          ...unassessedState.nextSteps[0],
          kcCode: 'PY43-KC-002',
          kcName: '数值类型',
          chapter: 2,
          chapterName: '第二章',
          action: 'PRACTICE',
          reason: '尚未评估，先从基础题建立证据',
        },
      ],
    });
    const router = makeRouter();
    await router.push('/profile-room');
    const wrapper = mount(ProfileRoomPage, {
      global: { plugins: [router], stubs: { AppLayout: { template: '<div><slot /></div>' } } },
    });
    await flushPromises();

    await wrapper.get('[data-testid="twin-next-primary"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/bug-mine');
    expect(router.currentRoute.value.query.review).toBe('PY43-KC-001');

    await router.push('/profile-room');
    await wrapper.get('[data-testid="twin-next-alternative"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/expedition');
    expect(router.currentRoute.value.query.focus).toBe('2');
    expect(router.currentRoute.value.query.kcCode).toBe('PY43-KC-002');
  });

  it('hides the alternative button when the service has only one recommendation', async () => {
    setActivePinia(createPinia());
    vi.mocked(fetchLearnerTwinState).mockResolvedValue(unassessedState);
    const router = makeRouter();
    await router.push('/profile-room');
    const wrapper = mount(ProfileRoomPage, {
      global: { plugins: [router], stubs: { AppLayout: { template: '<div><slot /></div>' } } },
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="twin-next-alternative"]').exists()).toBe(false);
  });

  it('stores a weekly Twin prompt for the workshop and navigates there', async () => {
    setActivePinia(createPinia());
    vi.mocked(fetchLearnerTwinState).mockResolvedValue(unassessedState);
    const promptText = '基于本周证据复习 PY43-KC-001';
    const ui = useWorkshopUiStore();
    const router = makeRouter();
    await router.push('/profile-room');
    const wrapper = mount(ProfileRoomPage, {
      global: {
        plugins: [router],
        stubs: {
          AppLayout: { template: '<div><slot /></div>' },
          ProfileInterviewPanel: true,
          AgentNotebook: true,
          TwinWeekly: {
            emits: ['prompt'],
            template: '<button data-testid="emit-prompt" @click="$emit(\'prompt\', promptText)">prompt</button>',
            setup() { return { promptText }; },
          },
        },
      },
    });
    await flushPromises();

    await wrapper.get('[data-testid="twin-weekly-summary"]').trigger('click');
    await wrapper.get('[data-testid="emit-prompt"]').trigger('click');
    await flushPromises();

    expect(ui.pendingPrompt).toBe(promptText);
    expect(router.currentRoute.value.path).toBe('/workshop');
  });
});
