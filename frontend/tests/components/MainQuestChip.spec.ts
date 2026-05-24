import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import MainQuestChip from '@/components/hud/MainQuestChip.vue';
import { useMainQuestStore } from '@stores/mainQuest';

describe('MainQuestChip', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
  });

  it('renders chapter + title + progress bar', () => {
    const wrapper = mount(MainQuestChip);
    expect(wrapper.text()).toContain('第 1 章');
    expect(wrapper.text()).toContain('0/5');
    expect(wrapper.find('[data-testid="main-quest-chip"]').exists()).toBe(true);
  });

  it('shows updated chapter when store advances after defeating boss', async () => {
    const store = useMainQuestStore();
    store.defeatBoss(1);
    const wrapper = mount(MainQuestChip);
    expect(wrapper.text()).toContain('第 2 章');
  });

  it('shows correct progress count (i/5)', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.completeSubQuest('1-2');
    const wrapper = mount(MainQuestChip);
    expect(wrapper.text()).toContain('2/5');
  });

  it('chip click toggles panel via store', async () => {
    const store = useMainQuestStore();
    const wrapper = mount(MainQuestChip);
    expect(store.panelOpen).toBe(false);
    await wrapper.find('[data-testid="main-quest-chip"]').trigger('click');
    expect(store.panelOpen).toBe(true);
    await wrapper.find('[data-testid="main-quest-chip"]').trigger('click');
    expect(store.panelOpen).toBe(false);
  });

  it('exposes overall progress percentage', async () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    const wrapper = mount(MainQuestChip);
    // 1/35 ≈ 3% (rounded)
    expect(wrapper.text()).toContain('3%');
  });

  it('aria-label includes chapter / progress / shortcut hint', () => {
    const wrapper = mount(MainQuestChip);
    const button = wrapper.find('[data-testid="main-quest-chip"]');
    const label = button.attributes('aria-label') ?? '';
    expect(label).toMatch(/第 1 章/);
    expect(label).toMatch(/按 Q/);
  });
});
