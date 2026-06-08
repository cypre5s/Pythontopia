import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';

async function mountLayout(meta: Record<string, unknown> = {}) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' }, meta }],
  });
  router.push('/');
  await router.isReady();
  return mount(AppLayout, {
    global: { plugins: [pinia, router], stubs: { AppHeader: true } },
  });
}

describe('AppLayout', () => {
  it('mounts the global companion by default', async () => {
    const wrapper = await mountLayout();
    expect(wrapper.find('[data-testid="companion-fab"]').exists()).toBe(true);
  });

  it('hides the companion when route.meta.hideCompanion is true', async () => {
    const wrapper = await mountLayout({ hideCompanion: true });
    expect(wrapper.find('[data-testid="companion-fab"]').exists()).toBe(false);
  });
});
