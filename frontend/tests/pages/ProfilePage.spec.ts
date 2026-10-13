/**
 * ProfilePage 认知风格纠偏（#1 学生纠偏阀）单测。
 *
 * 覆盖：
 *   1. 认知风格卡内渲染 3 选 1 纠偏（先看图解=visual / 先读推理=logical / 先跑例子=trial），
 *      初始按末尾 tag 高亮当前主导风格；
 *   2. 选某风格 → 更新主观自评标签，不增加可信证据，保存回读后高亮反映所选风格。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
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
  cognitiveStyle: { fieldDependent: 0.6, sequentialVsHolistic: 0.25 },
  narrativeSummary: null,
};

function dims(cognitiveTags: string[]): ProfileDimensionDto[] {
  return [
    {
      key: 'cognitive_style',
      label: '认知风格',
      score: 0.5,
      confidence: 0.5,
      evidenceCount: 4,
      selfEditable: true,
      tags: cognitiveTags,
    },
    {
      key: 'learning_goal',
      label: '学习目标',
      score: 0.5,
      confidence: 0.3,
      evidenceCount: 2,
      selfEditable: true,
      tags: [],
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

describe('ProfilePage cognitive_style 纠偏', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    getLearnerProfileMock.mockReset().mockResolvedValue({ ...PROFILE });
    getProfileDimensionsMock.mockReset().mockResolvedValue(dims(['logical']));
    selfUpdateDimensionMock
      .mockReset()
      .mockResolvedValue({ userId: 1, dimensionKey: 'cognitive_style', finalDimension: {} });
  });

  it('renders the 3-choice corrector and highlights the current dominant style', async () => {
    const wrapper = await mountPage();

    expect(wrapper.find('[data-testid="cognitive-style-correct"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="cog-style-visual"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="cog-style-logical"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="cog-style-trial"]').exists()).toBe(true);
    // 当前主导 = 末尾 tag 'logical' → 高亮（primary）；其余非高亮。
    expect(wrapper.find('[data-testid="cog-style-logical"]').classes()).toContain('py-btn--primary');
    expect(wrapper.find('[data-testid="cog-style-visual"]').classes()).not.toContain(
      'py-btn--primary',
    );
  });

  it('shows a retry action when loading profile dimensions fails', async () => {
    getProfileDimensionsMock
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(dims(['logical']));
    const wrapper = await mountPage();

    expect(wrapper.find('[data-testid="profile-dimensions-error"]').exists()).toBe(true);
    await wrapper.find('[data-testid="profile-dimensions-retry"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="profile-dimensions-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="cognitive-style-correct"]').exists()).toBe(true);
  });

  it('renders cognitive style using the profile schema scales', async () => {
    const wrapper = await mountPage();
    const card = wrapper.find('[data-testid="dim-cognitiveStyle"]');

    expect(card.find('.dim-card__value').text()).toBe('喜欢先看全局');
    expect(card.find('.dim-card__detail').text()).toContain('整体型 · 场依存 6/10');
  });

  it('renders negative sequential-versus-holistic values as step-by-step preference', async () => {
    getLearnerProfileMock.mockResolvedValueOnce({
      ...PROFILE,
      cognitiveStyle: { fieldDependent: 0.4, sequentialVsHolistic: -0.25 },
    });
    const wrapper = await mountPage();
    const card = wrapper.find('[data-testid="dim-cognitiveStyle"]');

    expect(card.find('.dim-card__value').text()).toBe('喜欢一步步来');
    expect(card.find('.dim-card__detail').text()).toContain('序列型 · 场依存 4/10');
  });

  it('picking a style cannot increase trusted evidence', async () => {
    const wrapper = await mountPage();
    // 保存后回读：下一次拉取返回 visual 落在末尾（主导）。
    getProfileDimensionsMock.mockResolvedValueOnce(dims(['logical', 'visual']));

    await wrapper.find('[data-testid="cog-style-visual"]').trigger('click');
    await flushPromises();

    expect(selfUpdateDimension).toHaveBeenCalledWith('cognitive_style', {
      scoreDelta: 0.05,
      tagsAdd: ['visual'],
    });
    // 反映已选：visual 成为主导高亮。
    expect(wrapper.find('[data-testid="cog-style-visual"]').classes()).toContain('py-btn--primary');
  });
});
