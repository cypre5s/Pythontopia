import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import MoodOverlay from '@/components/world/MoodOverlay.vue';
import { useMoodStore } from '@stores/mood';
import { fetchWorldState } from '@/api/learner';

const pushMock = vi.fn();

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock('@/api/learner', () => ({
  fetchWorldState: vi.fn(),
}));

describe('MoodOverlay', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    pushMock.mockReset();
    vi.mocked(fetchWorldState).mockReset();
  });

  it('低情绪（rainy）刷新后弹出助教台阶（含求助/收起/关闭氛围）', async () => {
    vi.mocked(fetchWorldState).mockResolvedValue({ mood: 'rainy', streak: -3, emotionScore: 0.2 });
    const wrapper = mount(MoodOverlay);
    await flushPromises();

    expect(fetchWorldState).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="mood-nudge"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="mood-nudge-help"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="mood-nudge-dismiss"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="mood-nudge-disable"]').exists()).toBe(true);
  });

  it('晴天（sunny）不弹台阶', async () => {
    vi.mocked(fetchWorldState).mockResolvedValue({ mood: 'sunny', streak: 4, emotionScore: 0.9 });
    const wrapper = mount(MoodOverlay);
    await flushPromises();

    expect(wrapper.find('[data-testid="mood-nudge"]').exists()).toBe(false);
  });

  it('点「看看助教」跳百晓工坊并收起台阶', async () => {
    vi.mocked(fetchWorldState).mockResolvedValue({ mood: 'rainy', streak: -3, emotionScore: 0.2 });
    const wrapper = mount(MoodOverlay);
    await flushPromises();

    await wrapper.find('[data-testid="mood-nudge-help"]').trigger('click');

    expect(pushMock).toHaveBeenCalledWith('/workshop');
    expect(wrapper.find('[data-testid="mood-nudge"]').exists()).toBe(false);
  });

  it('点「收起」隐藏台阶但不关闭氛围', async () => {
    vi.mocked(fetchWorldState).mockResolvedValue({ mood: 'rainy', streak: -3, emotionScore: 0.2 });
    const wrapper = mount(MoodOverlay);
    await flushPromises();

    await wrapper.find('[data-testid="mood-nudge-dismiss"]').trigger('click');

    expect(wrapper.find('[data-testid="mood-nudge"]').exists()).toBe(false);
    expect(window.localStorage.getItem('pyt_mood_disabled')).toBeNull();
  });

  it('点「关闭氛围」持久化开关并隐藏台阶', async () => {
    vi.mocked(fetchWorldState).mockResolvedValue({ mood: 'rainy', streak: -3, emotionScore: 0.2 });
    const wrapper = mount(MoodOverlay);
    await flushPromises();

    await wrapper.find('[data-testid="mood-nudge-disable"]').trigger('click');

    expect(window.localStorage.getItem('pyt_mood_disabled')).toBe('1');
    expect(useMoodStore().disabled).toBe(true);
    expect(wrapper.find('[data-testid="mood-nudge"]').exists()).toBe(false);
  });
});
