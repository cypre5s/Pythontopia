import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import ProbeCard from '@/components/world/ProbeCard.vue';
import { useProbeStore } from '@stores/probe';
import { submitProbe } from '@/api/learner';

vi.mock('@/api/learner', () => ({
  submitProbe: vi.fn(async () => undefined),
}));

describe('ProbeCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    vi.mocked(submitProbe).mockReset();
    vi.mocked(submitProbe).mockResolvedValue(undefined);
  });

  it('shows on a fresh watch-phase main quest with three neutral options + skip', () => {
    const wrapper = mount(ProbeCard);
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(true);
    expect(wrapper.findAll('.pyt-probe__option')).toHaveLength(3);
    expect(wrapper.find('[data-testid="probe-option-visual"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="probe-option-logical"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="probe-option-trial"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="probe-skip"]').exists()).toBe(true);
  });

  it('does not show again once the probe was answered', () => {
    useProbeStore().markAnswered('cog_intro');
    const wrapper = mount(ProbeCard);
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
  });

  it('choosing an option submits that choiceId then hides the card', async () => {
    const wrapper = mount(ProbeCard);
    await wrapper.find('[data-testid="probe-option-visual"]').trigger('click');
    expect(submitProbe).toHaveBeenCalledWith('cog_intro', 'visual');
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
  });

  it('skipping marks answered without calling the API and hides the card', async () => {
    const wrapper = mount(ProbeCard);
    await wrapper.find('[data-testid="probe-skip"]').trigger('click');
    expect(submitProbe).not.toHaveBeenCalled();
    expect(useProbeStore().answered.has('cog_intro')).toBe(true);
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
  });

  // ─── ③ fail_react 探针（练习失败后请求弹出 → emotion_persistence）───
  // 各用例先 answer cog_intro，确保「看」阶段探针不与 requested 探针竞争。

  it('shows the requested fail_react probe with retry/hint/skip + dismiss', () => {
    const store = useProbeStore();
    store.markAnswered('cog_intro');
    store.request('fail_react');
    const wrapper = mount(ProbeCard);
    const card = wrapper.find('[data-testid="probe-card"]');
    expect(card.exists()).toBe(true);
    expect(card.attributes('data-probe-key')).toBe('fail_react');
    expect(wrapper.findAll('.pyt-probe__option')).toHaveLength(3);
    expect(wrapper.find('[data-testid="probe-option-retry"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="probe-option-hint"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="probe-option-skip"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="probe-skip"]').exists()).toBe(true);
  });

  it('does not show fail_react until it is requested', () => {
    useProbeStore().markAnswered('cog_intro');
    const wrapper = mount(ProbeCard);
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
  });

  it('appears reactively when fail_react is requested after mount', async () => {
    const store = useProbeStore();
    store.markAnswered('cog_intro');
    const wrapper = mount(ProbeCard);
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
    store.request('fail_react');
    await flushPromises();
    const card = wrapper.find('[data-testid="probe-card"]');
    expect(card.exists()).toBe(true);
    expect(card.attributes('data-probe-key')).toBe('fail_react');
  });

  it.each(['retry', 'hint', 'skip'])(
    'choosing fail_react option "%s" submits that choiceId then hides',
    async (choiceId) => {
      const store = useProbeStore();
      store.markAnswered('cog_intro');
      store.request('fail_react');
      const wrapper = mount(ProbeCard);
      await wrapper.find(`[data-testid="probe-option-${choiceId}"]`).trigger('click');
      expect(submitProbe).toHaveBeenCalledWith('fail_react', choiceId);
      expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
    },
  );

  it('dismissing fail_react marks answered without calling the API', async () => {
    const store = useProbeStore();
    store.markAnswered('cog_intro');
    store.request('fail_react');
    const wrapper = mount(ProbeCard);
    await wrapper.find('[data-testid="probe-skip"]').trigger('click');
    expect(submitProbe).not.toHaveBeenCalled();
    expect(store.answered.has('fail_react')).toBe(true);
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
  });

  it('does not re-show an already answered fail_react even when requested', () => {
    const store = useProbeStore();
    store.markAnswered('cog_intro');
    store.markAnswered('fail_react');
    store.request('fail_react');
    const wrapper = mount(ProbeCard);
    expect(wrapper.find('[data-testid="probe-card"]').exists()).toBe(false);
  });
});
