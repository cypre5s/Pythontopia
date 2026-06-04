import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
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
});
