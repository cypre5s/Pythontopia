import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory, type Router } from 'vue-router';
import ProfilePage from '@/pages/ProfilePage.vue';
import { selfUpdateDimension, type ProfileDimensionDto } from '@/api/learner';

const { getLearnerProfileMock, getProfileDimensionsMock, selfUpdateDimensionMock } = vi.hoisted(
  () => ({
    getLearnerProfileMock: vi.fn(),
    getProfileDimensionsMock: vi.fn(),
    selfUpdateDimensionMock: vi.fn(),
  }),
);

vi.mock('@/api/learner', () => ({
  getLearnerProfile: getLearnerProfileMock,
  getProfileDimensions: getProfileDimensionsMock,
  selfUpdateDimension: selfUpdateDimensionMock,
  openProfileInterview: vi.fn(),
  sendProfileTurn: vi.fn(),
  finishProfileInterview: vi.fn(),
}));

vi.mock('@/composables/useStardewFx', () => ({ playUiClick: vi.fn() }));

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };
const BLANK_STUB = { template: '<div />' };
const PROFILE = {
  profileId: 1,
  userId: 1,
  packId: 43,
  major: 'CS',
  baseLevel: { programmingYears: 1, languagesKnown: [], pythonSelfRatedLevel: 3 },
  learningGoal: { kind: 'EXAM', deadlineIso: null, targetKcCodes: [] },
  learningPreference: { mediaPriority: ['CODE'], sessionMinutesPerDay: 30 },
  cognitiveStyle: { fieldDependent: 0.6, sequentialVsHolistic: 0.25 },
  narrativeSummary: null,
};

function dims(): ProfileDimensionDto[] {
  return [
    {
      key: 'professional_background', label: '专业背景', score: 0.5, confidence: 0.5,
      evidenceCount: 4, selfEditable: true, tags: [],
    },
    {
      key: 'learning_goal', label: '学习目标', score: 0.5, confidence: 0.3,
      evidenceCount: 2, selfEditable: true, tags: [],
    },
    {
      key: 'learning_preference', label: '学习偏好', score: 0.5, confidence: 0.3,
      evidenceCount: 2, selfEditable: true, tags: [],
    },
    {
      key: 'cognitive_style', label: '认知风格', score: 0.5, confidence: 0.5,
      evidenceCount: 4, selfEditable: false, tags: [],
    },
  ];
}

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/profile', component: ProfilePage },
      { path: '/world', component: BLANK_STUB },
    ],
  });
}

async function mountPage() {
  const router = setupRouter();
  await router.push('/profile');
  await router.isReady();
  const wrapper = mount(ProfilePage, {
    global: {
      plugins: [router],
      stubs: { AppLayout: APP_LAYOUT_STUB, ProfileRadar: BLANK_STUB, MarkdownView: BLANK_STUB },
    },
  });
  await flushPromises();
  return wrapper;
}

describe('ProfilePage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    getLearnerProfileMock.mockReset().mockResolvedValue({ ...PROFILE });
    getProfileDimensionsMock.mockReset().mockResolvedValue(dims());
    selfUpdateDimensionMock
      .mockReset()
      .mockResolvedValue({ userId: 1, dimensionKey: 'professional_background', finalDimension: {} });
  });

  it('shows profile context and only exposes dimensions marked self-editable by the server', async () => {
    const wrapper = await mountPage();

    expect(wrapper.find('[data-testid="dim-major"]').text()).toContain('CS');
    expect(wrapper.find('[data-testid="dim-baseLevel"]').text()).toContain('Python Lv.3');
    expect(wrapper.find('[data-testid="dim-learningGoal"]').text()).toContain('EXAM');
    expect(wrapper.find('[data-testid="self-rate-professional_background"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="self-rate-learning_goal"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="self-rate-learning_preference"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="self-rate-cognitive_style"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="cognitive-style-correct"]').exists()).toBe(false);
  });

  it('shows a retry action when loading profile dimensions fails', async () => {
    getProfileDimensionsMock
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(dims());
    const wrapper = await mountPage();

    expect(wrapper.find('[data-testid="profile-dimensions-error"]').exists()).toBe(true);
    await wrapper.find('[data-testid="profile-dimensions-retry"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="profile-dimensions-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="self-rate-professional_background"]').exists()).toBe(true);
  });

  it('submits a self-rating as a score delta without adding evidence fields', async () => {
    const wrapper = await mountPage();
    const slider = wrapper.get('[data-testid="self-rate-professional_background"] input');
    await slider.setValue('65');
    await slider.trigger('change');
    await flushPromises();

    expect(selfUpdateDimension).toHaveBeenCalledWith('professional_background', 0.15);
  });
});
